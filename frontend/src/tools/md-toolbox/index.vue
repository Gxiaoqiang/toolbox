<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：Markdown 编辑器 -->
    <div class="flex-1 flex flex-col min-w-0 h-full">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">Markdown 输入</label>

      <!-- 快捷插入工具栏 -->
      <div class="flex flex-wrap gap-x-1 gap-y-0.5 mb-2 flex-shrink-0">
        <template v-for="(group, gi) in toolbarButtons" :key="group.group">
          <span v-if="gi > 0" class="mx-0.5 w-px h-5 self-center" style="background: var(--border-color)"></span>
          <button
            v-for="btn in group.items"
            :key="btn.label"
            @click="insertMarkdown(btn.action)"
            :title="btn.title"
            class="px-1.5 py-0.5 text-xs rounded font-mono transition-colors hover:bg-indigo-50 hover:text-indigo-600" style="color: var(--text-muted)"
          >{{ btn.label }}</button>
        </template>
      </div>

      <textarea v-model="input" class="flex-1 p-4 border rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400 md-editor-textarea" style="background: var(--bg-input); border-color: var(--border-color); color: var(--text-primary)"
        placeholder="# 在这里输入 Markdown..." @input="renderHtml"></textarea>

      <!-- 语法参考面板 -->
      <div class="mt-2 flex-shrink-0 border rounded-lg overflow-hidden transition-colors duration-300" style="border-color: var(--border-color)">
        <button
          @click="toggleSyntaxRef"
          class="w-full flex items-center gap-1.5 px-3 py-1.5 text-xs transition-colors hover:bg-slate-50"
          style="color: var(--text-secondary)"
        >
          <span class="transform transition-transform duration-200 text-[10px]" :class="{ 'rotate-90': syntaxRefOpen }">▶</span>
          <span>Markdown 语法速查</span>
        </button>
        <div v-show="syntaxRefOpen" class="h-48 overflow-y-auto border-t transition-colors duration-300" style="border-color: var(--border-color)">
          <table class="w-full text-xs" style="color: var(--text-secondary)">
            <thead>
              <tr style="background: var(--bg-card-hover)">
                <th class="px-3 py-1.5 text-left font-medium">语法</th>
                <th class="px-3 py-1.5 text-left font-medium">效果</th>
                <th class="px-3 py-1.5 text-left font-medium">写法</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in syntaxRefs" :key="idx" class="border-t transition-colors" style="border-color: var(--border-color)">
                <td class="px-3 py-1 font-mono" style="color: var(--text-primary)">{{ item.syntax }}</td>
                <td class="px-3 py-1">{{ item.effect }}</td>
                <td class="px-3 py-1 font-mono" style="color: var(--accent-color)">{{ item.code }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 右侧：Tab 内容 -->
    <div class="flex-1 flex flex-col min-w-0 h-full">
      <!-- 工具栏 -->
      <div class="flex items-center justify-between mb-2 flex-shrink-0">
        <div class="flex gap-0.5 rounded-lg p-0.5" style="background: var(--bg-card-hover)">
          <button v-for="tab in tabs" :key="tab.key" @click="activeTab = tab.key"
            class="px-3 py-1 text-xs rounded-md transition-colors"
            :class="activeTab === tab.key ? 'bg-white shadow-sm font-medium' : ''"
            :style="activeTab === tab.key ? { color: 'var(--text-primary)' } : { color: 'var(--text-muted)' }"
          >{{ tab.label }}</button>
        </div>
        <div v-if="activeTab === 'html'" class="flex gap-2">
          <button @click="copyHtml" class="px-3 py-1 text-xs rounded-md" style="background: var(--bg-card-hover); color: var(--text-secondary)">{{ copied ? '✓ 已复制' : '复制 HTML' }}</button>
          <button @click="downloadHtml" class="px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">下载 .html</button>
        </div>
        <button v-else @click="convertToDocx" :disabled="converting"
          class="px-4 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1.5"
          :class="converting ? 'cursor-not-allowed' : 'bg-indigo-500 hover:bg-indigo-600 text-white'"
          :style="converting ? { background: 'var(--bg-card-hover)', color: 'var(--text-muted)' } : {}">
          <span v-if="converting" class="inline-block animate-spin text-xs">⟳</span>
          <span>{{ converting ? '转换中...' : '转为 DOCX 并下载' }}</span>
        </button>
      </div>

      <!-- HTML 预览区 -->
      <div v-if="activeTab === 'html'" class="flex-1 p-4 border rounded-lg overflow-auto markdown-body" style="background: var(--bg-card); border-color: var(--border-color); color: var(--text-primary)" v-html="htmlOutput"></div>

      <!-- DOCX 下载区 -->
      <div v-else class="flex-1 p-6 border rounded-lg overflow-auto flex flex-col items-center justify-center gap-4" style="background: var(--bg-card); border-color: var(--border-color)">
        <div class="text-center">
          <div class="w-12 h-12 mx-auto mb-3 rounded-xl bg-indigo-100 flex items-center justify-center"><span class="text-xl">📄</span></div>
          <h4 class="text-sm font-semibold mb-1" style="color: var(--text-primary)">导出为 Word 文档</h4>
          <p class="text-xs" style="color: var(--text-muted)">将 Markdown 内容转换为 .docx 文件</p>
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
  icon: '', category: 'file', group: 'Markdown', requiresBackend: true,
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

// ======== 光标插入工具 ========

interface InsertAction {
  syntax: string  // 插入语法模板，用 | 分隔光标位置，|P| 表示 placeholder
  placeholder?: string
  wrap?: boolean  // 是否包裹选中文字
}

const toolbarButtons: { group: string; items: { label: string; title: string; action: InsertAction }[] }[] = [
  {
    group: 'heading',
    items: [
      { label: 'H1', title: '一级标题', action: { syntax: '# |P|', placeholder: '标题' } },
      { label: 'H2', title: '二级标题', action: { syntax: '## |P|', placeholder: '标题' } },
      { label: 'H3', title: '三级标题', action: { syntax: '### |P|', placeholder: '标题' } },
    ],
  },
  {
    group: 'format',
    items: [
      { label: 'B', title: '粗体', action: { syntax: '**|P|**', placeholder: '粗体文字', wrap: true } },
      { label: 'I', title: '斜体', action: { syntax: '*|P|*', placeholder: '斜体文字', wrap: true } },
      { label: 'S', title: '删除线', action: { syntax: '~~|P|~~', placeholder: '删除文字', wrap: true } },
      { label: '`', title: '行内代码', action: { syntax: '`|P|`', placeholder: 'code', wrap: true } },
    ],
  },
  {
    group: 'list',
    items: [
      { label: '•', title: '无序列表', action: { syntax: '- |P|', placeholder: '列表项' } },
      { label: '1.', title: '有序列表', action: { syntax: '1. |P|', placeholder: '列表项' } },
      { label: '☑', title: '任务列表', action: { syntax: '- [ ] |P|', placeholder: '待办' } },
      { label: '❝', title: '引用', action: { syntax: '> |P|', placeholder: '引用文字' } },
    ],
  },
  {
    group: 'insert',
    items: [
      { label: '🔗', title: '链接', action: { syntax: '[|P|](url)', placeholder: '链接文字' } },
      { label: '💼', title: '图片', action: { syntax: '![|P|](url)', placeholder: '图片描述' } },
      { label: 'code', title: '代码块', action: { syntax: '```\n|P|\n```', placeholder: 'code' } },
      { label: '三', title: '表格', action: { syntax: '| 列1 | 列2 | 列3 |\n|-----|-----|-----|\n| |P| | |', placeholder: '内容' } },
      { label: '―', title: '分割线', action: { syntax: '\n---\n' } },
    ],
  },
]

function insertMarkdown(action: InsertAction) {
  const textarea = document.querySelector('.md-editor-textarea') as HTMLTextAreaElement
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = input.value.substring(start, end)
  const hasSelection = start !== end

  let syntax = action.syntax
  let cursorOffset = 0

  if (hasSelection && action.wrap) {
    // 包裹选中文字
    syntax = syntax.replace('|P|', selected)
    cursorOffset = syntax.length
  } else if (action.placeholder) {
    // 有 placeholder：替换 |P| 为 placeholder，光标选中 placeholder
    syntax = syntax.replace('|P|', action.placeholder)
    cursorOffset = syntax.indexOf(action.placeholder) + action.placeholder.length
  } else {
    // 无 placeholder：去掉 |P|
    syntax = syntax.replace('|P|', '')
    cursorOffset = syntax.length
  }

  // 组合新文本
  const newText = input.value.substring(0, start) + syntax + input.value.substring(end)
  input.value = newText

  // 恢复光标位置
  void (document.querySelector('.md-editor-textarea') as HTMLTextAreaElement)?.focus()
  // 下一帧设置光标
  requestAnimationFrame(() => {
    const ta = document.querySelector('.md-editor-textarea') as HTMLTextAreaElement
    if (!ta) return
    if (hasSelection && action.wrap) {
      // 包裹模式：光标移到语法末尾
      ta.selectionStart = start + cursorOffset
      ta.selectionEnd = start + cursorOffset
    } else if (action.placeholder) {
      // placeholder 模式：选中 placeholder 文字
      const placeholderStart = start + syntax.indexOf(action.placeholder!)
      ta.selectionStart = placeholderStart
      ta.selectionEnd = placeholderStart + action.placeholder!.length
    } else {
      // 普通模式：光标移到语法末尾
      ta.selectionStart = start + cursorOffset
      ta.selectionEnd = start + cursorOffset
    }
    ta.focus()
    renderHtml()
  })
}
// ======== 语法速查 ========

const syntaxRefOpen = ref(false)

const syntaxRefs = [
  { syntax: '# 标题', effect: '一级标题', code: '# text' },
  { syntax: '## 标题', effect: '二级标题', code: '## text' },
  { syntax: '### 标题', effect: '三级标题', code: '### text' },
  { syntax: '**粗体**', effect: '加粗文字', code: '**text**' },
  { syntax: '*斜体*', effect: '斜体文字', code: '*text*' },
  { syntax: '~~删除线~~', effect: '删除文字', code: '~~text~~' },
  { syntax: '`代码`', effect: '行内代码', code: '`code`' },
  { syntax: '```代码块', effect: '多行代码', code: '```lang' },
  { syntax: '- 列表', effect: '无序列表', code: '- item' },
  { syntax: '1. 列表', effect: '有序列表', code: '1. item' },
  { syntax: '- [ ] 任务', effect: '任务列表', code: '- [ ] item' },
  { syntax: '> 引用', effect: '引用文字', code: '> text' },
  { syntax: '[文字](url)', effect: '超链接', code: '[text](url)' },
  { syntax: '![alt](url)', effect: '图片', code: '![alt](url)' },
  { syntax: '---', effect: '分割线', code: '---' },
  { syntax: '| 表 | 格 |', effect: '表格', code: '| a | b |' },
]

function toggleSyntaxRef() {
  syntaxRefOpen.value = !syntaxRefOpen.value
}

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
    const resp = await fetch('/api/markdown/md-to-docx', { method: 'POST', body: fd })
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
.markdown-body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; font-size: 14px; line-height: 1.7; color: var(--text-primary); word-wrap: break-word; }
.markdown-body :deep(h1) { font-size: 1.8em; font-weight: 700; border-bottom: 2px solid var(--border-color); padding-bottom: 0.3em; margin: 0.8em 0 0.5em; }
.markdown-body :deep(h2) { font-size: 1.4em; font-weight: 600; border-bottom: 1px solid var(--border-color); padding-bottom: 0.25em; margin: 0.8em 0 0.4em; }
.markdown-body :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 0.7em 0 0.3em; }
.markdown-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 14px 16px; border-radius: 8px; overflow-x: auto; margin: 0.6em 0; font-size: 13px; line-height: 1.5; }
.markdown-body :deep(code) { background: var(--bg-card-hover); padding: 0.2em 0.4em; border-radius: 3px; font-family: "JetBrains Mono", monospace; font-size: 0.88em; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--border-color); padding: 8px 14px; }
.markdown-body :deep(th) { background: var(--bg-card-hover); font-weight: 600; }
.markdown-body :deep(blockquote) { border-left: 4px solid #3b82f6; padding: 0.3em 1rem; color: var(--text-secondary); margin: 0.5em 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 2em; }
.markdown-body :deep(a) { color: #3b82f6; }
</style>
