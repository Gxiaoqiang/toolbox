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
        <div v-if="!parsedJson" class="text-slate-400">等待输入...</div>
        <div v-else class="tree-view" v-html="treeHtml"></div>
      </div>

      <!-- 文本视图 -->
      <div v-else-if="activeTab === 'text'" class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ output || '等待输入...' }}</div>

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
import { ref, watch } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = {
  id: 'json-formatter',
  name: 'JSON 格式化',
  description: '格式化/压缩/校验/树形查看/排序/转义/JSONPath',
  icon: '',
  category: 'develop',
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
const rootKey = ref(0)
const tabs = [
  { key: 'tree' as const, label: '树形' },
  { key: 'text' as const, label: '文本' },
  { key: 'jsonpath' as const, label: 'JSONPath' },
]
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function renderTree(obj: any): string {
  const id = 't' + Math.random().toString(36).slice(2)
  const collapsed = new Set<string>()
  function node(val: any, path: string, depth: number, autoExpand: boolean): string {
    const nid = id + '-' + path.replace(/[^a-zA-Z0-9]/g, '_')
    if (val === null || val === undefined) return `<span style="color:#94a3b8">null</span>`
    if (typeof val === 'string') return `<span style="color:#16a34a">"${val.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;')}"</span>`
    if (typeof val === 'number') return `<span style="color:#2563eb">${val}</span>`
    if (typeof val === 'boolean') return `<span style="color:#9333ea">${val}</span>`
    const isArr = Array.isArray(val)
    const entries = Object.entries(val)
    const len = entries.length
    const bracket = isArr ? ['[',']'] : ['{','}']
    const summary = isArr ? `Array[${len}]` : `Object{${len}}`
    const expandClass = autoExpand ? '' : ' collapsed'
    let html = `<div class="tn">`
    html += `<div class="tl" onclick="this.parentElement.classList.toggle('collapsed')"><span class="ta${expandClass ? '' : ' expanded'}">▼</span><span class="tp">${path}</span><span class="ts">${summary}</span><span class="tb"> ${bracket[0]}</span><span class="tdots${expandClass ? '' : ' hidden'}"> ... ${bracket[1]}</span></div>`
    html += `<div class="tc${expandClass}">`
    for (const [k, v] of entries) {
      const cp = isArr ? `${path}[${k}]` : `${path}.${k}`
      html += `<div class="tch"><span class="tk">${isArr ? `${k}:` : `"${k}":`}</span>${node(v, cp, depth + 1, false)}</div>`
    }
    html += `</div><div class="tb2${expandClass ? ' hidden' : ''}">${bracket[1]}</div></div>`
    return html
  }
  return node(obj, '$', 0, true)
}

function tryParse(): any {
  try { return JSON.parse(input.value) } catch { return null }
}

function updateTree(obj: any) { parsedJson.value = obj; treeHtml.value = renderTree(obj) }

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
</style>
