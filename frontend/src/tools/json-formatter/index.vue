<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：输入区 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2 flex-wrap gap-2">
        <label class="text-xs font-semibold text-slate-500">JSON 输入</label>
        <div class="flex gap-1.5 flex-wrap">
          <button @click="format" class="px-2.5 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">格式化</button>
          <button @click="validateJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">校验</button>
          <button @click="escapeJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">转义</button>
          <button @click="unescapeJson" class="px-2.5 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">反转义</button>
        </div>
      </div>
      <textarea
        v-model="input"
        class="flex-1 p-4 border rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400"
        :class="xmlActive ? 'border-amber-300' : errorMessage ? 'border-red-400' : 'border-slate-200'"
        :placeholder="xmlActive ? '输入 XML 文本...' : `{ key: value }`"
      ></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
      <!-- XML 模式专用按钮 -->
      <div v-if="xmlActive" class="flex gap-2 mt-2">
        <button @click="xmlToJson" class="px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">XML → JSON</button>
        <button @click="jsonToXml" class="px-3 py-1 text-xs rounded-md bg-amber-500 hover:bg-amber-600 text-white">JSON → XML</button>
      </div>
    </div>

    <!-- 右侧：Tab 切换 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <div class="flex gap-0.5 bg-slate-100 rounded-lg p-0.5">
          <button v-for="tab in tabs" :key="tab.key" @click="activeTab = tab.key"
            class="px-3 py-1 text-xs rounded-md transition-colors"
            :class="activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm font-medium' : 'text-slate-500 hover:text-slate-700'"
          >{{ tab.label }}</button>
        </div>
        <button @click="copyFormatted" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">
          {{ copied ? '✓ 已复制' : '复制' }}
        </button>
      </div>

      <!-- 树形视图 -->
      <div v-if="activeTab === 'tree'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm">
        <div v-if="parsedJson" class="flex gap-2 mb-2">
          <button @click="expandAll" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500">全部展开</button>
          <button @click="collapseAll" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500">全部折叠</button>
        </div>
        <div v-if="!parsedJson" class="text-slate-400">等待输入 JSON...</div>
        <div v-else class="tree-view" v-html="treeHtml"></div>
      </div>

      <!-- 文本视图 -->
      <div v-else-if="activeTab === 'text'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm">
        <div v-if="parsedJson" class="flex gap-2 mb-2">
          <button @click="expandAllText" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500">全部展开</button>
          <button @click="collapseAllText" class="px-2 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 text-slate-500">全部折叠</button>
        </div>
        <div v-if="!parsedJson" class="text-slate-400">等待输入 JSON...</div>
        <div v-else class="text-interactive-view" v-html="textInteractiveHtml"></div>
      </div>

      <!-- JSONPath 查询 -->
      <div v-else-if="activeTab === 'jsonpath'" class="flex-1 flex flex-col">
        <div class="flex items-center gap-2 mb-2">
          <span class="text-xs text-slate-400">$</span>
          <input v-model="jsonPath" class="flex-1 px-3 py-1.5 border border-slate-200 rounded-lg text-xs font-mono focus:outline-none focus:ring-2 focus:ring-indigo-400"
            placeholder=".store.book[0].title" @input="runJsonPath" />
          <span class="text-[10px] text-slate-400">JSONPath</span>
        </div>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ jsonPathResult || '输入 JSONPath 表达式查询...' }}</div>
      </div>

      <!-- XML 互转 -->
      <div v-else class="flex-1 flex flex-col">
        <div class="flex items-center justify-between mb-2">
          <label class="text-xs font-semibold text-slate-500">XML 输出</label>
        </div>
        <textarea v-model="xmlOutput" readonly class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm bg-slate-50 focus:outline-none"
          placeholder="点击 JSON→XML 按钮生成 XML"></textarea>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'json-formatter',
  name: 'JSON 工具箱',
  description: '格式化/校验/树形/文本/JSONPath/XML互转',
  icon: '',
  category: 'develop',
  group: 'JSON',
}
defineExpose({ meta })

