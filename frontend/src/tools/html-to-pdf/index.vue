<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'html-to-pdf', name: 'HTML 转 PDF',
  description: '将网页 URL 或本地 HTML 文件转换为 PDF',
  icon: 'globe', category: 'file', group: '转换工具包', requiresBackend: true,
}
</script>

<template>
  <div class="flex gap-3 h-full">
    <!-- 左侧：输入区 -->
    <div class="flex-[2] flex flex-col min-w-0 border-2 border-dashed rounded-2xl p-4 gap-3"
         style="border-color: var(--border-color); background: var(--bg-card)">

      <!-- Tab 切换 -->
      <div class="flex gap-1 rounded-lg p-0.5" style="background: var(--bg-main)">
        <button v-for="tab in tabs" :key="tab.key"
          @click="activeTab = tab.key"
          class="flex-1 py-1.5 text-xs font-medium rounded-md transition-all"
          :style="activeTab === tab.key
            ? { background: 'var(--bg-card)', color: 'var(--text-primary)', boxShadow: '0 1px 2px rgba(0,0,0,0.08)' }
            : { color: 'var(--text-muted)' }">
          {{ tab.label }}
        </button>
      </div>

      <!-- ===== URL 模式 ===== -->
      <div v-if="activeTab === 'url'" class="flex flex-col gap-2">
        <input v-model="urlInput" type="url" placeholder="请输入网页 URL，如 https://example.com"
          class="w-full px-3 py-2 text-sm rounded-lg border outline-none transition-colors"
          style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)"
          @focus="($event.target as HTMLInputElement).style.borderColor = 'var(--accent-color)'"
          @blur="($event.target as HTMLInputElement).style.borderColor = 'var(--border-color)'" />
        <p class="text-xs" style="color: var(--text-muted)">💡 如需要登录才能访问，请用本地文件模式</p>
      </div>

      <!-- ===== 本地文件模式 ===== -->
      <div v-else class="flex flex-col gap-3 flex-1 min-h-0">
        <!-- 1. HTML 文件选择（必选） -->
        <div class="flex flex-col gap-1">
          <label class="text-xs font-medium" style="color: var(--text-secondary)">
            ① 选择 HTML 文件 <span style="color: var(--accent-color)">*必选</span>
          </label>
          <div
            class="border-2 border-dashed rounded-lg flex items-center justify-center cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
            :class="htmlFile ? 'h-12 gap-2' : 'h-16 gap-2'"
            style="border-color: var(--border-color); background: var(--bg-main)"
            @click="triggerHtmlInput"
            @dragover.prevent
            @drop.prevent="handleHtmlDrop">
            <template v-if="!htmlFile">
              <span class="text-lg">📄</span>
              <span class="text-xs" style="color: var(--text-muted)">点击选择或拖拽 .html 文件</span>
            </template>
            <template v-else>
              <span class="text-sm">📄</span>
              <span class="flex-1 text-sm truncate" style="color: var(--text-primary)">{{ htmlFile.name }}</span>
              <button v-if="!processing" @click.stop="clearHtmlFile"
                class="w-5 h-5 mr-2 rounded flex items-center justify-center text-xs hover:bg-red-50"
                style="color: var(--text-muted)">✕</button>
            </template>
          </div>
          <input ref="htmlInputRef" type="file" accept=".html,.htm" class="hidden" @change="handleHtmlSelect" />
        </div>

        <!-- 2. 资源文件夹选择（可选） -->
        <div class="flex flex-col gap-1 flex-1 min-h-0">
          <div class="flex items-center gap-2">
            <label class="text-xs font-medium" style="color: var(--text-secondary)">
              ② 选择资源文件夹 <span style="color: var(--text-muted)">可选</span>
            </label>
            <span v-if="suggestedFolder && !assetFiles.length" class="text-xs px-1.5 py-0.5 rounded" style="background: #fef3c7; color: #92400e">
              💡 建议选: {{ suggestedFolder }}
            </span>
          </div>
          <div
            class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
            :class="assetFiles.length > 0 ? 'gap-1 p-2' : 'flex-1 gap-2'"
            style="border-color: var(--border-color); background: var(--bg-main)"
            @click="triggerFolderInput"
            @dragover.prevent
            @drop.prevent="handleFolderDrop">
            <template v-if="assetFiles.length === 0">
              <span class="text-lg">📁</span>
              <span class="text-xs text-center" style="color: var(--text-muted)">点击选择或拖拽 _files 文件夹</span>
              <span class="text-xs" style="color: var(--text-muted)">包含 CSS / 图片 / JS 等关联资源</span>
            </template>
            <template v-else>
              <div class="flex items-center gap-2 w-full">
                <span class="text-sm">📁</span>
                <span class="flex-1 text-xs" style="color: var(--text-secondary)">
                  {{ assetFolderName }}/ · {{ assetFiles.length }} 个文件 · {{ formatFileSize(assetTotalSize) }}
                </span>
                <button @click.stop="showAssetList = !showAssetList" class="text-xs underline" style="color: var(--text-muted)">
                  {{ showAssetList ? '收起' : '展开' }}
                </button>
                <button v-if="!processing" @click.stop="clearFolder"
                  class="w-5 h-5 rounded flex items-center justify-center text-xs hover:bg-red-50"
                  style="color: var(--text-muted)">✕</button>
              </div>
              <div v-if="showAssetList"
                class="w-full max-h-24 overflow-y-auto rounded border text-xs"
                style="border-color: var(--border-color); background: var(--bg-card)">
                <div v-for="(f, idx) in assetFiles.slice(0, 30)" :key="idx"
                  class="flex items-center gap-2 px-2 py-0.5"
                  :style="idx > 0 ? { borderTop: '1px solid var(--border-color)' } : {}">
                  <span>{{ getFileIcon(f.name) }}</span>
                  <span class="flex-1 truncate" style="color: var(--text-secondary)">{{ f.relativePath }}</span>
                </div>
                <div v-if="assetFiles.length > 30" class="px-2 py-0.5" style="color: var(--text-muted)">
                  ...还有 {{ assetFiles.length - 30 }} 个文件
                </div>
              </div>
            </template>
          </div>
          <input ref="folderInputRef" type="file" webkitdirectory class="hidden" @change="handleFolderSelect" />
        </div>

        <p class="text-xs" style="color: var(--text-muted)">
          💡 浏览器 Ctrl+S → "网页，全部" → 得到 .html + _files 文件夹，分别选入上方即可
        </p>
      </div>

      <!-- 预览区 -->
      <div v-if="previewHtml !== null || previewSrc !== null" class="flex-1 min-h-0 min-w-0 rounded-lg border overflow-auto"
           style="border-color: var(--border-color)">
        <div class="flex items-center justify-between px-3 py-1.5 border-b sticky top-0 z-10"
             style="border-color: var(--border-color); background: var(--bg-main)">
          <span class="text-xs truncate" style="color: var(--text-muted)">{{ previewLabel }}</span>
          <button @click="clearPreview" class="text-xs underline" style="color: var(--text-muted)">清除预览</button>
        </div>
        <div v-if="previewLoading" class="flex items-center justify-center" style="min-height: 200px">
          <svg class="animate-spin mr-2" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <span class="text-sm" style="color: var(--text-muted)">正在加载预览...</span>
        </div>
        <img v-if="previewSrc" :src="previewSrc" class="max-w-full h-auto" alt="预览" />
        <iframe v-else-if="previewHtml" :srcdoc="previewHtml" class="w-full border-0" style="min-height: 400px" sandbox="allow-same-origin"></iframe>
        <div v-else class="flex flex-col items-center justify-center gap-2 px-4" style="min-height: 200px">
          <span class="text-2xl">⚠️</span>
          <p class="text-sm text-center" style="color: var(--text-muted)">{{ previewError || '预览加载失败' }}</p>
          <button v-if="previewError" @click="retryPreview" class="text-xs underline" style="color: var(--accent-color)">重试</button>
        </div>
      </div>

      <!-- 高级选项 -->
      <details class="group">
        <summary class="flex items-center gap-1 cursor-pointer text-xs font-medium select-none"
                 style="color: var(--text-secondary)">
          <span class="transition-transform group-open:rotate-90">▸</span> 高级选项
        </summary>
        <div class="mt-2 grid grid-cols-2 gap-2 text-xs">
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">纸张大小</label>
            <select v-model="options.paperSize" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="A4">A4</option><option value="Letter">Letter</option><option value="Legal">Legal</option>
            </select>
          </div>
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">方向</label>
            <select v-model="options.orientation" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="auto">自动</option><option value="portrait">纵向</option><option value="landscape">横向</option>
            </select>
          </div>
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">边距</label>
            <select v-model="options.margin" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="none">无</option><option value="narrow">窄</option><option value="medium">中</option><option value="wide">宽</option>
            </select>
          </div>
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">缩放 ({{ options.scale }}%)</label>
            <input v-model.number="options.scale" type="range" min="50" max="200" step="10" class="w-full accent-indigo-500" />
          </div>
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">视口</label>
            <select v-model="options.viewport" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="desktop">桌面</option><option value="tablet">平板</option><option value="mobile">手机</option>
            </select>
          </div>
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">页脚</label>
            <select v-model="options.footerMode" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="none">无</option><option value="pageNumber">页码</option><option value="date">日期</option>
            </select>
          </div>
          <div class="col-span-2 flex items-center gap-2">
            <input v-model="options.removeAds" type="checkbox" id="removeAds" class="accent-indigo-500" />
            <label for="removeAds" style="color: var(--text-secondary)">去除广告</label>
          </div>
          <div v-if="options.removeAds" class="col-span-2">
            <input v-model="options.customHideCss" type="text" placeholder="自定义隐藏 CSS 选择器"
              class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)" />
          </div>
        </div>
      </details>
    </div>

    <!-- 中间：转换按钮 -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 56px">
      <button @click="startConvert" :disabled="!canConvert"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="canConvert
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }">
        <svg v-if="processing" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-lg leading-none">▶</span>
        <span class="text-xs leading-tight">{{ processing ? '转换中' : '转换' }}</span>
      </button>
    </div>

    <!-- 右侧：结果区 -->
    <div class="flex-1 flex flex-col min-w-0 border-2 border-dashed rounded-2xl p-4"
         style="border-color: var(--border-color); background: var(--bg-card)">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">转换结果</label>
      <div class="flex-1 flex flex-col items-center justify-center overflow-hidden">
        <div v-if="!resultPdf && !processing" class="text-center">
          <span class="text-3xl">📑</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">点击"转换"生成 PDF</p>
        </div>
        <div v-else-if="processing" class="text-center">
          <svg class="animate-spin mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" opacity="0.15" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在渲染网页并生成 PDF...</p>
        </div>
        <div v-else-if="resultPdf" class="w-full flex-1 flex flex-col items-center justify-center gap-3">
          <span class="text-4xl">✅</span>
          <p class="text-sm" style="color: var(--text-primary)">PDF 生成成功</p>
          <button @click="downloadPdf"
            class="py-2 px-6 rounded-lg text-sm font-medium bg-indigo-500 hover:bg-indigo-600 text-white transition-colors">
            📥 下载 PDF
          </button>
          <button @click="resetAll" class="text-xs underline" style="color: var(--text-muted)">重新转换</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const { success: toastSuccess, error: toastError } = useToast()

