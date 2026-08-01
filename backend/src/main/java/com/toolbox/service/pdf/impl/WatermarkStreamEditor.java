package com.toolbox.service.pdf.impl;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF 去水印 — 内容流编辑引擎
 * <p>
 * 以"手动 token 扫描"方式遍历页面内容流，删除【渲染 bbox 与选中框相交】的
 * 文字绘制操作符（Tj / TJ / ' / "）与图片绘制操作符（Do），
 * 从而实现"只抠除水印、保留下方正文、保持矢量质量"。
 * <p>
 * 设计前提（与产品一致）：用户手动框选即认定"框内即水印"，因此不需要水印识别，
 * 只需删除与框相交的绘制指令。
 * <p>
 * v1 限制（命中这些场景时相应框上报 removed=false）：
 * <ul>
 *   <li>旋转页面（页面旋转 / 裁剪框偏移）下坐标可能偏移，文本起点判定位可能失准；</li>
 *   <li>深层 Form XObject 嵌套内容：v1 仅按 CTM 判 Do 的整体 bbox，不递归进 form 内部；</li>
 *   <li>矢量路径 / 线条水印（④）与栅格化背景水印不在本引擎处理范围。</li>
 * </ul>
 *
 * @author toolbox
 * @since 2026-08-01
 */
