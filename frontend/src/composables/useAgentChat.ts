// frontend/src/composables/useAgentChat.ts
import { ref, nextTick } from 'vue'
import { useSSE, type SseEvent } from './useSSE'

export interface ChatFile {
  name: string
  size: number
}

export interface ChatResult {
  fileName: string
  fileId: string
  size: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  files?: ChatFile[]
  result?: ChatResult
  isProcessing?: boolean
  isError?: boolean
  isRedo?: boolean
}

export type ChatState = 'idle' | 'waiting' | 'ready' | 'processing' | 'done' | 'error' | 'cancelled'
export type DuplicateChoice = 'return-last' | 'redo' | 'cancel'

/**
 * 生成请求指纹 — 用于判断是否为重复操作
 * 基于文件列表（名称+大小）+ 消息文本，不依赖 hash 函数
 */
function generateFingerprint(files: File[] | undefined, message: string): string {
  const filePart = (files ?? [])
    .map(f => `${f.name}:${f.size}`)
    .sort()
    .join('|')
  return `${filePart}||${message.trim()}`
}

/** 待确认的重复请求载荷 */
interface PendingPayload {
  text: string
  files?: File[]
  fingerprint: string
}

export function useAgentChat() {
  const messages = ref<ChatMessage[]>([])
  const state = ref<ChatState>('idle')
  const conversationId = ref<string | null>(null)
  const inputDisabled = ref(false)

  /** 上一次请求的指纹，用于判断重复操作 */
  const lastFingerprint = ref<string | null>(null)
  /** 上一次请求的结果（用于"返回上次结果"） */
  const lastResult = ref<ChatResult | null>(null)
  /** 上一次助手回复文本 */
  const lastReplyText = ref<string>('')
  /** 是否正在等待用户确认重复操作 */
  const pendingDuplicate = ref(false)
  /** 暂存的重复请求数据 */
  const pendingPayload = ref<PendingPayload | null>(null)

  const { connect: sseConnect, disconnect: sseDisconnect } = useSSE()

  function initChat(): void {
    messages.value = [{
      role: 'assistant',
      content: '你好！我是文档处理助手 🤖\n\n可以帮你处理以下文件：\n\n📄 文档转换\n· Word / WPS 文档转 PDF\n· Markdown 转 DOCX\n· HTML 转 PDF（网页 URL 或本地 HTML 文件）\n\n📑 PDF 处理\n· PDF 切分 / 合并 / 压缩 / 转图片\n· PDF 编排（排序/删页/旋转/插空白页）\n· PDF 加密（设置密码和权限保护）\n\n🖼️ 图片处理\n· 图片转 PDF（JPG/PNG/WEBP/GIF）\n\n请上传文件或直接告诉我你的需求 👇'
    }]
    state.value = 'idle'
    lastFingerprint.value = null
    lastResult.value = null
    lastReplyText.value = ''
    pendingDuplicate.value = false
    pendingPayload.value = null
  }

  async function sendMessage(text: string, files?: File[]): Promise<void> {
    if (!text.trim() && (!files || files.length === 0)) return
    if (state.value === 'processing') return

    // 幂等性检查：相同文件 + 相同指令 → 暂停等待用户三选
    const fingerprint = generateFingerprint(files, text)
    if (lastFingerprint.value && lastFingerprint.value === fingerprint
        && (lastResult.value || lastReplyText.value)) {
      pendingPayload.value = { text, files, fingerprint }
      pendingDuplicate.value = true
      return
    }

    await doSend(text, files, fingerprint, false)
  }

  /**
   * 实际发送请求 — isRedo=true 时消息气泡显示"重新执行"标记
   */
  async function doSend(text: string, files: File[] | undefined,
                        fingerprint: string, isRedo: boolean): Promise<void> {
    lastFingerprint.value = fingerprint

    // 添加用户消息
    const userMsg: ChatMessage = {
      role: 'user',
      content: text || '[上传了文件]',
      files: files?.map(f => ({ name: f.name, size: f.size })),
      isRedo
    }
    messages.value.push(userMsg)

    // 添加助手占位消息
    const assistantMsg: ChatMessage = { role: 'assistant', content: '', isProcessing: true }
    messages.value.push(assistantMsg)
    state.value = 'processing'
    inputDisabled.value = true

    // 构建 FormData
    const formData = new FormData()
    formData.append('message', text)
    if (files) files.forEach(f => formData.append('files', f))
    if (conversationId.value) formData.append('conversationId', conversationId.value)

    // SSE 连接
    await sseConnect(
      formData,
      (event) => handleEvent(event, assistantMsg),
      (error) => {
        assistantMsg.content = error
        assistantMsg.isProcessing = false
        assistantMsg.isError = true
        state.value = 'error'
        inputDisabled.value = false
      }
    )

    await nextTick()
  }

  /**
   * 处理重复操作的用户选择
   */
  async function resolveDuplicate(choice: DuplicateChoice): Promise<void> {
    const payload = pendingPayload.value
    pendingDuplicate.value = false
    pendingPayload.value = null

    if (!payload) return

    if (choice === 'cancel') {
      return
    }

    if (choice === 'return-last') {
      // 直接展示上次结果，不请求后端
      const userMsg: ChatMessage = {
        role: 'user',
        content: payload.text || '[上传了文件]',
        files: payload.files?.map(f => ({ name: f.name, size: f.size })),
      }
      messages.value.push(userMsg)

      const assistantMsg: ChatMessage = {
        role: 'assistant',
        content: lastReplyText.value || '处理完成。',
        result: lastResult.value ?? undefined,
      }
      messages.value.push(assistantMsg)
      return
    }

    // choice === 'redo' → 重新请求
    await doSend(payload.text, payload.files, payload.fingerprint, true)
  }

  function handleEvent(event: SseEvent, msg: ChatMessage): void {
    switch (event.type) {
      case 'thinking':
        msg.content = event.text || '思考中...'
        // 从服务器获取 conversationId，后续请求复用
        if (event.conversationId) {
          conversationId.value = event.conversationId
        }
        break
      case 'tool_call': {
        const toolNames: Record<string, string> = {
          pdfSplit: '切分 PDF', pdfMerge: '合并 PDF',
          pdfCompress: '压缩 PDF', pdfToImage: '转换图片',
          docToPdf: '转 PDF', mdToDocx: '转 DOCX'
        }
        msg.content = `正在${toolNames[event.tool || ''] || '处理'}...`
        break
      }
      case 'result':
        if (event.fileName && event.fileId) {
          msg.result = {
            fileName: event.fileName,
            fileId: event.fileId,
            size: event.size || ''
          }
          // 缓存结果用于"返回上次结果"
          lastResult.value = msg.result
        }
        break
      case 'reply':
        msg.content = event.text || ''
        msg.isProcessing = false
        state.value = 'done'
        inputDisabled.value = false
        // 缓存回复文本用于"返回上次结果"
        lastReplyText.value = event.text || ''
        break
      case 'error':
        msg.content = event.text || '处理出错'
        msg.isProcessing = false
        msg.isError = true
        state.value = 'error'
        inputDisabled.value = false
        break
      case 'done':
        if (msg.isProcessing) {
          msg.isProcessing = false
          state.value = 'done'
          inputDisabled.value = false
        }
        break
    }
  }

  async function cancelProcessing(): Promise<void> {
    sseDisconnect()
    if (conversationId.value) {
      await fetch(`/api/agent/cancel?conversationId=${conversationId.value}`, { method: 'POST' })
    }
    state.value = 'cancelled'
    inputDisabled.value = false
  }

  function downloadUrl(fileId: string): string {
    return `/api/agent/download/${fileId}`
  }

  return {
    messages, state, conversationId, inputDisabled,
    pendingDuplicate,
    initChat, sendMessage, resolveDuplicate, cancelProcessing, downloadUrl
  }
}
