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
          class="text-xs underline hover:text-red-500 transition-colors flex-shrink-0"
          style="color: var(--text-muted)">移除</button>
        <div class="flex-1"></div>
        <span class="text-xs flex-shrink-0" style="color: var(--text-muted)">共 {{ totalPages }} 页</span>
        <label class="flex items-center gap-1.5 text-xs cursor-pointer flex-shrink-0" style="color: var(--text-primary)">
          <input type="checkbox" v-model="previewMode" class="w-3.5 h-3.5 rounded accent-indigo-500" @change="redrawAllOverlays" />
          预览
        </label>
      </template>
    </header>

    <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

    <!-- ====== 主区：左配置 + 右预览 ====== -->
    <div class="flex flex-1 overflow-hidden mt-3 gap-4" v-if="stage !== 'noFile' && stage !== 'processing'">
      <!-- 左：配置表单 -->
      <div class="w-72 flex-shrink-0 overflow-y-auto pr-1 space-y-4"
        style="background: var(--bg-card); border: 1px solid var(--border-color)" :class="'rounded-2xl p-4'">
        <p class="text-sm font-semibold" style="color: var(--text-primary)">水印内容</p>
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

        <button @click="confirmAndSubmit" :disabled="!watermark.text.trim()"
          class="w-full py-2 rounded-lg text-sm font-medium text-white transition-colors hover:opacity-90"
          :style="!watermark.text.trim()
            ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }
            : { background: 'var(--accent-color)' }">
          💧 生成并下载
        </button>
      </div>

      <!-- 右：预览区 -->
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
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

// ======================== pdfjs-dist v6 兼容补丁 ========================
const proto = Uint8Array.prototype as any
if (!proto.toHex) {
  proto.toHex = function (this: Uint8Array): string {
    return Array.from(this).map(b => b.toString(16).padStart(2, '0')).join('')
  }
}

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).toString()
const CMAP_URL = new URL('/assets/cmaps/', window.location.origin).toString()

// ======================== 类型 ========================
type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

// 水印配置（与后端 WatermarkRequest 一致；工单01 先支持文本+居中+全部页）
interface WatermarkConfig {
  source: 'text' | 'image'
  text: string
  fontSize: number
  color: string
}

// ======================== 状态 ========================
const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const stage = ref<Stage>('noFile')
const processingLabel = ref('PDF 加载中，请稍候...')
const errorMsg = ref('')
const totalPages = ref(0)
const dragOver = ref(false)
const previewMode = ref(true)

const watermark = ref<WatermarkConfig>({ source: 'text', text: '内部资料', fontSize: 28, color: '#808080' })

const pdfCanvases: HTMLCanvasElement[] = []
const overlayCanvases: HTMLCanvasElement[] = []
const pageScales: number[] = []
let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let fileArrayBuffer: ArrayBuffer | null = null
let originalFilename = ''
const pageImages: (HTMLImageElement | null)[] = []

// ======================== 参数变化实时重绘预览 ========================
watch([() => watermark.value.text, () => watermark.value.fontSize, () => watermark.value.color, previewMode], () => {
  redrawAllOverlays()
}, { deep: true })

// ======================== Canvas 管理 ========================
function setPdfCanvas(index: number, el: HTMLCanvasElement) { pdfCanvases[index] = el }
function setOverlayCanvas(index: number, el: HTMLCanvasElement) { overlayCanvases[index] = el }
function getOverlay(pageIndex: number) { return overlayCanvases[pageIndex] }
function getPdfCanvas(pageIndex: number) { return pdfCanvases[pageIndex] }

function redrawAllOverlays() {
  for (let i = 0; i < totalPages.value; i++) drawOverlay(i)
}

