<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-dewatermark',
  name: 'PDF 去水印',
  description: '框选 PDF 水印区域，删除框内文字/图片，保留下方正文（矢量无损），支持应用到所有页',
  icon: '🧼',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- ====== 顶部工具栏 ====== -->
    <header class="flex items-center gap-3 pb-3 flex-shrink-0 border-b" style="border-color: var(--border-color)">
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
        <button v-if="stage !== 'processing'" @click="clearFile"
          class="text-xs underline hover:text-red-500 transition-colors flex-shrink-0"
          style="color: var(--text-muted)">移除</button>

        <div class="flex-1"></div>

        <span class="text-xs flex-shrink-0" style="color: var(--text-muted)">共 {{ totalPages }} 页 · {{ totalRects }} 区域</span>

        <div class="w-px h-5 flex-shrink-0" style="background: var(--border-color)"></div>

        <label class="flex items-center gap-1.5 text-xs cursor-pointer flex-shrink-0" style="color: var(--text-primary)">
          <input type="checkbox" v-model="previewMode" class="w-3.5 h-3.5 rounded accent-indigo-500" @change="redrawAllOverlays" />
          预览
        </label>

        <button @click="undo" :disabled="!canUndo || stage !== 'ready'"
          class="px-2 py-1 text-xs rounded border transition-colors flex-shrink-0"
          :style="canUndo && stage === 'ready'
            ? { color: 'var(--text-primary)', borderColor: 'var(--border-color)', background: 'var(--bg-card)' }
            : { color: 'var(--text-muted)', borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }">↩</button>
        <button @click="redo" :disabled="!canRedo || stage !== 'ready'"
          class="px-2 py-1 text-xs rounded border transition-colors flex-shrink-0"
          :style="canRedo && stage === 'ready'
            ? { color: 'var(--text-primary)', borderColor: 'var(--border-color)', background: 'var(--bg-card)' }
            : { color: 'var(--text-muted)', borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }">↪</button>
      </template>
    </header>

    <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

    <!-- ====== 工作区 ====== -->
    <div class="flex-1 overflow-hidden mt-3" ref="workspaceRef">
      <!-- 无文件状态 -->
      <div v-if="stage === 'noFile'" class="h-full flex items-center justify-center">
        <div class="border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-4 p-16 cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
          style="border-color: var(--border-color); background: var(--bg-card)"
          @click="triggerFileInput" @dragover.prevent="dragOver = true" @dragleave.prevent="dragOver = false" @drop.prevent="handleDrop">
          <span class="text-6xl">🧼</span>
          <div class="text-center">
            <p class="text-base font-semibold" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-sm mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
          </div>
        </div>
      </div>

      <!-- 处理中 -->
      <div v-else-if="stage === 'processing'" class="h-full flex items-center justify-center">
        <div class="flex flex-col items-center gap-4">
          <svg class="animate-spin" width="48" height="48" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">{{ processingLabel }}</p>
        </div>
      </div>

      <!-- 错误 -->
      <div v-else-if="stage === 'error'" class="h-full flex items-center justify-center">
        <div class="flex flex-col items-center gap-4 p-12">
          <span class="text-5xl">⚠️</span>
          <p class="text-sm text-center" style="color: #ef4444">{{ errorMsg }}</p>
          <button @click="clearFile" class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">重新上传</button>
        </div>
      </div>

      <!-- 所有页面纵向滚动预览 -->
      <div v-else class="h-full overflow-y-auto rounded-lg" style="background: var(--bg-card-hover)">
        <div class="flex flex-col items-center gap-6 py-6">
          <div v-for="pageIndex in totalPages" :key="pageIndex"
            class="relative inline-block shadow-lg" style="background: #fff">
            <canvas :ref="(el) => setPdfCanvas(pageIndex - 1, el as HTMLCanvasElement)" class="block"></canvas>
            <canvas :ref="(el) => setOverlayCanvas(pageIndex - 1, el as HTMLCanvasElement)"
              class="absolute top-0 left-0"
              :style="{ cursor: previewMode ? 'default' : 'crosshair', pointerEvents: previewMode ? 'none' : 'auto' }"
              @mousedown="(e) => onPageMouseDown(e, pageIndex - 1)"
            ></canvas>
            <div class="absolute bottom-1 left-1/2 -translate-x-1/2 text-[10px] px-2 py-0.5 rounded-full bg-black/40 text-white">
              第 {{ pageIndex }} 页
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 底部操作栏 ====== -->
    <footer class="flex items-center justify-between pt-3 flex-shrink-0 border-t" style="border-color: var(--border-color)"
      v-if="stage === 'ready' || stage === 'done'">
      <div class="flex items-center gap-3">
        <label class="text-xs flex-shrink-0" style="color: var(--text-secondary)">应用到</label>
        <select v-model="applyTo" class="px-2 py-1 text-xs border rounded-md"
          style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
          <option value="all">所有页（相同位置）</option>
          <option value="page">仅当前页</option>
        </select>
        <span class="text-[10px]" style="color: var(--text-muted)">
          提示：选中区域后可 Delete 删除，拖拽四角缩放
        </span>
      </div>
      <button @click="confirmAndSubmit" :disabled="totalRects === 0"
        class="px-5 py-2 rounded-lg text-sm font-medium text-white transition-colors hover:opacity-90 flex items-center gap-1.5"
        :style="totalRects === 0 ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' } : { background: 'var(--accent-color)' }">
        <span>🧼</span> 去除水印
      </button>
    </footer>

    <!-- ====== 处理结果 ====== -->
    <div v-if="stage === 'done'" class="flex items-center gap-3 mt-3 p-3 rounded-lg flex-shrink-0"
      style="background: var(--bg-card); border: 1px solid var(--border-color)">
      <span class="text-xl">✅</span>
      <div class="flex-1 min-w-0">
        <span class="text-sm font-medium" style="color: var(--text-primary)">处理完成：{{ removedCount }} 个区域已去除</span>
        <p v-if="failedRects.length > 0" class="text-xs mt-1" style="color: #ef4444">
          ⚠ {{ failedRects.length }} 个区域未能自动去除（已标红），可调整框选后重试
        </p>
      </div>
      <button @click="downloadResult" class="px-4 py-1.5 text-xs rounded-lg text-white transition-colors hover:opacity-90"
        style="background: var(--accent-color)">下载去水印后 PDF</button>
      <button @click="resetToReady" class="px-4 py-1.5 text-xs rounded-lg border transition-colors"
        style="border-color: var(--border-color); color: var(--text-primary)">继续编辑</button>
    </div>

    <!-- ====== 提交确认弹窗 ====== -->
    <Teleport to="body">
      <div v-if="showConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30" @click.self="showConfirm = false">
        <div class="rounded-xl shadow-xl border p-6 w-96 max-w-[90vw]" style="background: var(--bg-card); border-color: var(--border-color)">
          <h3 class="text-base font-semibold mb-3" style="color: var(--text-primary)">确认去水印操作</h3>
          <p class="text-sm mb-2" style="color: var(--text-secondary)">
            将对以下 {{ pageRectCount.size }} 页执行去除（共 {{ totalRects }} 个区域）：
          </p>
          <ul class="text-sm space-y-1 mb-3 max-h-48 overflow-y-auto" style="color: var(--text-primary)">
            <li v-for="[page, count] in Array.from(pageRectCount.entries())" :key="page">第 {{ page }} 页：{{ count }} 个区域</li>
          </ul>
          <p class="text-xs mb-4" style="color: var(--text-muted)">
            应用范围：{{ applyTo === 'all' ? '所有页（相同位置）' : '仅当前页' }} · 将删除框内文字/图片，保留下方正文
          </p>
          <div class="flex justify-end gap-2">
            <button @click="showConfirm = false" class="px-4 py-1.5 text-xs rounded-lg border transition-colors"
              style="border-color: var(--border-color); color: var(--text-primary)">取消</button>
            <button @click="doSubmit" class="px-4 py-1.5 text-xs rounded-lg text-white transition-colors hover:opacity-90"
              style="background: var(--accent-color)">确认去除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

