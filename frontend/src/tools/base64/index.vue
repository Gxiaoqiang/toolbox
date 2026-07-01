<template>
  <div class="flex gap-4 h-full">
    <div class="flex-1 flex flex-col">
      <label class="text-xs font-semibold text-slate-500 mb-2">原文</label>
      <textarea v-model="plainText" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" placeholder="输入要编码/解码的文本..."></textarea>
    </div>
    <div class="flex flex-col justify-center gap-3">
      <button @click="encode" class="px-4 py-2 rounded-lg bg-indigo-500 hover:bg-indigo-600 text-white text-sm font-medium transition-colors">▼ 编码</button>
      <button @click="decode" class="px-4 py-2 rounded-lg bg-slate-500 hover:bg-slate-600 text-white text-sm font-medium transition-colors">▲ 解码</button>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">结果</label>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <textarea v-model="result" readonly class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm bg-slate-50 focus:outline-none" placeholder="结果将显示在这里..."></textarea>
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
const meta: ToolMeta = { id: 'base64', name: 'Base64 编解码', description: 'Base64 编码和解码，支持 UTF-8', icon: 'binary', category: 'develop' }
defineExpose({ meta })

const plainText = ref('')
const result = ref('')
const errorMessage = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function encode() {
  try { result.value = btoa(unescape(encodeURIComponent(plainText.value))); errorMessage.value = '' }
  catch (e: any) { errorMessage.value = '编码失败: ' + e.message; toastError('编码失败') }
}
function decode() {
  try { result.value = decodeURIComponent(escape(atob(plainText.value.trim()))); errorMessage.value = '' }
  catch { errorMessage.value = '解码失败: 请确认输入为有效 Base64 字符串'; toastError('解码失败') }
}
function copyOutput() { if (result.value) { copy(result.value); success('已复制到剪贴板') } }
</script>
