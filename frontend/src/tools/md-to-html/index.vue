<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <label class="text-xs font-semibold text-slate-500 mb-2">Markdown 输入</label>
      <textarea
        v-model="input"
        class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        placeholder="# 在这里输入 Markdown..."
        @input="renderHtml"
      ></textarea>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">HTML 预览</label>
        <div class="flex gap-2">
          <button @click="copyHtml" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors">
            {{ copied ? '已复制' : '复制 HTML' }}
          </button>
          <button @click="downloadHtml" class="px-3 py-1 text-xs rounded-md bg-blue-500 hover:bg-blue-600 text-white transition-colors">
            下载 .html
          </button>
        </div>
      </div>
      <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white prose prose-sm max-w-none" v-html="htmlOutput"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'md-to-html', name: 'Markdown → HTML', description: '将 Markdown 文本实时转换为 HTML', icon: 'file-code', category: 'document' }
defineExpose({ meta })

const input = ref(`# 欢迎使用 Markdown 转换器

## 功能
- **实时预览** HTML 输出
- 复制到剪贴板
- 下载 .html 文件

\`\`\`js
console.log('支持代码块')
\`\`\`
`)

const htmlOutput = ref('')
const { copied, copy } = useClipboard()
const { success } = useToast()

function parseMarkdown(md: string): string {
  let html = md
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '<br><br>')
  return html
}

function renderHtml() { htmlOutput.value = parseMarkdown(input.value) }

function copyHtml() { copy(htmlOutput.value); success('HTML 已复制到剪贴板') }

function downloadHtml() {
  const fullHtml = `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>Output</title></head><body>${htmlOutput.value}</body></html>`
  const blob = new Blob([fullHtml], { type: 'text/html' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = 'output.html'; a.click()
  URL.revokeObjectURL(url); success('HTML 文件已下载')
}

renderHtml()
</script>
