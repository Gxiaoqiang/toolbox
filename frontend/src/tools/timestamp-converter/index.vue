<template>
  <div class="max-w-2xl mx-auto flex flex-col gap-6">
    <h3 class="text-base font-semibold text-slate-700">Unix 时间戳 ↔ 日期互转</h3>

    <!-- 时间戳 → 日期 -->
    <div class="p-5 bg-white border border-slate-200 rounded-xl">
      <label class="text-xs font-semibold text-slate-500 mb-3 block">时间戳 → 日期</label>
      <div class="flex gap-3 items-end">
        <div class="flex-1">
          <input v-model="tsInput" class="w-full px-3 py-2 border border-slate-200 rounded-lg font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" placeholder="输入 Unix 时间戳（秒或毫秒）" @input="tsToDate" />
        </div>
        <button @click="tsToDateNow" class="px-3 py-2 text-xs rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 flex-shrink-0">当前</button>
      </div>
      <div v-if="tsResult" class="mt-3 p-3 bg-slate-50 rounded-lg font-mono text-sm">
        <div class="flex gap-4 flex-wrap">
          <div><span class="text-slate-400">本地:</span> <span class="text-slate-800">{{ tsResult.local }}</span></div>
          <div><span class="text-slate-400">UTC:</span> <span class="text-slate-800">{{ tsResult.utc }}</span></div>
          <div><span class="text-slate-400">ISO:</span> <span class="text-slate-800">{{ tsResult.iso }}</span></div>
        </div>
      </div>
    </div>

    <!-- 日期 → 时间戳 -->
    <div class="p-5 bg-white border border-slate-200 rounded-xl">
      <label class="text-xs font-semibold text-slate-500 mb-3 block">日期 → 时间戳</label>
      <input v-model="dateInput" type="datetime-local" class="w-full px-3 py-2 border border-slate-200 rounded-lg font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 mb-3" @input="dateToTs" />
      <button @click="setNow" class="px-3 py-1.5 text-xs rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 mb-3">使用当前时间</button>
      <div v-if="dateResult" class="p-3 bg-slate-50 rounded-lg font-mono text-sm flex gap-4 flex-wrap">
        <div><span class="text-slate-400">秒:</span> <span class="text-slate-800">{{ dateResult.seconds }}</span></div>
        <div><span class="text-slate-400">毫秒:</span> <span class="text-slate-800">{{ dateResult.millis }}</span></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'timestamp-converter', name: '时间戳转换', description: 'Unix 时间戳与日期互转', icon: '', category: 'develop' }
defineExpose({ meta })

const tsInput = ref(String(Math.floor(Date.now() / 1000)))
const tsResult = ref<{ local: string; utc: string; iso: string } | null>(null)
const dateInput = ref('')
const dateResult = ref<{ seconds: number; millis: number } | null>(null)

function tsToDate() {
  const val = tsInput.value.trim()
  if (!val) { tsResult.value = null; return }
  let num = Number(val)
  if (isNaN(num)) { tsResult.value = null; return }
  // 如果是毫秒级 (13位)，转成秒
  if (num > 9999999999999) num = Math.floor(num)
  if (num > 9999999999) num = Math.floor(num / 1000)
  const d = new Date(num * 1000)
  tsResult.value = {
    local: d.toLocaleString('zh-CN'),
    utc: d.toUTCString(),
    iso: d.toISOString(),
  }
}

function tsToDateNow() { tsInput.value = String(Math.floor(Date.now() / 1000)); tsToDate() }

function dateToTs() {
  if (!dateInput.value) { dateResult.value = null; return }
  const d = new Date(dateInput.value)
  dateResult.value = {
    seconds: Math.floor(d.getTime() / 1000),
    millis: d.getTime(),
  }
}

function setNow() {
  const now = new Date()
  dateInput.value = now.toISOString().slice(0, 16)
  dateToTs()
}

tsToDate()
</script>
