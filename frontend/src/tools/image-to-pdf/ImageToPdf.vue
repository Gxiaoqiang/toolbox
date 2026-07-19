<!--
  图片转 PDF — Variant B 布局（大图网格 + 右侧设置面板）
-->
<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'

// ===== 类型 =====
interface ImageItem {
  uid: string
  name: string
  size: number
  url: string
  file: File
  error?: string
}

type Orientation = 'portrait' | 'landscape'
type Margin = 'none' | 'small' | 'large'
type FitMode = 'contain' | 'cover' | 'stretch'

// ===== 常量 =====
const MAX_FILES = 50
const MAX_SINGLE_SIZE = 5 * 1024 * 1024 // 5MB
const MAX_TOTAL_SIZE = 100 * 1024 * 1024 // 100MB
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

// ===== 状态 =====
const images = ref<ImageItem[]>([])
const orientation = ref<Orientation>('portrait')
const margin = ref<Margin>('small')
const fitMode = ref<FitMode>('contain')
const merge = ref(true)
const processing = ref(false)
const done = ref(false)
const errorMsg = ref('')

const dragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

// 放大预览
const previewImage = ref<ImageItem | null>(null)

// 组件卸载时清理所有 objectURL，防止内存泄漏
onUnmounted(() => {
  images.value.forEach(i => URL.revokeObjectURL(i.url))
})

const count = computed(() => images.value.length)
const totalSize = computed(() => images.value.reduce((s, i) => s + i.size, 0))

// ===== 文件处理 =====
function triggerFileInput() { fileInput.value?.click() }

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files) addFiles(e.dataTransfer.files)
}

function addFiles(files: FileList | File[]) {
  errorMsg.value = ''
  const skipped: string[] = []
  for (const file of Array.from(files)) {
    if (images.value.length >= MAX_FILES) {
      errorMsg.value = `最多上传 ${MAX_FILES} 张图片`
      break
    }
    if (!ACCEPTED_TYPES.includes(file.type)) {
      skipped.push(`${file.name}（格式不支持）`)
      continue
    }
    if (file.size > MAX_SINGLE_SIZE) {
      skipped.push(`${file.name}（超过 5MB）`)
      continue
    }
    if (totalSize.value + file.size > MAX_TOTAL_SIZE) {
      errorMsg.value = `总大小超过 100MB 限制`
      break
    }
    images.value.push({
      uid: crypto.randomUUID(),
      name: file.name,
      size: file.size,
      url: URL.createObjectURL(file),
      file,
    })
  }
  if (skipped.length > 0 && !errorMsg.value) {
    errorMsg.value = `跳过 ${skipped.length} 个文件: ${skipped.join('、')}`
  }
}

function removeImage(uid: string) {
  const idx = images.value.findIndex(i => i.uid === uid)
  if (idx >= 0) {
    URL.revokeObjectURL(images.value[idx].url)
    images.value.splice(idx, 1)
  }
}

function clearAll() {
  images.value.forEach(i => URL.revokeObjectURL(i.url))
  images.value = []
  done.value = false
  errorMsg.value = ''
}

// ===== 拖拽排序 =====
function onDragStart(idx: number) { dragIndex.value = idx }
function onDragOver(idx: number) { dragOverIndex.value = idx }
function onDragLeave() { dragOverIndex.value = null }
function onDrop(idx: number) {
  if (dragIndex.value === null || dragIndex.value === idx) return
  const [item] = images.value.splice(dragIndex.value, 1)
  images.value.splice(idx, 0, item)
  dragIndex.value = null
  dragOverIndex.value = null
}

// ===== 格式化 =====
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// ===== 转换 =====
async function convert() {
  if (images.value.length === 0) return
  processing.value = true
  done.value = false
  errorMsg.value = ''

  try {
    const formData = new FormData()
    images.value.forEach(img => formData.append('files', img.file))
    formData.append('orientation', orientation.value)
    formData.append('margin', margin.value)
    formData.append('fitMode', fitMode.value)
    formData.append('merge', String(merge.value))

    const resp = await fetch('/api/image/to-pdf', { method: 'POST', body: formData })
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '转换失败' }))
      throw new Error(err.message || '转换失败')
    }

    // 下载文件
    const blob = await resp.blob()
    const ext = merge.value ? 'pdf' : 'zip'
    const filename = resp.headers.get('Content-Disposition')
      ?.match(/filename\*?=(?:UTF-8'')?([^;\n]*)/)?.[1]
      ?? `images-to-pdf.${ext}`

    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = decodeURIComponent(filename)
    a.click()
    URL.revokeObjectURL(url)

    done.value = true
  } catch (e: any) {
    errorMsg.value = e.message || '转换失败，请重试'
  } finally {
    processing.value = false
  }
}
</script>

