<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-to-image',
  name: 'PDF 转图片',
  description: '将 PDF 每页转为 PNG/JPEG 图片，支持自定义 DPI 和质量',
  icon: 'image',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- ====== 左侧：上传区 + 参数设置 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">PDF 文件</label>

      <!-- 上传区 -->
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3 transition-colors flex-shrink-0"
        :class="[stage === 'noFile' ? 'flex-1' : 'h-20', stage === 'processing' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30']"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="stage !== 'processing' && triggerFileInput()"
        @dragover.prevent="stage !== 'processing' && (dragOver = true)"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="stage !== 'processing' && handleDrop($event)"
      >
        <!-- 无文件 -->
        <template v-if="stage === 'noFile'">
          <span class="text-4xl">🖼️</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
          </div>
        </template>

        <!-- 已选文件 / 转换中 -->
        <template v-else>
          <div class="flex items-center gap-3">
            <span class="text-2xl">📄</span>
            <div>
              <p class="text-sm font-medium" style="color: var(--text-primary)">{{ uploadedFile!.name }}</p>
              <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(uploadedFile!.size) }}</p>
            </div>
            <!-- 转换中不显示移除按钮 -->
            <button
              v-if="stage !== 'processing'"
              @click.stop="clearFile"
              class="text-xs underline hover:text-red-500 transition-colors"
              style="color: var(--text-muted)">移除</button>
          </div>
        </template>
      </div>

      <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

      <!-- 参数设置区（转换中只读） -->
      <div class="mt-4 border rounded-lg p-4 space-y-4 transition-opacity"
        :class="stage === 'processing' ? 'opacity-60 pointer-events-none' : ''"
        style="border-color: var(--border-color); background: var(--bg-card)">
        <p class="text-xs font-semibold" style="color: var(--text-secondary)">参数设置</p>

        <!-- DPI -->
        <div>
          <label class="text-xs" style="color: var(--text-secondary)">DPI（分辨率）</label>
          <div class="flex items-center gap-2 mt-1">
            <input v-model.number="dpi" type="number" min="72" max="600"
              class="w-24 px-3 py-1.5 text-xs border rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-400"
              style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
            <span class="text-[10px]" style="color: var(--text-muted)">范围 72 - 600，默认 200</span>
          </div>
          <p v-if="estimatedSize" class="text-[10px] mt-1" style="color: var(--text-muted)">
            预估图像尺寸: {{ estimatedSize.width }} × {{ estimatedSize.height }} px
          </p>
        </div>

        <!-- 图片格式 -->
        <div>
          <label class="text-xs" style="color: var(--text-secondary)">图片格式</label>
          <div class="flex gap-2 mt-1">
            <button v-for="opt in formatOptions" :key="opt.value"
              @click="format = opt.value"
              class="flex-1 py-1.5 text-xs rounded-md border transition-colors"
              :style="format === opt.value
                ? { background: 'var(--accent-color)', color: '#fff', borderColor: 'var(--accent-color)' }
                : { background: 'var(--bg-input)', color: 'var(--text-secondary)', borderColor: 'var(--border-color)' }"
            >{{ opt.label }}</button>
          </div>
        </div>

        <!-- JPEG 质量（仅 JPEG 显示） -->
        <div v-if="format === 'jpeg'">
          <div class="flex items-center justify-between">
            <label class="text-xs" style="color: var(--text-secondary)">JPEG 质量</label>
            <span class="text-xs font-mono font-semibold" style="color: var(--accent-color)">{{ Math.round(quality * 100) }}%</span>
          </div>
          <input v-model.number="quality" type="range" min="0.1" max="1" step="0.05"
            class="w-full mt-1 h-2 rounded-full cursor-pointer quality-slider"
            style="accent-color: var(--accent-color)" />
          <div class="flex justify-between text-[10px] mt-0.5" style="color: var(--text-muted)">
            <span>10%</span><span>50%</span><span>90%</span><span>100%</span>
          </div>
        </div>

        <!-- 页码范围 -->
        <div>
          <label class="text-xs" style="color: var(--text-secondary)">页码范围（可选）</label>
          <input v-model="pageRange" type="text"
            placeholder="如: 1-5 或 1,3,5（留空=全部）"
            class="w-full mt-1 px-3 py-1.5 text-xs border rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-400"
            style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)" />
        </div>

        <!-- 裁剪白边 -->
        <label class="flex items-center gap-2 text-xs cursor-pointer" style="color: var(--text-primary)">
          <input type="checkbox" v-model="trimMargin" class="w-4 h-4 rounded accent-indigo-500" />
          裁剪白色边框
        </label>
      </div>
    </div>

    <!-- ====== 中间：转换按钮 ====== -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startConvert"
        :disabled="stage === 'noFile' || stage === 'processing'"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="stage === 'ready'
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
      >
        <!-- 转换中：旋转 SVG -->
        <svg v-if="stage === 'processing'" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-base">→</span>
        <span class="text-xs">{{ stage === 'processing' ? '转换中' : '转换' }}</span>
      </button>
    </div>

    <!-- ====== 右侧：结果区 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">转换结果</label>
      <div class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3"
        style="border-color: var(--border-color); background: var(--bg-card)">

        <!-- 空闲 / 等待 -->
        <template v-if="stage === 'noFile' || stage === 'ready'">
          <span class="text-4xl">🖼️</span>
          <p class="text-sm" style="color: var(--text-muted)">转换后的图片将在此处下载</p>
        </template>

        <!-- 转换中 -->
        <template v-else-if="stage === 'processing'">
          <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在转换，请稍候...</p>
        </template>

        <!-- 成功 -->
        <template v-else-if="stage === 'done'">
          <span class="text-4xl">✅</span>
          <p class="text-sm font-medium" style="color: var(--text-primary)">转换完成</p>
          <p class="text-xs" style="color: var(--text-muted)">{{ resultInfo }}</p>
          <button @click="downloadResult"
            class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">下载图片</button>
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
import { ref, computed } from 'vue'

