<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-redact',
  name: 'PDF 涂黑遮盖',
  description: '在 PDF 页面上拖拽绘制方块遮盖敏感内容，支持标准/深度两种模式',
  icon: 'eye-off',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- ====== 顶部工具栏 ====== -->
    <header class="flex items-center gap-3 pb-3 flex-shrink-0 border-b" style="border-color: var(--border-color)">
      <!-- 上传按钮 -->
      <button
        @click="triggerFileInput"
        :disabled="stage === 'processing'"
        class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
        :style="stage === 'noFile'
          ? { background: 'var(--accent-light)', color: 'var(--accent-color)' }
          : { background: 'var(--bg-card)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }"
      >
        <span class="text-base leading-none">📄</span>
        <span v-if="stage === 'noFile'">上传 PDF</span>
        <span v-else class="max-w-[180px] truncate">{{ uploadedFile!.name }}</span>
      </button>

      <template v-if="stage !== 'noFile'">
        <span class="text-xs flex-shrink-0" style="color: var(--text-muted)">{{ formatSize(uploadedFile!.size) }}</span>
        <button
          v-if="stage === 'ready'"
          @click="clearFile"
          class="text-xs underline hover:text-red-500 transition-colors flex-shrink-0"
          style="color: var(--text-muted)">移除</button>

        <div class="flex-1"></div>

        <!-- 页码下拉 -->
        <label class="text-xs flex-shrink-0" style="color: var(--text-secondary)">页码</label>
        <select
          v-model.number="currentPage"
          class="px-2 py-1 text-xs border rounded-md flex-shrink-0"
          style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)"
        >
          <option v-for="p in totalPages" :key="p" :value="p">第 {{ p }} 页 / 共 {{ totalPages }} 页</option>
        </select>

        <span class="text-xs flex-shrink-0" style="color: var(--text-muted)">共 {{ totalRects }} 方块</span>

        <div class="w-px h-5 flex-shrink-0" style="background: var(--border-color)"></div>

        <!-- 颜色选择 -->
        <span class="text-xs flex-shrink-0" style="color: var(--text-secondary)">颜色</span>
        <div class="flex items-center gap-1">
          <button
            v-for="c in colors" :key="c.value"
            @click="selectedColor = c.value"
            class="w-6 h-6 rounded-full border-2 transition-all flex items-center justify-center flex-shrink-0"
            :style="{
              background: c.hex,
              borderColor: selectedColor === c.value ? 'var(--accent-color)' : 'transparent',
              boxShadow: selectedColor === c.value ? '0 0 0 1px var(--accent-color)' : 'none'
            }"
            :title="c.label"
          >
            <span v-if="selectedColor === c.value" class="text-white text-[8px] leading-none">✓</span>
          </button>
        </div>

        <div class="w-px h-5 flex-shrink-0" style="background: var(--border-color)"></div>

        <!-- 预览模式 -->
        <label class="flex items-center gap-1.5 text-xs cursor-pointer flex-shrink-0" style="color: var(--text-primary)">
          <input type="checkbox" v-model="previewMode" class="w-3.5 h-3.5 rounded accent-indigo-500" />
          预览
        </label>

        <!-- 撤销/重做 -->
        <button @click="undo" :disabled="!canUndo || stage !== 'ready'"
          class="px-2 py-1 text-xs rounded border transition-colors flex-shrink-0"
          :style="canUndo && stage === 'ready'
            ? { color: 'var(--text-primary)', borderColor: 'var(--border-color)', background: 'var(--bg-card)' }
            : { color: 'var(--text-muted)', borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }"
        >↩</button>
        <button @click="redo" :disabled="!canRedo || stage !== 'ready'"
          class="px-2 py-1 text-xs rounded border transition-colors flex-shrink-0"
          :style="canRedo && stage === 'ready'
            ? { color: 'var(--text-primary)', borderColor: 'var(--border-color)', background: 'var(--bg-card)' }
            : { color: 'var(--text-muted)', borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }"
        >↪</button>
      </template>
    </header>

    <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

    <!-- ====== 工作区 ====== -->
    <div class="flex-1 overflow-hidden mt-3" ref="workspaceRef">
      <!-- 无文件状态 -->
      <div v-if="stage === 'noFile'" class="h-full flex items-center justify-center">
        <div
          class="border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-4 p-16 cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
          style="border-color: var(--border-color); background: var(--bg-card)"
          @click="triggerFileInput"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop.prevent="handleDrop"
        >
          <span class="text-6xl">🖌️</span>
          <div class="text-center">
            <p class="text-base font-semibold" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-sm mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
          </div>
        </div>
      </div>

      <!-- 处理中 / 错误状态 -->
      <div v-else-if="stage === 'processing'" class="h-full flex items-center justify-center">
        <div class="flex flex-col items-center gap-4">
          <svg class="animate-spin" width="48" height="48" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">PDF 加载中，请稍候...</p>
        </div>
      </div>

      <div v-else-if="stage === 'error'" class="h-full flex items-center justify-center">
        <div class="flex flex-col items-center gap-4 p-12">
          <span class="text-5xl">⚠️</span>
          <p class="text-sm text-center" style="color: #ef4444">{{ errorMsg }}</p>
          <button @click="clearFile" class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">重新上传</button>
        </div>
      </div>

      <!-- Canvas 预览区（ready/done 状态） -->
      <div v-else class="h-full flex flex-col">
        <div class="flex-1 overflow-auto flex justify-center rounded-lg"
          style="background: var(--bg-card-hover)">
          <div class="relative inline-block" ref="canvasContainerRef">
            <canvas ref="pdfCanvasRef" class="shadow-lg block"></canvas>
            <canvas
              ref="overlayCanvasRef"
              class="absolute top-0 left-0"
              :style="overlayCanvasStyle"
              @mousedown="onMouseDown"
            ></canvas>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 底部操作栏 ====== -->
    <footer class="flex items-center justify-between pt-3 flex-shrink-0 border-t" style="border-color: var(--border-color)"
      v-if="stage === 'ready' || stage === 'done'">
      <div class="flex items-center gap-3">
        <label class="text-xs flex-shrink-0" style="color: var(--text-secondary)">遮盖模式</label>
        <select v-model="redactMode"
          class="px-2 py-1 text-xs border rounded-md"
          style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
          <option value="standard">标准遮盖（内容流覆盖）</option>
          <option value="deep">深度遮盖（页面转图片，彻底清除）</option>
        </select>
        <span class="text-[10px]" :style="redactMode === 'deep' ? { color: '#d97706' } : { color: 'var(--text-muted)' }">
          {{ redactMode === 'deep' ? '⚠ 全页转为图片，文字将不可选中' : '提示：选中方块后可用 Delete 键删除，拖拽四角可缩放' }}
        </span>
      </div>

      <button
        @click="confirmAndSubmit"
        :disabled="totalRects === 0"
        class="px-5 py-2 rounded-lg text-sm font-medium text-white transition-colors hover:opacity-90 flex items-center gap-1.5"
        :style="totalRects === 0
          ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }
          : { background: 'var(--accent-color)' }"
      >
        <span>🔒</span> 执行遮盖
      </button>
    </footer>

    <!-- ====== 处理结果（done） ====== -->
    <div v-if="stage === 'done'" class="flex items-center gap-3 mt-3 p-3 rounded-lg flex-shrink-0"
      style="background: var(--bg-card); border: 1px solid var(--border-color)">
      <span class="text-xl">✅</span>
      <span class="text-sm font-medium" style="color: var(--text-primary)">遮盖完成</span>
      <button @click="downloadResult"
        class="px-4 py-1.5 text-xs rounded-lg text-white transition-colors hover:opacity-90"
        style="background: var(--accent-color)">下载遮盖后 PDF</button>
      <button @click="resetToReady"
        class="px-4 py-1.5 text-xs rounded-lg border transition-colors"
        style="border-color: var(--border-color); color: var(--text-primary)">继续编辑</button>
    </div>

    <!-- ====== 提交确认弹窗 ====== -->
    <Teleport to="body">
      <div v-if="showConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
        @click.self="showConfirm = false">
        <div class="rounded-xl shadow-xl border p-6 w-96 max-w-[90vw]"
          style="background: var(--bg-card); border-color: var(--border-color)">
          <h3 class="text-base font-semibold mb-3" style="color: var(--text-primary)">确认遮盖操作</h3>
          <p class="text-sm mb-2" style="color: var(--text-secondary)">
            将对以下 {{ pageRectCount.size }} 页执行遮盖（共 {{ totalRects }} 个方块）：
          </p>
          <ul class="text-sm space-y-1 mb-3 max-h-48 overflow-y-auto" style="color: var(--text-primary)">
            <li v-for="[page, count] in Array.from(pageRectCount.entries())" :key="page">
              第 {{ page }} 页：{{ count }} 个方块
            </li>
          </ul>
          <p class="text-xs mb-4" style="color: var(--text-muted)">
            遮盖模式：{{ redactMode === 'standard' ? '标准遮盖（内容流覆盖）' : '深度遮盖（页面转图片，彻底清除）' }}
          </p>
          <div class="flex justify-end gap-2">
            <button @click="showConfirm = false"
              class="px-4 py-1.5 text-xs rounded-lg border transition-colors"
              style="border-color: var(--border-color); color: var(--text-primary)">取消</button>
            <button @click="doSubmit"
              class="px-4 py-1.5 text-xs rounded-lg text-white transition-colors hover:opacity-90"
              style="background: var(--accent-color)">确认遮盖</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

