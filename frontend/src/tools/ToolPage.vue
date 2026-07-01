<template>
  <component v-if="toolComponent" :is="toolComponent" />
  <div v-else class="flex items-center justify-center h-full text-slate-400">
    工具加载中...
  </div>
</template>

<script setup lang="ts">
import { ref, watch, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { getToolById, loadTools } from '@/tools/registry'

const route = useRoute()
const toolComponent = shallowRef<any>(null)

async function loadTool(toolId: string) {
  const tool = getToolById(toolId)
  if (!tool) {
    await loadTools()
    const retry = getToolById(toolId)
    if (!retry) {
      toolComponent.value = null
      return
    }
    const mod = await retry.component()
    toolComponent.value = mod.default
    return
  }
  const mod = await tool.component()
  toolComponent.value = mod.default
}

watch(() => route.params.toolId, (id) => {
  if (id && typeof id === 'string') {
    loadTool(id)
  }
}, { immediate: true })
</script>
