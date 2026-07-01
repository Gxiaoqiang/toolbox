<template>
  <div class="flex flex-col gap-4 h-full">
    <div class="flex items-center gap-3">
      <label class="text-sm font-semibold text-slate-600 flex-shrink-0">正则表达式</label>
      <div class="relative flex-1">
        <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">/</span>
        <input v-model="regexPattern" class="w-full pl-7 pr-10 py-2 border border-slate-200 rounded-lg font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" placeholder="\d+" @input="runRegex" />
        <span class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">/{{ flags }}</span>
      </div>
      <div class="flex items-center gap-1">
        <button v-for="f in ['g', 'i', 'm']" :key="f" @click="toggleFlag(f)" class="px-2 py-1 text-xs rounded font-mono transition-colors" :class="flags.includes(f) ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-500'">{{ f }}</button>
      </div>
    </div>
    <div class="flex-1 flex gap-4">
      <div class="flex-1 flex flex-col">
        <label class="text-xs font-semibold text-slate-500 mb-2">测试文本</label>
        <textarea v-model="testText" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" placeholder="输入测试文本..." @input="runRegex"></textarea>
      </div>
      <div class="flex-1 flex flex-col">
        <label class="text-xs font-semibold text-slate-500 mb-2">匹配结果 ({{ matchCount }} 项)</label>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm">
          <div v-if="!regexPattern" class="text-slate-400">等待输入正则...</div>
          <div v-else-if="regexError" class="text-red-500">{{ regexError }}</div>
          <div v-else-if="matches.length === 0" class="text-slate-400">无匹配</div>
          <div v-else>
            <span v-for="(part, i) in highlightedParts" :key="i" :class="part.match ? 'bg-yellow-200 rounded px-0.5' : ''">{{ part.text }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolMeta } from '@/tools/types'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'regex-tester', name: '正则测试', description: '测试正则表达式，高亮匹配结果', icon: 'regex', category: 'develop' }
defineExpose({ meta })

const regexPattern = ref('')
const flags = ref('g')
const testText = ref('')
const matches = ref<string[]>([])
const regexError = ref('')

function toggleFlag(f: string) { flags.value = flags.value.includes(f) ? flags.value.replace(f, '') : flags.value + f; runRegex() }
const matchCount = computed(() => matches.value.length)
const highlightedParts = computed(() => {
  if (!regexPattern.value || !testText.value || regexError.value) return []
  try {
    const regex = new RegExp(regexPattern.value, flags.value) as RegExp & { lastIndex?: number }
    const parts: Array<{ text: string; match: boolean }> = []
    let lastIdx = 0
    let m: RegExpExecArray | null
    while ((m = regex.exec(testText.value)) !== null) {
      if (m.index > lastIdx) parts.push({ text: testText.value.slice(lastIdx, m.index), match: false })
      parts.push({ text: m[0], match: true })
      lastIdx = regex.lastIndex!
      if (!flags.value.includes('g')) break
    }
    if (lastIdx < testText.value.length) parts.push({ text: testText.value.slice(lastIdx), match: false })
    return parts
  } catch { return [] }
})

function runRegex() {
  matches.value = []; regexError.value = ''
  if (!regexPattern.value || !testText.value) return
  try { const r = testText.value.match(new RegExp(regexPattern.value, flags.value)); if (r) matches.value = Array.from(r) }
  catch (e: any) { regexError.value = '正则错误: ' + e.message }
}
</script>