// ======================== pdfjs-dist v6 兼容补丁 ========================
// v6 的 calculateMD5/stringToBytes 返回普通 Uint8Array，
// 但内部代码期望有 .toHex() 方法（v3/v4 的自定义字节数组有该方法）
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const proto = Uint8Array.prototype as any
if (!proto.toHex) {
  proto.toHex = function (this: Uint8Array): string {
    return Array.from(this).map(b => b.toString(16).padStart(2, '0')).join('')
  }
}
// pdfjs v6 依赖 ES2025 的 Map.prototype.getOrInsertComputed，旧版 Chrome(<130) 缺失需补齐
const mapProto = Map.prototype as any
if (!mapProto.getOrInsertComputed) {
  mapProto.getOrInsertComputed = function (k: unknown, cb: (k: unknown, m: Map<unknown, unknown>) => unknown) {
    if (this.has(k)) return this.get(k)
    const v = cb(k, this)
    this.set(k, v)
    return v
  }
}
if (!mapProto.getOrInsert) {
  mapProto.getOrInsert = function (k: unknown, v: unknown) {
    if (this.has(k)) return this.get(k)
    this.set(k, v)
    return v
  }
}

// ======================== Worker 配置 ========================
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).toString()

// 配置 CMap 路径，让 pdfjs-dist 能处理 CJK 等 CID 字体
// cmaps 目录由 vite.config.ts 中的 copy-pdfjs-cmaps 插件在 build 时复制到 assets/cmaps/
const CMAP_URL = new URL('/assets/cmaps/', window.location.origin).toString()

