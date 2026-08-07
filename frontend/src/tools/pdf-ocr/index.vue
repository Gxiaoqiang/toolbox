<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-ocr',
  name: 'PDF OCR 识别',
  description: '识别扫描版 PDF 文字，输出可搜索 PDF / 文本 / Markdown / Excel',
  icon: 'scan-text',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- ====== 左侧：上传区 + 选项 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">PDF 文件</label>

      <!-- 上传区：固定高度，避免选中文件后布局跳变 -->
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-2 transition-colors flex-shrink-0 h-36"
        :class="stage === 'processing' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30'"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="stage !== 'processing' && triggerFileInput()"
        @dragover.prevent="stage !== 'processing' && (dragOver = true)"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="stage !== 'processing' && handleDrop($event)"
      >
        <template v-if="stage === 'noFile'">
          <span class="text-4xl">🔍</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
          </div>
        </template>

        <template v-else>
          <div class="flex items-center gap-3">
            <span class="text-2xl">📄</span>
            <div>
              <p class="text-sm font-medium" style="color: var(--text-primary)">{{ uploadedFile!.name }}</p>
              <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(uploadedFile!.size) }}</p>
            </div>
            <button
              v-if="stage !== 'processing'"
              @click.stop="clearFile"
              class="text-xs underline hover:text-red-500 transition-colors"
              style="color: var(--text-muted)">移除</button>
          </div>
        </template>
      </div>

      <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

      <!-- 输出格式 -->
      <div class="mt-4 border rounded-lg p-3 transition-opacity"
        :class="stage === 'processing' ? 'opacity-60 pointer-events-none' : ''"
        style="border-color: var(--border-color); background: var(--bg-card)">
        <p class="text-xs font-semibold mb-2" style="color: var(--text-secondary)">输出格式</p>
        <div class="grid grid-cols-2 gap-1.5">
          <div
            v-for="opt in formatOptions" :key="opt.value"
            @click="stage !== 'processing' && (format = opt.value)"
            class="relative border rounded-md py-2 px-2.5 cursor-pointer transition-all"
            :style="format === opt.value
              ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
              : { borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }"
          >
            <div class="flex items-center gap-1.5">
              <div class="w-3.5 h-3.5 rounded-full border-2 flex-shrink-0 flex items-center justify-center"
                :style="format === opt.value ? { borderColor: 'var(--accent-color)' } : { borderColor: 'var(--border-color)' }">
                <div v-if="format === opt.value" class="w-1.5 h-1.5 rounded-full" style="background: var(--accent-color)"></div>
              </div>
              <span class="text-xs font-semibold truncate" style="color: var(--text-primary)">{{ opt.label }}</span>
              <span v-if="opt.isDefault" class="text-[9px] px-1 rounded-full font-medium flex-shrink-0"
                style="background: var(--accent-light); color: var(--accent-color)">默认</span>
            </div>
            <p class="text-[11px] mt-0.5 leading-snug" style="color: var(--text-muted)">{{ opt.description }}</p>
          </div>
        </div>
      </div>

      <!-- 识别语言 -->
      <div class="mt-4 border rounded-lg p-3 transition-opacity"
        :class="stage === 'processing' ? 'opacity-60 pointer-events-none' : ''"
        style="border-color: var(--border-color); background: var(--bg-card)">
        <p class="text-xs font-semibold mb-2" style="color: var(--text-secondary)">识别语言</p>
        <select
          v-model="language"
          :disabled="stage === 'processing'"
          class="w-full rounded-md px-2 py-1.5 text-xs outline-none transition-colors cursor-pointer"
          style="border: 1px solid var(--border-color); background: var(--bg-card-hover); color: var(--text-primary)"
        >
          <option v-for="opt in languageOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
    </div>

    <!-- ====== 中间：识别按钮 ====== -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startOcr"
        :disabled="stage === 'noFile' || stage === 'processing'"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="stage === 'ready' || stage === 'done' || stage === 'error'
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
      >
        <svg v-if="stage === 'processing'" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-base">→</span>
        <span class="text-xs">{{ stage === 'processing' ? '识别中' : '识别' }}</span>
      </button>
    </div>

    <!-- ====== 右侧：结果区 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">识别结果</label>
      <div class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3"
        style="border-color: var(--border-color); background: var(--bg-card)">

        <!-- 空闲 / 等待 -->
        <template v-if="stage === 'noFile' || stage === 'ready'">
          <span class="text-4xl">🔍</span>
          <p class="text-sm" style="color: var(--text-muted)">
            {{ stage === 'noFile' ? '上传 PDF 后选择输出格式' : '点击"识别"开始' }}
          </p>
        </template>

        <!-- 识别中 -->
        <template v-else-if="stage === 'processing'">
          <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在识别扫描页文字，请稍候...</p>
        </template>

        <!-- 完成 -->
        <template v-else-if="stage === 'done' && result">
          <span class="text-4xl">✅</span>
          <p class="text-sm font-semibold" style="color: var(--text-primary)">识别完成</p>

          <!-- 统计信息 -->
          <div class="w-full max-w-xs space-y-1.5 px-4">
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">总页数</span>
              <span class="font-mono font-medium" style="color: var(--text-primary)">{{ result.totalPages }} 页</span>
            </div>
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">扫描页（已 OCR）</span>
              <span class="font-mono font-medium" style="color: var(--accent-color)">{{ result.scannedPages }} 页</span>
            </div>
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">原生文字页</span>
              <span class="font-mono font-medium" style="color: var(--text-primary)">{{ result.nativePages }} 页</span>
            </div>
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">识别耗时</span>
              <span class="font-mono font-medium" style="color: var(--text-primary)">{{ formatTime(result.elapsedMs) }}</span>
            </div>
          </div>

          <button @click="downloadResult"
            class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">下载 {{ formatLabel }}</button>

          <p class="text-[10px]" style="color: var(--text-muted)">
            如需其他格式，可切换后重新识别
          </p>
        </template>

        <!-- 错误 -->
        <template v-else-if="stage === 'error'">
          <span class="text-4xl">⚠️</span>
          <p class="text-sm text-center px-4" style="color: #ef4444">{{ errorMsg }}</p>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 页面状态机:
 *   noFile     — 未选择文件
 *   ready      — 已选文件，等待识别
 *   processing — 识别中，禁止移除文件、禁止切换格式
 *   done       — 识别完成，展示结果
 *   error      — 识别失败
 */
