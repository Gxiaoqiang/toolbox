import { ref, computed } from 'vue'
import { useToast } from '@/composables/useToast'
import { getPageInfos, renderThumbnail, type PageInfo } from './pdfRenderer'

// ========== 类型定义 ==========

/** 导出给后端的 plan JSON 格式，字段名严格匹配 PdfArrangeItem record */
export interface PlanItem {
  /** 源文件下标（0-based），blank 时为 null */
  file: number | null
  /** 页码（1-based），blank 时为 null */
  page: number | null
  /** 旋转度数 */
  rotate: number
  /** 是否空白页 */
  blank: boolean
  /** 空白页宽（pt） */
  width?: number
  /** 空白页高（pt） */
  height?: number
}

/** 编排中的单页条目 */
export interface PageEntry {
  uid: number
  /** 来源文件下标（-1 为空白页） */
  fileIndex: number
  /** 来源页码（1-based） */
  filePage: number
  /** 缩略图 data URL */
  thumbnail: string
  /** 用户旋转度数 */
  userRotation: number
  /** 原始页面宽度 pt */
  width: number
  /** 原始页面高度 pt */
  height: number
  /** 是否空白页 */
  blank: boolean
  /** 空白页指定宽度 pt */
  blankWidth?: number
  /** 空白页指定高度 pt */
  blankHeight?: number
}

/** 源文件条目 */
export interface FileItem {
  uid: number
  file: File
  buffer: ArrayBuffer | null
  pages: number
  pageInfos: PageInfo[]
  error: string
  loading: boolean
  color: string
}

// ========== 常量 ==========

const MAX_FILES = 10
const MAX_SIZE = 10 * 1024 * 1024
const MAX_TOTAL = 45 * 1024 * 1024
const MAX_PAGES = 300

const FILE_COLORS = [
  '#6366f1', '#ec4899', '#10b981', '#f59e0b', '#8b5cf6',
  '#06b6d4', '#f43f5e', '#84cc16', '#f97316', '#14b8a6',
]

// ========== 工厂 ==========

let _uidCounter = 0
function nextUid(): number {
  return ++_uidCounter
}

function blankPageEntry(): PageEntry {
  return {
    uid: nextUid(),
    fileIndex: -1,
    filePage: -1,
    thumbnail: '',
    userRotation: 0,
    width: 595,
    height: 842,
    blank: true,
  }
}

// ========== Composable ==========

