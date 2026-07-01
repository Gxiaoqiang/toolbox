<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">YAML 输入</label>
        <button @click="yamlToJson" class="px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">YAML → JSON</button>
      </div>
      <textarea v-model="yamlInput" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" placeholder="key: value"></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">JSON 结果</label>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ output || '等待输入...' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'yaml-formatter', name: 'YAML 格式化', description: 'YAML 与 JSON 互转', icon: 'file-json', category: 'develop' }
defineExpose({ meta })

const yamlInput = ref('')
const output = ref('')
const errorMessage = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function parseYaml(yaml: string): Record<string, any> {
  const result: Record<string, any> = {}
  for (const line of yaml.split('\n')) {
    const m = line.match(/^(\s*)([^:]+):\s*(.*)$/)
    if (!m) continue
    const key = m[2].trim(); let val: string = m[3].trim()
    if (val === 'true' || val === 'false') val = val
    else if (val === 'null' || val === '~') val = 'null'
    else if (/^-?\d+\.?\d*$/.test(val)) val = val
    result[key] = val
  }
  return result
}

function yamlToJson() {
  try { const obj = parseYaml(yamlInput.value); output.value = JSON.stringify(obj, null, 2); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = '解析错误: ' + e.message; toastError('YAML 格式不正确') }
}
function copyOutput() { if (output.value) { copy(output.value); success('已复制到剪贴板') } }
</script>
