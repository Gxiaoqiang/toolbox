<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-merge',
  name: 'PDF 合并',
  description: '将多个 PDF 文件按顺序合并为一个 PDF',
  icon: 'file-text',
  category: 'file',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：上传区 + 文件列表 — 始终可见 -->
    <div class="w-2/5 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">PDF 文件</label>

      <!-- 虚线拖拽上传区 — 始终可见 -->
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30 flex-shrink-0"
        :class="fileList.length === 0 ? 'flex-1 gap-3' : 'h-20 gap-1'"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <template v-if="fileList.length === 0">
          <span class="text-4xl">📤</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择 · 最多 10 个 · 每个 ≤5MB</p>
          </div>
        </template>
        <template v-else>
          <span class="text-xl">📤</span>
          <p class="text-xs" style="color: var(--text-muted)">拖拽或点击添加更多文件（{{ fileList.length }}/10）</p>
        </template>
      </div>

      <!-- 文件列表 — 拖拽排序 -->
      <div v-if="fileList.length > 0" class="flex-1 flex flex-col min-h-0 mt-3">
        <div class="flex items-center justify-between mb-2 flex-shrink-0">
          <span class="text-xs" style="color: var(--text-muted)">已选文件（拖拽排序）</span>
          <button v-if="!processing" @click="triggerFileInput" :disabled="fileList.length >= 10"
            class="text-xs underline" style="color: var(--accent-color)">+ 添加</button>
        </div>
        <div class="flex-1 overflow-y-auto space-y-1">
          <div
            v-for="(f, idx) in fileList" :key="f.uid"
            class="flex items-center gap-2 px-2 py-2 rounded-lg border transition-all group"
            :class="{ 'opacity-50': dragIndex === idx }"
            :style="{ borderColor: f.error ? '#f87171' : 'var(--border-color)', background: dragOverIndex === idx ? 'var(--accent-light)' : 'var(--bg-card)' }"
            draggable="true"
            @dragstart="onDragStart(idx)"
            @dragover.prevent="onDragOver(idx)"
            @dragleave="onDragLeave"
            @drop.prevent="onDrop(idx)"
            @dragend="onDragEnd"
          >
            <!-- 拖拽手柄 -->
            <span
              class="flex-shrink-0 text-xs cursor-grab active:cursor-grabbing select-none"
              :class="processing ? 'text-slate-300' : ''"
              style="color: var(--text-muted)"
              :title="processing ? '' : '拖拽排序'"
            >⋮⋮</span>
            <!-- 序号 -->
            <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-semibold flex-shrink-0"
              style="background: var(--accent-light); color: var(--accent-color)">{{ idx + 1 }}</span>
            <!-- 文件名 -->
            <span class="flex-1 text-sm truncate" :style="{ color: f.error ? '#f87171' : 'var(--text-primary)' }">{{ f.file.name }}</span>
            <!-- 页数 -->
            <span v-if="f.pages > 0" class="text-[11px] flex-shrink-0" style="color: var(--text-muted)">{{ f.pages }} 页</span>
            <!-- 文件大小 -->
            <span class="text-[11px] flex-shrink-0" style="color: var(--text-muted)">{{ formatSize(f.file.size) }}</span>
            <!-- 错误标记 -->
            <span v-if="f.error" class="text-xs text-red-400 flex-shrink-0" :title="f.error">⚠</span>
            <!-- 删除按钮 -->
            <button v-if="!processing" @click="removeFile(idx)"
              class="w-5 h-5 rounded flex items-center justify-center text-xs flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity hover:bg-red-50"
              style="color: var(--text-muted)">✕</button>
          </div>
        </div>
        <button v-if="!processing" @click="clearAll"
          class="mt-2 text-xs underline self-start" style="color: var(--text-muted)">清空全部</button>
      </div>

      <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" multiple class="hidden" @change="handleFileSelect" />
    </div>

    <!-- 中间：合并按钮 — 始终可见 -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startMerge"
        :disabled="fileList.length < 2 || processing || hasErrors"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="fileList.length >= 2 && !processing && !hasErrors
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
        :title="fileList.length < 2 ? '请至少选择 2 个 PDF 文件' : processing ? '正在合并...' : ''"
      >
        <svg v-if="processing" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-lg leading-none">▶</span>
        <span class="text-xs leading-tight">{{ processing ? '合并中' : '合并' }}</span>
      </button>
    </div>

    <!-- 右侧：结果区 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">合并结果</label>

      <!-- 状态 1: 空态 -->
      <div v-if="fileList.length === 0 && !mergeResult" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <span class="text-3xl">📄</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">请先选择 PDF 文件</p>
        </div>
      </div>

      <!-- 状态 2: 就绪 -->
      <div v-else-if="fileList.length > 0 && !processing && !mergeResult" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <span class="text-3xl">📑</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">
            已选择 {{ fileList.length }} 个文件，点击"合并"开始
          </p>
        </div>
      </div>

      <!-- 状态 3: 合并中 -->
      <div v-else-if="processing" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <svg class="animate-spin mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" opacity="0.15" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <p class="text-sm" style="color: var(--text-secondary)">正在合并 {{ fileList.length }} 个 PDF 文件...</p>
        </div>
      </div>

      <!-- 状态 4: 完成 -->
      <div v-else-if="mergeResult" class="flex-1 flex flex-col items-center justify-center">
        <div class="text-center">
          <span class="text-4xl">✅</span>
          <p class="text-sm font-semibold mt-2" style="color: var(--text-primary)">合并完成</p>
          <p class="text-xs mt-1" style="color: var(--text-muted)">
            已合并 {{ fileList.length }} 个文件，共 {{ mergeResult.totalPages }} 页
          </p>

          <button @click="downloadMerged"
            class="mt-5 px-6 py-2.5 rounded-lg text-sm font-medium bg-indigo-500 hover:bg-indigo-600 text-white transition-colors">
            📥 下载 merged.pdf
          </button>
          <button @click="resetMerge"
            class="block mx-auto mt-3 text-xs underline" style="color: var(--text-muted)">重新合并</button>
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
const MAX_SIZE = 5 * 1024 * 1024 // 5MB
const MAX_FILES = 10

