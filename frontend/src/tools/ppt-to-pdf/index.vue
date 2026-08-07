<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'ppt-to-pdf',
  name: 'PPT 转 PDF',
  description: '将 .ppt / .pptx 演示文稿转换为 PDF，支持选择页面和调整顺序',
  icon: 'presentation',
  category: 'file',
  group: '转换工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- ===== 顶部：模式切换器（单文件 / 批量） ===== -->
    <div class="flex-shrink-0 pb-3">
      <div class="flex items-center gap-1 p-1 rounded-lg w-fit" style="background: var(--bg-card-hover)">
        <button
          @click="switchMode('single')"
          class="px-4 py-1.5 text-xs font-medium rounded-md transition-colors"
          :style="mode === 'single'
            ? { background: 'var(--accent-color)', color: '#fff' }
            : { color: 'var(--text-muted)' }"
        >单文件</button>
        <button
          @click="switchMode('batch')"
          class="px-4 py-1.5 text-xs font-medium rounded-md transition-colors"
          :style="mode === 'batch'
            ? { background: 'var(--accent-color)', color: '#fff' }
            : { color: 'var(--text-muted)' }"
        >批量转换</button>
      </div>
    </div>

    <!-- ===== 单文件模式 ===== -->
    <template v-if="mode === 'single'">
    <!-- 顶部：文件上传区 -->
    <div class="flex-shrink-0 pb-3">
      <!-- 上传区 -->
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-3 transition-colors flex-shrink-0"
        :class="[
          stage === 'noFile' ? 'h-32' : 'h-20',
          stage === 'loading' || stage === 'converting' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30'
        ]"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="stage !== 'loading' && stage !== 'converting' && triggerFileInput()"
        @dragover.prevent="stage !== 'loading' && stage !== 'converting' && (dragOver = true)"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="stage !== 'loading' && stage !== 'converting' && handleDrop($event)"
      >
        <!-- 无文件 -->
        <template v-if="stage === 'noFile'">
          <span class="text-4xl">📊</span>
          <div class="text-center">
            <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PPT 到此处</p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 支持 .ppt / .pptx · 最大 50MB</p>
          </div>
        </template>

        <!-- 已选文件 -->
        <template v-else>
          <div class="flex items-center gap-3">
            <span class="text-2xl">📊</span>
            <div>
              <p class="text-sm font-medium" style="color: var(--text-primary)">{{ uploadedFile!.name }}</p>
              <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(uploadedFile!.size) }}</p>
            </div>
            <button
              v-if="stage !== 'loading' && stage !== 'converting'"
              @click.stop="clearFile"
              class="text-xs underline hover:text-red-500 transition-colors"
              style="color: var(--text-muted)"
            >移除</button>
          </div>
        </template>
      </div>

      <input ref="fileInputRef" type="file" accept=".ppt,.pptx,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation" class="hidden" @change="handleFileSelect" />
    </div>

    <!-- ===== 中间：页面预览网格 ===== -->
    <div class="flex-1 overflow-y-auto min-h-0">
      <!-- 加载中 -->
      <div v-if="stage === 'loading'" class="flex flex-col items-center justify-center h-full gap-3">
        <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p class="text-sm" style="color: var(--text-muted)">正在解析 PPT，请稍候...</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="stage === 'error'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">⚠️</span>
        <p class="text-sm text-center px-4" style="color: #ef4444">{{ errorMsg }}</p>
        <button @click="clearFile" class="text-xs underline" style="color: var(--text-muted)">重新选择</button>
      </div>

      <!-- 页面网格 -->
      <div v-else-if="stage === 'ready' && pages.length > 0">
        <!-- 工具栏 -->
        <div class="flex items-center gap-3 mb-3">
          <button
            @click="selectAll"
            class="px-3 py-1 text-xs rounded-md border transition-colors hover:opacity-80"
            style="border-color: var(--border-color); color: var(--text-primary); background: var(--bg-card)"
          >全选</button>
          <button
            @click="deselectAll"
            class="px-3 py-1 text-xs rounded-md border transition-colors hover:opacity-80"
            style="border-color: var(--border-color); color: var(--text-primary); background: var(--bg-card)"
          >全不选</button>
          <span class="text-xs" style="color: var(--text-muted)">
            已选 {{ selectedCount }} / {{ pages.length }} 页
          </span>
          <div class="flex-1"></div>
          <span class="text-xs" style="color: var(--text-muted)">拖拽调整顺序</span>
        </div>

        <!-- draggable 网格 -->
        <draggable
          v-model="pages"
          item-key="pageNumber"
          :animation="200"
          ghost-class="opacity-40"
          class="flex flex-wrap gap-2"
        >
          <template #item="{ element, index }">
            <div
              class="group relative flex flex-col rounded-lg border transition-all w-[130px] cursor-pointer"
              :style="{
                borderColor: element.selected ? 'var(--accent-color)' : 'var(--border-color)',
                background: element.selected ? 'var(--accent-light)' : 'var(--bg-card)',
                opacity: element.selected ? 1 : 0.6,
              }"
              @click="togglePage(element)"
            >
              <!-- 选中角标 -->
              <div
                class="absolute top-1 right-1 z-10 w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold transition-colors"
                :style="{
                  background: element.selected ? 'var(--accent-color)' : 'var(--bg-card-hover)',
                  color: element.selected ? '#fff' : 'var(--text-muted)',
                  border: element.selected ? 'none' : '1px solid var(--border-color)',
                }"
              >{{ element.selected ? '✓' : '' }}</div>

              <!-- 放大按钮 -->
              <button
                @click.stop="openZoom(element, index)"
                class="absolute bottom-7 right-1 z-10 w-6 h-6 rounded-full flex items-center justify-center text-xs opacity-0 group-hover:opacity-100 transition-opacity"
                style="background: rgba(0,0,0,0.5); color: #fff"
                title="放大预览"
              >🔍</button>

              <!-- 缩略图 -->
              <div class="flex items-center justify-center p-2 overflow-hidden" style="min-height: 100px">
                <img
                  v-if="element.thumbnailBase64"
                  :src="'data:image/jpeg;base64,' + element.thumbnailBase64"
                  class="w-full h-full object-contain rounded"
                  :style="{ opacity: element.selected ? 1 : 0.5 }"
                  loading="lazy"
                />
                <svg v-else class="animate-spin" width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.15" style="color: var(--accent-color)"/>
                  <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="color: var(--accent-color)"/>
                </svg>
              </div>

              <!-- 页码 -->
              <div
                class="text-center text-[10px] py-1 border-t"
                :style="{
                  borderColor: element.selected ? 'var(--accent-color)' : 'var(--border-color)',
                  color: element.selected ? 'var(--accent-color)' : 'var(--text-muted)',
                }"
              >
                第 {{ index + 1 }} 页（原第 {{ element.pageNumber }} 页）
              </div>
            </div>
          </template>
        </draggable>
      </div>

      <!-- 转换中 -->
      <div v-else-if="stage === 'converting'" class="flex flex-col items-center justify-center h-full gap-3">
        <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p class="text-sm" style="color: var(--text-muted)">正在生成 PDF...</p>
      </div>

      <!-- 转换完成 -->
      <div v-else-if="stage === 'done'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">✅</span>
        <p class="text-sm font-medium" style="color: var(--text-primary)">转换完成</p>
        <p class="text-xs" style="color: var(--text-muted)">{{ resultInfo }}</p>
        <button @click="downloadResult"
          class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
          style="background: var(--accent-color)">📥 下载 PDF</button>
        <button @click="resetToReady" class="text-xs underline" style="color: var(--text-muted)">返回重新选择</button>
      </div>

      <!-- 空态（未选择文件） -->
      <div v-else class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-5xl">📊</span>
        <p class="text-sm" style="color: var(--text-muted)">请先上传 PPT 文件</p>
      </div>
    </div>

    <!-- ===== 底部：转换按钮 ===== -->
    <div v-if="stage === 'ready'" class="flex-shrink-0 pt-3 flex items-center justify-between">
      <p class="text-xs" style="color: var(--text-muted)">
        将按当前顺序生成 PDF，仅包含选中的 {{ selectedCount }} 页
      </p>
      <button
        @click="startConvert"
        :disabled="selectedCount === 0"
        class="flex items-center gap-2 px-5 py-2 rounded-lg text-sm font-medium transition-all"
        :style="selectedCount > 0
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
      >
        <span class="text-base leading-none">▶</span>
        <span>转换为 PDF</span>
      </button>
    </div>

    <!-- ===== 放大预览弹窗 ===== -->
    <Teleport to="body">
      <div
        v-if="zoomedPage"
        class="fixed inset-0 z-50 flex items-center justify-center"
        style="background: rgba(0,0,0,0.75)"
        @click.self="closeZoom"
      >
        <!-- 关闭按钮 -->
        <button
          @click="closeZoom"
          class="absolute top-4 right-4 w-10 h-10 rounded-full flex items-center justify-center text-xl transition-colors hover:opacity-80"
          style="background: rgba(255,255,255,0.15); color: #fff"
        >✕</button>

        <!-- 上一页 -->
        <button
          v-if="zoomedIndex > 0"
          @click="zoomPrev"
          class="absolute left-4 w-10 h-10 rounded-full flex items-center justify-center text-lg transition-colors hover:opacity-80"
          style="background: rgba(255,255,255,0.15); color: #fff"
        >‹</button>

        <!-- 大图 -->
        <div class="flex flex-col items-center gap-3 max-w-[90vw] max-h-[90vh]">
          <!-- 加载中 -->
          <div v-if="zoomLoading" class="flex flex-col items-center gap-3" style="min-width: 300px; min-height: 200px; justify-content: center">
            <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="#fff" stroke-width="2" opacity="0.3"/>
              <path d="M12 2a10 10 0 0 1 10 10" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
            </svg>
            <span class="text-sm text-white/60">加载高清图中...</span>
          </div>

          <!-- 高清大图 -->
          <img
            v-else-if="zoomedImageUrl"
            :src="zoomedImageUrl"
            class="max-w-full max-h-[80vh] object-contain rounded-lg shadow-2xl"
            style="background: #fff"
          />

          <!-- 底部信息 -->
          <div class="flex items-center gap-4">
            <span class="text-sm text-white/80">
              第 {{ zoomedIndex + 1 }} 页（原第 {{ zoomedPage.pageNumber }} 页）
            </span>
            <span
              class="px-2 py-0.5 rounded text-xs"
              :style="{
                background: zoomedPage.selected ? 'var(--accent-color)' : 'rgba(255,255,255,0.2)',
                color: '#fff',
              }"
            >{{ zoomedPage.selected ? '已选中' : '未选中' }}</span>
          </div>
        </div>

        <!-- 下一页 -->
        <button
          v-if="zoomedIndex < pages.length - 1"
          @click="zoomNext"
          class="absolute right-4 w-10 h-10 rounded-full flex items-center justify-center text-lg transition-colors hover:opacity-80"
          style="background: rgba(255,255,255,0.15); color: #fff"
        >›</button>
      </div>
    </Teleport>
    </template>

    <!-- ===== 批量模式 ===== -->
    <template v-else>
    <!-- 批量上传区 -->
    <div class="flex-shrink-0 pb-3">
      <div
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center gap-2 transition-colors"
        :class="[
          batchStage === 'converting' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30'
        ]"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="batchStage !== 'converting' && triggerBatchInput()"
        @dragover.prevent="batchStage !== 'converting' && (batchDragOver = true)"
        @dragleave.prevent="batchDragOver = false"
        @drop.prevent="batchStage !== 'converting' && handleBatchDrop($event)"
      >
        <span class="text-3xl">📊</span>
        <div class="text-center">
          <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽多个 PPT 到此处</p>
          <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择 · 最多 10 个 · 单个 ≤50MB · 支持 .ppt / .pptx</p>
        </div>
      </div>
      <input ref="batchInputRef" type="file" accept=".ppt,.pptx,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation" multiple class="hidden" @change="handleBatchSelect" />

      <!-- 文件列表 -->
      <div v-if="batchFiles.length > 0" class="mt-2 flex flex-col gap-1.5">
        <div v-for="(f, i) in batchFiles" :key="f.name + i"
          class="flex items-center gap-2 px-3 py-2 rounded-md border"
          style="border-color: var(--border-color); background: var(--bg-card)"
        >
          <span class="text-lg">📊</span>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate" style="color: var(--text-primary)">{{ f.name }}</p>
            <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(f.size) }}</p>
          </div>
          <button
            v-if="batchStage !== 'converting'"
            @click.stop="removeBatchFile(i)"
            class="text-xs underline hover:text-red-500 transition-colors"
            style="color: var(--text-muted)"
          >移除</button>
        </div>
      </div>
    </div>

    <!-- 批量主体：转换方式 + 结果 -->
    <div class="flex-1 overflow-y-auto min-h-0">
      <!-- 转换中 -->
      <div v-if="batchStage === 'converting'" class="flex flex-col items-center justify-center h-full gap-3">
        <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p class="text-sm" style="color: var(--text-muted)">正在批量转换 {{ batchFiles.length }} 个文件...</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="batchStage === 'error'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">⚠️</span>
        <p class="text-sm text-center px-4" style="color: #ef4444">{{ batchErrorMsg }}</p>
      </div>

      <!-- 转换完成 -->
      <div v-else-if="batchStage === 'done'" class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-4xl">✅</span>
        <p class="text-sm font-medium" style="color: var(--text-primary)">转换完成</p>
        <p class="text-xs" style="color: var(--text-muted)">{{ batchResultInfo }}</p>
        <button @click="downloadBatchResult"
          class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
          style="background: var(--accent-color)">📥 下载{{ batchMerge ? 'PDF' : 'ZIP' }}</button>
        <button @click="resetBatch" class="text-xs underline" style="color: var(--text-muted)">返回重新选择</button>
      </div>

      <!-- 空态 -->
      <div v-else class="flex flex-col items-center justify-center h-full gap-3">
        <span class="text-5xl">📊</span>
        <p class="text-sm" style="color: var(--text-muted)">请先上传 PPT 文件</p>
      </div>
    </div>

    <!-- 批量底部：输出方式 + 转换按钮 -->
    <div v-if="batchStage !== 'converting' && batchFiles.length > 0" class="flex-shrink-0 pt-3 flex items-center justify-between gap-3">
      <div class="flex items-center gap-4">
        <label class="flex items-center gap-1.5 text-xs cursor-pointer" style="color: var(--text-primary)">
          <input type="radio" v-model="batchMerge" :value="true" class="accent-current" />
          合并为一个 PDF
        </label>
        <label class="flex items-center gap-1.5 text-xs cursor-pointer" style="color: var(--text-primary)">
          <input type="radio" v-model="batchMerge" :value="false" class="accent-current" />
          分别转换（ZIP）
        </label>
      </div>
      <button
        @click="startBatchConvert"
        :disabled="batchFiles.length === 0"
        class="flex items-center gap-2 px-5 py-2 rounded-lg text-sm font-medium transition-all flex-shrink-0"
        :style="batchFiles.length > 0
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
      >
        <span class="text-base leading-none">▶</span>
        <span>批量转换为 PDF</span>
      </button>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import draggable from 'vuedraggable'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const { success: toastSuccess, error: toastError } = useToast()

