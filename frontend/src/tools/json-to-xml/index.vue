<template>
  <div class="flex gap-4 h-full">
    <!-- 左侧：JSON 输入 -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold text-slate-500 mb-2">JSON 输入</label>
      <textarea v-model="jsonInput" class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400" placeholder='{"root": {"item": [{"id": 1}]}}'></textarea>
      <p v-if="errorMessage" class="mt-1 text-xs text-red-500">{{ errorMessage }}</p>
      <button @click="jsonToXml" class="mt-2 px-4 py-1.5 text-sm rounded-md bg-indigo-500 hover:bg-indigo-600 text-white self-start">JSON → XML</button>
    </div>
    <!-- 中间：方向切换 -->
    <div class="flex flex-col justify-center gap-3">
      <button @click="xmlToJson" class="px-4 py-2 rounded-lg bg-slate-500 hover:bg-slate-600 text-white text-sm font-medium transition-colors">XML<br>→ JSON</button>
    </div>
    <!-- 右侧：XML 输出 -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between mb-2">
        <label class="text-xs font-semibold text-slate-500">XML 输出</label>
        <button @click="copyOutput" class="px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <textarea v-model="xmlOutput" readonly class="flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm bg-slate-50 focus:outline-none" placeholder="XML 结果将显示在这里..."></textarea>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolMeta } from '@/tools/types'
import { useClipboard } from '@/composables/useClipboard'
import { useToast } from '@/composables/useToast'

defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: 'json-to-xml', name: 'JSON ↔ XML', description: 'JSON 与 XML 格式互转', icon: '', category: 'develop' }
defineExpose({ meta })

const jsonInput = ref('{"root":{"item":[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]}}')
const xmlOutput = ref('')
const errorMessage = ref('')
const { copied, copy } = useClipboard()
const { success, error: toastError } = useToast()

function jsonToXml() {
  try {
    const obj = JSON.parse(jsonInput.value)
    xmlOutput.value = '<?xml version="1.0" encoding="UTF-8"?>\n' + objToXml(obj)
    errorMessage.value = ''
  } catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('JSON 格式错误') }
}

function objToXml(obj: any, indent = ''): string {
  if (obj === null || obj === undefined) return ''
  if (typeof obj !== 'object') return String(obj)

  if (Array.isArray(obj)) {
    return obj.map(item => {
      const keys = typeof item === 'object' && item !== null ? Object.keys(item) : []
      const tag = keys.length > 0 ? keys[0].replace(/s$/, '') : 'item'
      return `${indent}<${tag}>\n${objToXml(item, indent + '  ')}\n${indent}</${tag}>`
    }).join('\n')
  }

  let xml = ''
  for (const [key, val] of Object.entries(obj)) {
    if (val !== null && typeof val === 'object') {
      xml += `${indent}<${key}>\n${objToXml(val, indent + '  ')}\n${indent}</${key}>\n`
    } else {
      const safeVal = val === null ? '' : String(val).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      xml += `${indent}<${key}>${safeVal}</${key}>\n`
    }
  }
  return xml.trimEnd()
}

function xmlToJson() {
  try {
    if (!jsonInput.value.trim()) { toastError('请在左侧输入 XML 内容'); return }
    const parser = new DOMParser()
    const doc = parser.parseFromString(jsonInput.value, 'text/xml')
    const errNode = doc.querySelector('parsererror')
    if (errNode) { toastError('XML 格式错误: ' + errNode.textContent); return }
    const result = xmlNodeToJson(doc.documentElement)
    xmlOutput.value = JSON.stringify(result, null, 2)
    errorMessage.value = ''
  } catch (e: any) { errorMessage.value = '错误: ' + e.message; toastError('XML 解析失败') }
}

function xmlNodeToJson(node: Element): any {
  const obj: any = {}
  // 属性
  for (const attr of node.attributes) { obj['@' + attr.name] = attr.value }
  // 子节点
  for (const child of node.children) {
    const childJson = xmlNodeToJson(child)
    if (obj[child.tagName] !== undefined) {
      if (!Array.isArray(obj[child.tagName])) obj[child.tagName] = [obj[child.tagName]]
      obj[child.tagName].push(childJson)
    } else { obj[child.tagName] = childJson }
  }
  // 文本内容
  const text = node.textContent?.trim()
  if (node.children.length === 0 && text) {
    if (Object.keys(obj).length === 0) return text
    obj['#text'] = text
  }
  return obj
}

function copyOutput() { if (xmlOutput.value) { copy(xmlOutput.value); success('已复制') } }
</script>
