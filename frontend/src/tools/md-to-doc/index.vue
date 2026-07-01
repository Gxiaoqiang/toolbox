<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">Markdown 输入</label>
        <button @click="convertToDocx" :disabled="converting" class="px-4 py-1.5 text-sm rounded-md transition-colors" :class="converting ? 'bg-slate-200 text-slate-400 cursor-not-allowed' : 'bg-blue-500 hover:bg-blue-600 text-white'">
          <span v-if="converting">⏳ 转换中...</span>
          <span v-else>转为 DOCX 并下载</span>
        </button>
      </div>
      <textarea v-model="markdown" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" placeholder="# 在这里输入 Markdown..."></textarea>
    </div>
    <div class="flex-1 flex flex-col">
      <label class="text-xs font-semibold text-slate-500 mb-2">预览</label>
      <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white prose prose-sm max-w-none" v-html="previewHtml"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'md-to-doc', name: 'Markdown → DOCX', description: '将 Markdown 转换为 Word 文档下载', icon: 'file-text', category: 'document', requiresBackend: true }
defineExpose({ meta })

const markdown = ref(`# 示例文档\n\n## 第一节\n\n这是一段**粗体**和*斜体*文字。\n\n- 列表项 1\n- 列表项 2\n\n> 引用文字`)
const previewHtml = ref('')
const converting = ref(false)
const { success, error: toastError } = useToast()

function updatePreview() {
  previewHtml.value = markdown.value
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
    .replace(/\n\n/g, '<br><br>')
}
watch(markdown, updatePreview, { immediate: true })

async function convertToDocx() {
  if (!markdown.value.trim()) { toastError('请先输入 Markdown 内容'); return }
  converting.value = true
  try {
    const formData = new FormData(); formData.append('content', markdown.value); formData.append('filename', 'converted')
    const response = await fetch('/api/convert/md-to-docx', { method: 'POST', body: formData })
    if (!response.ok) throw new Error('HTTP ' + response.status)
    const blob = await response.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'output.docx'; document.body.appendChild(a); a.click()
    document.body.removeChild(a); URL.revokeObjectURL(url)
    success('DOCX 文件已下载')
  } catch (e: any) { toastError('转换失败: ' + e.message) }
  finally { converting.value = false }
}
</script>
