<template>
  <div class="flex flex-col gap-4 h-full">
    <div class="flex gap-4" style="height: 38%">
      <div class="flex-1 flex flex-col">
        <label class="text-xs font-semibold text-slate-500 mb-2">原始文本</label>
        <textarea v-model="leftText" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" placeholder="粘贴原始文本..." @input="runDiff"></textarea>
      </div>
      <div class="flex-1 flex flex-col">
        <label class="text-xs font-semibold text-slate-500 mb-2">对比文本</label>
        <textarea v-model="rightText" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-400" placeholder="粘贴对比文本..." @input="runDiff"></textarea>
      </div>
    </div>
    <div class="flex-1 flex flex-col">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">差异对比 ({{ stats.added }} 行新增, {{ stats.removed }} 行删除)</label>
        <span class="text-xs text-slate-400">红色=删除 | 绿色=新增</span>
      </div>
      <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm leading-6">
        <div v-if="diffLines.length === 0" class="text-slate-400">等待输入...</div>
        <div v-for="(line, i) in diffLines" :key="i" class="whitespace-pre" :class="{ 'bg-red-50 text-red-700': line.type === 'removed', 'bg-green-50 text-green-700': line.type === 'added', 'text-slate-400': line.type === 'same' }">
          <span class="inline-block w-5 text-center mr-3 text-xs select-none">{{ line.prefix }}</span>{{ line.content }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { ToolMeta } from '@/tools/types'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'diff-checker', name: 'Diff 对比', description: '逐行对比两段文本差异', icon: 'git-compare', category: 'develop' }
defineExpose({ meta })

const leftText = ref('')
const rightText = ref('')
interface DiffLine { content: string; type: 'added' | 'removed' | 'same'; prefix: string }
const diffLines = ref<DiffLine[]>([])
const stats = reactive({ added: 0, removed: 0 })

function runDiff() {
  if (!leftText.value && !rightText.value) { diffLines.value = []; stats.added = 0; stats.removed = 0; return }
  const leftLines = leftText.value.split('\n'), rightLines = rightText.value.split('\n')
  const m = leftLines.length, n = rightLines.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0))
  for (let i = 1; i <= m; i++) for (let j = 1; j <= n; j++) dp[i][j] = leftLines[i - 1] === rightLines[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1])
  const result: DiffLine[] = []; let i = m, j = n, added = 0, removed = 0
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && leftLines[i - 1] === rightLines[j - 1]) { result.unshift({ content: leftLines[i - 1], type: 'same', prefix: '  ' }); i--; j-- }
    else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) { result.unshift({ content: rightLines[j - 1], type: 'added', prefix: '+ ' }); j--; added++ }
    else { result.unshift({ content: leftLines[i - 1], type: 'removed', prefix: '- ' }); i--; removed++ }
  }
  diffLines.value = result; stats.added = added; stats.removed = removed
}
</script>