// ======================== Worker 配置 ========================
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.mjs',
  import.meta.url
).toString()

// ======================== 类型 ========================

type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

interface RectItem {
  id: string
  x: number
  y: number
  w: number
  h: number
  color: string
  page: number
}

// ======================== 颜色预设 ========================

const colors = [
  { value: '#000000', label: '黑色', hex: '#000000' },
  { value: '#FFFFFF', label: '白色', hex: '#FFFFFF' },
  { value: '#808080', label: '灰色', hex: '#808080' },
  { value: '#FF0000', label: '红色', hex: '#FF0000' },
]

// ======================== 核心状态 ========================

const fileInputRef = ref<HTMLInputElement>()
const pdfCanvasRef = ref<HTMLCanvasElement>()
const overlayCanvasRef = ref<HTMLCanvasElement>()
const canvasContainerRef = ref<HTMLDivElement>()
const workspaceRef = ref<HTMLDivElement>()

const uploadedFile = ref<File | null>(null)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')
const redactMode = ref('standard')
const selectedColor = ref('#000000')
const currentPage = ref(1)
const totalPages = ref(0)
const dragOver = ref(false)
const previewMode = ref(false)

// 每页方块数据: Map<pageIndex, RectItem[]>
const pageRects = ref<Map<number, RectItem[]>>(new Map())
// undo/redo 栈 — 存储整个 pageRects 的快照
const undoStack = ref<Map<number, RectItem[]>[]>([])
const redoStack = ref<Map<number, RectItem[]>[]>([])

