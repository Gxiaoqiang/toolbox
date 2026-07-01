<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：Markdown 编辑器 -->
    <div class="flex-1 flex flex-col min-w-0 h-full">
      <label class="text-xs font-semibold text-slate-500 mb-2 flex-shrink-0">Markdown 输入</label>
      <textarea v-model="input" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400"
        placeholder="# 在这里输入 Markdown..." @input="renderHtml"></textarea>
    </div>

    <!-- 右侧：Tab 内容 -->
    <div class="flex-1 flex flex-col min-w-0 h-full">
      <!-- 工具栏 -->
      <div class="flex items-center justify-between mb-2 flex-shrink-0">
        <div class="flex gap-0.5 bg-slate-100 rounded-lg p-0.5">
          <button v-for="tab in tabs" :key="tab.key" @click="activeTab = tab.key"
            class="px-3 py-1 text-xs rounded-md transition-colors"
            :class="activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm font-medium' : 'text-slate-500 hover:text-slate-700'"
          >{{ tab.label }}</button>
        </div>
        <div v-if="activeTab === 'html'" class="flex gap-2">
          <button @click="copyHtml" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '✓ 已复制' : '复制 HTML' }}</button>
          <button @click="downloadHtml" class="px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">下载 .html</button>
        </div>
        <button v-else @click="convertToDocx" :disabled="converting"
          class="px-4 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1.5"
          :class="converting ? 'bg-slate-200 text-slate-400 cursor-not-allowed' : 'bg-indigo-500 hover:bg-indigo-600 text-white'">
          <span v-if="converting" class="inline-block animate-spin text-xs">⟳</span>
          <span>{{ converting ? '转换中...' : '转为 DOCX 并下载' }}</span>
        </button>
      </div>

      <!-- HTML 预览区 -->
      <div v-if="activeTab === 'html'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white markdown-body" v-html="htmlOutput"></div>

      <!-- DOCX 下载区 -->
      <div v-else class="flex-1 p-6 border border-slate-200 rounded-lg overflow-auto bg-white flex flex-col items-center justify-center gap-4">
        <div class="text-center">
          <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-indigo-100 flex items-center justify-center"><span class="text-xl">📄</span></div>
          <h4 class="text-sm font-semibold text-slate-700 mb-1">导出为 Word 文档</h4>
          <p class="text-xs text-slate-400">将 Markdown 内容转换为 .docx 文件</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { marked } from 'marked'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'md-toolbox', name: 'Markdown 工具箱',
  description: 'Markdown 实时预览、导出 HTML/DOCX',
  icon: '', category: 'document', group: 'Markdown', requiresBackend: true,
}
defineExpose({ meta })

marked.setOptions({ gfm: true, breaks: false })

const input = ref(`# 欢迎使用 Markdown 工具箱

## 基本语法

**粗体**、*斜体*、~~删除线~~、\`行内代码\`

### 代码块

\`\`\`python
def hello():
    print("Hello, World!")
\`\`\`

### 表格

| 姓名 | 年龄 | 城市 |
|------|------|------|
| 张三 | 28 | 北京 |
| 李四 | 32 | 上海 |

> 引用文字

- [x] 已完成项
- [ ] 待办项
`)

const htmlOutput = ref('')
const activeTab = ref<'html' | 'docx'>('html')
const converting = ref(false)
const tabs = [{ key: 'html' as const, label: 'HTML 预览' }, { key: 'docx' as const, label: 'DOCX 下载' }]
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function renderHtml() { try { htmlOutput.value = marked.parse(input.value) as string } catch { htmlOutput.value = '<p style="color:red">解析错误</p>' } }
function copyHtml() { copy(htmlOutput.value); success('HTML 已复制') }
function downloadHtml() {
  const h = '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>Output</title><style>body{max-width:900px;margin:0 auto;padding:2rem;font-family:-apple-system,sans-serif;line-height:1.6;color:#1a1a2e}h1{border-bottom:2px solid #eee;padding-bottom:.3em}h2{border-bottom:1px solid #eee;padding-bottom:.3em}pre{background:#1e1e2e;color:#cdd6f4;padding:1rem;border-radius:8px;overflow-x:auto}code{background:#f0f0f0;padding:.2em .4em;border-radius:4px}pre code{background:none;padding:0}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:8px 12px}th{background:#f5f5f5}blockquote{border-left:4px solid #0366d6;padding-left:1rem;color:#555}</style></head><body>' + htmlOutput.value + '</body></html>'
  const blob = new Blob([h], { type: 'text/html;charset=utf-8' }); const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = 'output.html'; a.click(); URL.revokeObjectURL(url)
  success('HTML 文件已下载')
}

async function convertToDocx() {
  if (!input.value.trim()) { toastError('请先输入 Markdown 内容'); return }
  converting.value = true
  try {
    const fd = new FormData(); fd.append('content', input.value); fd.append('filename', 'converted')
    const resp = await fetch('/api/convert/md-to-docx', { method: 'POST', body: fd })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'output.docx'
    document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
    success('DOCX 已下载')
  } catch (e: any) { toastError('转换失败: ' + e.message) }
  finally { converting.value = false }
}

renderHtml()
</script>

<style scoped>
.markdown-body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; font-size: 14px; line-height: 1.7; color: #1a1a2e; word-wrap: break-word; }
.markdown-body :deep(h1) { font-size: 1.8em; font-weight: 700; border-bottom: 2px solid #e5e7eb; padding-bottom: 0.3em; margin: 0.8em 0 0.5em; }
.markdown-body :deep(h2) { font-size: 1.4em; font-weight: 600; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.25em; margin: 0.8em 0 0.4em; }
.markdown-body :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 0.7em 0 0.3em; }
.markdown-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 14px 16px; border-radius: 8px; overflow-x: auto; margin: 0.6em 0; font-size: 13px; line-height: 1.5; }
.markdown-body :deep(code) { background: #f0f0f0; padding: 0.2em 0.4em; border-radius: 3px; font-family: "JetBrains Mono", monospace; font-size: 0.88em; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #e5e7eb; padding: 8px 14px; }
.markdown-body :deep(th) { background: #f8fafc; font-weight: 600; }
.markdown-body :deep(blockquote) { border-left: 4px solid #3b82f6; padding: 0.3em 1rem; color: #64748b; margin: 0.5em 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 2em; }
.markdown-body :deep(a) { color: #3b82f6; }
</style>