// 键盘事件：ESC 关闭，← → 翻页
function handleKeydown(e: KeyboardEvent) {
  if (!zoomedPage.value) return
  if (e.key === 'Escape') closeZoom()
  else if (e.key === 'ArrowLeft') zoomPrev()
  else if (e.key === 'ArrowRight') zoomNext()
}
onMounted(() => document.addEventListener('keydown', handleKeydown))
onUnmounted(() => document.removeEventListener('keydown', handleKeydown))

/**
 * 页面状态机:
 *   noFile    — 未选择文件
 *   loading   — 正在解析 PPT（调用 /api/ppt/preview）
 *   ready     — 预览就绪，可选择页面
 *   converting — 正在转换 PDF
 *   done      — 转换完成
 *   error     — 出错
 */
type Stage = 'noFile' | 'loading' | 'ready' | 'converting' | 'done' | 'error'

interface PageItem {
  pageNumber: number
  thumbnailBase64: string
  width: number
  height: number
  selected: boolean
}

/** 当前模式：single 单文件 / batch 批量 */
const mode = ref<'single' | 'batch'>('single')

// ===== 批量模式状态 =====
type BatchStage = 'idle' | 'converting' | 'done' | 'error'
const batchInputRef = ref<HTMLInputElement>()
const batchFiles = ref<File[]>([])
const batchDragOver = ref(false)
const batchStage = ref<BatchStage>('idle')
const batchMerge = ref(true) // true=合并为一个 PDF；false=分别转换打包 ZIP
const batchResultBlob = ref<Blob | null>(null)
const batchResultInfo = ref('')
const batchErrorMsg = ref('')

