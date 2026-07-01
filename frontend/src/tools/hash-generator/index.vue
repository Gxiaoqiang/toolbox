<template>
  <div class="max-w-2xl mx-auto flex flex-col gap-6">
    <h3 class="text-base font-semibold text-slate-700">哈希计算 (MD5 / SHA)</h3>
    <!-- 输入 -->
    <div class="flex flex-col">
      <label class="text-xs font-semibold text-slate-500 mb-2">输入文本</label>
      <textarea v-model="input" class="w-full p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" rows="4" placeholder="输入要计算哈希的文本..."
        @input="computeAll"></textarea>
    </div>
    <!-- 结果 -->
    <div class="space-y-3">
      <div v-for="algo in algorithms" :key="algo.name" class="p-4 bg-white border border-slate-200 rounded-xl">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs font-semibold text-slate-500">{{ algo.name }}</span>
          <button @click="copyHash(algo.name)" class="text-[10px] px-2 py-0.5 rounded bg-slate-100 hover:bg-slate-200 text-slate-500">复制</button>
        </div>
        <p class="font-mono text-sm text-slate-800 break-all">{{ results[algo.name] || '—' }}</p>
      </div>
    </div>
    <p class="text-[10px] text-slate-400">所有计算在浏览器本地完成，数据不会发送到服务器。</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import SparkMD5 from 'spark-md5'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'hash-generator', name: '哈希计算', description: 'MD5 / SHA1 / SHA256 / SHA512 哈希', icon: '', category: 'develop' }
defineExpose({ meta })

const input = ref('Hello, World!')
const results = reactive<Record<string, string>>({})
const { copy } = useClipboard()
const { success } = useToast()

const algorithms = [
  { name: 'MD5', algo: 'MD5' },
  { name: 'SHA-1', algo: 'SHA-1' },
  { name: 'SHA-256', algo: 'SHA-256' },
  { name: 'SHA-512', algo: 'SHA-512' },
]

async function computeAll() {
  const text = input.value
  if (!text) { algorithms.forEach(a => results[a.name] = ''); return }
  for (const { name, algo } of algorithms) {
    try {
      results[name] = algo === 'MD5' ? SparkMD5.hash(text) : Array.from(new Uint8Array(await crypto.subtle.digest(algo, new TextEncoder().encode(text)))).map(b => b.toString(16).padStart(2, '0')).join('')
    } catch { results[name] = '计算失败' }
  }
}

function copyHash(name: string) {
  if (results[name]) { copy(results[name]); success(`${name} 已复制`) }
}

computeAll()
</script>
