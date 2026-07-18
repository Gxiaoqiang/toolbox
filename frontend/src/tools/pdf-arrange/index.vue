<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-arrange',
  name: 'PDF 编排',
  description: '自由编排 PDF 页面——排序、删除、旋转、插入空白页、合并多文件',
  icon: 'file-text',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- ===== 顶部工具栏 ===== -->
    <div class="flex flex-col gap-2 flex-shrink-0 pb-3">
      <!-- 工具栏第一行 -->
      <div class="flex items-center gap-3">
        <button
          @click="triggerFileInput"
          :disabled="processing || fileCount >= MAX_FILES"
          class="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
          :style="fileCount >= MAX_FILES
            ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }
            : { background: 'var(--accent-light)', color: 'var(--accent-color)' }"
        >
          <span class="text-base leading-none">+</span>
          <span>添加 PDF</span>
        </button>

        <button
          @click="insertBlankPage(pages.length - 1)"
          :disabled="processing || pageCount >= MAX_PAGES"
          class="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
          :style="pageCount >= MAX_PAGES
            ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }
            : { background: 'var(--bg-card)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }"
        >
          <span class="text-base leading-none">□</span>
          <span>空白页</span>
        </button>

        <div class="flex-1"></div>

        <span class="text-xs" style="color: var(--text-muted)">
          共 {{ pageCount }} 页
        </span>

        <button
          @click="handleExport"
          :disabled="!canExport"
          class="flex items-center gap-1 px-4 py-1.5 rounded-lg text-sm font-medium transition-all"
          :style="canExport
            ? { background: 'var(--accent-color)', color: '#fff' }
            : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
        >
          <svg v-if="processing" class="animate-spin" width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
          </svg>
          <span v-else class="text-base leading-none">▶</span>
          <span>{{ processing ? '导出中' : '导出' }}</span>
        </button>
      </div>

      <!-- 源文件 chip 条 -->
      <div v-if="fileCount > 0" class="flex flex-wrap gap-1.5">
        <div
          v-for="(f, fi) in fileItems"
          :key="f.uid"
          class="flex items-center gap-1 px-2 py-1 rounded-full text-xs border"
          :style="f.error
            ? { borderColor: '#f87171', background: '#fef2f2', color: '#ef4444' }
            : { borderColor: f.color + '40', background: f.color + '18', color: f.color }"
        >
          <span
            class="w-2 h-2 rounded-full flex-shrink-0"
            :style="{ background: f.error ? '#ef4444' : f.color }"
          ></span>
          <span class="truncate max-w-[160px]">{{ f.file.name }}</span>
          <span class="opacity-70">{{ f.loading ? '加载中...' : f.error ? '⚠' : f.pages + '页' }}</span>
          <button
            v-if="!processing"
            @click="removeFile(fi)"
            class="ml-0.5 w-4 h-4 rounded-full flex items-center justify-center text-[10px] hover:opacity-70"
            :style="{ background: f.error ? '#fef2f2' : f.color + '20' }"
            title="移除该文件及其所有页面"
          >✕</button>
        </div>
      </div>
    </div>

    <!-- ===== 页面网格画布 ===== -->
    <div
      class="flex-1 overflow-y-auto relative"
      :class="pageCount === 0 && !processing ? 'flex items-center justify-center' : ''"
    >
      <!-- 空态 -->
      <div
        v-if="pageCount === 0 && !processing"
        class="absolute inset-0 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-3 cursor-pointer"
        style="border-color: var(--border-color); background: var(--bg-card)"
        @click="triggerFileInput"
        @dragover.prevent
        @drop.prevent="handleDrop"
      >
        <span class="text-5xl">📑</span>
        <div class="text-center">
          <p class="text-base font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处开始编排</p>
          <p class="text-sm mt-1" style="color: var(--text-muted)">
            或点击选择 · 最多 {{ MAX_FILES }} 个 · 单文件 ≤10MB · 总量 ≤45MB
          </p>
        </div>
      </div>

      <!-- VueDraggable 网格 — 始终挂载，空数组时不可见 -->
      <VueDraggable
        v-model="pages"
        :disabled="processing"
        item-key="uid"
        handle=".drag-handle"
        :animation="150"
        ghost-class="opacity-40"
        class="grid gap-3"
        :class="{ 'invisible': pageCount === 0 }"
        style="grid-template-columns: repeat(auto-fill, minmax(140px, 1fr))"
      >
        <template #item="{ element, index }">
          <div
            class="group relative flex flex-col rounded-lg border transition-shadow hover:shadow-md"
            :style="{
              borderColor: errorBorderColor(element),
              background: 'var(--bg-card)',
            }"
          >
            <!-- 拖拽手柄 -->
            <div
              class="drag-handle absolute top-1 left-1 z-10 w-6 h-6 rounded flex items-center justify-center cursor-grab active:cursor-grabbing opacity-0 group-hover:opacity-100 transition-opacity select-none"
              style="background: rgba(0,0,0,0.15); color: #fff; font-size: 12px"
              :class="processing ? 'pointer-events-none' : ''"
            >⋮⋮</div>

            <!-- 来源角标 -->
            <div
              v-if="!element.blank && element.fileIndex >= 0 && fileItems[element.fileIndex]"
              class="absolute top-1 right-1 z-10 px-1.5 py-0.5 rounded text-[10px] font-semibold"
              :style="{
                background: fileItems[element.fileIndex].color + '30',
                color: fileItems[element.fileIndex].color,
              }"
            >
              {{ shortName(fileItems[element.fileIndex].file.name) }}-{{ element.filePage }}
            </div>

            <!-- 旋转角标 -->
            <div
              v-if="!element.blank && element.userRotation !== 0"
              class="absolute top-1 left-8 z-10 px-1 py-0.5 rounded text-[10px] font-semibold"
              style="background: #f59e0b30; color: #f59e0b"
            >
              ↻{{ element.userRotation }}°
            </div>

            <!-- 缩略图区 -->
            <div
              class="flex-1 flex items-center justify-center p-2"
              :style="thumbnailStyle(element)"
            >
              <img
                v-if="element.thumbnail"
                :src="element.thumbnail"
                class="w-full h-full object-contain rounded"
                :style="{
                  transform: element.userRotation ? `rotate(${element.userRotation}deg)` : undefined,
                }"
                loading="lazy"
              />
              <span v-else-if="element.blank" class="text-2xl" style="color: var(--text-muted)">空白页</span>
              <svg v-else class="animate-spin" width="20" height="20" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.15" style="color: var(--accent-color)"/>
                <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="color: var(--accent-color)"/>
              </svg>
            </div>

            <!-- 悬浮操作条 -->
            <div
              v-if="!processing"
              class="absolute bottom-8 left-1/2 -translate-x-1/2 flex items-center gap-0.5 px-2 py-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
              style="background: rgba(0,0,0,0.6)"
            >
              <button
                v-if="!element.blank"
                @click.stop="rotatePage(index)"
                class="w-6 h-6 flex items-center justify-center text-xs rounded-full hover:bg-white/20 text-white"
                title="旋转 90°"
              >↻</button>
              <button
                @click.stop="duplicatePage(index)"
                class="w-6 h-6 flex items-center justify-center text-xs rounded-full hover:bg-white/20 text-white"
                title="复制"
              >⧉</button>
              <button
                @click.stop="deletePage(index)"
                class="w-6 h-6 flex items-center justify-center text-xs rounded-full hover:bg-red-400/60 text-white"
                title="删除"
              >✕</button>
            </div>

            <!-- 页码指示器 -->
            <div
              class="text-center text-[10px] py-1"
              style="color: var(--text-muted)"
            >
              {{ index + 1 }}
            </div>
          </div>
        </template>
      </VueDraggable>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      accept=".pdf,application/pdf"
      multiple
      class="hidden"
      @change="handleFileSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { usePdfArranger, type PageEntry } from './usePdfArranger'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const arranger = usePdfArranger()
