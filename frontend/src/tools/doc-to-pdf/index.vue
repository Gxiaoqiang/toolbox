<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'doc-to-pdf', name: '文档转 PDF',
  description: '将 .doc / .docx / .wps 文档转换为 PDF',
  icon: 'file-text', category: 'file', requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：上传区 — 始终可见 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">文档文件</label>

      <!-- 虚线拖拽上传区 — 始终可见，选中文件后缩小 -->
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30 flex-shrink-0"
        :class="fileList.length === 0 ? 'flex-1 gap-3' : 'h-24 gap-1'"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <template v-if="fileList.length === 0">
          <span class="text-4xl">📤</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽文档到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择 · 最多 5 个 · 每个 ≤50MB</p>
            <p class="text-xs mt-0.5" style="color: var(--text-muted)">支持 .doc .docx .wps</p>
          </div>
        </template>
        <template v-else>
          <span class="text-xl">📤</span>
          <p class="text-xs" style="color: var(--text-muted)">拖拽或点击添加更多文件（{{ fileList.length }}/5）</p>
        </template>
      </div>

      <!-- 文件列表 — 选中文件后显示 -->
      <div v-if="fileList.length > 0" class="flex-1 flex flex-col min-h-0 mt-3">
        <div class="flex-1 overflow-y-auto space-y-1.5">
          <div
            v-for="(f, idx) in fileList" :key="idx"
            class="flex items-center gap-2 px-3 py-2 rounded-lg border"
            :style="{ borderColor: f.error ? '#f87171' : 'var(--border-color)', background: 'var(--bg-card)' }"
          >
            <span class="text-lg flex-shrink-0">📄</span>
            <span class="flex-1 text-sm truncate" :style="{ color: f.error ? '#f87171' : 'var(--text-primary)' }">{{ f.file.name }}</span>
            <span v-if="f.error" class="text-xs text-red-400 flex-shrink-0">⚠ {{ f.error }}</span>
            <button v-if="!processing" @click="removeFile(idx)" class="w-5 h-5 rounded flex items-center justify-center text-xs flex-shrink-0 hover:bg-red-50" style="color: var(--text-muted)">✕</button>
          </div>
        </div>
        <button v-if="!processing" @click="clearAll"
          class="mt-2 text-xs underline self-start" style="color: var(--text-muted)">清空全部</button>
      </div>

      <input ref="fileInputRef" type="file" accept=".doc,.docx,.wps" multiple class="hidden" @change="handleFileSelect" />
    </div>

    <!-- 中间：转换按钮 — 始终可见 -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startConvert"
        :disabled="fileList.length === 0 || processing || hasErrors"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="fileList.length > 0 && !processing && !hasErrors
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
        :title="fileList.length === 0 ? '请先选择文档' : processing ? '正在转换...' : ''"
      >
        <!-- SVG 加载动画 -->
        <svg v-if="processing" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-lg leading-none">▶</span>
        <span class="text-xs leading-tight">{{ processing ? '转换中' : '转换' }}</span>
      </button>
    </div>

    <!-- 右侧：结果区 — 虚线框包裹 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">转换结果</label>

      <div
        class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center overflow-hidden"
        style="border-color: var(--border-color); background: var(--bg-card)"
      >
        <!-- 空态 -->
        <div v-if="fileList.length === 0 && convertResults.length === 0" class="text-center">
          <span class="text-3xl">📄</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">请先选择文档文件</p>
        </div>

        <!-- 就绪 -->
        <div v-else-if="convertResults.length === 0 && !processing" class="text-center">
          <span class="text-3xl">📑</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">点击"转换"开始</p>
        </div>

        <!-- 转换中 -->
        <div v-else-if="processing && convertResults.length === 0" class="text-center">
          <svg class="animate-spin mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" opacity="0.15" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在转换中...</p>
        </div>

        <!-- 有结果 -->
        <div v-else class="w-full flex-1 overflow-y-auto p-3 space-y-1.5">
          <div
            v-for="(r, idx) in convertResults" :key="idx"
            class="flex items-center gap-2 px-3 py-2 rounded-lg border text-sm"
            :style="{ borderColor: r.success && r.status === 'done' ? '#34d399' : r.status === 'done' ? '#f87171' : 'var(--border-color)', background: 'var(--bg-main)' }"
          >
            <span v-if="r.status === 'pending'" class="text-xs">⏳</span>
            <span v-else-if="r.success" class="text-xs">✅</span>
            <span v-else class="text-xs">❌</span>
            <span class="flex-1 truncate" style="color: var(--text-primary)">{{ r.name }}</span>
            <span v-if="!r.success && r.reason" class="text-xs text-red-400 truncate max-w-[140px]" :title="r.reason">{{ r.reason }}</span>
          </div>

          <div v-if="resultReady" class="pt-2">
            <button @click="downloadZip"
              class="w-full py-2 rounded-lg text-sm font-medium bg-indigo-500 hover:bg-indigo-600 text-white transition-colors">
              📥 下载 ZIP（{{ successCount }}/{{ convertResults.length }} 成功）
            </button>
            <button @click="resetAll" class="block mx-auto mt-2 text-xs underline" style="color: var(--text-muted)">重新转换</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const { success: toastSuccess, error: toastError } = useToast()