const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const dragOver = ref(false)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')
const pages = ref<PageItem[]>([])
const baseName = ref('')
const cacheKey = ref('') // 预览阶段返回的缓存 key，转换时传递以跳过二次 LibreOffice 调用
const resultBlob = ref<Blob | null>(null)
const resultInfo = ref('')

// 放大预览
const zoomedPage = ref<PageItem | null>(null)
const zoomedIndex = ref(0)
const zoomedImageUrl = ref('') // 高清大图 URL
const zoomLoading = ref(false)

const selectedCount = computed(() => pages.value.filter(p => p.selected).length)

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
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (ext === 'ppt' || ext === 'pptx') {
      setFile(f)
    } else {
      toastError('仅支持 .ppt / .pptx 格式')
    }
  }
}

function setFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    toastError('文件不能超过 50MB')
    return
  }
  uploadedFile.value = file
  errorMsg.value = ''
  resultBlob.value = null
  resultInfo.value = ''
  loadPreview(file)
}

function clearFile() {
  uploadedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  pages.value = []
  baseName.value = ''
  cacheKey.value = ''
  errorMsg.value = ''
  resultBlob.value = null
  resultInfo.value = ''
  stage.value = 'noFile'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

// ======================== 模式切换 ========================

/**
 * 切换单文件 / 批量模式
 */
function switchMode(target: 'single' | 'batch') {
  if (mode.value === target) return
  mode.value = target
  if (target === 'batch') {
    resetBatch()
  }
}

// ======================== 批量模式 ========================

/**
 * 重置批量状态到初始
 */
function resetBatch() {
  batchFiles.value = []
  if (batchInputRef.value) batchInputRef.value.value = ''
  batchStage.value = 'idle'
  batchResultBlob.value = null
  batchResultInfo.value = ''
  batchErrorMsg.value = ''
}

function triggerBatchInput() {
  batchInputRef.value?.click()
}

function isPptFile(name: string): boolean {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ext === 'ppt' || ext === 'pptx'
}

function handleBatchSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) addBatchFiles(Array.from(files))
}