/**
 * 页面状态机:
 *   noFile    — 未选择文件
 *   ready     — 已选文件，等待转换
 *   processing — 转换中，禁止移除文件、禁止修改参数
 *   done      — 转换成功
 *   error     — 转换失败
 */
type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const dragOver = ref(false)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')
const resultBlob = ref<Blob | null>(null)
const resultInfo = ref('')
const resultUrl = ref('')

// 参数
const dpi = ref(200)
const format = ref('png')
const quality = ref(0.9)
const pageRange = ref('')
const trimMargin = ref(false)

const formatOptions = [
  { label: 'PNG（无损）', value: 'png' },
  { label: 'JPEG（有损）', value: 'jpeg' },
]

const estimatedSize = computed(() => {
  if (!uploadedFile.value) return null
  const w = Math.round(dpi.value * 8.27)
  const h = Math.round(dpi.value * 11.69)
  return { width: w, height: h }
})

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
    return
  }
  uploadedFile.value = file
  errorMsg.value = ''
  // 选文件后重置结果，进入 ready 状态
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
  resultBlob.value = null
  resultUrl.value = ''
  resultInfo.value = ''
  errorMsg.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function startConvert() {
  if (!uploadedFile.value || stage.value === 'processing') return

  // 进入转换：清空结果区，禁用操作
  clearResult()
  stage.value = 'processing'

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('dpi', String(dpi.value))
    formData.append('format', format.value)
    if (format.value === 'jpeg') {
      formData.append('quality', String(quality.value))
    }
    if (pageRange.value.trim()) {
      formData.append('pageRange', pageRange.value.trim())
    }
    if (trimMargin.value) {
      formData.append('trimMargin', 'true')
    }

    const baseUrl = window.location.origin
    const resp = await fetch(`${baseUrl}/api/pdf/to-image`, {
      method: 'POST',
      body: formData,
    })

    if (!resp.ok) {
      const errText = await resp.text()
      throw new Error(errText || `HTTP ${resp.status}`)
    }

    const blob = await resp.blob()
    resultBlob.value = blob

    const contentType = resp.headers.get('content-type') || ''
    if (contentType.includes('zip')) {
      resultInfo.value = `ZIP 文件 (${formatSize(blob.size)})`
    } else {
      resultInfo.value = `图片 (${formatSize(blob.size)})`
    }
    resultUrl.value = URL.createObjectURL(blob)
    stage.value = 'done'
  } catch (e: any) {
    errorMsg.value = e.message || '转换失败'
    stage.value = 'error'
  }
}

function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  const ext = format.value === 'jpeg' ? 'jpg' : 'png'
  a.download = resultBlob.value.type.includes('zip') ? 'pdf-images.zip' : `output.${ext}`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
/* 滑块控件在 Tailwind v4 的 appearance-none 下会消失，显式恢复原生渲染 */
.quality-slider {
  appearance: auto;
  -webkit-appearance: auto;
}
</style>
