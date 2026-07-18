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
}

export type ChatState = 'idle' | 'waiting' | 'ready' | 'processing' | 'done' | 'error' | 'cancelled'

export function useAgentChat() {
  const messages = ref<ChatMessage[]>([])
  const state = ref<ChatState>('idle')
  const conversationId = ref<string | null>(null)
  const inputDisabled = ref(false)

  const { connect: sseConnect, disconnect: sseDisconnect } = useSSE()

  function initChat(): void {
    messages.value = [{
      role: 'assistant',
      content: '你好！我是文档处理助手 🤖\n\n可以帮你处理 PDF / Word / WPS / Markdown 文件：\n· PDF 切分 / 合并 / 压缩 / 转图片\n· PDF 编排（排序/删页/旋转/插空白页）\n· Word / WPS 文档转 PDF\n· Markdown 转 DOCX\n\n请上传文件或直接告诉我你的需求 👇'
    }]
    state.value = 'idle'
  }

  async function sendMessage(text: string, files?: File[]): Promise<void> {
    if (!text.trim() && (!files || files.length === 0)) return
    if (state.value === 'processing') return

    // 添加用户消息
    const userMsg: ChatMessage = {
      role: 'user',
      content: text || '[上传了文件]',
      files: files?.map(f => ({ name: f.name, size: f.size }))
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

  function handleEvent(event: SseEvent, msg: ChatMessage): void {
    switch (event.type) {
      case 'thinking':
        msg.content = event.text || '思考中...'
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
        }
        break
      case 'reply':
        msg.content = event.text || ''
        msg.isProcessing = false
        state.value = 'done'
        inputDisabled.value = false
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
    initChat, sendMessage, cancelProcessing, downloadUrl
  }
}
