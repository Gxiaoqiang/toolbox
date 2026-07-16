<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-compress',
  name: 'PDF 压缩',
  description: '压缩 PDF 文件体积，5 档预设从极度压缩到极限画质',
  icon: 'file-text',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- ====== 左侧：上传区 + 压缩等级选择 ====== -->
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
        <template v-if="stage === 'noFile'">
          <span class="text-4xl">📦</span>
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

      <!-- 压缩等级选择（转换中只读） -->
      <div class="mt-4 border rounded-lg p-3 transition-opacity"
        :class="stage === 'processing' ? 'opacity-60 pointer-events-none' : ''"
        style="border-color: var(--border-color); background: var(--bg-card)">
        <p class="text-xs font-semibold mb-2" style="color: var(--text-secondary)">压缩等级</p>

        <!-- 双列网格 — 5 档无需滚动 -->
        <div class="grid grid-cols-2 gap-1.5">
          <div
            v-for="item in displayLevels" :key="item.value"
            @click="stage !== 'processing' && (level = item.value)"
            class="group relative border rounded-md py-1.5 px-2.5 cursor-pointer transition-all"
            :style="level === item.value
              ? { borderColor: 'var(--accent-color)', background: 'var(--accent-light)' }
              : { borderColor: 'var(--border-color)', background: 'var(--bg-card-hover)' }"
          >
            <!-- 标题行 -->
            <div class="flex items-center gap-1.5">
              <div class="w-3.5 h-3.5 rounded-full border-2 flex-shrink-0 flex items-center justify-center"
                :style="level === item.value
                  ? { borderColor: 'var(--accent-color)' }
                  : { borderColor: 'var(--border-color)' }">
                <div v-if="level === item.value" class="w-1.5 h-1.5 rounded-full" style="background: var(--accent-color)"></div>
              </div>
              <span class="text-xs font-semibold truncate" style="color: var(--text-primary)">{{ item.label }}</span>
              <span v-if="item.isDefault" class="text-[9px] px-1 rounded-full font-medium flex-shrink-0"
                style="background: var(--accent-light); color: var(--accent-color)">默认</span>
              <span v-if="item.warning" class="text-[9px] flex-shrink-0" title="需注意取舍">⚠</span>
            </div>
            <!-- 描述 — 两行截断，hover 浮层看全部 -->
            <p class="text-[11px] mt-0.5 leading-snug line-clamp-2" style="color: var(--text-muted)">{{ item.description }}</p>
            <!-- hover 浮层 — 完整说明 -->
            <div
              class="absolute left-0 right-0 bottom-full mb-1 px-2.5 py-2 rounded-md shadow-lg
                     opacity-0 pointer-events-none group-hover:opacity-100 group-hover:pointer-events-auto
                     transition-opacity z-10"
              style="background: var(--text-primary); color: var(--bg-main)">
              <p class="text-[11px] leading-relaxed whitespace-normal">{{ item.description }}</p>
              <div class="absolute left-4 top-full w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent"
                style="border-top-color: var(--text-primary)"></div>
            </div>
          </div>
          <!-- 占位补全 6 格（5=3+2） -->
          <div class="border border-transparent rounded-md py-1.5 px-2.5" style="visibility: hidden">
            <div class="flex items-center gap-1.5">
              <div class="w-3.5 h-3.5 rounded-full border-2"></div>
              <span class="text-xs">占位</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 中间：转换按钮 ====== -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startCompress"
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
        <span class="text-xs">{{ stage === 'processing' ? '压缩中' : '压缩' }}</span>
      </button>
    </div>

    <!-- ====== 右侧：结果区 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">压缩结果</label>
      <div class="flex-1 border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3"
        style="border-color: var(--border-color); background: var(--bg-card)">

        <!-- 空闲 / 等待 -->
        <template v-if="stage === 'noFile' || stage === 'ready'">
          <span class="text-4xl">📦</span>
          <p class="text-sm" style="color: var(--text-muted)">
            {{ stage === 'noFile' ? '上传 PDF 后选择压缩等级' : '点击"压缩"开始' }}
          </p>
        </template>

        <!-- 压缩中 -->
        <template v-else-if="stage === 'processing'">
          <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在压缩，请稍候...</p>
        </template>

        <!-- 完成 -->
        <template v-else-if="stage === 'done' && compressResult">
          <span class="text-4xl">✅</span>
          <p class="text-sm font-semibold" style="color: var(--text-primary)">压缩完成</p>

          <!-- 体积对比 -->
          <div class="w-full max-w-xs space-y-2 px-4">
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">原始大小</span>
              <span class="font-mono font-medium" style="color: var(--text-primary)">{{ formatSize(compressResult.originalSize) }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">压缩后</span>
              <span class="font-mono font-medium" style="color: var(--accent-color)">{{ formatSize(compressResult.compressedSize) }}</span>
            </div>

            <!-- 压缩率进度条 -->
            <div class="mt-2">
              <div class="flex justify-between text-xs mb-1">
                <span style="color: var(--text-muted)">压缩率</span>
                <span class="font-mono font-semibold" :style="compressResult.ratio < 0 ? { color: '#d97706' } : { color: 'var(--accent-color)' }">
                  {{ compressResult.ratio >= 0 ? '↓' : '↑' }} {{ Math.abs(compressResult.ratio).toFixed(1) }}%
                </span>
              </div>
              <div class="w-full h-2 rounded-full overflow-hidden" style="background: var(--bg-card-hover)">
                <div
                  class="h-full rounded-full transition-all duration-500"
                  :style="{
                    width: Math.min(Math.abs(compressResult.ratio), 100) + '%',
                    background: compressResult.ratio < 0
                      ? 'linear-gradient(90deg, #fbbf24, #f59e0b)'
                      : 'linear-gradient(90deg, #22c55e, #10b981)'
                  }">
                </div>
              </div>
            </div>

            <!-- 变大提示 -->
            <p v-if="compressResult.ratio < 0" class="text-xs text-center" style="color: #d97706">
              ⚠ 该文件不适合压缩，压缩后体积反而增大
            </p>
            <!-- 无效果提示 -->
            <p v-else-if="compressResult.ratio < 1" class="text-xs text-center" style="color: var(--text-muted)">
              该文件无可压缩内容（可能为纯文本 PDF）
            </p>
          </div>

          <button @click="downloadResult"
            class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">下载压缩文件</button>

          <p class="text-[10px]" style="color: var(--text-muted)">
            可切换压缩等级后重新压缩
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
import { ref } from 'vue'

