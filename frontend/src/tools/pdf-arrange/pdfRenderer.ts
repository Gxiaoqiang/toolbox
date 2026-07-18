import * as pdfjsLib from 'pdfjs-dist'

// pdfjs-dist v6 worker 使用 CDN（避免 Rollup 无法解析 .mjs 的 ?url import）
pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`

export interface PageInfo {
  /** 页码（1-based） */
  pageNumber: number
  /** 页面宽度 (pt) */
  width: number
  /** 页面高度 (pt) */
  height: number
  /** 页面旋转 (度) */
  rotation: number
}

/**
 * 获取 PDF 所有页面的尺寸和旋转信息（不含渲染）。
 */
export async function getPageInfos(data: ArrayBuffer): Promise<PageInfo[]> {
  const loadingTask = pdfjsLib.getDocument({ data })
  const pdf = await loadingTask.promise

  const infos: PageInfo[] = []
  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i)
    const viewport = page.getViewport({ scale: 1 })
    infos.push({
      pageNumber: i,
      width: viewport.width,
      height: viewport.height,
      rotation: page.rotate,
    })
    page.cleanup()
  }
  await loadingTask.destroy()
  return infos
}

/**
 * 渲染单页缩略图，返回 data URL。
 *
 * @param data  PDF 文件 ArrayBuffer
 * @param page  页码（1-based）
 * @param scale 缩放比例（默认 0.25）
 * @returns PNG base64 data URL
 */
export async function renderThumbnail(
  data: ArrayBuffer,
  page: number,
  scale: number = 0.25
): Promise<string> {
  const loadingTask = pdfjsLib.getDocument({ data })
  const pdf = await loadingTask.promise

  const pdfPage = await pdf.getPage(page)
  const viewport = pdfPage.getViewport({ scale })

  const canvas = document.createElement('canvas')
  canvas.width = viewport.width
  canvas.height = viewport.height

  await pdfPage.render({
    canvas,
    viewport,
  }).promise

  const dataUrl = canvas.toDataURL('image/png')
  canvas.remove()
  pdfPage.cleanup()
  await loadingTask.destroy()
  return dataUrl
}