// ======================== 类型 ========================

type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

interface RectItem {
  id: string
  x: number
  y: number
  w: number
  h: number
  page: number
}

// ======================== 核心状态 ========================

const fileInputRef = ref<HTMLInputElement>()
const workspaceRef = ref<HTMLDivElement>()

const uploadedFile = ref<File | null>(null)
const stage = ref<Stage>('noFile')
const processingLabel = ref('PDF 加载中，请稍候...')
const errorMsg = ref('')
const applyTo = ref('all')
const totalPages = ref(0)
const dragOver = ref(false)
const previewMode = ref(false)

// 每页区域数据 + 每页 canvas 引用
const pageRects = ref<Map<number, RectItem[]>>(new Map())
const pdfCanvases: HTMLCanvasElement[] = []
const overlayCanvases: HTMLCanvasElement[] = []
const pageScales: number[] = []

// undo/redo 栈
const undoStack = ref<Map<number, RectItem[]>[]>([])
const redoStack = ref<Map<number, RectItem[]>[]>([])

const selectedRectId = ref<string | null>(null)

// 处理结果：未去除的区域（标红）
const failedRects = ref<{ page: number; x: number; y: number; w: number; h: number }[]>([])

// PDF 实例
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null

// 交互状态
let activePage = -1
let isDrawing = false
let drawStartX = 0
let drawStartY = 0
let draggingRect: RectItem | null = null
let dragOffsetX = 0
let dragOffsetY = 0
let resizingRect: RectItem | null = null
let resizeHandle = ''

// 提交结果 + 渲染降级
const resultBlob = ref<Blob | null>(null)
const showConfirm = ref(false)
let fileArrayBuffer: ArrayBuffer | null = null  // 保留文件数据用于后端降级渲染
let originalFilename = ''  // 原始文件名，用于后端渲染 FormData
const pageImages: (HTMLImageElement | null)[] = []  // 后端渲染的图片（替代 canvas）

// ======================== Canvas 引用管理 ========================

function setPdfCanvas(index: number, el: HTMLCanvasElement) {
  pdfCanvases[index] = el
}
function setOverlayCanvas(index: number, el: HTMLCanvasElement) {
  overlayCanvases[index] = el
}

