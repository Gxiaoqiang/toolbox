<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：上传区 -->
    <div class="w-2/5 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">文档文件</label>

      <div
        v-if="fileList.length === 0"
        class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3 cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <span class="text-4xl">📤</span>
        <div class="text-center">
          <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽文档到此处</p>
          <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择 · 最多 5 个 · 每个 ≤50MB</p>
          <p class="text-xs mt-0.5" style="color: var(--text-muted)">支持 .doc .docx .wps</p>
        </div>
      </div>

      <!-- 已选文件列表 -->
      <div v-else class="flex-1 flex flex-col min-h-0">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs" style="color: var(--text-muted)">已选 {{ fileList.length }}/5 个文件</span>
          <button @click="triggerFileInput" class="text-xs underline" style="color: var(--accent-color)"
            :disabled="fileList.length >= 5">+ 添加文件</button>
        </div>
        <div class="flex-1 overflow-y-auto space-y-1.5">
          <div
            v-for="(f, idx) in fileList"
            :key="idx"
            class="flex items-center gap-2 px-3 py-2 rounded-lg border"
            :style="{ borderColor: f.error ? '#f87171' : 'var(--border-color)', background: 'var(--bg-card)' }"
          >
            <span class="text-lg flex-shrink-0">📄</span>
            <span class="flex-1 text-sm truncate" :style="{ color: f.error ? '#f87171' : 'var(--text-primary)' }">{{ f.file.name }}</span>
            <span v-if="f.error" class="text-xs text-red-400 flex-shrink-0">⚠ {{ f.error }}</span>
            <button v-if="!processing" @click="removeFile(idx)" class="w-5 h-5 rounded flex items-center justify-center text-xs flex-shrink-0 hover:bg-red-50" style="color: var(--text-muted)">✕</button>
          </div>
        </div>
      </div>

      <input ref="fileInputRef" type="file" accept=".doc,.docx,.wps" multiple class="hidden" @change="handleFileSelect" />
    </div>

    <!-- 右侧：操作区 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-3 flex-shrink-0" style="color: var(--text-secondary)">转换</label>

      <div v-if="!resultReady" class="flex-1 flex flex-col items-center justify-center gap-3">
        <p v-if="fileList.length === 0" class="text-sm" style="color: var(--text-muted)">请先选择要转换的文档</p>

        <button
          v-else
          @click="startConvert"
          :disabled="processing || hasErrors"
          class="w-full py-2.5 rounded-lg text-sm font-medium transition-all"
          :class="processing || hasErrors ? 'cursor-not-allowed opacity-50' : 'bg-indigo-500 hover:bg-indigo-600 text-white'"
          :style="processing || hasErrors ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)' } : {}"
        >
          <span v-if="processing" class="inline-block animate-spin mr-1">⟳</span>
          {{ processing ? '正在转换...' : '开始转换' }}
        </button>

        <!-- 转换进度 -->
        <div v-if="processing || convertResults.length > 0" class="w-full space-y-1.5 mt-2">
          <div
            v-for="(r, idx) in convertResults"
            :key="idx"
            class="flex items-center gap-2 px-3 py-2 rounded-lg border text-sm"
            :style="{ borderColor: r.success ? '#34d399' : '#f87171', background: 'var(--bg-card)' }"
          >
            <span v-if="r.status === 'pending'" class="text-xs">⏳</span>
            <span v-else-if="r.success" class="text-xs">✅</span>
            <span v-else class="text-xs">❌</span>
            <span class="flex-1 truncate" style="color: var(--text-primary)">{{ r.name }}</span>
            <span v-if="!r.success && r.reason" class="text-xs text-red-400">{{ r.reason }}</span>
          </div>
        </div>
      </div>

      <!-- 完成结果 -->
      <div
        v-else
        class="flex-1 border rounded-lg p-4 text-center flex flex-col items-center justify-center gap-3"
        style="border-color: var(--accent-color); background: var(--accent-light)"
      >
        <p class="text-sm font-semibold" style="color: var(--accent-color)">
          ✓ 转换完成 — {{ successCount }}/{{ fileList.length }} 成功
        </p>
        <button
          @click="downloadZip"
          class="px-6 py-2 rounded-lg text-sm font-medium bg-indigo-500 hover:bg-indigo-600 text-white transition-colors"
        >📥 下载 ZIP</button>
        <button
          @click="resetAll"
          class="text-xs underline"
          style="color: var(--text-muted)"
        >重新转换</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'doc-to-pdf', name: '文档转 PDF',
  description: '将 .doc / .docx / .wps 文档转换为 PDF',
  icon: 'file-text', category: 'file', requiresBackend: true,
}
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
    if (!ALLOWED_EXTS.includes(ext)) { fileList.value.push({ file: f, error: '不支持此格式' }); continue }
    if (f.size > MAX_SIZE) { fileList.value.push({ file: f, error: '超过 50MB' }); continue }
    if (f.size === 0) { fileList.value.push({ file: f, error: '文件为空' }); continue }
    fileList.value.push({ file: f, error: '' })
  }
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

function removeFile(idx: number) { fileList.value.splice(idx, 1) }

async function startConvert() {
  if (fileList.value.length === 0 || hasErrors.value || processing.value) return
  processing.value = true
  convertResults.value = fileList.value.map(f => ({ name: f.file.name, status: 'pending' as const, success: false }))

  const formData = new FormData()
  fileList.value.forEach(f => formData.append('files', f.file))

  try {
    const resp = await fetch('/api/document/convert-to-pdf', { method: 'POST', body: formData })
    if (!resp.ok) {
      const err = await resp.json()
      throw new Error(err.message || '转换失败')
    }
    zipBlob.value = await resp.blob()
    // 标记全部成功（实际成功/失败信息在 ZIP 内的 _errors.json 中）
    convertResults.value.forEach(r => { r.status = 'done'; r.success = true })
    resultReady.value = true
    toastSuccess('转换完成')
  } catch (e: any) {
    convertResults.value.forEach(r => { r.status = 'done'; r.reason = e.message })
    toastError(e.message || '转换失败')
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

function resetAll() { fileList.value = []; convertResults.value = []; zipBlob.value = null; resultReady.value = false }
</script>

<style scoped>
.hidden { display: none; }
</style>
