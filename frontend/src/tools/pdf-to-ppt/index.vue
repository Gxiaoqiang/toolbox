<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-to-ppt',
  name: 'PDF 转 PPT / Word',
  description: '将 PDF 转为可编辑的 PPTX 或 Word，支持算法还原与 AI 重排两种方式',
  icon: 'slideshow',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- ===== 上传区 ===== -->
    <div class="flex-shrink-0 pb-3">
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3 transition-colors"
        :class="[
          stage === 'converting' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30'
        ]"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="stage !== 'converting' && triggerInput()"
        @dragover.prevent="stage !== 'converting' && (dragOver = true)"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="stage !== 'converting' && handleDrop($event)"
      >
        <!-- 未选文件 -->
        <template v-if="!file">
          <span class="text-4xl">📄</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 仅支持 .pdf · 最大 50MB</p>
          </div>
        </template>

        <!-- 已选文件 -->
        <template v-else>
          <div class="flex items-center gap-3">
            <span class="text-2xl">📄</span>
            <div>
              <p class="text-sm font-medium" style="color: var(--text-primary)">{{ file.name }}</p>
              <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(file.size) }}</p>
            </div>
            <button
              v-if="stage !== 'converting'"
              @click.stop="clearFile"
              class="text-xs underline hover:text-red-500 transition-colors"
              style="color: var(--text-muted)"
            >移除</button>
          </div>
        </template>
      </div>

      <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleSelect" />
    </div>

    <!-- ===== 输出格式选择 ===== -->
    <div v-if="file && stage !== 'done'" class="flex-shrink-0 pb-3">
      <p class="text-xs mb-2" style="color: var(--text-muted)">输出格式</p>
      <div class="flex gap-2">
        <label class="flex-1 flex items-center gap-2 cursor-pointer px-3 py-2 rounded-lg border transition-colors"
          :style="format === 'ppt'
            ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
            : { borderColor: 'var(--border-color)', background: 'var(--bg-card)' }">
          <input type="radio" v-model="format" value="ppt" class="accent-current" />
          <div>
            <p class="text-sm font-medium" style="color: var(--text-primary)">PPTX</p>
            <p class="text-xs" style="color: var(--text-muted)">PowerPoint 演示</p>
          </div>
        </label>
        <label class="flex-1 flex items-center gap-2 cursor-pointer px-3 py-2 rounded-lg border transition-colors"
          :style="format === 'word'
            ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
            : { borderColor: 'var(--border-color)', background: 'var(--bg-card)' }">
          <input type="radio" v-model="format" value="word" class="accent-current" />
          <div>
            <p class="text-sm font-medium" style="color: var(--text-primary)">Word</p>
            <p class="text-xs" style="color: var(--text-muted)">文档 · 含表格与图片</p>
          </div>
        </label>
      </div>
    </div>

    <!-- ===== 转换方式选择 ===== -->
    <div v-if="file && stage !== 'done'" class="flex-shrink-0 pb-3">
      <p class="text-xs mb-2" style="color: var(--text-muted)">转换方式</p>
      <div class="flex flex-col gap-2">
        <label class="flex items-start gap-2 cursor-pointer px-3 py-2 rounded-lg border transition-colors"
          :style="engine === 'algorithm'
            ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
            : { borderColor: 'var(--border-color)', background: 'var(--bg-card)' }">
          <input type="radio" v-model="engine" value="algorithm" class="mt-1 accent-current" />
          <div>
            <p class="text-sm font-medium" style="color: var(--text-primary)">算法还原 <span class="text-xs font-normal" style="color: var(--text-muted)">（离线，推荐）</span></p>
            <p class="text-xs mt-0.5" style="color: var(--text-muted)">按原样还原版面，文字可编辑；速度快、无需联网</p>
          </div>
        </label>
        <label class="flex items-start gap-2 cursor-pointer px-3 py-2 rounded-lg border transition-colors"
          :style="engine === 'ai'
            ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
            : { borderColor: 'var(--border-color)', background: 'var(--bg-card)' }">
          <input type="radio" v-model="engine" value="ai" class="mt-1 accent-current" />
          <div>
            <p class="text-sm font-medium" style="color: var(--text-primary)">AI 重排</p>
            <p class="text-xs mt-0.5" style="color: var(--text-muted)">大模型提炼为标题+要点；需已配置大模型</p>
          </div>
        </label>
      </div>
    </div>

    <!-- ===== 中间：结果区 ===== -->
    <div class="flex-1 overflow-y-auto min-h-0">
      <!-- 转换中 -->
      <div v-if="stage === 'converting'" class="flex flex-col items-center justify-center h-full gap-3">
        <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p class="text-sm" style="color: var(--text-muted)">正在{{ engine === 'ai' ? 'AI 重排' : '转换' }}，请稍候...</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="stage === 'error'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">⚠️</span>
        <p class="text-sm text-center px-4" style="color: #ef4444">{{ errorMsg }}</p>
      </div>

      <!-- 完成 -->
      <div v-else-if="stage === 'done'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">✅</span>
        <p class="text-sm font-medium" style="color: var(--text-primary)">转换完成</p>
        <p class="text-xs" style="color: var(--text-muted)">{{ resultInfo }}</p>
        <button @click="downloadResult"
          class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
          style="background: var(--accent-color)">📥 下载 {{ format === 'word' ? 'Word' : 'PPTX' }}</button>
        <button @click="resetToReady" class="text-xs underline" style="color: var(--text-muted)">返回重新选择</button>
      </div>

      <!-- 空态 -->
      <div v-else class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-5xl">📄</span>
        <p class="text-sm" style="color: var(--text-muted)">请先上传 PDF 文件</p>
      </div>
    </div>

    <!-- ===== 底部：转换按钮 ===== -->
    <div v-if="file && stage !== 'converting' && stage !== 'done'" class="flex-shrink-0 pt-3">
      <button
        @click="startConvert"
        class="flex items-center gap-2 px-5 py-2 rounded-lg text-sm font-medium transition-all w-full justify-center"
        style="background: var(--accent-color); color: #fff"
      >
        <span class="text-base leading-none">▶</span>
        <span>转换为 {{ format === 'word' ? 'Word' : 'PPT' }}{{ engine === 'ai' ? '（AI 重排）' : '' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const { success: toastSuccess, error: toastError } = useToast()

type Stage = 'idle' | 'converting' | 'done' | 'error'

const fileInputRef = ref<HTMLInputElement>()
const file = ref<File | null>(null)
const engine = ref<'algorithm' | 'ai'>('algorithm')
const format = ref<'ppt' | 'word'>('ppt')
const dragOver = ref(false)
const stage = ref<Stage>('idle')
const errorMsg = ref('')
const resultBlob = ref<Blob | null>(null)
const resultInfo = ref('')
const baseName = ref('')

function triggerInput() {
  fileInputRef.value?.click()
}

function handleSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) setFile(files[0])
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  const files = e.dataTransfer?.files
  if (files && files.length > 0) setFile(files[0])
}