const input = ref('')
const output = ref('') // 始终保存格式化 JSON
const errorMessage = ref('')
const parsedJson = ref<any>(null)
const activeTab = ref<'tree' | 'text' | 'jsonpath' | 'xml'>('tree')
const xmlActive = ref(false)
const jsonPath = ref('')
const jsonPathResult = ref('')
const treeHtml = ref('')
const textInteractiveHtml = ref('')
const xmlOutput = ref('')
const tabs = [
  { key: 'tree' as const, label: '树形' },
  { key: 'text' as const, label: '文本' },
  { key: 'jsonpath' as const, label: 'JSONPath' },
  { key: 'xml' as const, label: 'XML' },
]
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

// 监听 XML tab 切换
watch(activeTab, (t) => { xmlActive.value = t === 'xml' })

// ======== 格式化 / 校验 / 转义 ========

function tryParse(): any { try { return JSON.parse(input.value) } catch { return null } }

function updateAll(obj: any) {
  parsedJson.value = obj
  output.value = JSON.stringify(obj, null, 2)
  treeHtml.value = renderTree(obj)
  textInteractiveHtml.value = renderInteractiveText(obj)
  errorMessage.value = ''
}

function format() {
  const obj = tryParse()
  if (obj !== null) { updateAll(obj); if (activeTab.value === 'xml') jsonToXml() }
  else { errorMessage.value = 'JSON 格式错误'; toastError('格式错误') }
}

function validateJson() {
  try { JSON.parse(input.value); errorMessage.value = ''; success('JSON 格式正确 ✓') }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('格式错误') }
}

function escapeJson() {
  try { JSON.parse(input.value); output.value = JSON.stringify(input.value); } catch { output.value = JSON.stringify(input.value) }
  success('已转义')
}

