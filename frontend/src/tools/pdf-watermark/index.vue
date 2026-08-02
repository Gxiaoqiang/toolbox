<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-watermark',
  name: 'PDF 添加水印',
  description: '给 PDF 添加文字或图片水印，支持自定义样式、位置与页面范围',
  icon: '💧',
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
          class="text-xs underline hover:text-red-500 transition-colors flex-shrink-0" style="color: var(--text-muted)">移除</button>
        <div class="flex-1"></div>
        <span class="text-xs flex-shrink-0" style="color: var(--text-muted)">共 {{ totalPages }} 页</span>
        <label class="flex items-center gap-1.5 text-xs cursor-pointer flex-shrink-0" style="color: var(--text-primary)">
          <input type="checkbox" v-model="previewMode" class="w-3.5 h-3.5 rounded accent-indigo-500" />
          预览
        </label>
      </template>
    </header>

    <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

    <!-- ====== 主区：左配置 + 右预览 ====== -->
    <div class="flex flex-1 overflow-hidden mt-3 gap-4" v-if="stage !== 'noFile' && stage !== 'processing'">
      <div class="w-72 flex-shrink-0 overflow-y-auto pr-1 space-y-4 rounded-2xl p-4"
        style="background: var(--bg-card); border: 1px solid var(--border-color)">
        <!-- 来源 -->
        <div class="flex gap-2">
          <button v-for="s in sources" :key="s.value" @click="watermark.source = s.value"
            class="flex-1 py-1.5 text-xs rounded-lg border transition-colors"
            :style="watermark.source === s.value
              ? { background: 'var(--accent-light)', color: 'var(--accent-color)', borderColor: 'var(--accent-color)' }
              : { background: 'var(--bg-card)', color: 'var(--text-secondary)', borderColor: 'var(--border-color)' }">
            {{ s.label }}
          </button>
        </div>

        <!-- 文本水印 -->
        <template v-if="watermark.source === 'text'">
          <div>
            <label class="text-xs block mb-1" style="color: var(--text-secondary)">水印文本</label>
            <input v-model="watermark.text" type="text" placeholder="例如：内部资料"
              class="w-full px-2 py-1.5 text-sm border rounded-md"
              style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">字号(pt)</label>
              <input v-model.number="watermark.fontSize" type="number" min="8" max="200"
                class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            </div>
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">颜色</label>
              <input v-model="watermark.color" type="color" class="w-full h-8 border rounded-md cursor-pointer"
                style="background: var(--bg-input); border-color: var(--border-color)" />
            </div>
          </div>
        </template>

        <!-- 图片水印 -->
        <div v-else>
          <label class="text-xs block mb-1" style="color: var(--text-secondary)">水印图片（PNG/JPG/GIF/BMP）</label>
          <div class="border-2 border-dashed rounded-lg p-3 text-center cursor-pointer"
            style="border-color: var(--border-color)"
            @click="imageInputRef?.click()" @dragover.prevent @drop.prevent="onImageDrop">
            <img v-if="imagePreviewUrl" :src="imagePreviewUrl" class="max-h-20 mx-auto mb-1" />
            <p class="text-xs" style="color: var(--text-muted)">{{ imageFile ? imageFile.name : '点击选择图片' }}</p>
          </div>
          <input ref="imageInputRef" type="file" accept=".png,.jpg,.jpeg,.gif,.bmp" class="hidden" @change="onImageSelect" />
        </div>

        <!-- 外观 -->
        <div class="pt-1 border-t" style="border-color: var(--border-color)">
          <p class="text-xs font-semibold mb-2" style="color: var(--text-primary)">外观</p>
          <div class="mb-2">
            <label class="text-xs block mb-1" style="color: var(--text-secondary)">旋转角度</label>
            <select v-model.number="watermark.angle" class="w-full px-2 py-1.5 text-sm border rounded-md"
              style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
              <option :value="0">无旋转 (0°)</option>
              <option :value="45">45°</option>
              <option :value="-45">-45°</option>
              <option :value="watermark.customAngle">自定义</option>
            </select>
            <input v-if="Number(watermark.angle) === watermark.customAngle" v-model.number="watermark.customAngle"
              type="number" class="mt-1 w-full px-2 py-1 text-sm border rounded-md"
              style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" placeholder="输入角度" />
          </div>
          <div class="mb-2">
            <label class="text-xs block mb-1" style="color: var(--text-secondary)">透明度：{{ Math.round(watermark.opacity * 100) }}%</label>
            <input v-model.number="watermark.opacity" type="range" min="0" max="1" step="0.05" class="w-full" />
          </div>
          <div v-if="watermark.source === 'image'" class="mb-2">
            <label class="text-xs block mb-1" style="color: var(--text-secondary)">相对页面宽度：{{ watermark.ratio }}%</label>
            <input v-model.number="watermark.ratio" type="range" min="5" max="100" step="5" class="w-full" />
          </div>
          <label class="flex items-start gap-2 text-xs cursor-pointer" style="color: var(--text-secondary)">
            <input type="checkbox" v-model="watermark.fixedRatio" class="mt-0.5 w-3.5 h-3.5 accent-indigo-500" />
            固定水印比例（不同页面尺寸时保持大小不变）
          </label>
        </div>

        <!-- 位置 -->
        <div class="pt-1 border-t" style="border-color: var(--border-color)">
          <p class="text-xs font-semibold mb-2" style="color: var(--text-primary)">位置</p>
          <div class="grid grid-cols-2 gap-2 mb-2">
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">水平对齐</label>
              <select v-model="watermark.alignX" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
                <option value="left">左</option><option value="center">居中</option><option value="right">右</option>
              </select>
            </div>
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">垂直对齐</label>
              <select v-model="watermark.alignY" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
                <option value="top">上方</option><option value="middle">居中</option><option value="bottom">下方</option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">水平偏移(cm)</label>
              <input v-model.number="watermark.offsetX" type="number" step="0.1" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            </div>
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">垂直偏移(cm)</label>
              <input v-model.number="watermark.offsetY" type="number" step="0.1" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            </div>
          </div>
        </div>

        <!-- 页面范围 -->
        <div class="pt-1 border-t" style="border-color: var(--border-color)">
          <p class="text-xs font-semibold mb-2" style="color: var(--text-primary)">页面范围</p>
          <div class="flex items-center gap-2 text-xs mb-2">
            <label class="flex items-center gap-1 cursor-pointer" style="color: var(--text-secondary)">
              <input type="radio" value="all" v-model="watermark.range" class="accent-indigo-500" />所有页面
            </label>
            <label class="flex items-center gap-1 cursor-pointer" style="color: var(--text-secondary)">
              <input type="radio" value="pageRange" v-model="watermark.range" class="accent-indigo-500" />指定范围
            </label>
          </div>
          <div v-if="watermark.range === 'pageRange'" class="grid grid-cols-2 gap-2 mb-2">
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">从页</label>
              <input v-model.number="watermark.fromPage" type="number" min="1" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            </div>
            <div>
              <label class="text-xs block mb-1" style="color: var(--text-secondary)">到页</label>
              <input v-model.number="watermark.toPage" type="number" min="1" class="w-full px-2 py-1.5 text-sm border rounded-md"
                style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            </div>
          </div>
          <div>
            <label class="text-xs block mb-1" style="color: var(--text-secondary)">子集</label>
            <select v-model="watermark.subset" class="w-full px-2 py-1.5 text-sm border rounded-md"
              style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)">
              <option value="all">全部页面</option><option value="odd">奇数页</option><option value="even">偶数页</option>
            </select>
          </div>
        </div>

        <button @click="doSubmit" :disabled="!canSubmit"
          class="w-full py-2 rounded-lg text-sm font-medium text-white transition-colors hover:opacity-90"
          :style="canSubmit
            ? { background: 'var(--accent-color)' }
            : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }">
          💧 生成并下载
        </button>
      </div>

      <!-- 右：预览 -->
      <div class="flex-1 overflow-y-auto rounded-lg" style="background: var(--bg-card-hover)">
        <div class="flex flex-col items-center gap-6 py-6">
          <div v-if="stage === 'error'" class="p-8 text-center">
            <p class="text-sm" style="color: #ef4444">{{ errorMsg }}</p>
          </div>
          <div v-for="pageIndex in totalPages" :key="pageIndex" class="relative inline-block shadow-lg" style="background: #fff">
            <canvas :ref="(el) => setPdfCanvas(pageIndex - 1, el as HTMLCanvasElement)" class="block"></canvas>
            <canvas :ref="(el) => setOverlayCanvas(pageIndex - 1, el as HTMLCanvasElement)" class="absolute top-0 left-0"></canvas>
            <div class="absolute bottom-1 left-1/2 -translate-x-1/2 text-[10px] px-2 py-0.5 rounded-full bg-black/40 text-white">
              第 {{ pageIndex }} 页
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 无文件/处理中 -->
    <div v-if="stage === 'noFile'" class="flex-1 mt-3 flex items-center justify-center">
      <div class="border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-4 p-16 cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput" @dragover.prevent="dragOver = true" @dragleave.prevent="dragOver = false" @drop.prevent="handleDrop">
        <span class="text-6xl">💧</span>
        <div class="text-center">
          <p class="text-base font-semibold" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
          <p class="text-sm mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
        </div>
      </div>
    </div>
    <div v-if="stage === 'processing'" class="flex-1 mt-3 flex items-center justify-center">
      <div class="flex flex-col items-center gap-4">
        <svg class="animate-spin" width="48" height="48" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p class="text-sm" style="color: var(--text-muted)">{{ processingLabel }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

const proto = Uint8Array.prototype as any
if (!proto.toHex) {
  proto.toHex = function (this: Uint8Array): string {
    return Array.from(this).map(b => b.toString(16).padStart(2, '0')).join('')
  }
}
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).toString()
const CMAP_URL = new URL('/assets/cmaps/', window.location.origin).toString()

