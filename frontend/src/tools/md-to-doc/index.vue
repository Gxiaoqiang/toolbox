<template>
  <div class="flex gap-4 h-full">
    <!-- Markdown 编辑器 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">Markdown 输入</label>
        <button
          @click="convertToDocx"
          :disabled="converting"
          class="px-4 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1.5"
          :class="converting
            ? 'bg-slate-200 text-slate-400 cursor-not-allowed'
            : 'bg-indigo-500 hover:bg-indigo-600 text-white'"
        >
          <span v-if="converting" class="inline-block animate-spin text-xs">⟳</span>
          <span>{{ converting ? '转换中...' : '转为 DOCX 并下载' }}</span>
        </button>
      </div>
      <textarea
        v-model="markdown"
        class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400"
        placeholder="# 在这里输入 Markdown..."
      ></textarea>
    </div>

    <!-- 实时预览 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold text-slate-500 mb-2">预览</label>
      <div
        class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white markdown-body"
        v-html="previewHtml"
      ></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { marked } from 'marked'
import type { ToolMeta } from '@/tools/types'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'md-to-doc',
  name: 'Markdown → DOCX',
  description: '将 Markdown 转换为 Word 文档（.docx）下载',
  icon: 'file-text',
  category: 'document',
  requiresBackend: true,
  group: 'Markdown',
}
defineExpose({ meta })

// 配置 marked
marked.setOptions({ gfm: true, breaks: false })

const markdown = ref(`# 示例文档

## 第一节

这是一段**粗体**和*斜体*文字。支持 ~~删除线~~ 和 \`行内代码\`。

### 代码示例

\`\`\`javascript
function greet(name) {
  return "Hello, " + name;
}
\`\`\`

### 表格

| 项目 | 状态 | 负责人 |
|------|------|--------|
| 需求评审 | 已完成 | 张三 |
| 开发实现 | 进行中 | 李四 |

### 列表

1. 第一步
2. 第二步
3. 第三步

> 重要提示：转换结果请人工复核。

[查看详情](https://example.com)
`)

const previewHtml = ref('')
const converting = ref(false)
const { success, error: toastError } = useToast()

function updatePreview() {
  try {
    previewHtml.value = marked.parse(markdown.value) as string
  } catch {
    previewHtml.value = '<p style="color:red">Markdown 解析错误</p>'
  }
}

watch(markdown, updatePreview, { immediate: true })

async function convertToDocx() {
  if (!markdown.value.trim()) {
    toastError('请先输入 Markdown 内容')
    return
  }

  converting.value = true
  try {
    const formData = new FormData()
    formData.append('content', markdown.value)
    formData.append('filename', 'converted')

    const response = await fetch('/api/convert/md-to-docx', {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      const errData = await response.json().catch(() => null)
      throw new Error(errData?.message || 'HTTP ' + response.status)
    }

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'output.docx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    success('DOCX 文件已下载')
  } catch (e: any) {
    toastError('转换失败: ' + e.message)
  } finally {
    converting.value = false
  }
}
</script>

<style scoped>
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.7;
  color: #1a1a2e;
  word-wrap: break-word;
}
.markdown-body :deep(h1) { font-size: 1.8em; font-weight: 700; border-bottom: 2px solid #e5e7eb; padding-bottom: 0.3em; margin: 0.8em 0 0.5em; }
.markdown-body :deep(h2) { font-size: 1.4em; font-weight: 600; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.25em; margin: 0.8em 0 0.4em; }
.markdown-body :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 0.7em 0 0.3em; }
.markdown-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 14px 16px; border-radius: 8px; overflow-x: auto; margin: 0.6em 0; font-size: 13px; line-height: 1.5; }
.markdown-body :deep(code) { background: #f0f0f0; padding: 0.2em 0.4em; border-radius: 3px; font-family: "JetBrains Mono", monospace; font-size: 0.88em; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #e5e7eb; padding: 8px 14px; text-align: left; }
.markdown-body :deep(th) { background: #f8fafc; font-weight: 600; font-size: 13px; }
.markdown-body :deep(tr:nth-child(even)) { background: #fafbfc; }
.markdown-body :deep(blockquote) { border-left: 4px solid #3b82f6; padding: 0.3em 1rem; color: #64748b; margin: 0.5em 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 2em; margin: 0.3em 0; }
.markdown-body :deep(a) { color: #3b82f6; }
.markdown-body :deep(hr) { border: none; border-top: 2px solid #e5e7eb; margin: 1em 0; }
.markdown-body :deep(del) { color: #94a3b8; }
</style>
