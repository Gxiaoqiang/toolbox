<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：上传区 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">PDF 文件</label>

      <!-- 拖拽上传区域 -->
      <div
        v-if="!uploadedFile"
        class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3 cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <span class="text-4xl">📤</span>
        <div class="text-center">
          <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
          <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
        </div>
      </div>

      <!-- 已上传文件信息 -->
      <div
        v-else
        class="flex-1 border rounded-lg p-4 flex flex-col items-center justify-center gap-3"
        style="border-color: var(--border-color); background: var(--bg-card)"
      >
        <div class="w-14 h-14 rounded-xl flex items-center justify-center" style="background: var(--accent-light)">
          <span class="text-2xl">📄</span>
        </div>
        <div class="text-center">
          <p class="text-sm font-semibold" style="color: var(--text-primary)">{{ uploadedFile.name }}</p>
          <p class="text-xs mt-0.5" style="color: var(--text-muted)">
            {{ totalPages > 0 ? totalPages + ' 页' : '' }}
            {{ totalPages > 0 ? '·' : '' }}
            {{ formatFileSize(uploadedFile.size) }}
          </p>
        </div>
        <button
          @click="resetFile"
          class="text-xs underline transition-colors"
          style="color: var(--text-muted)"
        >重新选择</button>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept=".pdf,application/pdf"
        class="hidden"
        @change="handleFileSelect"
      />

      <!-- 错误提示 -->
      <p v-if="uploadError" class="mt-2 text-xs text-red-500">{{ uploadError }}</p>
    </div>

    <!-- 右侧：配置区 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-3 flex-shrink-0" style="color: var(--text-secondary)">切分配置</label>

      <!-- 模式选择 -->
      <div class="space-y-2 mb-4">
        <label
          v-for="opt in modeOptions"
          :key="opt.value"
          class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border cursor-pointer transition-colors"
          :style="{
            borderColor: mode === opt.value ? 'var(--accent-color)' : 'var(--border-color)',
            background: mode === opt.value ? 'var(--accent-light)' : 'var(--bg-card)'
          }"
        >
          <input
            type="radio"
            :value="opt.value"
            v-model="mode"
            class="sr-only"
          />
          <div
            class="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
            :style="{ borderColor: mode === opt.value ? 'var(--accent-color)' : 'var(--text-muted)' }"
          >
            <div v-if="mode === opt.value" class="w-2 h-2 rounded-full" style="background: var(--accent-color)"></div>
          </div>
          <div>
            <p class="text-sm font-medium" style="color: var(--text-primary)">{{ opt.label }}</p>
            <p class="text-xs" style="color: var(--text-muted)">{{ opt.hint }}</p>
          </div>
        </label>
      </div>

      <!-- 动态参数区 -->
      <div v-if="mode === 'by-range'" class="mb-4">
        <label class="text-xs font-medium" style="color: var(--text-secondary)">页码范围</label>
        <input
          v-model="pageRangeInput"
          type="text"
          placeholder="1,3,5-8,10"
          class="w-full mt-1 px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 font-mono"
          style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)"
          @input="validatePageRange"
        />
        <!-- 实时校验反馈 -->
        <p v-if="rangeValidation.message" class="mt-1 text-xs" :class="rangeValidation.valid ? 'text-emerald-500' : 'text-red-500'">
          {{ rangeValidation.valid ? '✓ ' : '⚠ ' }}{{ rangeValidation.message }}
        </p>
      </div>

      <div v-if="mode === 'by-n'" class="mb-4">
        <label class="text-xs font-medium" style="color: var(--text-secondary)">每 N 页拆分为一个文件</label>
        <input
          v-model.number="everyN"
          type="number"
          min="1"
          placeholder="3"
          class="w-full mt-1 px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
          style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)"
        />
        <p v-if="everyN > 0 && totalPages > 0" class="mt-1 text-xs" style="color: var(--text-muted)">
          将生成 {{ Math.ceil(totalPages / everyN) }} 个文件（最后一组{{ totalPages % everyN > 0 ? totalPages % everyN + ' 页' : everyN + ' 页' }}）
        </p>
      </div>

      <!-- 元数据选项 -->
      <label class="flex items-center gap-2 mb-5 cursor-pointer">
        <input type="checkbox" v-model="preserveMeta" class="w-4 h-4 rounded accent-indigo-500" />
        <span class="text-sm" style="color: var(--text-secondary)">保留原始 PDF 元数据（标题、作者等）</span>
      </label>

      <!-- 操作按钮 -->
      <button
        v-if="!zipResult"
        @click="executeSplit"
        :disabled="!canExecute || processing"
        class="w-full py-2.5 rounded-lg text-sm font-medium transition-all"
        :class="canExecute && !processing ? 'bg-indigo-500 hover:bg-indigo-600 text-white' : 'cursor-not-allowed opacity-50'"
        :style="!(canExecute && !processing) ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)' } : {}"
      >
        <span v-if="processing" class="inline-block animate-spin mr-1">⟳</span>
        {{ processing ? '正在拆分...' : '执行拆分' }}
      </button>

      <!-- 结果区 -->
      <div
        v-else
        class="border rounded-lg p-4 text-center"
        style="border-color: var(--accent-color); background: var(--accent-light)"
      >
        <p class="text-sm font-semibold mb-1" style="color: var(--accent-color)">✓ 拆分完成</p>
        <p class="text-xs mb-3" style="color: var(--text-secondary)">已生成 {{ zipFileCount }} 个 PDF 文件</p>
        <button
          @click="downloadZip"
          class="px-6 py-2 rounded-lg text-sm font-medium bg-indigo-500 hover:bg-indigo-600 text-white transition-colors"
        >📥 下载 ZIP</button>
        <button
          @click="resetAfterSplit"
          class="block mx-auto mt-2 text-xs underline"
          style="color: var(--text-muted)"
        >继续拆分</button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-splitter',
  name: 'PDF 切分',
  description: 'PDF 逐页拆分、按页码范围、每 N 页拆分',
  icon: 'file-text',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const { success, error: toastError } = useToast()

