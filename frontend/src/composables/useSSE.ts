// frontend/src/composables/useSSE.ts
import { ref, onUnmounted } from 'vue'

export interface SseEvent {
  type: 'thinking' | 'tool_call' | 'progress' | 'result' | 'reply' | 'error' | 'heartbeat' | 'done'
  text?: string
  tool?: string
  params?: string
  fileName?: string
  fileId?: string
  size?: string
  progress?: number
}

export function useSSE() {
  const isConnected = ref(false)
  const lastEvent = ref<SseEvent | null>(null)
  const reconnectCount = ref(0)

  let abortController: AbortController | null = null
  let heartbeatTimer: ReturnType<typeof setTimeout> | null = null
  const MAX_RECONNECT = 3

  async function connect(
    formData: FormData,
    onEvent: (event: SseEvent) => void,
    onError?: (error: string) => void
  ): Promise<void> {
    disconnect()
    abortController = new AbortController()
    isConnected.value = true

    try {
      const response = await fetch('/api/agent/chat', {
        method: 'POST',
        body: formData,
        signal: abortController.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      /** 逐行解析 SSE 协议格式 */
      function processLine(line: string): void {
        // 兼容 "event:xxx" 和 "event: xxx" 两种格式
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const raw = line.slice(5).trim()
          if (!raw) return
          try {
            const data: SseEvent = JSON.parse(raw)
            data.type = (currentEvent || data.type || 'reply') as SseEvent['type']
            lastEvent.value = data
            onEvent(data)
            resetHeartbeat()
          } catch {
            // skip malformed JSON
          }
          currentEvent = ''
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          // 流结束前处理 buffer 中剩余的数据
          if (buffer.trim()) {
            for (const line of buffer.split('\n')) processLine(line)
          }
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) processLine(line)
      }
    } catch (err: any) {
      if (err.name === 'AbortError') return
      isConnected.value = false
      if (reconnectCount.value < MAX_RECONNECT) {
        reconnectCount.value++
        setTimeout(() => connect(formData, onEvent, onError), 3000)
      } else {
        onError?.('连接已断开，请刷新页面重试')
      }
    }
  }

  function resetHeartbeat(): void {
    if (heartbeatTimer) clearTimeout(heartbeatTimer)
    heartbeatTimer = setTimeout(() => {
      isConnected.value = false
    }, 35000)
  }

  function disconnect(): void {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    if (heartbeatTimer) {
      clearTimeout(heartbeatTimer)
      heartbeatTimer = null
    }
    isConnected.value = false
  }

  onUnmounted(() => disconnect())

  return { isConnected, lastEvent, reconnectCount, connect, disconnect }
}