type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

const sources: { value: WatermarkConfig['source']; label: string }[] = [
  { value: 'text', label: '文字水印' },
  { value: 'image', label: '图片水印' },
]

interface WatermarkConfig {
  source: 'text' | 'image'
  text: string
  fontSize: number
  color: string
  angle: number
  customAngle: number
  opacity: number
  ratio: number
  fixedRatio: boolean
  alignX: 'left' | 'center' | 'right'
  alignY: 'top' | 'middle' | 'bottom'
  offsetX: number
  offsetY: number
  range: 'all' | 'pageRange'
  fromPage: number
  toPage: number
  subset: 'all' | 'odd' | 'even'
}

const watermark = ref<WatermarkConfig>({
  source: 'text', text: '内部资料', fontSize: 28, color: '#808080',
  angle: 0, customAngle: 30, opacity: 0.5, ratio: 50, fixedRatio: false,
  alignX: 'center', alignY: 'middle', offsetX: 0, offsetY: 0,
  range: 'all', fromPage: 1, toPage: 1, subset: 'all',
})

const fileInputRef = ref<HTMLInputElement>()
const imageInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const imageFile = ref<File | null>(null)
const imagePreviewUrl = ref('')
const stage = ref<Stage>('noFile')
const processingLabel = ref('PDF 加载中，请稍候...')
const errorMsg = ref('')
const totalPages = ref(0)
const dragOver = ref(false)
const previewMode = ref(true)