let uidCounter = 0

interface FileItem {
  uid: number
  file: File
  error: string
  pages: number
}

interface MergeResult {
  blob: Blob
  totalPages: number
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const processing = ref(false)
const fileList = ref<FileItem[]>([])
const mergeResult = ref<MergeResult | null>(null)

// 拖拽排序状态
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

const hasErrors = computed(() => fileList.value.some(f => f.error))

function triggerFileInput() {
  fileInputRef.value?.click()
}

function addFiles(files: FileList | File[]) {
  const arr = Array.from(files)
  for (const f of arr) {
    if (fileList.value.length >= MAX_FILES) {
      toastError(`最多合并 ${MAX_FILES} 个文件`)
      break
    }
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (ext !== 'pdf') {
      toastError(`不支持的文件格式: ${f.name}`)
      continue
    }
    if (f.size === 0) {
      toastError(`文件为空: ${f.name}`)
      continue
    }
    if (f.size > MAX_SIZE) {
      toastError(`文件过大 (超过5MB): ${f.name}`)
      continue
    }
    fileList.value.push({ uid: ++uidCounter, file: f, error: '', pages: 0 })
  }
  // 异步估算页数
  for (const item of fileList.value) {
    if (item.pages === 0) estimatePages(item)
  }
  resetResult()
}

async function estimatePages(item: FileItem) {
  try {
    const buffer = await item.file.arrayBuffer()
    const text = new TextDecoder('latin1').decode(new Uint8Array(buffer))
    const matches = text.match(/\/Type\s*\/Page[^s]/g)
    item.pages = matches ? matches.length : 0
  } catch {
    item.pages = 0
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

function removeFile(idx: number) {
  fileList.value.splice(idx, 1)
  resetResult()
}

function clearAll() {
  fileList.value = []
  resetResult()
}

function resetResult() {
  mergeResult.value = null
}

// ======== 拖拽排序 ========

function onDragStart(idx: number) {
  if (processing.value) return
  dragIndex.value = idx
}

function onDragOver(idx: number) {
  if (dragIndex.value === null) return
  dragOverIndex.value = idx
}

function onDragLeave() {
  dragOverIndex.value = null
}

function onDrop(idx: number) {
  if (dragIndex.value === null || dragIndex.value === idx) {
    dragIndex.value = null
    dragOverIndex.value = null
    return
  }
  const item = fileList.value.splice(dragIndex.value, 1)[0]
  fileList.value.splice(idx, 0, item)
  dragIndex.value = null
  dragOverIndex.value = null
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

// ======== 合并 ========

async function startMerge() {
  if (fileList.value.length < 2 || hasErrors.value || processing.value) return
  processing.value = true
  mergeResult.value = null

  const formData = new FormData()
  fileList.value.forEach(f => formData.append('files', f.file))

  try {
    const resp = await fetch('/api/pdf/merge', { method: 'POST', body: formData })
    if (!resp.ok) {
      const errJson = await resp.json().catch(() => ({ message: '合并失败' }))
      throw new Error(errJson.message || '合并失败')
    }
    const blob = await resp.blob()
    // 估算总页数
    const totalPages = fileList.value.reduce((sum, f) => sum + f.pages, 0)
    mergeResult.value = { blob, totalPages }
    toastSuccess('PDF 合并完成')
  } catch (e: any) {
    toastError(e.message || 'PDF 合并失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

function downloadMerged() {
  if (!mergeResult.value) return
  const url = URL.createObjectURL(mergeResult.value.blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'merged.pdf'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function resetMerge() {
  mergeResult.value = null
  // 不重置 fileList，方便用户调整文件后重新合并
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}
</script>

<style scoped>
.hidden { display: none; }
</style>