// 选中状态
const selectedRectId = ref<string | null>(null)

// PDF 实例 + 渲染状态
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let currentRenderTask: pdfjsLib.RenderTask | null = null
let currentScale = 1

// 交互状态
let isDrawing = false
let drawStartX = 0
let drawStartY = 0
let drawEndX = 0
let drawEndY = 0
let draggingRect: RectItem | null = null
let dragOffsetX = 0
let dragOffsetY = 0
let resizingRect: RectItem | null = null
let resizeHandle = ''

// 提交结果
const resultBlob = ref<Blob | null>(null)
const showConfirm = ref(false)

// ======================== 计算属性 ========================

const totalRects = computed(() => {
  let count = 0
  for (const rects of pageRects.value.values()) count += rects.length
  return count
})

const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)

const pageRectCount = computed(() => {
  const map = new Map<number, number>()
  for (const [page, rects] of pageRects.value.entries()) {
    if (rects.length > 0) map.set(page + 1, rects.length)
  }
  return map
})

const currentPageRects = computed(() => pageRects.value.get(currentPage.value - 1) || [])

const overlayCanvasStyle = computed(() => ({
  cursor: previewMode.value ? 'default' : 'crosshair',
  pointerEvents: previewMode.value ? 'none' : 'auto',
}) as Record<string, string>)

// ======================== 文件处理 ========================

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) loadFile(files[0])
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    const f = files[0]
    if (f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')) loadFile(f)
  }
}

function clearFile() {
  uploadedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  if (currentRenderTask) currentRenderTask.cancel()
  pdfDoc = null
  totalPages.value = 0
  currentPage.value = 1
  pageRects.value = new Map()
  undoStack.value = []
  redoStack.value = []
  selectedRectId.value = null
  clearResult()
  stage.value = 'noFile'
}

function clearResult() {
  resultBlob.value = null
  errorMsg.value = ''
}

function resetToReady() {
  clearResult()
  stage.value = 'ready'
}