function getOverlay(pageIndex: number): HTMLCanvasElement | undefined {
  return overlayCanvases[pageIndex]
}

function getPdfCanvas(pageIndex: number): HTMLCanvasElement | undefined {
  return pdfCanvases[pageIndex]
}

// ======================== 计算属性 ========================

const totalRects = computed(() => {
  let count = 0
  for (const rects of pageRects.value.values()) count += rects.length
  return count
})

const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)

const removedCount = computed(() => totalRects.value - failedRects.value.length)

const pageRectCount = computed(() => {
  const map = new Map<number, number>()
  for (const [page, rects] of pageRects.value.entries()) {
    if (rects.length > 0) map.set(page + 1, rects.length)
  }
  return map
})

function getPageRectItems(pageIndex: number): RectItem[] {
  return pageRects.value.get(pageIndex) || []
}

// ======================== 文件处理 ========================

function triggerFileInput() { fileInputRef.value?.click() }

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
  pdfDoc = null
  totalPages.value = 0
  pdfCanvases.length = 0
  overlayCanvases.length = 0
  pageScales.length = 0
  pageRects.value = new Map()
  undoStack.value = []
  redoStack.value = []
  selectedRectId.value = null
  failedRects.value = []
  clearResult()
  stage.value = 'noFile'
}

function clearResult() {
  resultBlob.value = null
  errorMsg.value = ''
}

function resetToReady() {
  clearResult()
  failedRects.value = []
  stage.value = 'ready'
  // done 后 canvas 被重置了，需要重新渲染
  nextTick().then(() => new Promise(r => requestAnimationFrame(r)).then(() => renderAllPages()))
}

async function loadFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    errorMsg.value = '文件不能超过 50MB'
    stage.value = 'error'
    return
  }
  uploadedFile.value = file
  originalFilename = file.name
  errorMsg.value = ''
  pageRects.value = new Map()
  undoStack.value = []
  redoStack.value = []
  selectedRectId.value = null
  failedRects.value = []
  processingLabel.value = 'PDF 加载中，请稍候...'
  stage.value = 'processing'

  try {
    fileArrayBuffer = await file.arrayBuffer()
    // slice(0) 复制一份——pdfjs getDocument 会将 ArrayBuffer 转移到 Worker 导致原 buffer 被清空，
    // 后续空白页回退渲染 render-page 还需复用 fileArrayBuffer
    pdfDoc = await pdfjsLib.getDocument({
      data: fileArrayBuffer.slice(0),
      cMapUrl: CMAP_URL,
      cMapPacked: true,
    }).promise
    totalPages.value = pdfDoc.numPages
    stage.value = 'ready'
    await nextTick()
    await new Promise(resolve => requestAnimationFrame(resolve))
    await renderAllPages()
    // 检测 pdfjs-dist 渲染白页，自动走后端降级
    await checkAndFallbackRender()
  } catch (e: any) {
    errorMsg.value = 'PDF 加载失败: ' + (e.message || '未知错误')
    stage.value = 'error'
  }
}

// ======================== PDF 渲染（全页） ========================