/**
 * 页面状态机:
 *   noFile     — 未选择文件
 *   ready      — 已选文件，等待压缩
 *   processing — 压缩中，禁止移除文件、禁止切换等级
 *   done       — 压缩完成，展示结果
 *   error      — 压缩失败
 */
type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

interface CompressResult {
  blob: Blob
  originalSize: number
  compressedSize: number
  ratio: number   // 压缩率百分比，正数=缩小，负数=变大
}

interface LevelOption {
  value: number
  label: string
  description: string
  isDefault: boolean
  warning: boolean
}

const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const dragOver = ref(false)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')
const level = ref(3) // 默认推荐压缩
const compressResult = ref<CompressResult | null>(null)
const resultUrl = ref('')

/** 压缩等级选项 — 按实用性排序 */
const displayLevels: LevelOption[] = [
  {
    value: 3,
    label: '推荐压缩',
    description: '均衡压缩率与视觉质量。图片降至 150 DPI，中高 JPEG 质量，保留文档元数据。适合日常分享、文档归档等大部分通用场景',
    isDefault: true,
    warning: false,
  },
  {
    value: 2,
    label: '高度压缩',
    description: '显著缩小体积，保持基础可读性。图片降至 100 DPI，中等 JPEG 质量，移除元数据。适合邮件附件、OA 审批、有文件大小限制的提交',
    isDefault: false,
    warning: false,
  },
  {
    value: 1,
    label: '极度压缩',
    description: '以最低画质换取极限体积缩减。图片降至 72 DPI，JPEG 低质量重编码，移除文档元数据。适合内部流转、长期归档',
    isDefault: false,
    warning: true,
  },
  {
    value: 4,
    label: '轻度压缩',
    description: '保持较优质画面，适度减小体积。图片降至 200 DPI，高 JPEG 质量。适合报告、标书、宣传材料等需要较高画质的文档',
    isDefault: false,
    warning: false,
  },
  {
    value: 5,
    label: '极限画质',
    description: '画质优先，仅去除文档冗余数据并轻微压缩图片。图片保持 300 DPI，JPEG 近无损质量。适合画册、设计稿等需放大审阅或再印刷的场景',
    isDefault: false,
    warning: true,
  },
]

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
  compressResult.value = null
  resultUrl.value = ''
  errorMsg.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

async function startCompress() {
  if (!uploadedFile.value || stage.value === 'processing') return

  clearResult()
  stage.value = 'processing'

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('level', String(level.value))

    const baseUrl = window.location.origin
    const resp = await fetch(`${baseUrl}/api/pdf/compress`, {
      method: 'POST',
      body: formData,
    })

    if (!resp.ok) {
      const errText = await resp.text()
      throw new Error(errText || `HTTP ${resp.status}`)
    }

    const blob = await resp.blob()
    const originalSize = parseInt(resp.headers.get('X-Original-Size') || '0')
    const compressedSize = parseInt(resp.headers.get('X-Compressed-Size') || '0')
    const ratio = originalSize > 0 ? ((originalSize - compressedSize) / originalSize) * 100 : 0

    compressResult.value = {
      blob,
      originalSize,
      compressedSize,
      ratio,
    }
    resultUrl.value = URL.createObjectURL(blob)
    stage.value = 'done'
  } catch (e: any) {
    errorMsg.value = e.message || '压缩失败'
    stage.value = 'error'
  }
}

function downloadResult() {
  if (!compressResult.value) return
  const url = URL.createObjectURL(compressResult.value.blob)
  const a = document.createElement('a')
  a.href = url
  a.download = uploadedFile.value ? 'compressed-' + uploadedFile.value.name : 'compressed.pdf'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.hidden { display: none; }
</style>