async function loadFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    errorMsg.value = '文件不能超过 50MB'
    stage.value = 'error'
    return
  }
  uploadedFile.value = file
  errorMsg.value = ''
  pageRects.value = new Map()
  undoStack.value = []
  redoStack.value = []
  selectedRectId.value = null
  stage.value = 'processing'

  try {
    const arrayBuffer = await file.arrayBuffer()
    pdfDoc = await pdfjsLib.getDocument({ data: arrayBuffer }).promise
    totalPages.value = pdfDoc.numPages
    currentPage.value = 1
    stage.value = 'ready'
    // 等待 Vue DOM 更新 + 浏览器布局完成，确保 canvas ref 可用
    await nextTick()
    await new Promise(resolve => requestAnimationFrame(resolve))
    await renderCurrentPage()
  } catch (e: any) {
    errorMsg.value = 'PDF 加载失败: ' + (e.message || '未知错误')
    stage.value = 'error'
  }
}

// ======================== PDF 渲染 ========================

async function renderCurrentPage() {
  if (!pdfDoc || !pdfCanvasRef.value) return
  if (currentRenderTask) currentRenderTask.cancel()

  const page = await pdfDoc.getPage(currentPage.value)
  const containerWidth = (workspaceRef.value?.clientWidth || 800) - 80
  const baseViewport = page.getViewport({ scale: 1 })
  currentScale = containerWidth / baseViewport.width

  const viewport = page.getViewport({ scale: currentScale })
  const canvas = pdfCanvasRef.value
  canvas.width = viewport.width
  canvas.height = viewport.height

  if (overlayCanvasRef.value) {
    overlayCanvasRef.value.width = viewport.width
    overlayCanvasRef.value.height = viewport.height
  }

  currentRenderTask = page.render({ canvas, viewport })
  await currentRenderTask.promise
  currentRenderTask = null

  drawOverlay()
}

/** 绘制方块覆盖层 */
function drawOverlay() {
  const canvas = overlayCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  if (previewMode.value) {
    for (const rect of currentPageRects.value) {
      const sx = rect.x * currentScale
      const sy = rect.y * currentScale
      const sw = rect.w * currentScale
      const sh = rect.h * currentScale
      ctx.fillStyle = rect.color + '40'
      ctx.fillRect(sx, sy, sw, sh)
      ctx.strokeStyle = rect.color
      ctx.lineWidth = 2
      ctx.strokeRect(sx, sy, sw, sh)
    }
  } else {
    for (const rect of currentPageRects.value) {
      const sx = rect.x * currentScale
      const sy = rect.y * currentScale
      const sw = rect.w * currentScale
      const sh = rect.h * currentScale
      ctx.fillStyle = rect.color + 'CC'
      ctx.fillRect(sx, sy, sw, sh)

      if (rect.id === selectedRectId.value) {
        ctx.strokeStyle = '#3b82f6'
        ctx.lineWidth = 2
        ctx.setLineDash([4, 2])
        ctx.strokeRect(sx, sy, sw, sh)
        ctx.setLineDash([])
        const hs = 7
        ctx.fillStyle = '#3b82f6'
        ;[sx, sy, sx + sw - hs, sy, sx, sy + sh - hs, sx + sw - hs, sy + sh - hs]
          .forEach((v, i, arr) => { if (i % 2 === 0) ctx.fillRect(arr[i], arr[i + 1], hs, hs) })
      }
    }
  }
}

/** Canvas 像素坐标 → PDF points 坐标 */
function toCanvasCoords(rect: RectItem) {
  return {
    x: rect.x * currentScale,
    y: rect.y * currentScale,
    w: rect.w * currentScale,
    h: rect.h * currentScale,
  }
}

function getCanvasPos(e: MouseEvent): { x: number; y: number } {
  const canvas = overlayCanvasRef.value!
  const r = canvas.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top }
}

// ======================== 方块命中检测 ========================

function findRectAt(cx: number, cy: number): RectItem | null {
  const rects = currentPageRects.value
  for (let i = rects.length - 1; i >= 0; i--) {
    const r = rects[i]
    const c = toCanvasCoords(r)
    if (cx >= c.x && cx <= c.x + c.w && cy >= c.y && cy <= c.y + c.h) return r
  }
  return null
}

