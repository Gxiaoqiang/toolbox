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
    <!-- 左侧：输入区（占 2 份宽度） -->
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

      <!-- URL 输入 -->
      <div v-if="activeTab === 'url'" class="flex flex-col gap-2">
        <input v-model="urlInput" type="url" placeholder="请输入网页 URL，如 https://example.com"
          class="w-full px-3 py-2 text-sm rounded-lg border outline-none transition-colors"
          style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)"
          @focus="($event.target as HTMLInputElement).style.borderColor = 'var(--accent-color)'"
          @blur="($event.target as HTMLInputElement).style.borderColor = 'var(--border-color)'" />
        <p class="text-xs" style="color: var(--text-muted)">💡 如需转换需要登录的页面，请在浏览器中打开后 Ctrl+S 保存为 HTML 文件上传</p>
      </div>

      <!-- 文件上传 -->
      <div v-else
        class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center cursor-pointer transition-colors hover:border-indigo-400 hover:bg-indigo-50/30"
        :class="htmlFile ? 'h-16 gap-1' : 'flex-1 gap-3'"
        style="border-color: var(--border-color); background: var(--bg-main)"
        @click="triggerFileInput"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop">
        <template v-if="!htmlFile">
          <span class="text-3xl">🌐</span>
          <p class="text-sm" style="color: var(--text-primary)">拖拽 HTML 文件到此处</p>
          <p class="text-xs" style="color: var(--text-muted)">或点击选择 · .html / .htm · ≤10MB</p>
        </template>
        <template v-else>
          <div class="flex items-center gap-2 w-full px-3">
            <span class="text-sm">📄</span>
            <span class="flex-1 text-sm truncate" style="color: var(--text-primary)">{{ htmlFile.name }}</span>
            <button v-if="!processing" @click.stop="clearFile"
              class="w-5 h-5 rounded flex items-center justify-center text-xs hover:bg-red-50"
              style="color: var(--text-muted)">✕</button>
          </div>
        </template>
      </div>
      <input ref="fileInputRef" type="file" accept=".html,.htm" class="hidden" @change="handleFileSelect" />

      <!-- 预览区 -->
      <div v-if="previewHtml !== null" class="flex-1 min-h-0 rounded-lg border overflow-hidden"
           style="border-color: var(--border-color)">
        <div class="flex items-center justify-between px-3 py-1.5 border-b"
             style="border-color: var(--border-color); background: var(--bg-main)">
          <span class="text-xs truncate" style="color: var(--text-muted)">{{ previewLabel }}</span>
          <button @click="clearPreview" class="text-xs underline" style="color: var(--text-muted)">清除预览</button>
        </div>
        <!-- 加载中 -->
        <div v-if="previewLoading" class="flex items-center justify-center h-full">
          <svg class="animate-spin mr-2" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <span class="text-sm" style="color: var(--text-muted)">正在加载预览...</span>
        </div>
        <!-- 预览内容 -->
        <iframe v-else-if="previewHtml" :srcdoc="previewHtml"
          class="w-full h-full border-0" style="min-height: 200px" sandbox="allow-same-origin"></iframe>
        <!-- 预览失败 -->
        <div v-else class="flex flex-col items-center justify-center h-full gap-2">
          <span class="text-2xl">⚠️</span>
          <p class="text-sm" style="color: var(--text-muted)">预览加载失败</p>
          <p class="text-xs" style="color: var(--text-muted)">{{ previewError }}</p>
        </div>
      </div>

      <!-- 高级选项 -->
      <details class="group">
        <summary class="flex items-center gap-1 cursor-pointer text-xs font-medium select-none"
                 style="color: var(--text-secondary)">
          <span class="transition-transform group-open:rotate-90">▸</span>
          高级选项
        </summary>
        <div class="mt-2 grid grid-cols-2 gap-2 text-xs">
          <!-- 纸张 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">纸张大小</label>
            <select v-model="options.paperSize" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="A4">A4</option>
              <option value="Letter">Letter</option>
              <option value="Legal">Legal</option>
            </select>
          </div>
          <!-- 方向 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">方向</label>
            <select v-model="options.orientation" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="portrait">纵向</option>
              <option value="landscape">横向</option>
            </select>
          </div>
          <!-- 边距 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">边距</label>
            <select v-model="options.margin" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="none">无</option>
              <option value="narrow">窄 (10mm)</option>
              <option value="medium">中 (20mm)</option>
              <option value="wide">宽 (30mm)</option>
            </select>
          </div>
          <!-- 缩放 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">缩放 ({{ options.scale }}%)</label>
            <input v-model.number="options.scale" type="range" min="50" max="200" step="10"
              class="w-full accent-indigo-500" />
          </div>
          <!-- 视口 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">视口</label>
            <select v-model="options.viewport" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="desktop">桌面 (1280px)</option>
              <option value="tablet">平板 (768px)</option>
              <option value="mobile">手机 (375px)</option>
            </select>
          </div>
          <!-- 页脚 -->
          <div>
            <label class="block mb-1" style="color: var(--text-muted)">页脚</label>
            <select v-model="options.footerMode" class="w-full px-2 py-1.5 rounded border text-xs"
              style="border-color: var(--border-color); background: var(--bg-main); color: var(--text-primary)">
              <option value="none">无</option>
              <option value="pageNumber">页码</option>
              <option value="date">日期</option>
            </select>
          </div>
          <!-- 去广告 -->
          <div class="col-span-2 flex items-center gap-2">
            <input v-model="options.removeAds" type="checkbox" id="removeAds" class="accent-indigo-500" />
            <label for="removeAds" style="color: var(--text-secondary)">去除广告</label>
          </div>
          <!-- 自定义隐藏 CSS -->
          <div v-if="options.removeAds" class="col-span-2">
            <label class="block mb-1" style="color: var(--text-muted)">自定义隐藏 CSS（可选）</label>
            <input v-model="options.customHideCss" type="text" placeholder=".ad-container, #banner"
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
        <!-- 空态 -->
        <div v-if="!resultPdf && !processing" class="text-center">
          <span class="text-3xl">📑</span>
          <p class="text-sm mt-2" style="color: var(--text-muted)">点击"转换"生成 PDF</p>
        </div>
        <!-- 转换中 -->
        <div v-else-if="processing" class="text-center">
          <svg class="animate-spin mx-auto mb-3" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2.5" opacity="0.15" style="color: var(--accent-color)"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="color: var(--accent-color)"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在渲染网页并生成 PDF...</p>
        </div>
        <!-- 有结果 -->
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
  { key: 'file' as const, label: '📄 文件上传' },
]
const activeTab = ref<'url' | 'file'>('url')