function handleBatchDrop(e: DragEvent) {
  batchDragOver.value = false
  const files = e.dataTransfer?.files
  if (files && files.length > 0) addBatchFiles(Array.from(files))
}

/**
 * 新增批量文件，做格式/大小/数量校验
 */
function addBatchFiles(newFiles: File[]) {
  for (const f of newFiles) {
    if (!isPptFile(f.name)) {
      toastError(`「${f.name}」格式不支持，仅支持 .ppt / .pptx`)
      continue
    }
    if (f.size > 50 * 1024 * 1024) {
      toastError(`「${f.name}」超过 50MB`)
      continue
    }
    if (batchFiles.value.some(existing => existing.name === f.name && existing.size === f.size)) {
      toastError(`「${f.name}」已添加`)
      continue
    }
    if (batchFiles.value.length >= 10) {
      toastError('单次最多上传 10 个文件')
      break
    }
    batchFiles.value.push(f)
  }
  batchStage.value = 'idle'
  batchResultBlob.value = null
  batchResultInfo.value = ''
}

/**
 * 移除批量文件
 */
function removeBatchFile(index: number) {
  batchFiles.value.splice(index, 1)
  batchStage.value = 'idle'
  batchResultBlob.value = null
  batchResultInfo.value = ''
}

/**
 * 开始批量转换
 */
