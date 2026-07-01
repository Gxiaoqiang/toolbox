<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：输入区 + 操作按钮 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2 flex-wrap gap-2">
        <label class="text-xs font-semibold text-slate-500">JSON 输入</label>
        <div class="flex gap-1.5 flex-wrap">
          <button @click="format" class="px-2.5 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">格式化</button>
          <button @click="compress" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">压缩</button>
          <button @click="sortKeys" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">排序</button>
          <button @click="validateJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">校验</button>
          <button @click="escapeJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">转义</button>
          <button @click="unescapeJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">反转义</button>
        </div>
      </div>
      <textarea
        v-model="input"
        class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400"
        :class="{ 'border-red-400': errorMessage }"
        placeholder='{"key": "value"}'
        @input="autoFormat"
      ></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
    </div>

    <!-- 右侧：Tab 切换 树形/文本/JSONPath -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <div class="flex gap-0.5 bg-slate-100 rounded-lg p-0.5">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            @click="activeTab = tab.key"
            class="px-3 py-1 text-xs rounded-md transition-colors"
            :class="activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm font-medium' : 'text-slate-500 hover:text-slate-700'"
          >{{ tab.label }}</button>
        </div>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">
          {{ copied ? '✓ 已复制' : '复制' }}
        </button>
      </div>

      <!-- 树形视图 -->
      <div v-if="activeTab === 'tree'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm">
        <div v-if="parsedJson" class="flex gap-2 mb-2">
          <button @click="expandAll" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors">全部展开</button>
          <button @click="collapseAll" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors">全部折叠</button>
        </div>
        <div v-if="!parsedJson" class="text-slate-400">等待输入...</div>
        <div v-else class="tree-view" ref="treeViewRef" v-html="treeHtml"></div>
      </div>

      <!-- 文本视图（交互式带伸缩） -->
      <div v-else-if="activeTab === 'text'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm">
        <div v-if="parsedJson" class="flex gap-2 mb-2">
          <button @click="expandAllText" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors">全部展开</button>
          <button @click="collapseAllText" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors">全部折叠</button>
        </div>
        <div v-if="!parsedJson" class="text-slate-400">等待输入...</div>
        <div v-else class="text-interactive-view" v-html="textInteractiveHtml"></div>
      </div>

      <!-- JSONPath 查询 -->
      <div v-else class="flex-1 flex flex-col">
        <div class="flex items-center gap-2 mb-2">
          <span class="text-xs text-slate-400">$</span>
          <input
            v-model="jsonPath"
            class="flex-1 px-3 py-1.5 border border-slate-200 rounded-lg text-xs font-mono focus:outline-none focus:ring-2 focus:ring-indigo-400"
            placeholder=".store.book[0].title"
            @input="runJsonPath"
          />
          <span class="text-[10px] text-slate-400">JSONPath</span>
        </div>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ jsonPathResult || '输入 JSONPath 表达式查询...' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'json-formatter',
  name: 'JSON 格式化',
  description: '格式化/压缩/校验/树形查看/排序/转义/JSONPath',
  icon: '',
  category: 'develop', group: 'JSON',
}
defineExpose({ meta })

const input = ref('')
const output = ref('')
const errorMessage = ref('')
const parsedJson = ref<any>(null)
const activeTab = ref<'tree' | 'text' | 'jsonpath'>('tree')
const jsonPath = ref('')
const jsonPathResult = ref('')
const treeHtml = ref('')
const treeViewRef = ref<HTMLElement | null>(null)
const textInteractiveHtml = ref('')
const rootKey = ref(0)
const tabs = [
  { key: 'tree' as const, label: '树形' },
  { key: 'text' as const, label: '文本' },
  { key: 'jsonpath' as const, label: 'JSONPath' },
]
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

// ============ 树形视图 ============