// ======== 上传相关 ========
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadedFile = ref<File | null>(null)
const uploadError = ref('')
const dragOver = ref(false)
const totalPages = ref(0)

const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files[0]) {
    validateAndSetFile(input.files[0])
  }
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) {
    validateAndSetFile(e.dataTransfer.files[0])
  }
}

async function validateAndSetFile(file: File) {
  uploadError.value = ''

  // 扩展名校验
  if (!file.name.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
    uploadError.value = '请上传 PDF 格式的文件'
    return
  }

  // 大小校验
  if (file.size > MAX_FILE_SIZE) {
    uploadError.value = '文件大小不能超过 50MB'
    return
  }

  uploadedFile.value = file

  // 尝试读取总页数（通过后端或前端读取）
  try {
    const arrayBuffer = await file.arrayBuffer()
    // 简单方法：通过搜索 PDF 页面标记估算页数
    const uint8 = new Uint8Array(arrayBuffer)
    const text = new TextDecoder('latin1').decode(uint8)
    const matches = text.match(/\/Type\s*\/Page[^s]/g)
    totalPages.value = matches ? matches.length : 0
  } catch {
    totalPages.value = 0
  }
}

function resetFile() {
  uploadedFile.value = null
  totalPages.value = 0
  uploadError.value = ''
  zipResult.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

// ======== 配置相关 ========
const modeOptions = [
  { value: 'by-page' as const, label: '逐页拆分', hint: '每一页生成一个独立 PDF 文件' },
  { value: 'by-range' as const, label: '按页码范围', hint: '自定义页码范围，如 1,3,5-8,10' },
  { value: 'by-n' as const, label: '每 N 页拆分', hint: '按固定页数等量拆分' },
]

const mode = ref<'by-page' | 'by-range' | 'by-n'>('by-page')
const pageRangeInput = ref('')
const everyN = ref(3)
const preserveMeta = ref(false)
const processing = ref(false)

// ======== 页码范围校验 ========
const rangeValidation = ref<{ valid: boolean; message: string }>({ valid: false, message: '' })

function validatePageRange() {
  const input = pageRangeInput.value.trim()
  if (!input) {
    rangeValidation.value = { valid: false, message: '' }
    return
  }

  // 格式校验
  if (!/^[0-9,\- ]+$/.test(input)) {
    rangeValidation.value = { valid: false, message: '格式不正确，请输入如 "1,3,5-8"' }
    return
  }

  const parts = input.split(',')
  const seenPages = new Set<number>()

  for (const part of parts) {
    const trimmed = part.trim()
    if (!trimmed) {
      rangeValidation.value = { valid: false, message: '格式不正确，存在空值' }
      return
    }

    if (trimmed.includes('-')) {
      const pair = trimmed.split('-')
      if (pair.length !== 2) {
        rangeValidation.value = { valid: false, message: '区间格式错误: ' + trimmed }
        return
      }
      const start = parseInt(pair[0], 10)
      const end = parseInt(pair[1], 10)
      if (isNaN(start) || isNaN(end)) {
        rangeValidation.value = { valid: false, message: '区间格式错误: ' + trimmed }
        return
      }
      if (start > end) {
        rangeValidation.value = { valid: false, message: '区间起始页大于结束页: ' + trimmed }
        return
      }
      if (totalPages.value > 0 && (start < 1 || end > totalPages.value)) {
        rangeValidation.value = { valid: false, message: `页码 ${start < 1 ? start : end} 超出文档总页数（共 ${totalPages.value} 页）` }
        return
      }
      // 重叠检查
      for (let p = start; p <= end; p++) {
        if (seenPages.has(p)) {
          rangeValidation.value = { valid: false, message: `页码 ${p} 存在重复或重叠` }
          return
        }
        seenPages.add(p)
      }
    } else {
      const page = parseInt(trimmed, 10)
      if (isNaN(page)) {
        rangeValidation.value = { valid: false, message: '页码格式错误: ' + trimmed }
        return
      }
      if (totalPages.value > 0 && (page < 1 || page > totalPages.value)) {
        rangeValidation.value = { valid: false, message: `页码 ${page} 超出文档总页数（共 ${totalPages.value} 页）` }
        return
      }
      if (seenPages.has(page)) {
        rangeValidation.value = { valid: false, message: `页码 ${page} 存在重复或重叠` }
        return
      }
      seenPages.add(page)
    }
  }

  const fileCount = parts.length
  rangeValidation.value = { valid: true, message: `将生成 ${fileCount} 个 PDF 文件` }
}

// ======== 执行拆分 ========
const canExecute = computed(() => {
  if (!uploadedFile.value) return false
  if (mode.value === 'by-range') return rangeValidation.value.valid
  if (mode.value === 'by-n') return everyN.value > 0
  return true
})

const zipResult = ref<Blob | null>(null)
const zipFileCount = ref(0)

async function executeSplit() {
  if (!uploadedFile.value || !canExecute.value) return

  processing.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('mode', mode.value)
    if (mode.value === 'by-range') {
      formData.append('pages', pageRangeInput.value.trim())
    }
    if (mode.value === 'by-n') {
      formData.append('everyN', String(everyN.value))
    }
    formData.append('preserveMeta', String(preserveMeta.value))

    const resp = await fetch('/api/pdf/split', { method: 'POST', body: formData })

    if (!resp.ok) {
      const err = await resp.json()
      throw new Error(err.message || '处理失败')
    }

    zipResult.value = await resp.blob()

    // 估算生成文件数
    if (mode.value === 'by-page') {
      zipFileCount.value = totalPages.value
    } else if (mode.value === 'by-range') {
      zipFileCount.value = pageRangeInput.value.trim().split(',').length
    } else {
      zipFileCount.value = Math.ceil(totalPages.value / everyN.value)
    }

    success('PDF 拆分完成')
  } catch (e: any) {
    toastError(e.message || 'PDF 处理失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

function downloadZip() {
  if (!zipResult.value) return
  const url = URL.createObjectURL(zipResult.value)
  const a = document.createElement('a')
  a.href = url
  a.download = 'pdf-split-result.zip'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function resetAfterSplit() {
  zipResult.value = null
  zipFileCount.value = 0
}
</script>

<style scoped>
.hidden {
  display: none;
}

input[type="number"]::-webkit-inner-spin-button,
input[type="number"]::-webkit-outer-spin-button {
  opacity: 1;
}
</style>