function findHandleAt(cx: number, cy: number): { rect: RectItem; handle: string } | null {
  if (!selectedRectId.value) return null
  const rect = currentPageRects.value.find(r => r.id === selectedRectId.value)
  if (!rect) return null
  const c = toCanvasCoords(rect)
  const hs = 10
  const handles: [number, number, string][] = [
    [c.x, c.y, 'tl'], [c.x + c.w - hs, c.y, 'tr'],
    [c.x, c.y + c.h - hs, 'bl'], [c.x + c.w - hs, c.y + c.h - hs, 'br'],
  ]
  for (const [hx, hy, name] of handles) {
    if (cx >= hx && cx <= hx + hs && cy >= hy && cy <= hy + hs) return { rect, handle: name }
  }
  return null
}

// ======================== 鼠标交互 ========================

function onMouseDown(e: MouseEvent) {
  if (stage.value !== 'ready' || previewMode.value) return
  const pos = getCanvasPos(e)

  // 1. 检查缩放手柄
  const handleHit = findHandleAt(pos.x, pos.y)
  if (handleHit) {
    pushHistory()  // 缩放前保存快照
    resizingRect = handleHit.rect
    resizeHandle = handleHit.handle
    drawStartX = pos.x
    drawStartY = pos.y
    return
  }

  // 2. 检查方块点击（拖动移动）
  const hitRect = findRectAt(pos.x, pos.y)
  if (hitRect) {
    pushHistory()  // 拖动前保存快照，确保 undo 能还原位置
    selectedRectId.value = hitRect.id
    draggingRect = hitRect
    const c = toCanvasCoords(hitRect)
    dragOffsetX = pos.x - c.x
    dragOffsetY = pos.y - c.y
    drawOverlay()
    return
  }

  // 3. 新绘制
  selectedRectId.value = null
  isDrawing = true
  drawStartX = pos.x
  drawStartY = pos.y
  drawEndX = pos.x
  drawEndY = pos.y
  drawOverlay()
}

function onMouseMove(e: MouseEvent) {
  const pos = getCanvasPos(e)

  if (isDrawing) {
    drawEndX = pos.x
    drawEndY = pos.y
    drawOverlay()
    const ctx = overlayCanvasRef.value?.getContext('2d')
    if (ctx) {
      const x = Math.min(drawStartX, drawEndX)
      const y = Math.min(drawStartY, drawEndY)
      const w = Math.abs(drawEndX - drawStartX)
      const h = Math.abs(drawEndY - drawStartY)
      ctx.fillStyle = selectedColor.value + '60'
      ctx.fillRect(x, y, w, h)
      ctx.strokeStyle = selectedColor.value
      ctx.lineWidth = 2
      ctx.strokeRect(x, y, w, h)
    }
  } else if (draggingRect) {
    const nc = toCanvasCoords(draggingRect)
    const newX = pos.x - dragOffsetX
    const newY = pos.y - dragOffsetY
    updateRect(draggingRect.id, {
      x: Math.max(0, newX / currentScale),
      y: Math.max(0, newY / currentScale),
    })
    drawOverlay()
  } else if (resizingRect) {
    const dx = (pos.x - drawStartX) / currentScale
    const dy = (pos.y - drawStartY) / currentScale
    let { x, y, w, h } = resizingRect
    if (resizeHandle.includes('l')) { x = resizingRect.x + dx; w = resizingRect.w - dx }
    if (resizeHandle.includes('r')) { w = resizingRect.w + dx }
    if (resizeHandle.includes('t')) { h = resizingRect.h - dy }
    if (resizeHandle.includes('b')) { y = resizingRect.y + dy; h = resizingRect.h + dy }
    if (w < 5) w = 5
    if (h < 5) h = 5
    updateRect(resizingRect.id, { x: Math.max(0, x), y: Math.max(0, y), w, h })
    drawStartX = pos.x
    drawStartY = pos.y
    drawOverlay()
  }
}

function onMouseUp(e: MouseEvent) {
  if (isDrawing) {
    isDrawing = false
    const pos = getCanvasPos(e)
    const cx = Math.min(drawStartX, pos.x)
    const cy = Math.min(drawStartY, pos.y)
    const cw = Math.abs(pos.x - drawStartX)
    const ch = Math.abs(pos.y - drawStartY)

    if (cw >= 5 && ch >= 5) {
      pushHistory()
      const newRect: RectItem = {
        id: 'r_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
        x: Math.max(0, cx / currentScale),
        y: Math.max(0, cy / currentScale),
        w: cw / currentScale,
        h: ch / currentScale,
        color: selectedColor.value,
        page: currentPage.value - 1,
      }
      addRect(newRect)
      selectedRectId.value = newRect.id
    }
    drawOverlay()
  } else {
    draggingRect = null
    resizingRect = null
    resizeHandle = ''
  }
}