// ===== Tab =====
const tabs = [
  { key: 'url' as const, label: '🔗 URL' },
  { key: 'file' as const, label: '📂 本地文件' },
]
const activeTab = ref<'url' | 'file'>('url')

// ===== URL 输入 =====
const urlInput = ref('')

// ===== HTML 文件 =====
const htmlInputRef = ref<HTMLInputElement | null>(null)
const htmlFile = ref<File | null>(null)

// ===== 资源文件夹 =====
const folderInputRef = ref<HTMLInputElement | null>(null)
const assetFiles = ref<AssetFile[]>([])
const showAssetList = ref(false)

interface AssetFile {
  file: File
  name: string
  relativePath: string
  size: number
}

const assetFolderName = computed(() => {
  if (assetFiles.value.length === 0) return ''
  const firstPath = assetFiles.value[0].relativePath
  const slashIndex = firstPath.indexOf('/')
  return slashIndex > 0 ? firstPath.substring(0, slashIndex) : 'assets'
})

const assetTotalSize = computed(() => assetFiles.value.reduce((s, f) => s + f.size, 0))

const suggestedFolder = computed(() => {
  if (!htmlFile.value) return ''
  const name = htmlFile.value.name
  const dotIndex = name.lastIndexOf('.')
  if (dotIndex <= 0) return ''
  return `${name.substring(0, dotIndex)}_files/`
})