type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

interface OcrResult {
  blob: Blob
  totalPages: number
  scannedPages: number
  nativePages: number
  elapsedMs: number
}

interface FormatOption {
  value: string
  label: string
  description: string
  isDefault: boolean
}

const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const dragOver = ref(false)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')
const format = ref('searchable_pdf')
const language = ref('chi_sim+eng')
const result = ref<OcrResult | null>(null)
const resultUrl = ref('')

const formatOptions: FormatOption[] = [
  { value: 'searchable_pdf', label: '可搜索 PDF', description: '在原 PDF 上叠加透明文字层', isDefault: true },
  { value: 'text', label: '纯文本 TXT', description: '提取全部文字，按页输出', isDefault: false },
  { value: 'md', label: 'Markdown', description: '保留标题/段落结构', isDefault: false },
  { value: 'xlsx', label: '表格 Excel', description: '导出为 Excel 表格', isDefault: false },
]

const languageOptions = [
  { value: 'chi_sim+eng', label: '中文 + 英文' },
  { value: 'chi_sim', label: '仅中文' },
  { value: 'eng', label: '仅英文' },
]

const formatLabel = computed(() =>
  formatOptions.find(o => o.value === format.value)?.label ?? format.value,
)

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) setFile(files[0])
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    const f = files[0]
    if (f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')) {
      setFile(f)
    }
  }
}

function setFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    errorMsg.value = '文件不能超过 50MB'
    stage.value = 'error'
    return
  }
  uploadedFile.value = file
  errorMsg.value = ''
  clearResult()
  stage.value = 'ready'
}

function clearFile() {
  uploadedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  clearResult()
  stage.value = 'noFile'
}

function clearResult() {
  result.value = null
  resultUrl.value = ''
  errorMsg.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

function formatTime(ms: number): string {
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(1) + ' s'
}

async function startOcr() {
  if (!uploadedFile.value || stage.value === 'processing') return

  clearResult()
  stage.value = 'processing'

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('format', format.value)
    formData.append('language', language.value)

    const baseUrl = window.location.origin
    const resp = await fetch(`${baseUrl}/api/pdf/ocr`, {
      method: 'POST',
      body: formData,
    })

    // 后端错误以 HTTP 200 + application/json 返回，需优先识别
    const contentType = resp.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const err = await resp.json().catch(() => null)
      throw new Error(err?.message || '识别失败，请稍后重试')
    }
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`)
    }

    const blob = await resp.blob()
    result.value = {
      blob,
      totalPages: parseInt(resp.headers.get('X-Total-Pages') || '0'),
      scannedPages: parseInt(resp.headers.get('X-Scanned-Pages') || '0'),
      nativePages: parseInt(resp.headers.get('X-Native-Pages') || '0'),
      elapsedMs: parseInt(resp.headers.get('X-Ocr-Time-Ms') || '0'),
    }
    resultUrl.value = URL.createObjectURL(blob)
    stage.value = 'done'
  } catch (e: any) {
    errorMsg.value = e.message || '识别失败'
    stage.value = 'error'
  }
}

function downloadResult() {
  if (!result.value) return
  const url = URL.createObjectURL(result.value.blob)
  const ext = format.value === 'searchable_pdf' ? 'pdf'
    : format.value === 'text' ? 'txt'
    : format.value === 'md' ? 'md' : 'xlsx'
  const a = document.createElement('a')
  a.href = url
  a.download = uploadedFile.value ? uploadedFile.value.name.replace(/\.pdf$/i, '') + '-ocr.' + ext : 'ocr-result.' + ext
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.hidden { display: none; }
</style>