async function renderAllPages() {
  if (!pdfDoc) return

  const containerWidth = (workspaceRef.value?.clientWidth || 800) - 80
  const errors: string[] = []

  for (let i = 0; i < totalPages.value; i++) {
    processingLabel.value = `渲染中 ${i + 1}/${totalPages.value} 页...`
    const page = await pdfDoc.getPage(i + 1)
    const baseViewport = page.getViewport({ scale: 1 })
    const scale = containerWidth / baseViewport.width
    pageScales[i] = scale
    const viewport = page.getViewport({ scale })

    const pdfCanvas = getPdfCanvas(i)
    const overlayCanvas = getOverlay(i)
    if (!pdfCanvas || !overlayCanvas) continue

    pdfCanvas.width = viewport.width
    pdfCanvas.height = viewport.height
    overlayCanvas.width = viewport.width
    overlayCanvas.height = viewport.height

    try {
      // 单页渲染超时 30s，防止巨型嵌入图像导致永久 hang
      await Promise.race([
        page.render({ canvas: pdfCanvas, viewport }).promise,
        new Promise((_, reject) => setTimeout(() => reject(new Error('render timeout')), 30000)),
      ])
    } catch (e: any) {
      errors.push(`第 ${i + 1} 页: ${e.message || '未知错误'}`)
      // 渲染失败时显示提示文字在 canvas 上
      const ctx = pdfCanvas.getContext('2d')
      if (ctx) {
        ctx.fillStyle = '#fef2f2'
        ctx.fillRect(0, 0, pdfCanvas.width, pdfCanvas.height)
        ctx.fillStyle = '#ef4444'
        ctx.font = '14px sans-serif'
        ctx.textAlign = 'center'
        ctx.fillText('页面渲染失败', pdfCanvas.width / 2, pdfCanvas.height / 2)
        ctx.fillText(e.message || '', pdfCanvas.width / 2, pdfCanvas.height / 2 + 20)
      }
    }
  }

  if (errors.length > 0) {
    errorMsg.value = `部分页面前端渲染失败: ${errors.join('; ')}`
    console.warn(`[pdf-dewatermark] renderAllPages errors: ${errors.join('; ')}`)
  }

  redrawAllOverlays()
}

/**
 * 检测 pdfjs-dist 渲染的白页，自动调用后端 PDFBox 重新渲染
 */
async function checkAndFallbackRender() {
  if (!fileArrayBuffer || totalPages.value === 0) return
  const blankPages: number[] = []

  for (let i = 0; i < totalPages.value; i++) {
    const canvas = getPdfCanvas(i)
    if (!canvas) continue
    // willReadFrequently: 避免重复 getImageData 触发 Canvas2D 性能告警
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) continue

    const sampleSize = 10
    const totalSamples = sampleSize * sampleSize
    const stepX = canvas.width / (sampleSize + 1)
    const stepY = canvas.height / (sampleSize + 1)

    const brightnesses: number[] = []
    let minBrightness = 255
    let allChannelsMin = 255

    for (let sx = 1; sx <= sampleSize; sx++) {
      for (let sy = 1; sy <= sampleSize; sy++) {
        const pixel = ctx.getImageData(Math.round(sx * stepX), Math.round(sy * stepY), 1, 1).data
        const b = pixel[0] * 0.299 + pixel[1] * 0.587 + pixel[2] * 0.114
        brightnesses.push(b)
        if (b < minBrightness) minBrightness = b
        const chMin = Math.min(pixel[0], pixel[1], pixel[2])
        if (chMin < allChannelsMin) allChannelsMin = chMin
      }
    }

    const mean = brightnesses.reduce((a, b) => a + b, 0) / totalSamples
    const variance = brightnesses.reduce((sum, b) => sum + (b - mean) ** 2, 0) / totalSamples

    const isBlankPage = variance < 0.5 && mean > 250
    const isLowQuality = !isBlankPage && allChannelsMin > 80 && mean > 180

    console.log(`[pdf-dewatermark] page ${i}: variance=${variance.toFixed(2)}, mean=${mean.toFixed(1)}, minChannel=${allChannelsMin}, blank=${isBlankPage}, lowQuality=${isLowQuality}`)
    if (isBlankPage || isLowQuality) blankPages.push(i)
  }

  if (blankPages.length === 0) return

  const pdfData = fileArrayBuffer!
  processingLabel.value = `后台渲染 ${blankPages.length}/${totalPages.value} 页中...`

  try {
    for (const pageIndex of blankPages) {
      const fd = new FormData()
      fd.append('file', new Blob([pdfData], { type: 'application/pdf' }), originalFilename)
      fd.append('pageIndex', String(pageIndex))
      fd.append('dpi', '150')

      try {
        const resp = await fetch(`${window.location.origin}/api/pdf/render-page`, {
          method: 'POST',
          body: fd,
        })
        if (!resp.ok) continue

        const blob = await resp.blob()
        const url = URL.createObjectURL(blob)
        const img = new Image()
        await new Promise<void>((resolve, reject) => {
          img.onload = () => resolve()
          img.onerror = () => reject(new Error('image load failed'))
          img.src = url
        })
        await img.decode().catch(() => {})

        const canvas = getPdfCanvas(pageIndex)
        if (canvas) {
          const pdfPageWidth = canvas.width / (pageScales[pageIndex] || 1)
          canvas.width = img.width
          canvas.height = img.height
          const ctx = canvas.getContext('2d')!
          ctx.drawImage(img, 0, 0)
          pageScales[pageIndex] = img.width / pdfPageWidth

          const overlay = getOverlay(pageIndex)
          if (overlay) {
            overlay.width = img.width
            overlay.height = img.height
          }
        }
        pageImages[pageIndex] = img
        drawOverlay(pageIndex)
        URL.revokeObjectURL(url)
      } catch (e) {
        console.warn(`[pdf-dewatermark] page ${pageIndex}: backend fallback FAILED:`, e)
      }
      // 节流：render-page 有后端限流，逐页串行+小间隔，避免批量触发 429 导致页面空白
      await new Promise(r => setTimeout(r, 70))
    }
  } catch (e) {
    console.warn('[pdf-dewatermark] backend fallback render failed:', e)
  }

  processingLabel.value = ''
  errorMsg.value = ''
}