export function usePdfArranger() {
  const { error: toastError, success: toastSuccess } = useToast()

  const fileItems = ref<FileItem[]>([])
  const pages = ref<PageEntry[]>([])
  const processing = ref(false)

  // ===== 计算属性 =====

  const hasErrors = computed(() => fileItems.value.some(f => !!f.error))
  const fileCount = computed(() => fileItems.value.length)
  const pageCount = computed(() => pages.value.length)
  const totalSize = computed(() =>
    fileItems.value.reduce((sum, f) => sum + f.file.size, 0)
  )
  const canExport = computed(() =>
    pageCount.value > 0 && !hasErrors.value && !processing.value
  )

  // ===== 文件管理 =====

  async function addFiles(files: FileList | File[]) {
    const arr = Array.from(files)

    for (const f of arr) {
      if (fileItems.value.length >= MAX_FILES) {
        toastError(`最多导入 ${MAX_FILES} 个 PDF 文件`)
        break
      }
      const ext = f.name.split('.').pop()?.toLowerCase() ?? ''
      if (ext !== 'pdf') {
        toastError(`不支持的文件格式: ${f.name}`)
        continue
      }
      if (f.size === 0) {
        toastError(`文件为空: ${f.name}`)
        continue
      }
      if (f.size > MAX_SIZE) {
        toastError(`文件过大（超过 10MB）: ${f.name}`)
        continue
      }
      if (totalSize.value + f.size > MAX_TOTAL) {
        toastError(`所有文件总大小不能超过 45MB`)
        continue
      }

      const item: FileItem = {
        uid: nextUid(),
        file: f,
        buffer: null,
        pages: 0,
        pageInfos: [],
        error: '',
        loading: true,
        color: FILE_COLORS[fileItems.value.length % FILE_COLORS.length],
      }

      fileItems.value.push(item)
      const fileIndex = fileItems.value.length - 1

      try {
        const buffer = await f.arrayBuffer()
        item.buffer = buffer

        const pageInfos = await getPageInfos(buffer)
        item.pages = pageInfos.length
        item.pageInfos = pageInfos
        item.loading = false

        if (pageCount.value + item.pages > MAX_PAGES) {
          item.error = `文件页数 (${item.pages}) 将导致总量超过 ${MAX_PAGES} 页上限`
          fileItems.value.splice(fileIndex, 1)
          toastError(item.error)
          continue
        }

        // 渲染前 50 页缩略图（其余走 IntersectionObserver 懒渲染）
        const toRender = Math.min(item.pages, 50)
        for (let p = 1; p <= toRender; p++) {
          const dataUrl = await renderThumbnail(buffer, p, 0.25)
          pages.value.push({
            uid: nextUid(),
            fileIndex,
            filePage: p,
            thumbnail: dataUrl,
            userRotation: 0,
            width: pageInfos[p - 1].width,
            height: pageInfos[p - 1].height,
            blank: false,
          })
        }
        for (let p = toRender + 1; p <= item.pages; p++) {
          pages.value.push({
            uid: nextUid(),
            fileIndex,
            filePage: p,
            thumbnail: '',
            userRotation: 0,
            width: pageInfos[p - 1].width,
            height: pageInfos[p - 1].height,
            blank: false,
          })
        }
      } catch (_e) {
        item.error = `${f.name} 无法读取（已加密或损坏），请修复或移除该文件后再继续`
        item.loading = false
        toastError(item.error)
      }
    }
  }

  function removeFile(fileIndex: number) {
    pages.value = pages.value.filter(p => p.fileIndex !== fileIndex)
    fileItems.value.splice(fileIndex, 1)
    // 修正后续 fileIndex
    for (const p of pages.value) {
      if (!p.blank && p.fileIndex > fileIndex) {
        p.fileIndex--
      }
    }
  }

  // ===== 页面操作 =====

  function deletePage(pageIdx: number) {
    if (processing.value) return
    pages.value.splice(pageIdx, 1)
  }

  function rotatePage(pageIdx: number) {
    if (processing.value || pages.value[pageIdx].blank) return
    const entry = pages.value[pageIdx]
    entry.userRotation = (entry.userRotation + 90) % 360
  }

  function duplicatePage(pageIdx: number) {
    if (processing.value) return
    const src = pages.value[pageIdx]
    const copy: PageEntry = { ...src, uid: nextUid() }
    pages.value.splice(pageIdx + 1, 0, copy)
  }

  function insertBlankPage(afterIdx: number) {
    if (processing.value) return
    const entry = blankPageEntry()
    if (afterIdx >= 0 && afterIdx < pages.value.length) {
      const prev = pages.value[afterIdx]
      entry.width = prev.width
      entry.height = prev.height
    }
    pages.value.splice(afterIdx + 1, 0, entry)
  }

  function clearAll() {
    pages.value = []
    fileItems.value = []
    _uidCounter = 0
  }

  // ===== 导出 =====

  function buildPlan(): PlanItem[] {
    return pages.value.map(p => {
      if (p.blank) {
        const item: PlanItem = { file: null, page: null, rotate: 0, blank: true }
        if (p.blankWidth) {
          item.width = p.blankWidth
          item.height = p.blankHeight
        }
        return item
      }
      return {
        file: p.fileIndex,
        page: p.filePage,
        rotate: p.userRotation,
        blank: false,
      }
    })
  }

  async function exportPdf(): Promise<Blob | null> {
    if (!canExport.value) return null
    processing.value = true

    try {
      const formData = new FormData()
      for (const item of fileItems.value) {
        formData.append('files', item.file)
      }
      formData.append('plan', JSON.stringify(buildPlan()))

      const resp = await fetch('/api/pdf/arrange', {
        method: 'POST',
        body: formData,
      })

      if (!resp.ok) {
        const errJson = await resp.json().catch(() => ({ message: '编排失败' }))
        throw new Error(errJson.message || '编排失败')
      }

      const blob = await resp.blob()
      toastSuccess('PDF 编排完成')
      return blob
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'PDF 编排失败，请稍后重试'
      toastError(msg)
      return null
    } finally {
      processing.value = false
    }
  }

  return {
    fileItems,
    pages,
    processing,
    hasErrors,
    fileCount,
    pageCount,
    totalSize,
    canExport,
    addFiles,
    removeFile,
    deletePage,
    rotatePage,
    duplicatePage,
    insertBlankPage,
    clearAll,
    exportPdf,
    MAX_FILES,
    MAX_SIZE,
    MAX_PAGES,
    FILE_COLORS,
  }
}
