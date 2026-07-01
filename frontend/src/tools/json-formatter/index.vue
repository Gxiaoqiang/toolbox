<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">JSON 输入</label>
        <div class="flex gap-2">
          <button @click="format" class="px-3 py-1 text-xs rounded-md bg-blue-500 hover:bg-blue-600 text-white">格式化</button>
          <button @click="compress" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">压缩</button>
          <button @click="validateJson" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">校验</button>
        </div>
      </div>
      <textarea
        v-model="input"
        class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        :class="{ 'border-red-400': errorMessage }"
        placeholder='{"key": "value"}'
      ></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">结果</label>
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
const meta: ToolMeta = { id: 'json-formatter', name: 'JSON 格式化', description: '格式化、压缩和校验 JSON 数据', icon: 'braces', category: 'develop' }
defineExpose({ meta })

const input = ref('')
const output = ref('')
const errorMessage = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function format() {
  try { const obj = JSON.parse(input.value); output.value = JSON.stringify(obj, null, 2); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('JSON 格式不正确') }
}
function compress() {
  try { const obj = JSON.parse(input.value); output.value = JSON.stringify(obj); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('JSON 格式不正确') }
}
function validateJson() {
  try { JSON.parse(input.value); errorMessage.value = ''; success('JSON 格式正确') }
  catch (e: any) { errorMessage.value = 'JSON 格式错误: ' + e.message; toastError('JSON 格式不正确') }
}
function copyOutput() { if (output.value) { copy(output.value); success('已复制到剪贴板') } }
</script>