function renderTree(obj: any): string {
  const baseId = 'tt' + Math.random().toString(36).slice(2)
  let nodeCount = 0
  function node(val: any, path: string, depth: number, autoExpand: boolean): string {
    const nid = baseId + '-' + (nodeCount++)
    if (val === null || val === undefined) return `<span style="color:#94a3b8">null</span>`
    if (typeof val === 'string') return `<span style="color:#16a34a">"${val.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;')}"</span>`
    if (typeof val === 'number') return `<span style="color:#2563eb">${val}</span>`
    if (typeof val === 'boolean') return `<span style="color:#9333ea">${val}</span>`
    const isArr = Array.isArray(val)
    const entries = Object.entries(val)
    const len = entries.length
    const bracket = isArr ? ['[',']'] : ['{','}']
    const summary = isArr ? `Array[${len}]` : `Object{${len}}`
    // 初始状态：全部展开
    const isCollapsed = !autoExpand
    let html = `<div class="tn" data-nid="${nid}">`
    html += `<div class="tl" onclick="window.__toggleTreeNode('${nid}',this)"><span class="ta${isCollapsed ? '' : ' expanded'}">▼</span><span class="tp">${path}</span><span class="ts">${summary}</span><span class="tb"> ${bracket[0]}</span><span class="tdots${isCollapsed ? '' : ' hidden'}"> ... ${bracket[1]}</span></div>`
    html += `<div class="tc${isCollapsed ? ' collapsed' : ''}">`
    for (const [k, v] of entries) {
      const cp = isArr ? `${path}[${k}]` : `${path}.${k}`
      html += `<div class="tch"><span class="tk">${isArr ? `${k}:` : `"${k}":`}</span>${node(v, cp, depth + 1, true)}</div>`
    }
    html += `</div><div class="tb2${isCollapsed ? ' hidden' : ''}">${bracket[1]}</div></div>`
    return html
  }
  return node(obj, '$', 0, true)
}

// 全局树节点切换函数
;(window as any).__toggleTreeNode = function(nid: string, tlEl: HTMLElement) {
  const tn = document.querySelector(`.tn[data-nid="${nid}"]`) as HTMLElement
  if (!tn) return
  const tc = tn.querySelector('.tc') as HTMLElement
  const tdots = tn.querySelector('.tdots') as HTMLElement
  const tb2 = tn.querySelector('.tb2') as HTMLElement
  const ta = tlEl.querySelector('.ta') as HTMLElement
  const isCollapsed = tc && tc.classList.contains('collapsed')
  if (isCollapsed) {
    // 展开
    if (tc) tc.classList.remove('collapsed')
    if (tdots) tdots.classList.add('hidden')
    if (tb2) tb2.classList.remove('hidden')
    if (ta) ta.classList.add('expanded')
  } else {
    // 折叠
    if (tc) tc.classList.add('collapsed')
    if (tdots) tdots.classList.remove('hidden')
    if (tb2) tb2.classList.add('hidden')
    if (ta) ta.classList.remove('expanded')
  }
}

function expandAll() {
  document.querySelectorAll('.tree-view .tc.collapsed').forEach(el => {
    const tc = el as HTMLElement
    const tn = tc.closest('.tn') as HTMLElement
    const tdots = tn?.querySelector('.tdots') as HTMLElement
    const tb2 = tn?.querySelector('.tb2') as HTMLElement
    const ta = tn?.querySelector('.ta') as HTMLElement
    tc.classList.remove('collapsed')
    if (tdots) tdots.classList.add('hidden')
    if (tb2) tb2.classList.remove('hidden')
    if (ta) ta.classList.add('expanded')
  })
}

function collapseAll() {
  document.querySelectorAll('.tree-view .tc:not(.collapsed)').forEach(el => {
    const tc = el as HTMLElement
    const tn = tc.closest('.tn') as HTMLElement
    // 保留根节点展开
    if (tn && tn.parentElement?.closest('.tn') === null) return
    const tdots = tn?.querySelector('.tdots') as HTMLElement
    const tb2 = tn?.querySelector('.tb2') as HTMLElement
    const ta = tn?.querySelector('.ta') as HTMLElement
    tc.classList.add('collapsed')
    if (tdots) tdots.classList.remove('hidden')
    if (tb2) tb2.classList.add('hidden')
    if (ta) ta.classList.remove('expanded')
  })
}

