<template>
  <div class="flex h-screen overflow-hidden">
    <!-- 侧边栏 -->
    <aside class="w-60 bg-white border-r border-slate-200 flex flex-col flex-shrink-0">
      <div class="p-4 border-b border-slate-100">
        <h1 class="text-lg font-bold text-slate-800 flex items-center gap-2">
          <span class="text-xl">🧰</span> 工具箱
        </h1>
      </div>
      <nav class="flex-1 overflow-y-auto p-3">
        <div v-for="category in categories" :key="category.key" class="mb-4">
          <h2 class="px-2 mb-1 text-xs font-semibold text-slate-400 uppercase tracking-wider">
            {{ CATEGORY_CONFIG[category.key].emoji }} {{ CATEGORY_CONFIG[category.key].label }}
          </h2>
          <ul>
            <li v-for="tool in category.tools" :key="tool.meta.id">
              <router-link
                :to="`/tools/${tool.meta.id}`"
                class="flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors"
                :class="activeToolId === tool.meta.id
                  ? 'bg-slate-100 text-slate-900 font-medium'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'"
              >
                <span class="text-xs">{{ tool.meta.icon }}</span>
                <span>{{ tool.meta.name }}</span>
                <span v-if="tool.meta.requiresBackend" class="ml-auto text-[10px] text-amber-500" title="需要后端支持">
                  ●
                </span>
              </router-link>
            </li>
          </ul>
        </div>
      </nav>
      <div class="p-3 border-t border-slate-100 text-xs text-slate-400 text-center">
        Toolbox v1.0
      </div>
    </aside>

    <!-- 内容区 -->
    <main class="flex-1 overflow-hidden flex flex-col">
      <!-- 工具标题栏 -->
      <header v-if="currentTool" class="px-6 py-3 bg-white border-b border-slate-100 flex items-center gap-3 flex-shrink-0">
        <h2 class="text-base font-semibold text-slate-800">{{ currentTool.meta.name }}</h2>
        <span class="text-xs text-slate-400">{{ currentTool.meta.description }}</span>
      </header>

      <!-- 工具内容 -->
      <div class="flex-1 overflow-auto p-6">
        <router-view />
      </div>
    </main>

    <!-- Toast 通知 -->
    <ToastNotification />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getTools, getToolsByCategory } from '@/tools/registry'
import { CATEGORY_CONFIG } from '@/tools/types'
import type { ToolCategory } from '@/tools/types'
import ToastNotification from '@/components/ToastNotification.vue'

const route = useRoute()

const activeToolId = computed(() => {
  const id = route.params.toolId
  return typeof id === 'string' ? id : id[0]
})

const tools = computed(() => getTools())
const currentTool = computed(() => {
  return tools.value.find((t) => t.meta.id === activeToolId.value)
})

const categories = computed(() => {
  const catOrder: ToolCategory[] = ['document', 'develop', 'data']
  return catOrder.map((key) => ({
    key,
    tools: getToolsByCategory(key),
  })).filter((c) => c.tools.length > 0)
})
</script>