const pdfCanvases: HTMLCanvasElement[] = []
const overlayCanvases: HTMLCanvasElement[] = []
const pageScales: number[] = []
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let fileArrayBuffer: ArrayBuffer | null = null
let originalFilename = ''
let imageElement: HTMLImageElement | null = null

const canSubmit = computed(() => {
  if (stage.value === 'processing' || stage.value !== 'ready') return false
  if (watermark.value.source === 'text') return !!watermark.value.text.trim()
  return !!imageFile.value
})

watch(watermark, () => redrawAllOverlays(), { deep: true })
watch([previewMode, imagePreviewUrl], () => redrawAllOverlays())

function setPdfCanvas(index: number, el: HTMLCanvasElement) { pdfCanvases[index] = el }
function setOverlayCanvas(index: number, el: HTMLCanvasElement) { overlayCanvases[index] = el }
function getOverlay(i: number) { return overlayCanvases[i] }
function getPdfCanvas(i: number) { return pdfCanvases[i] }

function redrawAllOverlays() {
  for (let i = 0; i < totalPages.value; i++) drawOverlay(i)
}

// ======================== 预览绘制（与后端坐标/定位数学一致） ========================
const CM_TO_PT = 28.3465

function drawOverlay(pageIndex: number) {
  const canvas = getOverlay(pageIndex)
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (!previewMode.value || stage.value !== 'ready') return

  const scale = pageScales[pageIndex] || 1
  const W = canvas.width
  const H = canvas.height
  const cfg = watermark.value

  // 计算水印尺寸（canvas 像素）
  let wmW = 0, wmH = 0
  if (cfg.source === 'text') {
    const fs = cfg.fontSize * scale
    ctx.font = `${fs}px sans-serif`
    const m = ctx.measureText(cfg.text)
    wmW = m.width
    wmH = fs * 1.2
  } else if (imageElement) {
    if (cfg.fixedRatio) {
      wmW = imageElement.naturalWidth
      wmH = imageElement.naturalHeight
    } else {
      wmW = W * (cfg.ratio / 100)
      wmH = wmW * (imageElement.naturalHeight / imageElement.naturalWidth)
    }
  }
  if (wmW <= 0 || wmH <= 0) return

  // 对齐 + 偏移（cm→pt→px）
  const offsetXPx = cfg.offsetX * CM_TO_PT * scale
  const offsetYPx = cfg.offsetY * CM_TO_PT * scale
  let x = 0
  if (cfg.alignX === 'center') x = (W - wmW) / 2
  else if (cfg.alignX === 'right') x = W - wmW
  let y = 0
  if (cfg.alignY === 'middle') y = (H - wmH) / 2
  else if (cfg.alignY === 'bottom') y = H - wmH
  x += offsetXPx
  y += offsetYPx

  const cx = x + wmW / 2
  const cy = y + wmH / 2

  ctx.save()
  ctx.translate(cx, cy)
  ctx.rotate((cfg.angle * Math.PI) / 180)
  ctx.globalAlpha = Math.max(0, Math.min(1, cfg.opacity))
  if (cfg.source === 'text') {
    ctx.fillStyle = cfg.color
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(cfg.text, 0, 0)
  } else if (imageElement) {
    ctx.drawImage(imageElement, -wmW / 2, -wmH / 2, wmW, wmH)
  }
  ctx.restore()
}