// ============ 文本视图（交互式带伸缩） ============

let textSectionCounter = 0

function renderInteractiveText(obj: any): string {
  textSectionCounter = 0
  return `<div class="itv-root">${walkTree(obj, 0, false)}</div>`
}

function escHtml(s: string): string {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;')
}

function walkTree(val: any, depth: number, isLast: boolean): string {
  const br = '\n'
  const indent = '  '.repeat(depth)
  const indentInner = '  '.repeat(depth + 1)

  if (val === null || val === undefined) return `<span class="itv-null">null</span>`
  if (typeof val === 'string') return `<span class="itv-string">"${escHtml(val)}"</span>`
  if (typeof val === 'number') return `<span class="itv-number">${val}</span>`
  if (typeof val === 'boolean') return `<span class="itv-bool">${val}</span>`

  const isArr = Array.isArray(val)
  const entries = Object.entries(val)
  const len = entries.length
  const [open, close] = isArr ? ['[',']'] : ['{','}']

  if (len === 0) return `<span class="itv-bracket">${open}${close}</span>`

  const sectionId = 'itvsec-' + (textSectionCounter++)

  let html = ''
  html += `<span class="itv-bracket">${open}<span class="itv-arrow itv-arrow-expanded" data-section="${sectionId}" onclick="window.__toggleTextSection('${sectionId}',this)" title="点击折叠">▼</span></span>`
  html += `<span class="itv-section itv-section-open" id="${sectionId}">`

  for (let i = 0; i < entries.length; i++) {
    const [k, v] = entries[i]
    // 每个键值对前换行 + 缩进
    html += br + indentInner
    if (!isArr) html += `<span class="itv-key">"${escHtml(k)}"</span>: `
    html += walkTree(v, depth + 1, i === entries.length - 1)
    if (i < entries.length - 1) html += ','
  }

  // 闭合括号前换行 + 缩进
  html += br + indent
  html += `</span><span class="itv-bracket itv-close-bracket">${close}</span>`
  return html
}

function expandAllText() {
  document.querySelectorAll('.itv-section').forEach(el => {
    el.classList.add('itv-section-open')
    el.classList.remove('itv-section-collapsed')
  })
  document.querySelectorAll('.itv-arrow').forEach(el => {
    el.classList.add('itv-arrow-expanded')
    el.classList.remove('itv-arrow-collapsed')
    el.textContent = '▼'
  })
}

function collapseAllText() {
  // 折叠所有可折叠区域（根级别除外）
  document.querySelectorAll('.itv-section').forEach(el => {
    el.classList.add('itv-section-collapsed')
    el.classList.remove('itv-section-open')
  })
  // 保留根级别展开
  const root = document.querySelector('.itv-root > .itv-section')
  if (root) {
    root.classList.add('itv-section-open')
    root.classList.remove('itv-section-collapsed')
  }
  // 更新箭头
  document.querySelectorAll('.itv-arrow').forEach(el => {
    const sectionId = (el as HTMLElement).dataset.section
    const section = document.getElementById(sectionId || '')
    if (section && section.classList.contains('itv-section-open')) {
      el.classList.add('itv-arrow-expanded')
      el.classList.remove('itv-arrow-collapsed')
      el.textContent = '▼'
    } else {
      el.classList.add('itv-arrow-collapsed')
      el.classList.remove('itv-arrow-expanded')
      el.textContent = '▶'
    }
  })
}