/** 重绘所有页面的覆盖层 */
function redrawAllOverlays() {
  for (let i = 0; i < totalPages.value; i++) {
    drawOverlay(i)
  }
}

const INDIGO = '#6366f1'
const RED = '#ef4444'

function drawOverlay(pageIndex: number) {
  const canvas = getOverlay(pageIndex)
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const scale = pageScales[pageIndex] || 1
  const rects = getPageRectItems(pageIndex)

  // 编辑中的选中区域：indigo 虚线框
  if (stage.value !== 'done') {
    for (const rect of rects) {
      const sx = rect.x * scale; const sy = rect.y * scale
      const sw = rect.w * scale; const sh = rect.h * scale
      if (previewMode.value) {
        ctx.fillStyle = INDIGO + '22'
        ctx.fillRect(sx, sy, sw, sh)
      }
      ctx.strokeStyle = rect.id === selectedRectId.value ? INDIGO : INDIGO + 'AA'
      ctx.lineWidth = 2
      ctx.setLineDash([5, 3])
      ctx.strokeRect(sx, sy, sw, sh)
      ctx.setLineDash([])
      if (rect.id === selectedRectId.value) {
        const hs = 7; ctx.fillStyle = INDIGO
        ;[sx, sy, sx + sw - hs, sy, sx, sy + sh - hs, sx + sw - hs, sy + sh - hs]
          .forEach((v, idx, arr) => { if (idx % 2 === 0) ctx.fillRect(arr[idx], arr[idx + 1], hs, hs) })
      }
    }
    return
  }

  // done 状态：仅标红未去除的区域
  for (const r of failedRects.value) {
    if (r.page !== pageIndex) continue
    const sx = r.x * scale; const sy = r.y * scale
    const sw = r.w * scale; const sh = r.h * scale
    ctx.fillStyle = RED + '33'
    ctx.fillRect(sx, sy, sw, sh)
    ctx.strokeStyle = RED
    ctx.lineWidth = 2
    ctx.setLineDash([5, 3])
    ctx.strokeRect(sx, sy, sw, sh)
    ctx.setLineDash([])
  }
}

function toCanvasCoords(rect: RectItem) {
  const scale = pageScales[rect.page] || 1
  return { x: rect.x * scale, y: rect.y * scale, w: rect.w * scale, h: rect.h * scale }
}

function getCanvasPos(e: MouseEvent, pageIndex: number): { x: number; y: number } {
  const canvas = getOverlay(pageIndex)
  if (!canvas) return { x: 0, y: 0 }
  const r = canvas.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top }
}