// ======================== 文件处理 ========================
function triggerFileInput() { fileInputRef.value?.click() }
function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) loadFile(files[0])
}
function handleDrop(e: DragEvent) {
  dragOver.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f && (f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'))) loadFile(f)
}
function onImageSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) setImage(files[0])
}
function onImageDrop(e: DragEvent) {
  const f = e.dataTransfer?.files?.[0]
  if (f) setImage(f)
}
function setImage(f: File) {
  imageFile.value = f
  imageElement = new Image()
  const url = URL.createObjectURL(f)
  imageElement.onload = () => { imagePreviewUrl.value = url; redrawAllOverlays() }
  imageElement.src = url
}

function clearFile() {
  uploadedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  pdfDoc = null
  totalPages.value = 0
  pdfCanvases.length = 0
  overlayCanvases.length = 0
  pageScales.length = 0
  errorMsg.value = ''
  stage.value = 'noFile'
}

async function loadFile(file: File) {
  if (file.size > 50 * 1024 * 1024) { errorMsg.value = '文件不能超过 50MB'; stage.value = 'error'; return }
  uploadedFile.value = file
  originalFilename = file.name
  errorMsg.value = ''
  processingLabel.value = 'PDF 加载中，请稍候...'
  stage.value = 'processing'
  try {
    fileArrayBuffer = await file.arrayBuffer()
    pdfDoc = await pdfjsLib.getDocument({ data: fileArrayBuffer, cMapUrl: CMAP_URL, cMapPacked: true }).promise
    totalPages.value = pdfDoc.numPages
    stage.value = 'ready'
    await nextTick()
    await new Promise(r => requestAnimationFrame(r))
    await renderAllPages()
    await checkAndFallbackRender()
  } catch (e: any) {
    errorMsg.value = 'PDF 加载失败: ' + (e.message || '未知错误')
    stage.value = 'error'
  }
}

async function renderAllPages() {
  if (!pdfDoc) return
  const containerWidth = Math.min(window.innerWidth - 360, 900) - 80
  for (let i = 0; i < totalPages.value; i++) {
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
      await Promise.race([
        page.render({ canvas: pdfCanvas, viewport }).promise,
        new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 30000)),
      ])
    } catch {
      const ctx = pdfCanvas.getContext('2d')
      if (ctx) { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, viewport.width, viewport.height) }
    }
  }
  redrawAllOverlays()
}