// ===== 高级选项 =====
const options = ref({
  paperSize: 'A4', orientation: 'auto', margin: 'medium',
  scale: 100, viewport: 'desktop', footerMode: 'pageNumber',
  removeAds: true, customHideCss: '',
})

// ===== 状态 =====
const processing = ref(false)
const resultPdf = ref<Blob | null>(null)

// ===== 预览 =====
const previewHtml = ref<string | null>(null)
const previewSrc = ref<string | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
let previewDebounce: ReturnType<typeof setTimeout> | null = null
let assetPreviewDebounce: ReturnType<typeof setTimeout> | null = null

const previewLabel = computed(() => {
  if (activeTab.value === 'url') return urlInput.value
  return htmlFile.value?.name || ''
})

const canConvert = computed(() => {
  if (processing.value || previewLoading.value) return false
  if (activeTab.value === 'url') return urlInput.value.trim().length > 0
  return htmlFile.value !== null
})

watch(urlInput, (url) => {
  if (previewDebounce) clearTimeout(previewDebounce)
  if (activeTab.value !== 'url' || !url || !url.startsWith('http')) {
    revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false; return
  }
  previewLoading.value = true; previewError.value = ''
  previewDebounce = setTimeout(() => fetchUrlPreview(url), 800)
})

watch([htmlFile, assetFiles], ([file, assets]) => {
  if (assetPreviewDebounce) clearTimeout(assetPreviewDebounce)
  if (!file) {
    revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false; return
  }
  previewError.value = ''
  if (assets && assets.length > 0) {
    previewLoading.value = true
    assetPreviewDebounce = setTimeout(() => fetchFolderPreview(file, assets), 600)
  } else {
    const reader = new FileReader()
    reader.onload = () => { revokePreviewSrc(); previewHtml.value = stripAssetReferences(reader.result as string); previewLoading.value = false }
    reader.onerror = () => { revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false }
    previewLoading.value = true
    reader.readAsText(file)
  }
})