<template>
  <div class="flex h-full">
    <!-- 左侧：大图网格区 -->
    <div class="flex-1 flex flex-col min-w-0 p-4">
      <!-- 顶部工具栏 -->
      <div class="flex items-center justify-between mb-3 flex-shrink-0">
        <div class="flex items-center gap-2">
          <span class="text-lg">🖼️</span>
          <span class="text-sm font-semibold" style="color: var(--text-primary)">图片转 PDF</span>
          <span v-if="count > 0" class="text-xs px-2 py-0.5 rounded-full" style="background: var(--accent-light); color: var(--accent-color)">
            {{ count }} 张
          </span>
        </div>
        <div class="flex gap-2">
          <button
            v-if="count > 0"
            @click="triggerFileInput"
            class="text-xs px-3 py-1 rounded border transition hover:bg-black/5"
            style="border-color: var(--border-color); color: var(--text-primary)"
          >+ 添加</button>
          <button
            v-if="count > 0"
            @click="clearAll"
            class="text-xs px-3 py-1 rounded border transition hover:bg-red-50 hover:border-red-300"
            style="border-color: var(--border-color); color: var(--text-muted)"
          >清空</button>
        </div>
      </div>

      <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple class="hidden" @change="onFileChange" />

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="mb-2 px-3 py-2 rounded-lg text-xs bg-red-50 text-red-600 border border-red-200 flex-shrink-0">
        ⚠ {{ errorMsg }}
      </div>

      <!-- 图片网格 / 空状态 -->
      <div
        v-if="count === 0"
        class="flex-1 border-2 border-dashed rounded-xl flex flex-col items-center justify-center cursor-pointer transition-colors"
        :class="dragOver ? 'border-indigo-400 bg-indigo-50/30' : ''"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <span class="text-5xl mb-3">📤</span>
        <p class="text-base font-medium" style="color: var(--text-primary)">拖拽图片到此处</p>
        <p class="text-sm mt-1" style="color: var(--text-muted)">支持 JPG / PNG / WEBP / GIF · 最多 50 张 · 单张 ≤5MB</p>
      </div>

      <div v-else class="flex-1 min-h-0 overflow-y-auto">
        <div class="grid gap-2 grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8">
          <div
            v-for="(img, idx) in images" :key="img.uid"
            class="relative group rounded-lg overflow-hidden border shadow-sm hover:shadow-md transition-shadow cursor-move"
            :class="{ 'opacity-50': dragIndex === idx, 'ring-2 ring-indigo-400': dragOverIndex === idx }"
            style="border-color: var(--border-color)"
            draggable="true"
            @dragstart="onDragStart(idx)"
            @dragover.prevent="onDragOver(idx)"
            @dragleave="onDragLeave"
            @drop.prevent="onDrop(idx)"
          >
            <img :src="img.url" class="w-full aspect-square object-cover" />
            <!-- 序号角标 -->
            <div class="absolute top-1 left-1 w-5 h-5 rounded-full bg-black/50 text-white text-[10px] flex items-center justify-center font-medium">
              {{ idx + 1 }}
            </div>
            <!-- 操作按钮组 -->
            <div class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition">
              <!-- 放大按钮 -->
              <button
                @click.stop="previewImage = img"
                class="w-5 h-5 rounded-full bg-black/50 text-white text-[10px] flex items-center justify-center hover:bg-black/70"
                title="放大预览"
              >🔍</button>
              <!-- 删除按钮 -->
              <button
                @click.stop="removeImage(img.uid)"
                class="w-5 h-5 rounded-full bg-black/50 text-white text-[10px] flex items-center justify-center hover:bg-red-500"
                title="删除"
              >×</button>
            </div>
            <!-- 文件名 -->
            <div class="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/60 to-transparent px-1.5 py-1">
              <p class="text-[10px] text-white truncate">{{ img.name }}</p>
            </div>
          </div>
        </div>
        <p class="text-xs mt-2" style="color: var(--text-muted)">{{ count }} 张图片 · {{ formatSize(totalSize) }} · 拖拽可排序</p>
      </div>

      <!-- 放大预览弹窗 -->
      <Teleport to="body">
        <div
          v-if="previewImage"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
          @click="previewImage = null"
        >
          <div class="relative max-w-[90vw] max-h-[90vh]" @click.stop>
            <img :src="previewImage.url" class="max-w-[90vw] max-h-[85vh] object-contain rounded-lg shadow-2xl" />
            <button
              @click="previewImage = null"
              class="absolute -top-3 -right-3 w-8 h-8 rounded-full bg-white shadow-lg text-gray-700 flex items-center justify-center text-lg hover:bg-gray-100"
            >×</button>
            <div class="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/70 to-transparent px-4 py-3 rounded-b-lg">
              <p class="text-sm text-white font-medium">{{ previewImage.name }}</p>
              <p class="text-xs text-white/70">{{ formatSize(previewImage.size) }}</p>
            </div>
          </div>
        </div>
      </Teleport>
    </div>

    <!-- 右侧：设置面板 -->
    <div
      class="w-[260px] flex-shrink-0 flex flex-col border-l p-4"
      style="border-color: var(--border-color); background: var(--bg-card)"
    >
      <h3 class="text-sm font-semibold mb-4" style="color: var(--text-primary)">转换设置</h3>

      <!-- 设置项 -->
      <div class="space-y-4 flex-1">
        <div>
          <label class="text-xs font-medium mb-1.5 block" style="color: var(--text-secondary)">页面方向</label>
          <div class="flex gap-2">
            <button
              v-for="opt in [{v:'portrait',l:'纵向'},{v:'landscape',l:'横向'}]" :key="opt.v"
              @click="orientation = opt.v as Orientation"
              class="flex-1 text-xs py-2 rounded-lg border transition"
              :class="orientation === opt.v ? 'border-indigo-400 bg-indigo-50 text-indigo-600' : ''"
              :style="orientation !== opt.v ? 'border-color: var(--border-color); color: var(--text-primary)' : ''"
            >{{ opt.l }}</button>
          </div>
        </div>

        <div>
          <label class="text-xs font-medium mb-1.5 block" style="color: var(--text-secondary)">页面边距</label>
          <div class="flex gap-2">
            <button
              v-for="opt in [{v:'none',l:'无'},{v:'small',l:'小'},{v:'large',l:'大'}]" :key="opt.v"
              @click="margin = opt.v as Margin"
              class="flex-1 text-xs py-2 rounded-lg border transition"
              :class="margin === opt.v ? 'border-indigo-400 bg-indigo-50 text-indigo-600' : ''"
              :style="margin !== opt.v ? 'border-color: var(--border-color); color: var(--text-primary)' : ''"
            >{{ opt.l }}</button>
          </div>
        </div>

        <div>
          <label class="text-xs font-medium mb-1.5 block" style="color: var(--text-secondary)">图片适配</label>
          <div class="flex gap-2">
            <button
              v-for="opt in [{v:'contain',l:'等比'},{v:'cover',l:'裁剪'},{v:'stretch',l:'拉伸'}]" :key="opt.v"
              @click="fitMode = opt.v as FitMode"
              class="flex-1 text-xs py-2 rounded-lg border transition"
              :class="fitMode === opt.v ? 'border-indigo-400 bg-indigo-50 text-indigo-600' : ''"
              :style="fitMode !== opt.v ? 'border-color: var(--border-color); color: var(--text-primary)' : ''"
            >{{ opt.l }}</button>
          </div>
        </div>

        <label class="flex items-center gap-2 text-xs cursor-pointer" style="color: var(--text-primary)">
          <input type="checkbox" v-model="merge" class="w-4 h-4 rounded accent-indigo-500" />
          合并为一个 PDF
        </label>
      </div>

      <!-- 转换按钮 + 结果 -->
      <div class="mt-4 space-y-3">
        <button
          @click="convert"
          :disabled="count === 0 || processing"
          class="w-full py-2.5 rounded-xl font-medium text-sm transition-all"
          :class="count > 0 && !processing ? 'bg-indigo-500 text-white hover:bg-indigo-600 shadow-md' : 'bg-gray-200 text-gray-400 cursor-not-allowed'"
        >
          <template v-if="processing">
            <span class="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-1 align-middle" />
            转换中...
          </template>
          <template v-else>开始转换</template>
        </button>

        <div v-if="done" class="text-center p-3 rounded-lg" style="background: var(--bg-main)">
          <p class="text-sm font-medium" style="color: var(--text-primary)">✅ 转换完成</p>
          <p class="text-xs mt-1" style="color: var(--text-muted)">文件已自动下载</p>
        </div>
      </div>
    </div>
  </div>
</template>