// 切换 Tab 时重置预览
watch(activeTab, () => {
  previewHtml.value = null
  previewLoading.value = false
  previewError.value = ''
})

// ===== URL 输入 =====
const urlInput = ref('')

// ===== 文件上传 =====
const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const htmlFile = ref<File | null>(null)

// ===== 高级选项 =====
const options = ref({
  paperSize: 'A4',
  orientation: 'portrait',
  margin: 'medium',
  scale: 100,
  viewport: 'desktop',
  footerMode: 'pageNumber',
  removeAds: true,
  customHideCss: '',
})

// ===== 状态 =====
const processing = ref(false)
const resultPdf = ref<Blob | null>(null)

// ===== 预览 =====
const previewHtml = ref<string | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
let previewDebounce: ReturnType<typeof setTimeout> | null = null

const previewLabel = computed(() => {
  if (activeTab.value === 'url') return urlInput.value
  return htmlFile.value?.name || ''
})

// URL 输入变化时，防抖抓取预览
watch(urlInput, (url) => {
  if (previewDebounce) clearTimeout(previewDebounce)
  if (activeTab.value !== 'url' || !url || !url.startsWith('http')) {
    previewHtml.value = null
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  previewError.value = ''
  previewDebounce = setTimeout(() => fetchPreview(url), 800)
})

// 文件选择后读取内容用于预览
watch(htmlFile, (file) => {
  if (file) {
    const reader = new FileReader()
    reader.onload = () => {
      previewHtml.value = reader.result as string
      previewLoading.value = false
      previewError.value = ''
    }
    reader.onerror = () => {
      previewHtml.value = null
      previewLoading.value = false
      previewError.value = '文件读取失败'
    }
    previewLoading.value = true
    reader.readAsText(file)
  } else {
    previewHtml.value = null
  }
})

// 通过后端代理抓取 URL 的 HTML 内容
async function fetchPreview(url: string) {
  try {
    const resp = await fetch(`/api/pdf/preview-html?url=${encodeURIComponent(url)}`)
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ message: '预览加载失败' }))
      throw new Error(err.message || '预览加载失败')
    }
    previewHtml.value = await resp.text()
    previewLoading.value = false
  } catch (e: any) {
    previewHtml.value = null
    previewLoading.value = false
    previewError.value = e.message || '无法加载页面预览'
  }
}