async function fetchUrlPreview(url: string) {
  try {
    const resp = await fetch(`/api/pdf/preview-html?url=${encodeURIComponent(url)}`)
    if (!resp.ok) throw new Error('预览加载失败')
    const blob = await resp.blob()
    revokePreviewSrc(); previewHtml.value = null; previewSrc.value = URL.createObjectURL(blob); previewLoading.value = false
  } catch (e: any) {
    revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false
    previewError.value = e.message || '无法加载页面预览'
  }
}

async function fetchFolderPreview(htmlFile: File, assets: AssetFile[]) {
  try {
    const formData = new FormData()
    formData.append('mainHtml', htmlFile.name)
    formData.append('files', htmlFile, htmlFile.name)
    for (const f of assets) formData.append('files', f.file, f.relativePath)
    const resp = await fetch('/api/pdf/preview-folder', { method: 'POST', body: formData })
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '预览加载失败' }))
      throw new Error(err.message || '预览加载失败')
    }
    const blob = await resp.blob()
    revokePreviewSrc(); previewHtml.value = null
    previewSrc.value = URL.createObjectURL(blob); previewLoading.value = false
  } catch (e: any) {
    revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false
    previewError.value = e.message || '无法加载页面预览'
  }
}

function revokePreviewSrc() {
  if (previewSrc.value) { URL.revokeObjectURL(previewSrc.value); previewSrc.value = null }
}

