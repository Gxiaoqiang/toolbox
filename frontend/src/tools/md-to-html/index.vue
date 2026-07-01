<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：Markdown 编辑器 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">Markdown 输入</label>
        <span class="text-[10px] text-slate-400">支持 GFM 语法（表格/代码/任务列表等）</span>
      </div>
      <textarea
        v-model="input"
        class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400"
        placeholder="# 在这里输入 Markdown..."
        @input="renderHtml"
      ></textarea>
    </div>

    <!-- 右侧：HTML 预览 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">HTML 预览</label>
        <div class="flex gap-2">
          <button
            @click="copyHtml"
            class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"
          >
            {{ copied ? '✓ 已复制' : '复制 HTML' }}
          </button>
          <button
            @click="downloadHtml"
            class="px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white transition-colors"
          >
            下载 .html
          </button>
        </div>
      </div>
      <div
        class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white markdown-body"
        v-html="htmlOutput"
      ></div>
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
  id: 'md-to-html',
  name: 'Markdown → HTML',
  description: '将 Markdown 文本实时转换为 HTML，支持 GFM 语法',
  icon: 'file-code',
  category: 'document',
}
defineExpose({ meta })

// 配置 marked
marked.setOptions({
  gfm: true,        // GitHub Flavored Markdown: 表格/任务列表/删除线等
  breaks: false,     // 单个换行不转 <br>
})

const input = ref(`# 欢迎使用 Markdown 转换器

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

### 列表

- 无序列表项 1
- 无序列表项 2
  - 嵌套项

1. 有序列表项 1
2. 有序列表项 2

### 引用与链接

> 这是一段引用文字

[OpenAI](https://openai.com)

### 任务列表

- [x] 已完成项
- [ ] 待办项
`)

const htmlOutput = ref('')
const { copied, copy } = useClipboard()
const { success } = useToast()

function renderHtml() {
  try {
    htmlOutput.value = marked.parse(input.value) as string
  } catch {
    htmlOutput.value = '<p style="color:red">Markdown 解析错误</p>'
  }
}

function copyHtml() {
  copy(htmlOutput.value)
  success('HTML 已复制到剪贴板')
}

function downloadHtml() {
  const fullHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Markdown Output</title>
<style>
  body {
    max-width: 900px;
    margin: 0 auto;
    padding: 2rem;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    line-height: 1.6;
    color: #1a1a2e;
  }
  h1 { border-bottom: 2px solid #eee; padding-bottom: 0.3em; }
  h2 { border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
  pre { background: #1e1e2e; color: #cdd6f4; padding: 1rem; border-radius: 8px; overflow-x: auto; }
  code { background: #f0f0f0; padding: 0.2em 0.4em; border-radius: 4px; font-size: 0.9em; }
  pre code { background: none; padding: 0; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
  th { background: #f5f5f5; }
  blockquote { border-left: 4px solid #0366d6; padding-left: 1rem; color: #555; margin-left: 0; }
  img { max-width: 100%; }
</style>
</head>
<body>
${htmlOutput.value}
</body>
</html>`

  const blob = new Blob([fullHtml], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'output.html'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  success('HTML 文件已下载')
}

// 初始化渲染
renderHtml()
</script>

<style scoped>
/* GitHub 风格 Markdown 渲染 */
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.7;
  color: #1a1a2e;
  word-wrap: break-word;
}
.markdown-body :deep(h1) {
  font-size: 1.8em;
  font-weight: 700;
  border-bottom: 2px solid #e5e7eb;
  padding-bottom: 0.3em;
  margin: 0.8em 0 0.5em;
}
.markdown-body :deep(h2) {
  font-size: 1.4em;
  font-weight: 600;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.25em;
  margin: 0.8em 0 0.4em;
}
.markdown-body :deep(h3) {
  font-size: 1.15em;
  font-weight: 600;
  margin: 0.7em 0 0.3em;
}
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  font-weight: 600;
  margin: 0.6em 0 0.2em;
}
.markdown-body :deep(p) {
  margin: 0.4em 0;
}
.markdown-body :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.6em 0;
  font-size: 13px;
  line-height: 1.5;
}
.markdown-body :deep(code) {
  background: #f0f0f0;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: "JetBrains Mono", "Fira Code", "Cascadia Code", monospace;
  font-size: 0.88em;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.6em 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 14px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  font-size: 13px;
}
.markdown-body :deep(tr:nth-child(even)) {
  background: #fafbfc;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid #3b82f6;
  padding: 0.3em 1rem;
  color: #64748b;
  margin: 0.5em 0;
}
.markdown-body :deep(blockquote p) {
  margin: 0.3em 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 2em;
  margin: 0.3em 0;
}
.markdown-body :deep(li) {
  margin: 0.15em 0;
}
.markdown-body :deep(a) {
  color: #3b82f6;
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 2px solid #e5e7eb;
  margin: 1em 0;
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}
.markdown-body :deep(del) {
  color: #94a3b8;
}
.markdown-body :deep(input[type="checkbox"]) {
  margin-right: 0.4em;
}
</style>