function clearPreview() {
  urlInput.value = ''
  htmlFile.value = null
  previewHtml.value = null
  previewLoading.value = false
  previewError.value = ''
}

// ===== 文件操作 =====
function triggerFileInput() { fileInputRef.value?.click() }

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files[0]) {
    setFile(input.files[0])
  }
  input.value = ''
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) {
    setFile(e.dataTransfer.files[0])
  }
}

function setFile(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!['html', 'htm'].includes(ext)) {
    toastError('仅支持 .html / .htm 格式')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    toastError('文件不能超过 10MB')
    return
  }
  if (file.size === 0) {
    toastError('文件为空')
    return
  }
  htmlFile.value = file
  resetResult()
}

function clearFile() {
  htmlFile.value = null
  resetResult()
}

// ===== 计算属性 =====
const canConvert = computed(() => {
  if (processing.value) return false
  if (activeTab.value === 'url') return urlInput.value.trim().length > 0
  return htmlFile.value !== null
})

// ===== 转换 =====
async function startConvert() {
  if (!canConvert.value || processing.value) return
  processing.value = true
  resultPdf.value = null

  try {
    let resp: Response

    if (activeTab.value === 'url') {
      resp = await fetch('/api/pdf/url-to-pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          url: urlInput.value.trim(),
          paperSize: options.value.paperSize,
          orientation: options.value.orientation,
          margin: options.value.margin,
          scale: options.value.scale,
          viewport: options.value.viewport,
          footerMode: options.value.footerMode,
          removeAds: options.value.removeAds,
          customHideCss: options.value.customHideCss,
        }),
      })
    } else {
      const formData = new FormData()
      formData.append('file', htmlFile.value!)
      formData.append('paperSize', options.value.paperSize)
      formData.append('orientation', options.value.orientation)
      formData.append('margin', options.value.margin)
      formData.append('scale', String(options.value.scale))
      formData.append('viewport', options.value.viewport)
      formData.append('footerMode', options.value.footerMode)
      formData.append('removeAds', String(options.value.removeAds))
      formData.append('customHideCss', options.value.customHideCss)
      resp = await fetch('/api/pdf/file-to-pdf', { method: 'POST', body: formData })
    }

    if (!resp.ok) {
      const errJson = await resp.json().catch(() => ({ message: '转换服务异常' }))
      throw new Error(errJson.message || '转换失败')
    }

    resultPdf.value = await resp.blob()
    toastSuccess('PDF 生成成功')
  } catch (e: any) {
    toastError(e.message || '转换失败，请检查服务是否可用')
  } finally {
    processing.value = false
  }
}

// ===== 下载 =====
function downloadPdf() {
  if (!resultPdf.value) return
  const url = URL.createObjectURL(resultPdf.value)
  const a = document.createElement('a')
  a.href = url
  a.download = activeTab.value === 'url' ? 'webpage.pdf' : 'converted.pdf'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function resetResult() {
  resultPdf.value = null
}

function resetAll() {
  urlInput.value = ''
  htmlFile.value = null
  resultPdf.value = null
}
</script>