function retryPreview() {
  previewError.value = ''
  const file = htmlFile.value; const assets = assetFiles.value
  if (file && assets.length > 0) { previewLoading.value = true; fetchFolderPreview(file, assets) }
  else if (file) {
    const reader = new FileReader()
    reader.onload = () => { revokePreviewSrc(); previewHtml.value = stripAssetReferences(reader.result as string); previewLoading.value = false }
    reader.onerror = () => { revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false }
    previewLoading.value = true; reader.readAsText(file)
  }
}

function clearPreview() {
  urlInput.value = ''; htmlFile.value = null; assetFiles.value = []
  revokePreviewSrc(); previewHtml.value = null; previewLoading.value = false
}

// ===== HTML 文件操作 =====
function triggerHtmlInput() { htmlInputRef.value?.click() }
function handleHtmlSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.[0]) setHtmlFile(input.files[0])
  input.value = ''
}
function handleHtmlDrop(e: DragEvent) {
  const file = e.dataTransfer?.files?.[0]
  if (file) setHtmlFile(file)
}
function setHtmlFile(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!['html', 'htm'].includes(ext)) { toastError('仅支持 .html / .htm 格式'); return }
  if (file.size > 10 * 1024 * 1024) { toastError('文件不能超过 10MB'); return }
  if (file.size === 0) { toastError('文件为空'); return }
  htmlFile.value = file; resetResult()
}
function clearHtmlFile() { htmlFile.value = null; assetFiles.value = []; resetResult() }

// ===== 文件夹操作 =====
function triggerFolderInput() { folderInputRef.value?.click() }
function handleFolderSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) processFolderFiles(input.files)
  input.value = ''
}
function handleFolderDrop(e: DragEvent) {
  const items = e.dataTransfer?.items
  if (!items) return
  const entries: FileSystemEntry[] = []
  for (let i = 0; i < items.length; i++) {
    const entry = items[i].webkitGetAsEntry?.()
    if (entry) entries.push(entry)
  }
  if (entries.length === 0 && e.dataTransfer?.files) { processFolderFiles(e.dataTransfer.files); return }
  const allFiles: File[] = []
  let pending = entries.length
  entries.forEach(entry => {
    if (entry.isDirectory) {
      readDirRecursive(entry as FileSystemDirectoryEntry, '', (files) => { allFiles.push(...files); pending--; if (pending === 0) processFolderFilesFromList(allFiles) })
    } else {
      (entry as unknown as FileSystemFileEntry).file((f: File) => { allFiles.push(f); pending--; if (pending === 0) processFolderFilesFromList(allFiles) })
    }
  })
}
function readDirRecursive(entry: FileSystemDirectoryEntry, basePath: string, callback: (files: File[]) => void) {
  const reader = entry.createReader(); const allFiles: File[] = []
  function readBatch() {
    reader.readEntries((entries) => {
      if (entries.length === 0) { callback(allFiles); return }
      let pending = entries.length
      entries.forEach(e => {
        const path = basePath ? `${basePath}/${e.name}` : e.name
        if (e.isDirectory) {
          readDirRecursive(e as FileSystemDirectoryEntry, path, (files) => { allFiles.push(...files); pending--; if (pending === 0) readBatch() })
        } else {
          (e as unknown as FileSystemFileEntry).file((f: File) => {
            Object.defineProperty(f, '_relativePath', { value: path, writable: true })
            allFiles.push(f); pending--; if (pending === 0) readBatch()
          })
        }
      })
    })
  }
  readBatch()
}
function processFolderFiles(fileList: FileList) { processFolderFilesFromList(Array.from(fileList)) }
function processFolderFilesFromList(files: File[]) {
  if (files.length === 0) { toastError('文件夹为空'); return }
  if (files.length > 100) { toastError('文件数量不能超过 100 个'); return }
  const result: AssetFile[] = []
  for (const file of files) {
    const path = (file as any)._relativePath || file.webkitRelativePath || file.name
    result.push({ file, name: file.name, relativePath: path, size: file.size })
  }
  const totalSize = result.reduce((s, f) => s + f.size, 0)
  if (totalSize > 50 * 1024 * 1024) { toastError('文件夹总大小不能超过 50MB'); return }
  assetFiles.value = result; showAssetList.value = false; resetResult()
}
function clearFolder() { assetFiles.value = []; showAssetList.value = false; resetResult() }