async function startBatchConvert() {
  if (batchFiles.value.length === 0 || batchStage.value === 'converting') return

  batchStage.value = 'converting'
  batchResultBlob.value = null
  batchResultInfo.value = ''

  try {
    const formData = new FormData()
    batchFiles.value.forEach(f => formData.append('files', f))
    formData.append('mode', batchMerge.value ? 'merge' : 'separate')

    const resp = await fetch('/api/ppt/batch-to-pdf', { method: 'POST', body: formData })

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '批量转换失败' }))
      throw new Error(err.message || `HTTP ${resp.status}`)
    }

    batchResultBlob.value = await resp.blob()
    batchResultInfo.value = batchMerge.value
      ? `合并 PDF 文件 (${formatSize(batchResultBlob.value.size)})`
      : `ZIP 压缩包 (${formatSize(batchResultBlob.value.size)})`
    batchStage.value = 'done'
    toastSuccess('批量转换成功')
  } catch (e: any) {
    batchErrorMsg.value = e.message || '批量转换失败'
    batchStage.value = 'error'
    toastError(e.message || '批量转换失败')
  }
}

/**
 * 下载批量转换结果
 */
function downloadBatchResult() {
  if (!batchResultBlob.value) return
  const url = URL.createObjectURL(batchResultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = batchMerge.value ? 'ppt-merge-result.pdf' : 'ppt-to-pdf-result.zip'
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * 加载 PPT 预览
 */
async function loadPreview(file: File) {
  stage.value = 'loading'
  pages.value = []

  try {
    const formData = new FormData()
    formData.append('file', file)

    const resp = await fetch('/api/ppt/preview', { method: 'POST', body: formData })

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '预览加载失败' }))
      throw new Error(err.message || `HTTP ${resp.status}`)
    }

    const result = await resp.json()
    // R 统一响应体：code === 0 表示成功
    if (result.code !== 0 || !result.data) {
      throw new Error(result.message || '预览加载失败')
    }

    const data = result.data
    baseName.value = data.baseName || 'output'
    cacheKey.value = data.cacheKey || ''
    pages.value = (data.pages || []).map((p: any) => ({
      pageNumber: p.pageNumber,
      thumbnailBase64: p.thumbnailBase64,
      width: p.width,
      height: p.height,
      selected: true, // 默认全选
    }))

    stage.value = 'ready'
  } catch (e: any) {
    errorMsg.value = e.message || 'PPT 解析失败'
    stage.value = 'error'
  }
}

