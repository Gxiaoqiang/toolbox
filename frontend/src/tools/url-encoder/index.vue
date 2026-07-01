<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧输入 -->
    <div class="flex-1 flex flex-col">
      <label class="text-xs font-semibold text-slate-500 mb-2">原文</label>
      <textarea v-model="input" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
        placeholder="输入要编码/解码的文本或 URL..."></textarea>
      <div class="flex gap-2 mt-2">
        <button @click="doEncode" class="px-4 py-1.5 text-sm rounded-md bg-indigo-500 hover:bg-indigo-600 text-white">编码 →</button>
        <button @click="doDecode" class="px-4 py-1.5 text-sm rounded-md bg-slate-500 hover:bg-slate-600 text-white">← 解码</button>
      </div>
    </div>
    <!-- 右侧输出 -->
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">结果</label>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <textarea v-model="output" readonly class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm bg-slate-50 focus:outline-none" placeholder="结果将显示在这里..."></textarea>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'url-encoder', name: 'URL 编解码', description: 'URL 编码 (encodeURIComponent) 与解码', icon: '', category: 'develop' }
defineExpose({ meta })

const input = ref('https://example.com?name=张三&city=北京')
const output = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function doEncode() { output.value = encodeURIComponent(input.value) }
function doDecode() {
  try { output.value = decodeURIComponent(input.value) } catch { toastError('解码失败：输入不是有效的编码字符串') }
}
function copyOutput() { if (output.value) { copy(output.value); success('已复制') } }
</script>