const {
  fileItems,
  pages,
  processing,
  fileCount,
  pageCount,
  canExport,
  hasErrors,
  MAX_FILES,
  MAX_PAGES,
  addFiles,
  removeFile,
  deletePage,
  rotatePage,
  duplicatePage,
  insertBlankPage,
  exportPdf,
} = arranger

const fileInputRef = ref<HTMLInputElement | null>(null)

// ===== 文件选择 =====

function triggerFileInput() {
  if (processing.value || fileCount.value >= MAX_FILES) return
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

function handleDrop(e: DragEvent) {
  if (e.dataTransfer?.files) addFiles(e.dataTransfer.files)
}

// ===== 导出 =====

function downloadBlob(blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'arranged.pdf'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function handleExport() {
  const blob = await exportPdf()
  if (blob) downloadBlob(blob)
}

// ===== 辅助 =====

function shortName(filename: string): string {
  const base = filename.split('.').slice(0, -1).join('.')
  return base.length > 10 ? base.slice(0, 9) + '…' : base
}

function errorBorderColor(element: PageEntry): string {
  if (element.blank) return 'var(--border-color)'
  if (element.fileIndex >= 0 && fileItems.value[element.fileIndex]?.error) {
    return '#f87171'
  }
  return 'var(--border-color)'
}

function thumbnailStyle(element: PageEntry): Record<string, string> {
  if (element.blank) {
    return { minHeight: '120px', background: 'var(--bg-main)' }
  }
  const ratio = element.width / element.height
  if (ratio > 1.2) return { aspectRatio: '1.4/1' }
  if (ratio < 0.8) return { aspectRatio: '0.7/1' }
  return { aspectRatio: '0.707/1' }
}
</script>

<style scoped>
.hidden { display: none; }
</style>