function unescapeJson() {
  try {
    const u = JSON.parse(input.value)
    if (typeof u === 'string') {
      try { const p = JSON.parse(u); updateAll(p); return } catch { output.value = u }
    } else { updateAll(u); return }
    errorMessage.value = ''
  } catch {
    try {
      const c = input.value.replace(/\\"/g, '"').replace(/^"/, '').replace(/"$/, '')
      output.value = c
      try { updateAll(JSON.parse(c)) } catch { }
    } catch { toastError('反转义失败') }
  }
}

function autoFormat() {
  const obj = tryParse()
  if (obj !== null) updateAll(obj)
}

function copyFormatted() {
  let content: string
  if (activeTab.value === 'xml') content = xmlOutput.value
  else if (activeTab.value === 'jsonpath') content = jsonPathResult.value
  else content = output.value || input.value
  if (content) { copy(content); success('已复制到剪贴板') }
}

// ======== 树形视图 ========

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

;(window as any).__toggleTreeNode = function(nid: string, tlEl: HTMLElement) {
  const tn = document.querySelector(`.tn[data-nid="${nid}"]`) as HTMLElement
  if (!tn) return
  const tc = tn.querySelector('.tc') as HTMLElement
  const tdots = tn.querySelector('.tdots') as HTMLElement
  const tb2 = tn.querySelector('.tb2') as HTMLElement
  const ta = tlEl.querySelector('.ta') as HTMLElement
  const isCollapsed = tc && tc.classList.contains('collapsed')
  if (isCollapsed) { if (tc) tc.classList.remove('collapsed'); if (tdots) tdots.classList.add('hidden'); if (tb2) tb2.classList.remove('hidden'); if (ta) ta.classList.add('expanded') }
  else { if (tc) tc.classList.add('collapsed'); if (tdots) tdots.classList.remove('hidden'); if (tb2) tb2.classList.add('hidden'); if (ta) ta.classList.remove('expanded') }
}

function expandAll() {
  document.querySelectorAll('.tree-view .tc.collapsed').forEach(el => {
    const tc = el as HTMLElement; const tn = tc.closest('.tn') as HTMLElement
    tc.classList.remove('collapsed')
    const tdots = tn?.querySelector('.tdots') as HTMLElement; if (tdots) tdots.classList.add('hidden')
    const tb2 = tn?.querySelector('.tb2') as HTMLElement; if (tb2) tb2.classList.remove('hidden')
    const ta = tn?.querySelector('.ta') as HTMLElement; if (ta) ta.classList.add('expanded')
  })
}

function collapseAll() {
  document.querySelectorAll('.tree-view .tc:not(.collapsed)').forEach(el => {
    const tc = el as HTMLElement; const tn = tc.closest('.tn') as HTMLElement
    if (tn && tn.parentElement?.closest('.tn') === null) return
    tc.classList.add('collapsed')
    const tdots = tn?.querySelector('.tdots') as HTMLElement; if (tdots) tdots.classList.remove('hidden')
    const tb2 = tn?.querySelector('.tb2') as HTMLElement; if (tb2) tb2.classList.add('hidden')
    const ta = tn?.querySelector('.ta') as HTMLElement; if (ta) ta.classList.remove('expanded')
  })
}

// ======== 文本视图 ========

let textSectionCounter = 0

function renderInteractiveText(obj: any): string {
  textSectionCounter = 0
  return `<div class="itv-root">${walkTree(obj, 0)}</div>`
}

function escHtml(s: string): string { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') }

function walkTree(val: any, depth: number): string {
  const br = '\n'; const indent = '  '.repeat(depth); const indentInner = '  '.repeat(depth + 1)
  if (val === null || val === undefined) return `<span class="itv-null">null</span>`
  if (typeof val === 'string') return `<span class="itv-string">"${escHtml(val)}"</span>`
  if (typeof val === 'number') return `<span class="itv-number">${val}</span>`
  if (typeof val === 'boolean') return `<span class="itv-bool">${val}</span>`
  const isArr = Array.isArray(val); const entries = Object.entries(val); const len = entries.length
  const [open, close] = isArr ? ['[',']'] : ['{','}']
  if (len === 0) return `<span class="itv-bracket">${open}${close}</span>`
  const sectionId = 'itvsec-' + (textSectionCounter++)
  let html = `<span class="itv-bracket">${open}<span class="itv-arrow itv-arrow-expanded" data-section="${sectionId}" onclick="window.__toggleTextSection('${sectionId}',this)" title="点击折叠">▼</span></span>`
  html += `<span class="itv-section itv-section-open" id="${sectionId}">`
  for (let i = 0; i < entries.length; i++) {
    const [k, v] = entries[i]
    html += br + indentInner
    if (!isArr) html += `<span class="itv-key">"${escHtml(k)}"</span>: `
    html += walkTree(v, depth + 1)
    if (i < entries.length - 1) html += ','
  }
  html += br + indent
  html += `</span><span class="itv-bracket itv-close-bracket">${close}</span>`
  return html
}

;(window as any).__toggleTextSection = function(sectionId: string, arrowEl: HTMLElement) {
  const section = document.getElementById(sectionId)
  if (!section) return
  const isOpen = section.classList.contains('itv-section-open')
  if (isOpen) { section.classList.remove('itv-section-open'); section.classList.add('itv-section-collapsed'); arrowEl.classList.remove('itv-arrow-expanded'); arrowEl.classList.add('itv-arrow-collapsed'); arrowEl.textContent = '▶' }
  else { section.classList.add('itv-section-open'); section.classList.remove('itv-section-collapsed'); arrowEl.classList.add('itv-arrow-expanded'); arrowEl.classList.remove('itv-arrow-collapsed'); arrowEl.textContent = '▼' }
}

function expandAllText() {
  document.querySelectorAll('.itv-section').forEach(el => { el.classList.add('itv-section-open'); el.classList.remove('itv-section-collapsed') })
  document.querySelectorAll('.itv-arrow').forEach(el => { el.classList.add('itv-arrow-expanded'); el.classList.remove('itv-arrow-collapsed'); el.textContent = '▼' })
}
function collapseAllText() {
  document.querySelectorAll('.itv-section').forEach(el => { el.classList.add('itv-section-collapsed'); el.classList.remove('itv-section-open') })
  document.querySelectorAll('.itv-arrow').forEach(el => { el.classList.add('itv-arrow-collapsed'); el.classList.remove('itv-arrow-expanded'); el.textContent = '▶' })
}

// ======== JSONPath ========

function runJsonPath() {
  if (!parsedJson.value || !jsonPath.value) { jsonPathResult.value = ''; return }
  try { jsonPathResult.value = JSON.stringify(evalPath(parsedJson.value, jsonPath.value), null, 2) }
  catch (e: any) { jsonPathResult.value = '查询错误: ' + e.message }
}

function evalPath(obj: any, path: string): any {
  const parts = path.replace(/^\$\.?/, '').split(/(?=\[)|(?<=])\./).filter(Boolean)
  let cur = obj
  for (const part of parts) {
    if (!cur) return undefined
    const m = part.match(/^(.+)?\[(.+)\]$/)
    if (m) {
      const prop = m[1] || ''; const idx = m[2].replace(/['"]/g, '')
      if (prop) cur = cur[prop]
      if (idx === '*') return Object.values(cur || {})
      if (idx.startsWith('?(')) return filterPath(cur, idx)
      cur = isNaN(Number(idx)) ? cur[idx] : cur[Number(idx)]
    } else { cur = cur[part] }
  }
  return cur
}

function filterPath(arr: any[], expr: string): any[] {
  const m = expr.match(/@\.(\w+)\s*(==|!=|>|<)\s*(.+)/)
  if (!m) return arr
  const [, key, op, vs] = m; const val = vs.replace(/['"]/g, '')
  return arr.filter(item => { const iv = String(item[key]); switch(op) { case '==': return iv == val; case '!=': return iv != val; case '>': return Number(iv) > Number(val); case '<': return Number(iv) < Number(val); default: return false } })
}

watch(jsonPath, runJsonPath)
watch(input, autoFormat)

// ======== JSON ↔ XML ========

function jsonToXml() {
  try {
    const obj = JSON.parse(input.value || output.value)
    xmlOutput.value = '<?xml version="1.0" encoding="UTF-8"?>\n' + objToXml(obj)
    errorMessage.value = ''
  } catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('JSON 格式错误') }
}

function objToXml(obj: any, indent = ''): string {
  if (obj === null || obj === undefined) return ''
  if (typeof obj !== 'object') return String(obj)
  if (Array.isArray(obj)) {
    return obj.map(item => {
      const keys = typeof item === 'object' && item !== null ? Object.keys(item) : []
      const tag = keys.length > 0 ? keys[0].replace(/s$/, '') : 'item'
      return `${indent}<${tag}>\n${objToXml(item, indent + '  ')}\n${indent}</${tag}>`
    }).join('\n')
  }
  let xml = ''
  for (const [key, val] of Object.entries(obj)) {
    if (val !== null && typeof val === 'object') xml += `${indent}<${key}>\n${objToXml(val, indent + '  ')}\n${indent}</${key}>\n`
    else { const sv = val === null ? '' : String(val).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); xml += `${indent}<${key}>${sv}</${key}>\n` }
  }
  return xml.trimEnd()
}

function xmlToJson() {
  try {
    if (!input.value.trim()) { toastError('请输入 XML 内容'); return }
    const doc = new DOMParser().parseFromString(input.value, 'text/xml')
    const err = doc.querySelector('parsererror')
    if (err) { toastError('XML 格式错误: ' + err.textContent); return }
    const result = xmlNodeToJson(doc.documentElement)
    xmlOutput.value = JSON.stringify(result, null, 2)
    errorMessage.value = ''
  } catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('XML 解析失败') }
}

function xmlNodeToJson(node: Element): any {
  const obj: any = {}
  for (const attr of node.attributes) obj['@' + attr.name] = attr.value
  for (const child of node.children) {
    const cj = xmlNodeToJson(child)
    if (obj[child.tagName] !== undefined) { if (!Array.isArray(obj[child.tagName])) obj[child.tagName] = [obj[child.tagName]]; obj[child.tagName].push(cj) }
    else obj[child.tagName] = cj
  }
  const text = node.textContent?.trim()
  if (node.children.length === 0 && text) { if (Object.keys(obj).length === 0) return text; obj['#text'] = text }
  return obj
}
</script>

<style>
/* 树形视图 */
.tree-view { font-size: 13px; line-height: 1.9; }
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

/* 交互式文本视图 */
.text-interactive-view { white-space: pre; line-height: 1.8; font-size: 13px; }
.itv-string { color: #16a34a; }
.itv-number { color: #2563eb; }
.itv-bool { color: #9333ea; }
.itv-null { color: #94a3b8; }
.itv-key { color: #6366f1; }
.itv-bracket { color: #94a3b8; }
.itv-arrow { display: inline-flex; align-items: center; justify-content: center; width: 14px; height: 14px; font-size: 8px; color: #94a3b8; cursor: pointer; user-select: none; border-radius: 3px; margin: 0 2px; vertical-align: middle; transition: background .15s; }
.itv-arrow:hover { background: #e2e8f0; color: #64748b; }
.itv-section-collapsed { display: none; }
.itv-root { display: inline; }
</style>