function setFile(f: File) {
  if (f.size > 50 * 1024 * 1024) {
    toastError('文件不能超过 50MB')
    return
  }
  file.value = f
  errorMsg.value = ''
  resultBlob.value = null
  resultInfo.value = ''
  stage.value = 'idle'
}

function clearFile() {
  file.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  errorMsg.value = ''
  resultBlob.value = null
  resultInfo.value = ''
  stage.value = 'idle'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function startConvert() {
  if (!file.value || stage.value === 'converting') return

  stage.value = 'converting'
  resultBlob.value = null
  resultInfo.value = ''

  const formData = new FormData()
  formData.append('file', file.value)
  formData.append('engine', engine.value)
  formData.append('format', format.value)

  try {
    const resp = await fetch('/api/pdf/to-ppt', { method: 'POST', body: formData })

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '转换失败' }))
      throw new Error(err.message || `HTTP ${resp.status}`)
    }

    resultBlob.value = await resp.blob()
    resultInfo.value = format.value === 'word'
      ? `Word 文件 (${formatSize(resultBlob.value.size)})`
      : `PPTX 文件 (${formatSize(resultBlob.value.size)})`
    baseName.value = file.value.name.replace(/\.pdf$/i, '')
    stage.value = 'done'
    toastSuccess(format.value === 'word'
      ? (engine.value === 'ai' ? 'AI 重排完成' : 'PDF 转 Word 成功')
      : (engine.value === 'ai' ? 'AI 重排完成' : 'PDF 转 PPT 成功'))
  } catch (e: any) {
    errorMsg.value = e.message || '转换失败'
    stage.value = 'error'
    toastError(e.message || '转换失败')
  }
}

function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = (baseName.value || 'output') + (format.value === 'word' ? '.docx' : '.pptx')
  a.click()
  URL.revokeObjectURL(url)
}

function resetToReady() {
  resultBlob.value = null
  resultInfo.value = ''
  errorMsg.value = ''
  stage.value = 'idle'
}
</script>

<style scoped>
.hidden { display: none; }
</style>