/**
 * 切换页面选中状态
 */
function togglePage(page: PageItem) {
  page.selected = !page.selected
}

/**
 * 全选
 */
function selectAll() {
  pages.value.forEach(p => { p.selected = true })
}

/**
 * 全不选
 */
function deselectAll() {
  pages.value.forEach(p => { p.selected = false })
}

/**
 * 开始转换
 */
async function startConvert() {
  if (!uploadedFile.value || selectedCount.value === 0 || stage.value === 'converting') return

  stage.value = 'converting'
  resultBlob.value = null
  resultInfo.value = ''

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)

    // 传递缓存 key，后端跳过二次 LibreOffice 调用
    if (cacheKey.value) {
      formData.append('cacheKey', cacheKey.value)
    }

    // 构建页码参数（按当前顺序，仅选中的页）
    const selectedPages = pages.value
      .filter(p => p.selected)
      .map(p => String(p.pageNumber))
    formData.append('pages', selectedPages.join(','))

    const resp = await fetch('/api/ppt/convert-to-pdf', { method: 'POST', body: formData })

    if (!resp.ok) {
      const errText = await resp.text()
      throw new Error(errText || `HTTP ${resp.status}`)
    }

    resultBlob.value = await resp.blob()
    resultInfo.value = `PDF 文件 (${formatSize(resultBlob.value.size)})`
    stage.value = 'done'
    toastSuccess('PPT 转 PDF 成功')
  } catch (e: any) {
    errorMsg.value = e.message || '转换失败'
    stage.value = 'error'
    toastError(e.message || '转换失败')
  }
}

/**
 * 下载结果
 */
function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = (baseName.value || 'output') + '.pdf'
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * 返回重新选择页面
 */
function resetToReady() {
  resultBlob.value = null
  resultInfo.value = ''
  errorMsg.value = ''
  stage.value = 'ready'
}

/**
 * 放大预览 — 按需加载高清大图
 */
function openZoom(page: PageItem, index: number) {
  zoomedPage.value = page
  zoomedIndex.value = index
  loadZoomImage(page.pageNumber)
}

function closeZoom() {
  if (zoomedImageUrl.value) {
    URL.revokeObjectURL(zoomedImageUrl.value)
  }
  zoomedPage.value = null
  zoomedImageUrl.value = ''
  zoomLoading.value = false
}

function zoomPrev() {
  if (zoomedIndex.value > 0) {
    zoomedIndex.value--
    zoomedPage.value = pages.value[zoomedIndex.value]
    loadZoomImage(zoomedPage.value.pageNumber)
  }
}

function zoomNext() {
  if (zoomedIndex.value < pages.value.length - 1) {
    zoomedIndex.value++
    zoomedPage.value = pages.value[zoomedIndex.value]
    loadZoomImage(zoomedPage.value.pageNumber)
  }
}

async function loadZoomImage(pageNumber: number) {
  if (!cacheKey.value) return
  zoomLoading.value = true
  // 释放旧的 Object URL
  if (zoomedImageUrl.value) {
    URL.revokeObjectURL(zoomedImageUrl.value)
    zoomedImageUrl.value = ''
  }
  try {
    const formData = new FormData()
    formData.append('cacheKey', cacheKey.value)
    formData.append('page', String(pageNumber))
    const resp = await fetch('/api/ppt/page-image', { method: 'POST', body: formData })
    if (!resp.ok) throw new Error('加载失败')
    const blob = await resp.blob()
    zoomedImageUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    toastError('高清图加载失败')
  } finally {
    zoomLoading.value = false
  }
}
</script>

<style scoped>
.hidden { display: none; }
</style>