const ALLOWED_EXTS = ['doc', 'docx', 'wps']
const MAX_SIZE = 50 * 1024 * 1024

const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const processing = ref(false)

interface FileItem { file: File; error: string }
interface ConvertResult { name: string; status: 'pending' | 'done'; success: boolean; reason?: string }

const fileList = ref<FileItem[]>([])
const convertResults = ref<ConvertResult[]>([])
const zipBlob = ref<Blob | null>(null)
const resultReady = ref(false)

const hasErrors = computed(() => fileList.value.some(f => f.error))
const successCount = computed(() => convertResults.value.filter(r => r.success).length)

function triggerFileInput() { fileInputRef.value?.click() }

function addFiles(files: FileList | File[]) {
  const arr = Array.from(files)
  for (const f of arr) {
    if (fileList.value.length >= 5) { toastError('最多上传 5 个文件'); break }
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (!ALLOWED_EXTS.includes(ext)) { toastError(`不支持的文件格式: ${f.name}`); continue }
    if (f.size > MAX_SIZE) { toastError(`文件过大 (超过50MB): ${f.name}`); continue }
    if (f.size === 0) { toastError(`文件为空: ${f.name}`); continue }
    fileList.value.push({ file: f, error: '' })
  }
  resetResult()
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files) addFiles(e.dataTransfer.files)
}

function removeFile(idx: number) {
  fileList.value.splice(idx, 1)
  resetResult()
}

function clearAll() {
  fileList.value = []
  resetResult()
}

function resetResult() {
  convertResults.value = []
  zipBlob.value = null
  resultReady.value = false
}

async function startConvert() {
  if (fileList.value.length === 0 || hasErrors.value || processing.value) return
  processing.value = true
  resultReady.value = false
  convertResults.value = fileList.value.map(f => ({ name: f.file.name, status: 'pending' as const, success: false }))

  const formData = new FormData()
  fileList.value.forEach(f => formData.append('files', f.file))

  try {
    const resp = await fetch('/api/document/convert-to-pdf', { method: 'POST', body: formData })
    if (!resp.ok) {
      const errJson = await resp.json().catch(() => ({ message: '转换服务异常' }))
      convertResults.value.forEach(r => { r.status = 'done'; r.success = false; r.reason = errJson.message || '转换失败' })
      toastError(errJson.message || '转换失败，请检查服务是否可用')
      return
    }
    zipBlob.value = await resp.blob()
    convertResults.value.forEach(r => { r.status = 'done'; r.success = true })
    resultReady.value = true
    toastSuccess(`全部 ${successCount.value} 个文件转换成功`)
  } catch (e: any) {
    const msg = e.message || '网络异常，请检查服务是否可用'
    convertResults.value.forEach(r => { r.status = 'done'; r.success = false; r.reason = msg })
    toastError(msg)
  } finally {
    processing.value = false
  }
}

function downloadZip() {
  if (!zipBlob.value) return
  const url = URL.createObjectURL(zipBlob.value)
  const a = document.createElement('a')
  a.href = url; a.download = 'doc-to-pdf-result.zip'
  document.body.appendChild(a); a.click()
  document.body.removeChild(a); URL.revokeObjectURL(url)
}

function resetAll() {
  fileList.value = []
  resetResult()
}
</script>

<style scoped>
.hidden { display: none; }
</style>