// 暴露到 window 供 onclick 调用
;(window as any).__toggleTextSection = function(sectionId: string, arrowEl: HTMLElement) {
  const section = document.getElementById(sectionId)
  if (!section) return
  const isOpen = section.classList.contains('itv-section-open')
  if (isOpen) {
    section.classList.remove('itv-section-open')
    section.classList.add('itv-section-collapsed')
    arrowEl.classList.remove('itv-arrow-expanded')
    arrowEl.classList.add('itv-arrow-collapsed')
    arrowEl.textContent = '▶'
  } else {
    section.classList.remove('itv-section-collapsed')
    section.classList.add('itv-section-open')
    arrowEl.classList.remove('itv-arrow-collapsed')
    arrowEl.classList.add('itv-arrow-expanded')
    arrowEl.textContent = '▼'
  }
}

// ============ 通用逻辑 ============

function tryParse(): any {
  try { return JSON.parse(input.value) } catch { return null }
}

function updateTree(obj: any) {
  parsedJson.value = obj
  treeHtml.value = renderTree(obj)
  textInteractiveHtml.value = renderInteractiveText(obj)
}

function format() {
  try { const obj = JSON.parse(input.value); output.value = JSON.stringify(obj, null, 2); updateTree(obj); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('格式错误') }
}

function compress() {
  try { output.value = JSON.stringify(JSON.parse(input.value)); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('格式错误') }
}

function validateJson() {
  try { JSON.parse(input.value); errorMessage.value = ''; success('JSON 格式正确 ✓') }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('格式错误') }
}

function sortKeys() {
  try {
    const sorted = sortObjectKeys(JSON.parse(input.value))
    output.value = JSON.stringify(sorted, null, 2)
    updateTree(sorted)
    errorMessage.value = ''
  } catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('格式错误') }
}

function sortObjectKeys(obj: any): any {
  if (Array.isArray(obj)) return obj.map(sortObjectKeys)
  if (obj !== null && typeof obj === 'object') {
    return Object.keys(obj).sort().reduce((acc: any, key) => { acc[key] = sortObjectKeys(obj[key]); return acc }, {})
  }
  return obj
}

function escapeJson() {
  try { JSON.parse(input.value); output.value = JSON.stringify(input.value); errorMessage.value = '' } catch { output.value = JSON.stringify(input.value) }
  success('已转义')
}

