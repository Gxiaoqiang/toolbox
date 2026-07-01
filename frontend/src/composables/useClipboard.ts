import { ref } from 'vue'

/**
 * 剪贴板操作 composable
 */
export function useClipboard() {
  const copied = ref(false)

  async function copy(text: string): Promise<boolean> {
    try {
      await navigator.clipboard.writeText(text)
      copied.value = true
      setTimeout(() => { copied.value = false }, 2000)
      return true
    } catch {
      // 降级方案
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      copied.value = true
      setTimeout(() => { copied.value = false }, 2000)
      return true
    }
  }

  return { copied, copy }
}