/** 在 overlay 上绘制水印预览（居中文字，与后端坐标一致：Y 自下而上翻转） */
function drawOverlay(pageIndex: number) {
  const canvas = getOverlay(pageIndex)
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (stage.value !== 'ready' && stage.value !== 'done') return
  if (!previewMode.value) return
  if (!watermark.value.text.trim()) return

  const scale = pageScales[pageIndex] || 1
  const fontSize = watermark.value.fontSize * scale
  ctx.save()
  ctx.font = `${fontSize}px sans-serif`
  ctx.fillStyle = watermark.value.color
  ctx.globalAlpha = 0.5
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(watermark.value.text, canvas.width / 2, canvas.height / 2)
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

// ======================== PDF 渲染（全页） ========================
async function renderAllPages() {
  if (!pdfDoc) return
  const containerWidth = (getPreviewWidth()) - 80
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
        new Promise((_, reject) => setTimeout(() => reject(new Error('render timeout')), 30000)),
      ])
    } catch (e: any) {
      const ctx = pdfCanvas.getContext('2d')
      if (ctx) { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, viewport.width, viewport.height) }
    }
  }
  redrawAllOverlays()
}

function getPreviewWidth() {
  return Math.min(window.innerWidth - 360, 900)
}

async function checkAndFallbackRender() {
  if (!fileArrayBuffer || totalPages.value === 0) return
  const blankPages: number[] = []
  for (let i = 0; i < totalPages.value; i++) {
    const canvas = getPdfCanvas(i)
    if (!canvas) continue
    const ctx = canvas.getContext('2d')
    if (!ctx) continue
    const sampleSize = 10
    const stepX = canvas.width / (sampleSize + 1)
    const stepY = canvas.height / (sampleSize + 1)
    const brightnesses: number[] = []
    let allChannelsMin = 255
    for (let sx = 1; sx <= sampleSize; sx++) {
      for (let sy = 1; sy <= sampleSize; sy++) {
        const pixel = ctx.getImageData(Math.round(sx * stepX), Math.round(sy * stepY), 1, 1).data
        const b = pixel[0] * 0.299 + pixel[1] * 0.587 + pixel[2] * 0.114
        brightnesses.push(b)
        allChannelsMin = Math.min(allChannelsMin, pixel[0], pixel[1], pixel[2])
      }
    }
    const mean = brightnesses.reduce((a, b) => a + b, 0) / brightnesses.length
    const variance = brightnesses.reduce((s, b) => s + (b - mean) ** 2, 0) / brightnesses.length
    if ((variance < 0.5 && mean > 250) || (!(variance < 0.5 && mean > 250) && allChannelsMin > 80 && mean > 180)) {
      blankPages.push(i)
    }
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
      await new Promise<void>((resolve, reject) => { img.onload = () => resolve(); img.onerror = () => reject(); img.src = url })
      const canvas = getPdfCanvas(pageIndex)
      if (canvas) {
        const pdfPageWidth = canvas.width / (pageScales[pageIndex] || 1)
        canvas.width = img.width; canvas.height = img.height
        canvas.getContext('2d')!.drawImage(img, 0, 0)
        pageScales[pageIndex] = img.width / pdfPageWidth
        const overlay = getOverlay(pageIndex)
        if (overlay) { overlay.width = img.width; overlay.height = img.height }
      }
      pageImages[pageIndex] = img
      URL.revokeObjectURL(url)
    } catch { /* ignore */ }
  }
  errorMsg.value = ''
  redrawAllOverlays()
}

// ======================== 提交 ========================
function confirmAndSubmit() { if (watermark.value.text.trim()) doSubmit() }

async function doSubmit() {
  if (!uploadedFile.value) return
  errorMsg.value = ''
  processingLabel.value = '生成水印 PDF 中，请稍候...'
  stage.value = 'processing'
  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('watermark', JSON.stringify({
      source: watermark.value.source,
      text: watermark.value.text,
      fontSize: watermark.value.fontSize,
      color: watermark.value.color,
    }))
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
    await nextTick()
    await new Promise(r => requestAnimationFrame(r))
    await renderAllPages()
  } catch (e: any) {
    errorMsg.value = e.message || '添加水印失败'
    stage.value = 'error'
  }
}

// ======================== 工具 ========================
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