async function checkAndFallbackRender() {
  if (!fileArrayBuffer || totalPages.value === 0) return
  const blankPages: number[] = []
  for (let i = 0; i < totalPages.value; i++) {
    const canvas = getPdfCanvas(i)
    if (!canvas) continue
    const ctx = canvas.getContext('2d')
    if (!ctx) continue
    const s = 10, stepX = canvas.width / (s + 1), stepY = canvas.height / (s + 1)
    const bs: number[] = []
    let min = 255
    for (let sx = 1; sx <= s; sx++) for (let sy = 1; sy <= s; sy++) {
      const p = ctx.getImageData(Math.round(sx * stepX), Math.round(sy * stepY), 1, 1).data
      const b = p[0] * 0.299 + p[1] * 0.587 + p[2] * 0.114
      bs.push(b); min = Math.min(min, p[0], p[1], p[2])
    }
    const mean = bs.reduce((a, b) => a + b, 0) / bs.length
    const variance = bs.reduce((s2, b) => s2 + (b - mean) ** 2, 0) / bs.length
    if ((variance < 0.5 && mean > 250) || (min > 80 && mean > 180)) blankPages.push(i)
  }
  if (blankPages.length === 0) return
  const pdfData = fileArrayBuffer!
  for (const pageIndex of blankPages) {
    const fd = new FormData()
    fd.append('file', new Blob([pdfData], { type: 'application/pdf' }), originalFilename)
    fd.append('pageIndex', String(pageIndex))
    fd.append('dpi', '150')
    try {
      const resp = await fetch(`${window.location.origin}/api/pdf/render-page`, { method: 'POST', body: fd })
      if (!resp.ok) continue
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const img = new Image()
      await new Promise<void>((res, rej) => { img.onload = () => res(); img.onerror = () => rej(); img.src = url })
      const canvas = getPdfCanvas(pageIndex)
      if (canvas) {
        const pdfW = canvas.width / (pageScales[pageIndex] || 1)
        canvas.width = img.width; canvas.height = img.height
        canvas.getContext('2d')!.drawImage(img, 0, 0)
        pageScales[pageIndex] = img.width / pdfW
        const ov = getOverlay(pageIndex)
        if (ov) { ov.width = img.width; ov.height = img.height }
      }
      URL.revokeObjectURL(url)
    } catch { /* ignore */ }
  }
  errorMsg.value = ''
  redrawAllOverlays()
}

// ======================== 提交 ========================
async function doSubmit() {
  if (!uploadedFile.value || !canSubmit.value) return
  errorMsg.value = ''
  processingLabel.value = '生成水印 PDF 中，请稍候...'
  stage.value = 'processing'
  try {
    const cfg = watermark.value
    const payload: Record<string, unknown> = {
      source: cfg.source,
      text: cfg.text,
      fontSize: cfg.fontSize,
      color: cfg.color,
      angle: cfg.angle === cfg.customAngle ? cfg.customAngle : cfg.angle,
      opacity: cfg.opacity,
      ratio: cfg.ratio,
      fixedRatio: cfg.fixedRatio,
      alignX: cfg.alignX,
      alignY: cfg.alignY,
      offsetX: cfg.offsetX,
      offsetY: cfg.offsetY,
      range: cfg.range,
      fromPage: cfg.range === 'pageRange' ? cfg.fromPage : null,
      toPage: cfg.range === 'pageRange' ? cfg.toPage : null,
      subset: cfg.subset,
    }
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('watermark', JSON.stringify(payload))
    if (cfg.source === 'image' && imageFile.value) formData.append('image', imageFile.value)

    const resp = await fetch(`${window.location.origin}/api/pdf/watermark`, { method: 'POST', body: formData })
    if (!resp.ok) {
      const json = await resp.json().catch(() => null)
      throw new Error((json && json.message) || `HTTP ${resp.status}`)
    }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'watermark-' + uploadedFile.value.name
    a.click()
    URL.revokeObjectURL(url)
    stage.value = 'ready'
    // processing 阶段预览区被卸载，回到 ready 后需重新渲染页面，避免预览变成空白缩略图
    await nextTick()
    await new Promise(r => requestAnimationFrame(r))
    await renderAllPages()
  } catch (e: any) {
    errorMsg.value = e.message || '添加水印失败'
    stage.value = 'error'
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

onMounted(() => { window.addEventListener('resize', redrawAllOverlays) })
onUnmounted(() => { window.removeEventListener('resize', redrawAllOverlays) })
</script>

<style scoped>
.hidden { display: none; }
</style>