// ======================== 区域命中检测 ========================

function findRectAt(pageIndex: number, cx: number, cy: number): RectItem | null {
  const rects = getPageRectItems(pageIndex)
  for (let i = rects.length - 1; i >= 0; i--) {
    const r = rects[i]
    const c = toCanvasCoords(r)
    if (cx >= c.x && cx <= c.x + c.w && cy >= c.y && cy <= c.y + c.h) return r
  }
  return null
}

function findHandleAt(pageIndex: number, cx: number, cy: number): { rect: RectItem; handle: string } | null {
  if (!selectedRectId.value) return null
  const rect = getPageRectItems(pageIndex).find(r => r.id === selectedRectId.value)
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

function onPageMouseDown(e: MouseEvent, pageIndex: number) {
  if (stage.value !== 'ready' || previewMode.value) return
  activePage = pageIndex
  const pos = getCanvasPos(e, pageIndex)

  const handleHit = findHandleAt(pageIndex, pos.x, pos.y)
  if (handleHit) {
    pushHistory()
    resizingRect = handleHit.rect
    resizeHandle = handleHit.handle
    drawStartX = pos.x; drawStartY = pos.y
    return
  }

  const hitRect = findRectAt(pageIndex, pos.x, pos.y)
  if (hitRect) {
    pushHistory()
    selectedRectId.value = hitRect.id
    draggingRect = hitRect
    const c = toCanvasCoords(hitRect)
    dragOffsetX = pos.x - c.x; dragOffsetY = pos.y - c.y
    drawOverlay(pageIndex)
    return
  }

  selectedRectId.value = null
  isDrawing = true
  drawStartX = pos.x; drawStartY = pos.y
  drawOverlay(pageIndex)
}

function onMouseMove(e: MouseEvent) {
  if (activePage < 0) return
  const scale = pageScales[activePage] || 1
  const pos = getCanvasPos(e, activePage)

  if (isDrawing) {
    drawOverlay(activePage)
    const ctx = getOverlay(activePage)?.getContext('2d')
    if (ctx) {
      const x = Math.min(drawStartX, pos.x); const y = Math.min(drawStartY, pos.y)
      const w = Math.abs(pos.x - drawStartX); const h = Math.abs(pos.y - drawStartY)
      ctx.fillStyle = INDIGO + '22'; ctx.fillRect(x, y, w, h)
      ctx.strokeStyle = INDIGO; ctx.lineWidth = 2; ctx.strokeRect(x, y, w, h)
    }
  } else if (draggingRect) {
    const newX = pos.x - dragOffsetX; const newY = pos.y - dragOffsetY
    updateRect(draggingRect.id, { x: Math.max(0, newX / scale), y: Math.max(0, newY / scale) })
    drawOverlay(activePage)
  } else if (resizingRect) {
    const dx = (pos.x - drawStartX) / scale; const dy = (pos.y - drawStartY) / scale
    let { x, y, w, h } = resizingRect
    if (resizeHandle.includes('l')) { x = resizingRect.x + dx; w = resizingRect.w - dx }
    if (resizeHandle.includes('r')) { w = resizingRect.w + dx }
    if (resizeHandle.includes('t')) { h = resizingRect.h - dy }
    if (resizeHandle.includes('b')) { y = resizingRect.y + dy; h = resizingRect.h + dy }
    if (w < 5) w = 5; if (h < 5) h = 5
    updateRect(resizingRect.id, { x: Math.max(0, x), y: Math.max(0, y), w, h })
    drawStartX = pos.x; drawStartY = pos.y
    drawOverlay(activePage)
  }
}

function onMouseUp(e: MouseEvent) {
  if (isDrawing && activePage >= 0) {
    isDrawing = false
    const pos = getCanvasPos(e, activePage)
    const scale = pageScales[activePage] || 1
    const cx = Math.min(drawStartX, pos.x); const cy = Math.min(drawStartY, pos.y)
    const cw = Math.abs(pos.x - drawStartX); const ch = Math.abs(pos.y - drawStartY)

    if (cw >= 5 && ch >= 5) {
      pushHistory()
      const newRect: RectItem = {
        id: 'r_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
        x: Math.max(0, cx / scale), y: Math.max(0, cy / scale),
        w: cw / scale, h: ch / scale,
        page: activePage,
      }
      addRect(newRect)
      selectedRectId.value = newRect.id
    }
    drawOverlay(activePage)
  }
  activePage = -1
  draggingRect = null; resizingRect = null; resizeHandle = ''
}

// ======================== 区域操作 ========================

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
      const updated = [...rects]; updated.splice(idx, 1)
      next.set(page, updated); break
    }
  }
  selectedRectId.value = null
  pageRects.value = next
  redrawAllOverlays()
}