function stripAssetReferences(html: string): string {
  let result = html
  result = result.replace(/<base[^>]*\/?>/gi, '')
  result = result.replace(/\s+(?:src|srcset|poster)=["'][^"']*["']/gi, '')
  result = result.replace(/(<link\b[^>]*?)\s+href=["'][^"']*["']/gi, '$1')
  result = result.replace(/\burl\(\s*["']?(?!https?:\/\/|data:)([^)"']+)["']?\s*\)/gi, 'none')
  return result
}

// ===== 工具函数 =====
function getFileIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const m: Record<string, string> = { css: '🎨', js: '📜', png: '🖼️', jpg: '🖼️', jpeg: '🖼️', gif: '🖼️', svg: '🖼️', webp: '🖼️', ico: '🖼️', woff: '🔤', woff2: '🔤', ttf: '🔤', eot: '🔤', otf: '🔤' }
  return m[ext] || '📎'
}
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// ===== 转换 =====
async function startConvert() {
  if (!canConvert.value || processing.value) return
  processing.value = true; resultPdf.value = null
  try {
    let resp: Response
    if (activeTab.value === 'url') {
      resp = await fetch('/api/pdf/url-to-pdf', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: urlInput.value.trim(), paperSize: options.value.paperSize, orientation: options.value.orientation, margin: options.value.margin, scale: options.value.scale, viewport: options.value.viewport, footerMode: options.value.footerMode, removeAds: options.value.removeAds, customHideCss: options.value.customHideCss }),
      })
    } else {
      if (assetFiles.value.length > 0) {
        const formData = new FormData()
        formData.append('mainHtml', htmlFile.value!.name)
        formData.append('files', htmlFile.value!, htmlFile.value!.name)
        for (const f of assetFiles.value) formData.append('files', f.file, f.relativePath)
        formData.append('paperSize', options.value.paperSize); formData.append('orientation', options.value.orientation)
        formData.append('margin', options.value.margin); formData.append('scale', String(options.value.scale))
        formData.append('viewport', options.value.viewport); formData.append('footerMode', options.value.footerMode)
        formData.append('removeAds', String(options.value.removeAds)); formData.append('customHideCss', options.value.customHideCss)
        resp = await fetch('/api/pdf/folder-to-pdf', { method: 'POST', body: formData })
      } else {
        const formData = new FormData()
        formData.append('file', htmlFile.value!)
        formData.append('paperSize', options.value.paperSize); formData.append('orientation', options.value.orientation)
        formData.append('margin', options.value.margin); formData.append('scale', String(options.value.scale))
        formData.append('viewport', options.value.viewport); formData.append('footerMode', options.value.footerMode)
        formData.append('removeAds', String(options.value.removeAds)); formData.append('customHideCss', options.value.customHideCss)
        resp = await fetch('/api/pdf/file-to-pdf', { method: 'POST', body: formData })
      }
    }
    if (!resp.ok) { const err = await resp.json().catch(() => ({ message: '转换服务异常' })); throw new Error(err.message || '转换失败') }
    resultPdf.value = await resp.blob(); toastSuccess('PDF 生成成功')
  } catch (e: any) { toastError(e.message || '转换失败，请检查服务是否可用') }
  finally { processing.value = false }
}

// ===== 下载 =====
function downloadPdf() {
  if (!resultPdf.value) return
  const url = URL.createObjectURL(resultPdf.value)
  const a = document.createElement('a')
  a.href = url; a.download = 'converted.pdf'
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function resetResult() { resultPdf.value = null }
function resetAll() { urlInput.value = ''; htmlFile.value = null; assetFiles.value = []; resultPdf.value = null }
</script>
