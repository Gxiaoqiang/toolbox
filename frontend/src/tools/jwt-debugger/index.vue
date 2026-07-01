<template>
  <div class="flex flex-col gap-4 h-full">
    <!-- JWT Token 输入 -->
    <div>
      <label class="text-xs font-semibold text-slate-500 mb-2 block">JWT Token</label>
      <textarea v-model="token" class="w-full p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" rows="3" placeholder="eyJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiQWxpY2UifQ..."
        @input="decode"></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
    </div>

    <!-- 解码结果三栏 -->
    <div class="flex-1 flex gap-4">
      <!-- Header -->
      <div class="flex-1 flex flex-col min-w-0">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs font-semibold text-slate-500">Header</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded-full font-medium" :class="headerValid ? 'bg-emerald-100 text-emerald-600' : 'bg-red-100 text-red-500'">{{ headerValid ? '✓' : '✕' }}</span>
        </div>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ headerJson || '—' }}</div>
      </div>
      <!-- Payload -->
      <div class="flex-1 flex flex-col min-w-0">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs font-semibold text-slate-500">Payload</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded-full font-medium" :class="payloadValid ? 'bg-emerald-100 text-emerald-600' : 'bg-red-100 text-red-500'">{{ payloadValid ? '✓' : '✕' }}</span>
        </div>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white font-mono text-sm whitespace-pre-wrap">{{ payloadJson || '—' }}</div>
      </div>
      <!-- Signature -->
      <div class="w-52 flex-shrink-0 flex flex-col">
        <span class="text-xs font-semibold text-slate-500 mb-2">Signature</span>
        <div class="flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-slate-50">
          <p class="text-[10px] text-slate-500 mb-2 break-all font-mono">{{ signature || '—' }}</p>
          <p class="text-[10px] text-slate-400">签名无法在浏览器中验证，仅做展示。</p>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="flex gap-2">
      <button @click="copyPart('header')" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">复制 Header</button>
      <button @click="copyPart('payload')" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">复制 Payload</button>
      <button @click="clearToken" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">清空</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'jwt-debugger', name: 'JWT 解码', description: '解码 JWT Token，查看 Header/Payload', icon: '', category: 'develop' }
defineExpose({ meta })

const token = ref('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c')
const headerJson = ref('')
const payloadJson = ref('')
const signature = ref('')
const headerValid = ref(false)
const payloadValid = ref(false)
const errorMessage = ref('')
const { copy } = useClipboard()
const { success } = useToast()

function decode() {
  errorMessage.value = ''
  headerJson.value = ''; payloadJson.value = ''; signature.value = ''
  headerValid.value = false; payloadValid.value = false

  const parts = token.value.trim().split('.')
  if (parts.length !== 3) { errorMessage.value = 'JWT 格式错误：应包含 3 个由 . 分隔的部分'; return }

  try {
    headerJson.value = JSON.stringify(JSON.parse(atob(parts[0])), null, 2)
    headerValid.value = true
  } catch { headerJson.value = atob(parts[0]); headerValid.value = false }
  try {
    payloadJson.value = JSON.stringify(JSON.parse(atob(parts[1])), null, 2)
    // 检查过期时间
    const payload = JSON.parse(atob(parts[1]))
    if (payload.exp) {
      const expDate = new Date(payload.exp * 1000)
      payloadJson.value += `\n\n// 过期时间: ${expDate.toLocaleString()}`
      if (Date.now() > payload.exp * 1000) payloadJson.value += '\n// ⚠ Token 已过期'
    }
    if (payload.iat) {
      payloadJson.value += `\n// 签发时间: ${new Date(payload.iat * 1000).toLocaleString()}`
    }
    payloadValid.value = true
  } catch { payloadJson.value = atob(parts[1]); payloadValid.value = false }
  signature.value = parts[2]
}

function copyPart(part: 'header' | 'payload') {
  const content = part === 'header' ? headerJson.value : payloadJson.value
  if (content) { copy(content); success(`已复制 ${part === 'header' ? 'Header' : 'Payload'}`) }
}

function clearToken() { token.value = ''; headerJson.value = ''; payloadJson.value = ''; signature.value = ''; errorMessage.value = '' }

decode()
</script>