// ======================== Undo / Redo ========================

function clonePageRects(source: Map<number, RectItem[]>): Map<number, RectItem[]> {
  const copy = new Map<number, RectItem[]>()
  for (const [page, rects] of source.entries()) copy.set(page, rects.map(r => ({ ...r })))
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
  redrawAllOverlays()
}

function redo() {
  if (!canRedo.value) return
  undoStack.value.push(clonePageRects(pageRects.value))
  pageRects.value = redoStack.value.pop()!
  selectedRectId.value = null
  redrawAllOverlays()
}

// ======================== 提交 ========================

function confirmAndSubmit() { if (totalRects.value > 0) showConfirm.value = true }

async function doSubmit() {
  showConfirm.value = false
  if (!uploadedFile.value) return

  clearResult()
  failedRects.value = []
  processingLabel.value = '去水印处理中，请稍候...'
  stage.value = 'processing'

  try {
    const allRects: any[] = []
    for (const [page, rects] of pageRects.value.entries()) {
      for (const r of rects) allRects.push({ page, x: r.x, y: r.y, w: r.w, h: r.h })
    }

    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('applyTo', applyTo.value)
    formData.append('regions', JSON.stringify(allRects))

    const resp = await fetch(`${window.location.origin}/api/pdf/dewatermark`, { method: 'POST', body: formData })
    const json = await resp.json().catch(() => null)
    if (!resp.ok || !json) {
      throw new Error((json && json.message) || `HTTP ${resp.status}`)
    }
    if (json.code !== 0) {
      throw new Error(json.message || '去水印处理失败')
    }

    const data = json.data
    if (!data || !data.pdfBase64) throw new Error('返回数据无效')

    // base64 → Blob
    const binary = atob(data.pdfBase64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    resultBlob.value = new Blob([bytes], { type: 'application/pdf' })

    // 记录未去除区域（标红）
    failedRects.value = (data.failed || []).map((f: any) => ({ page: f.page, x: f.x, y: f.y, w: f.w, h: f.h }))

    stage.value = 'done'
    await nextTick()
    await new Promise(r => requestAnimationFrame(r))
    await renderAllPages()
    // 重新回退渲染 pdf.js 空白页，避免下载后空白页保持空白
    await checkAndFallbackRender()
    drawOverlayFailed()
  } catch (e: any) {
    errorMsg.value = e.message || '去水印处理失败'
    stage.value = 'error'
  }
}

function drawOverlayFailed() {
  redrawAllOverlays()
}

function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = uploadedFile.value ? 'dewatermark-' + uploadedFile.value.name : 'dewatermark.pdf'
  a.click()
  URL.revokeObjectURL(url)
}

// ======================== 键盘事件 ========================

function onKeyDown(e: KeyboardEvent) {
  if (stage.value !== 'ready') return
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedRectId.value) { e.preventDefault(); deleteSelected() }
  if ((e.ctrlKey || e.metaKey) && e.key === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo() }
}

// ======================== 工具方法 ========================

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

// ======================== 生命周期 ========================

onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped>
.hidden { display: none; }
</style>
