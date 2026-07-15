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

  /**
   * 通过 fetch + ReadableStream 发起 SSE 连接（POST multipart）
   */
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

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event: ')) {
            currentEvent = line.slice(7).trim()
          } else if (line.startsWith('data: ')) {
            try {
              const data: SseEvent = JSON.parse(line.slice(6))
              data.type = (currentEvent || data.type || 'reply') as SseEvent['type']
              lastEvent.value = data
              onEvent(data)
              resetHeartbeat()
            } catch {
              // skip malformed JSON lines
            }
            currentEvent = ''
          }
        }
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
    }, 35000) // 30s + 5s buffer
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
