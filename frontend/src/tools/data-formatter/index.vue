<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">输入数据</label>
        <select v-model="inputFormat" class="text-xs border border-slate-200 rounded px-2 py-1">
          <option value="csv">CSV</option>
          <option value="json">JSON</option>
        </select>
      </div>
      <textarea v-model="input" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" :placeholder="inputFormat === 'csv' ? 'name,age,city' + '\nAlice,30,NYC' : '[{&quot;name&quot;:&quot;Alice&quot;}]'"></textarea>
      <div class="flex gap-2 mt-2">
        <button @click="convert('json')" class="px-3 py-1 text-xs rounded-md bg-blue-500 hover:bg-blue-600 text-white">→ JSON</button>
        <button @click="convert('csv')" class="px-3 py-1 text-xs rounded-md bg-blue-500 hover:bg-blue-600 text-white">→ CSV</button>
        <button @click="dedupe" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">去重</button>
        <button @click="sortData" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">排序</button>
      </div>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">结果</label>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <textarea v-model="output" readonly class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm bg-slate-50 focus:outline-none" placeholder="结果将显示在这里..."></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'data-formatter', name: '数据处理', description: 'CSV/JSON 互转、去重、排序', icon: 'table', category: 'data' }
defineExpose({ meta })

const input = ref('')
const output = ref('')
const inputFormat = ref('csv')
const errorMessage = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function parseInput(): any[] {
  if (inputFormat.value === 'csv') {
    const lines = input.value.trim().split('\n')
    if (lines.length < 2) return []
    const headers = lines[0].split(',').map(h => h.trim())
    return lines.slice(1).map(line => { const vals = line.split(',').map(v => v.trim()); const obj: Record<string, string> = {}; headers.forEach((h, i) => { obj[h] = vals[i] || '' }); return obj })
  }
  return JSON.parse(input.value)
}

function toCsv(data: any[]): string {
  if (!data.length) return ''
  const headers = Object.keys(data[0])
  return [headers.join(',')].concat(data.map(row => headers.map(h => JSON.stringify(row[h] ?? '')).join(','))).join('\n')
}

function convert(target: 'json' | 'csv') {
  try { const data = parseInput(); output.value = target === 'json' ? JSON.stringify(data, null, 2) : toCsv(data); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = '转换错误: ' + e.message; toastError('数据格式不正确') }
}

function dedupe() {
  try { const data = parseInput(); const seen = new Set<string>(); const unique = data.filter(item => { const key = JSON.stringify(item); if (seen.has(key)) return false; seen.add(key); return true }); output.value = JSON.stringify(unique, null, 2); errorMessage.value = ''; success(`去重: ${data.length} → ${unique.length}`) }
  catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('数据处理失败') }
}

function sortData() {
  try { const data = parseInput(); const keys = Object.keys(data[0] || {}); if (!keys.length) return; data.sort((a, b) => String(a[keys[0]]).localeCompare(String(b[keys[0]]))); output.value = JSON.stringify(data, null, 2); errorMessage.value = ''; success(`按 ${keys[0]} 排序完成`) }
  catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('数据处理失败') }
}

function copyOutput() { if (output.value) { copy(output.value); success('已复制到剪贴板') } }
</script>