// ======================== 方块操作 ========================

function addRect(rect: RectItem) {
  const next = new Map(pageRects.value)
  const arr = next.get(rect.page) || []
  next.set(rect.page, [...arr, rect])
  pageRects.value = next
}

function updateRect(id: string, patch: Partial<Pick<RectItem, 'x' | 'y' | 'w' | 'h'>>) {
  const next = new Map(pageRects.value)
  for (const [page, rects] of next.entries()) {
    const idx = rects.findIndex(r => r.id === id)
    if (idx >= 0) {
      const updated = [...rects]
      updated[idx] = { ...updated[idx], ...patch }
      next.set(page, updated)
      pageRects.value = next
      return
    }
  }
}

function deleteSelected() {
  if (!selectedRectId.value) return
  pushHistory()
  const next = new Map(pageRects.value)
  for (const [page, rects] of next.entries()) {
    const idx = rects.findIndex(r => r.id === selectedRectId.value)
    if (idx >= 0) {
      const updated = [...rects]
      updated.splice(idx, 1)
      next.set(page, updated)
      break
    }
  }
  selectedRectId.value = null
  pageRects.value = next
  drawOverlay()
}

// ======================== Undo / Redo ========================

/** 深度拷贝 pageRects Map（数组也拷贝，确保历史快照独立） */
function clonePageRects(source: Map<number, RectItem[]>): Map<number, RectItem[]> {
  const copy = new Map<number, RectItem[]>()
  for (const [page, rects] of source.entries()) {
    copy.set(page, rects.map(r => ({ ...r })))
  }
  return copy
}

function pushHistory() {
  undoStack.value.push(clonePageRects(pageRects.value))
  redoStack.value = []
}

function undo() {
  if (!canUndo.value) return
  redoStack.value.push(clonePageRects(pageRects.value))
  pageRects.value = undoStack.value.pop()!
  selectedRectId.value = null
  drawOverlay()
}

function redo() {
  if (!canRedo.value) return
  undoStack.value.push(clonePageRects(pageRects.value))
  pageRects.value = redoStack.value.pop()!
  selectedRectId.value = null
  drawOverlay()
}

// ======================== 提交 ========================

function confirmAndSubmit() {
  if (totalRects.value === 0) return
  showConfirm.value = true
}

async function doSubmit() {
  showConfirm.value = false
  if (!uploadedFile.value) return

  clearResult()
  stage.value = 'processing'

  try {
    const allRects: any[] = []
    for (const [page, rects] of pageRects.value.entries()) {
      for (const r of rects) {
        allRects.push({ page, x: r.x, y: r.y, w: r.w, h: r.h, color: r.color })
      }
    }

    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('mode', redactMode.value)
    formData.append('rects', JSON.stringify(allRects))

    const baseUrl = window.location.origin
    const resp = await fetch(`${baseUrl}/api/pdf/redact`, {
      method: 'POST',
      body: formData,
    })

    if (!resp.ok) {
      const errText = await resp.text()
      throw new Error(errText || `HTTP ${resp.status}`)
    }

    resultBlob.value = await resp.blob()
    stage.value = 'done'
  } catch (e: any) {
    errorMsg.value = e.message || '遮盖处理失败'
    stage.value = 'error'
  }
}

function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = uploadedFile.value ? 'redacted-' + uploadedFile.value.name : 'redacted.pdf'
  a.click()
  URL.revokeObjectURL(url)
}

// ======================== 键盘事件 ========================

function onKeyDown(e: KeyboardEvent) {
  if (stage.value !== 'ready') return
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedRectId.value) {
    e.preventDefault()
    deleteSelected()
  }
  if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
    e.preventDefault()
    e.shiftKey ? redo() : undo()
  }
}

// ======================== 工具方法 ========================

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

// ======================== 生命周期 ========================

watch(currentPage, async () => {
  selectedRectId.value = null
  await renderCurrentPage()
})

onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  if (currentRenderTask) currentRenderTask.cancel()
})
</script>

<style scoped>
.hidden { display: none; }
</style>