function unescapeJson() {
  try {
    const unescaped = JSON.parse(input.value)
    if (typeof unescaped === 'string') {
      try { const parsed = JSON.parse(unescaped); output.value = JSON.stringify(parsed, null, 2); updateTree(parsed) } catch { output.value = unescaped }
    } else { output.value = JSON.stringify(unescaped, null, 2); updateTree(unescaped) }
    errorMessage.value = ''
  } catch {
    try {
      const cleaned = input.value.replace(/\\"/g, '"').replace(/^"/, '').replace(/"$/, '')
      output.value = cleaned
      try { updateTree(JSON.parse(cleaned)) } catch {}
    } catch { toastError('反转义失败') }
  }
}

function autoFormat() {
  const obj = tryParse()
  if (obj !== null) {
    output.value = JSON.stringify(obj, null, 2)
    updateTree(obj)
    errorMessage.value = ''
  }
}

function runJsonPath() {
  if (!parsedJson.value || !jsonPath.value) { jsonPathResult.value = ''; return }
  try {
    const results = evaluateJsonPath(parsedJson.value, jsonPath.value)
    jsonPathResult.value = JSON.stringify(results, null, 2)
  } catch (e: any) {
    jsonPathResult.value = '查询错误: ' + e.message
  }
}

function evaluateJsonPath(obj: any, path: string): any {
  // 简易 JSONPath 实现
  const parts = path.replace(/^\$\.?/, '').split(/(?=\[)|(?<=])\./).filter(Boolean)
  let current = obj
  for (const part of parts) {
    if (!current) return undefined
    const arrMatch = part.match(/^(.+)?\[(.+)\]$/)
    if (arrMatch) {
      const prop = arrMatch[1] || ''
      const idx = arrMatch[2].replace(/['"]/g, '')
      if (prop) current = current[prop]
      if (idx === '*') { return Object.values(current || {}) }
      if (isNaN(Number(idx))) {
        // 条件过滤如 [?(@.price<10)]
        if (idx.startsWith('?(')) return filterJsonPath(current, idx)
        current = current[idx]
      } else {
        current = current[Number(idx)]
      }
    } else {
      current = current[part]
    }
  }
  return current
}

function filterJsonPath(arr: any[], expr: string): any[] {
  // 简化条件: ?(@.key==value)
  const match = expr.match(/@\.(\w+)\s*(==|!=|>|<)\s*(.+)/)
  if (!match) return arr
  const [, key, op, valStr] = match
  const val = valStr.replace(/['"]/g, '')
  return arr.filter(item => {
    const itemVal = String(item[key])
    switch (op) {
      case '==': return itemVal == val
      case '!=': return itemVal != val
      case '>': return Number(itemVal) > Number(val)
      case '<': return Number(itemVal) < Number(val)
      default: return false
    }
  })
}

function copyOutput() {
  const content = activeTab.value === 'jsonpath' ? jsonPathResult.value : output.value
  if (content) { copy(content); success('已复制到剪贴板') }
}

watch(jsonPath, runJsonPath)
</script>

<style>
/* ======== 树形视图样式 ======== */
.tree-view { font-size: 13px; line-height: 1.9; }
.tree-view .tn { }
.tree-view .tl { display: flex; align-items: center; gap: 4px; padding: 1px 4px; border-radius: 4px; cursor: pointer; user-select: none; }
.tree-view .tl:hover { background: #f1f5f9; }
.tree-view .ta { font-size: 8px; color: #94a3b8; width: 12px; flex-shrink: 0; transition: transform .15s; display: inline-block; }
.tree-view .ta.expanded { transform: rotate(90deg); }
.tree-view .tp { color: #64748b; flex-shrink: 0; }
.tree-view .ts { color: #94a3b8; font-size: 11px; margin-left: 4px; flex-shrink: 0; }
.tree-view .tb { color: #94a3b8; flex-shrink: 0; }
.tree-view .tdots { color: #cbd5e1; font-size: 11px; }
.tree-view .tdots.hidden { display: none; }
.tree-view .tc { padding-left: 20px; border-left: 1px solid #e2e8f0; margin-left: 6px; }
.tree-view .tc.collapsed { display: none; }
.tree-view .tch { padding: 1px 0; }
.tree-view .tk { color: #6366f1; margin-right: 4px; }
.tree-view .tb2 { color: #94a3b8; }
.tree-view .tb2.hidden { display: none; }

/* ======== 交互式文本视图样式 ======== */
.text-interactive-view {
  white-space: pre;
  line-height: 1.8;
  font-size: 13px;
}

/* 值颜色 */
.itv-string { color: #16a34a; }
.itv-number { color: #2563eb; }
.itv-bool { color: #9333ea; }
.itv-null { color: #94a3b8; }
.itv-key { color: #6366f1; }
.itv-bracket { color: #94a3b8; }
.itv-close-bracket { }

/* 折叠箭头 */
.itv-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  font-size: 8px;
  color: #94a3b8;
  cursor: pointer;
  user-select: none;
  border-radius: 3px;
  margin: 0 2px;
  vertical-align: middle;
  transition: background .15s, transform .15s;
}
.itv-arrow:hover {
  background: #e2e8f0;
  color: #64748b;
}
.itv-arrow-expanded {
  /* 展开状态，不需要额外旋转 */
}
.itv-arrow-collapsed {
  /* 折叠状态 */
}

/* 折叠/展开区域 */
.itv-section { }
.itv-section.itv-section-open { }
.itv-section.itv-section-collapsed {
  display: none;
}

/* 根级区域 */
.itv-root {
  display: inline;
}
</style>