public class WatermarkStreamEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatermarkStreamEditor.class);

    /**
     * 选中框（PDF 用户空间坐标，原点左下角，单位 point）
     */
    public static class RegionBox {
        private final float x;
        private final float y;
        private final float w;
        private final float h;

        public RegionBox(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** 点是否落在框内（含边界） */
        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        /** 两个轴对齐矩形是否相交 */
        boolean intersects(RegionBox other) {
            return this.x < other.x + other.w
                    && this.x + this.w > other.x
                    && this.y < other.y + other.h
                    && this.y + this.h > other.y;
        }
    }

    /** 选中框列表 */
    private final List<RegionBox> boxes;

    /** 每个框是否成功删除了内容（与 boxes 下标一一对应） */
    private final boolean[] removed;

    /**
     * 构造引擎
     *
     * @param boxes 选中框列表（PDF 用户空间坐标）
     */
    public WatermarkStreamEditor(List<RegionBox> boxes) {
        this.boxes = boxes;
        this.removed = new boolean[boxes.size()];
    }

    /**
     * 处理单个页面：删除框内文字/图片绘制操作符，并将结果写回页面内容流
     *
     * @param doc  当前打开的 PDF 文档
     * @param page 目标页面
     * @throws IOException 内容流解析/写回失败
     */
    /**
     * 返回每个框是否成功删除内容（与构造传入的 boxes 下标一一对应）
     *
     * @return 每个框的删除结果标志数组
     */
    public boolean[] getRemovedFlags() {
        return removed;
    }

    /**
     * 处理单个页面：删除框内文字/图片绘制操作符，并将结果写回页面内容流
     *
     * @param doc  当前打开的 PDF 文档
     * @param page 目标页面
     * @throws IOException 内容流解析/写回失败
     */
    public void processPage(PDDocument doc, PDPage page) throws IOException {
        if (page.getContents() == null) {
            return;
        }
        PDFStreamParser parser = new PDFStreamParser((PDContentStream) page);
        List<Object> tokens = parser.parse();

        List<Object> newTokens = new ArrayList<>();
        List<COSBase> operands = new ArrayList<>();

        // 可变状态持有者（跨循环/方法更新）
        Deque<Matrix> ctmStack = new ArrayDeque<>();
        Matrix[] ctm = { new Matrix() };
        Matrix[] textMatrix = { new Matrix() };
        Matrix[] lineMatrix = { new Matrix() };
        float[] leading = { 0f };

        PDResources resources = page.getResources();

        for (Object token : tokens) {
            if (token instanceof Operator op) {
                boolean drop = shouldDrop(op, operands, textMatrix[0], ctm[0], resources);
                if (!drop) {
                    newTokens.addAll(operands);
                    newTokens.add(op);
                }
                applyState(op, operands, ctmStack, ctm, textMatrix, lineMatrix, leading);
                operands.clear();
            } else if (token instanceof COSBase cb) {
                operands.add(cb);
            }
        }

        // 有删除才写回，避免空写损坏页面
        if (hasRemovedAny()) {
            writeBack(doc, page, newTokens);
        }
    }

    /**
     * 是否删除某操作符：文字起点落在框内，或图片 bbox 与框相交
     */
    private boolean shouldDrop(Operator op, List<COSBase> operands,
                               Matrix textMatrix, Matrix ctm, PDResources resources) {
        String name = op.getName();
        if (isTextShow(name)) {
            return dropTextAt(textMatrix);
        }
        if (OperatorName.DRAW_OBJECT.equals(name)) {
            return dropObjectAt(operands, ctm, resources);
        }
        return false;
    }

    /**
     * 判断是否为文本显示操作符
     */
    private static boolean isTextShow(String name) {
        return OperatorName.SHOW_TEXT.equals(name)
                || OperatorName.SHOW_TEXT_ADJUSTED.equals(name)
                || OperatorName.SHOW_TEXT_LINE.equals(name)
                || OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(name);
    }

    /**
     * 文字绘制：文本起点（textMatrix 原点）落在任一框内则删除，并标记对应框 removed
     */
    private boolean dropTextAt(Matrix textMatrix) {
        Point2D.Float p = textMatrix.transformPoint(0, 0);
        boolean hit = false;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(p.x, p.y)) {
                removed[i] = true;
                hit = true;
            }
        }
        return hit;
    }

    /**
     * 图片绘制（Do）：用当前 CTM 映射单位正方形得到图片 bbox，
     * 与任一框相交则删除该 Do 及操作数
     */
    private boolean dropObjectAt(List<COSBase> operands, Matrix ctm, PDResources resources) {
        if (operands.isEmpty() || !(operands.get(0) instanceof COSName name)) {
            return false;
        }
        if (resources == null) {
            return false;
        }
        PDXObject xobj;
        try {
            xobj = resources.getXObject(name);
        } catch (IOException e) {
            LOGGER.warn("[WatermarkStreamEditor#dropObjectAt] resolve XObject {} failed", name.getName());
            return false;
        }
        if (xobj == null) {
            return false;
        }
        // 用 CTM 映射单位正方形 [0,1]x[0,1] 得到图片区域
        Point2D.Float p0 = ctm.transformPoint(0, 0);
        Point2D.Float p1 = ctm.transformPoint(1, 1);
        float ix = Math.min(p0.x, p1.x);
        float iy = Math.min(p0.y, p1.y);
        float iw = Math.abs(p1.x - p0.x);
        float ih = Math.abs(p1.y - p0.y);
        if (iw <= 0 || ih <= 0) {
            return false;
        }
        RegionBox imgBox = new RegionBox(ix, iy, iw, ih);
        boolean hit = false;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).intersects(imgBox)) {
                removed[i] = true;
                hit = true;
            }
        }
        return hit;
    }

    /**
     * 更新文本/图形状态，用于持续追踪文本起点与 CTM。
     * <p>
     * 文本矩阵与图形状态相互独立：q/Q 仅影响 CTM，BT、Td、TD、Tm、T* 与 Tf 影响文本状态。
     */
    private void applyState(Operator op, List<COSBase> operands, Deque<Matrix> ctmStack,
                            Matrix[] ctm, Matrix[] textMatrix, Matrix[] lineMatrix, float[] leading) {
        String name = op.getName();
        switch (name) {
            case OperatorName.SAVE -> ctmStack.push(ctm[0]);
            case OperatorName.RESTORE -> {
                if (!ctmStack.isEmpty()) {
                    ctm[0] = ctmStack.pop();
                }
            }
            case OperatorName.CONCAT -> {
                Matrix m = matrixOf6(operands);
                if (m != null) {
                    ctm[0] = m.multiply(ctm[0]);
                }
            }
            case OperatorName.BEGIN_TEXT -> {
                textMatrix[0] = new Matrix();
                lineMatrix[0] = new Matrix();
            }
            case OperatorName.MOVE_TEXT, OperatorName.MOVE_TEXT_SET_LEADING -> {
                float tx = floatAt(operands, 0);
                float ty = floatAt(operands, 1);
                Matrix t = new Matrix(1, 0, 0, 1, tx, ty);
                lineMatrix[0] = t.multiply(lineMatrix[0]);
                textMatrix[0] = lineMatrix[0];
                if (OperatorName.MOVE_TEXT_SET_LEADING.equals(name)) {
                    leading[0] = -ty;
                }
            }
            case OperatorName.SET_MATRIX -> {
                Matrix m = matrixOf6(operands);
                if (m != null) {
                    textMatrix[0] = m;
                    lineMatrix[0] = m;
                }
            }
            case OperatorName.SET_TEXT_LEADING -> leading[0] = floatAt(operands, 0);
            case OperatorName.NEXT_LINE -> {
                Matrix t = new Matrix(1, 0, 0, 1, 0, -leading[0]);
                lineMatrix[0] = t.multiply(lineMatrix[0]);
                textMatrix[0] = lineMatrix[0];
            }
            default -> { }
        }
    }

    /**
     * 从 6 个操作数构建变换矩阵（cm / Tm），操作数为独立 COSNumber
     */
    private static Matrix matrixOf6(List<COSBase> operands) {
        if (operands.size() < 6) {
            return null;
        }
        return new Matrix(floatAt(operands, 0), floatAt(operands, 1), floatAt(operands, 2),
                floatAt(operands, 3), floatAt(operands, 4), floatAt(operands, 5));
    }

    /**
     * 读取操作数为 float，缺失或类型不合法返回 0
     */
    private static float floatAt(List<COSBase> operands, int index) {
        if (index >= operands.size() || !(operands.get(index) instanceof COSNumber num)) {
            return 0f;
        }
        return num.floatValue();
    }

    private boolean hasRemovedAny() {
        for (boolean b : removed) {
            if (b) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将过滤后的 token 写入新的内容流并替换页面内容
     */
    private void writeBack(PDDocument doc, PDPage page, List<Object> newTokens) throws IOException {
        PDStream newStream = new PDStream(doc);
        try (OutputStream out = newStream.createOutputStream()) {
            ContentStreamWriter writer = new ContentStreamWriter(out);
            writer.writeTokens(newTokens);
        }
        page.setContents(newStream);
    }
}
