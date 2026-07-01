<template>
  <div class="flex h-screen overflow-hidden bg-gradient-to-br from-slate-50 via-white to-indigo-50/30">
    <!-- 侧边栏 -->
    <aside class="w-60 bg-white/80 backdrop-blur border-r border-slate-200/60 flex flex-col flex-shrink-0 shadow-sm">
      <!-- Logo -->
      <div class="px-5 py-4 border-b border-slate-100">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center shadow-sm">
            <span class="text-white text-sm">🔧</span>
          </div>
          <div>
            <h1 class="text-base font-bold text-slate-800 leading-tight">工具箱</h1>
            <p class="text-[10px] text-slate-400">Developer Utilities</p>
          </div>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav class="flex-1 overflow-y-auto px-3 py-3 space-y-1">
        <div v-for="category in categories" :key="category.key">
          <!-- 分类标题（可点击折叠） -->
          <button
            @click="toggleCategory(category.key)"
            class="w-full flex items-center gap-2 px-2 py-1.5 mb-0.5 rounded-md text-xs font-semibold transition-colors hover:bg-slate-100/70 group"
          >
            <span
              class="transform transition-transform duration-200 text-slate-400 group-hover:text-slate-600"
              :class="{ 'rotate-90': expandedCategories.has(category.key) }"
            >
              ▶
            </span>
            <span class="flex-1 text-left text-slate-500 uppercase tracking-wider">
              {{ CATEGORY_CONFIG[category.key].emoji }} {{ CATEGORY_CONFIG[category.key].label }}
            </span>
            <span class="text-[10px] text-slate-300">{{ category.tools.length }}</span>
          </button>

          <!-- 工具列表（可折叠） -->
          <div
            v-show="expandedCategories.has(category.key)"
            class="mb-2 ml-2 space-y-0.5 overflow-hidden transition-all duration-200"
          >
            <router-link
              v-for="tool in category.tools"
              :key="tool.meta.id"
              :to="`/tools/${tool.meta.id}`"
              class="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-all duration-150 group relative"
              :class="activeToolId === tool.meta.id
                ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm ring-1 ring-indigo-200/60'
                : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'"
            >
              <!-- 选中指示条 -->
              <span
                v-if="activeToolId === tool.meta.id"
                class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-indigo-500 rounded-full"
              ></span>

              <span class="w-1.5 h-1.5 rounded-full flex-shrink-0"
                :class="{
                  'bg-blue-400': tool.meta.category === 'document',
                  'bg-violet-400': tool.meta.category === 'develop',
                  'bg-emerald-400': tool.meta.category === 'data',
                }"
              ></span>
              <span class="truncate">{{ tool.meta.name }}</span>
              <span
                v-if="tool.meta.requiresBackend"
                class="ml-auto w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0"
                title="需要后端支持"
              ></span>
            </router-link>
          </div>
        </div>
      </nav>

      <!-- 底部信息 -->
      <div class="px-4 py-3 border-t border-slate-100 bg-slate-50/50">
        <p class="text-[10px] text-slate-400 text-center">
          Toolbox v1.0 · {{ totalToolCount }} tools
        </p>
      </div>
    </aside>

    <!-- 内容区 -->
    <main class="flex-1 overflow-hidden flex flex-col">
      <!-- 工具标题栏 -->
      <header
        v-if="currentTool"
        class="px-6 py-3.5 bg-white/70 backdrop-blur border-b border-slate-200/60 flex items-center gap-3 flex-shrink-0"
      >
        <div
          class="w-7 h-7 rounded-md flex items-center justify-center"
          :class="{
            'bg-blue-100': currentTool.meta.category === 'document',
            'bg-violet-100': currentTool.meta.category === 'develop',
            'bg-emerald-100': currentTool.meta.category === 'data',
          }"
        >
          <span class="text-sm">{{ CATEGORY_CONFIG[currentTool.meta.category].emoji }}</span>
        </div>
        <div>
          <h2 class="text-sm font-semibold text-slate-800">{{ currentTool.meta.name }}</h2>
          <p class="text-[11px] text-slate-400">{{ currentTool.meta.description }}</p>
        </div>

        <!-- 当前分类标签 -->
        <span
          class="ml-auto px-2 py-0.5 text-[10px] rounded-full font-medium"
          :class="currentCategoryBadgeClass"
        >
          {{ CATEGORY_CONFIG[currentTool.meta.category].label }}
        </span>
      </header>

      <!-- 无工具选中时的引导页 -->
      <div v-if="!currentTool" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-indigo-100 to-violet-100 flex items-center justify-center">
            <span class="text-2xl">🧰</span>
          </div>
          <h2 class="text-lg font-semibold text-slate-700 mb-1">欢迎使用工具箱</h2>
          <p class="text-sm text-slate-400">从左侧选择一个工具开始使用</p>
        </div>
      </div>

      <!-- 工具内容 -->
      <div v-else class="flex-1 overflow-auto p-6">
        <router-view />
      </div>
    </main>

    <!-- Toast -->
    <ToastNotification />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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

// 默认展开所有分类
const expandedCategories = ref(new Set<ToolCategory>(['document', 'develop', 'data']))

function toggleCategory(key: ToolCategory) {
  if (expandedCategories.value.has(key)) {
    expandedCategories.value.delete(key)
  } else {
    expandedCategories.value.add(key)
  }
  // 触发响应式更新
  expandedCategories.value = new Set(expandedCategories.value)
}

const tools = computed(() => getTools())
const currentTool = computed(() => {
  return tools.value.find((t) => t.meta.id === activeToolId.value)
})
const totalToolCount = computed(() => tools.value.length)

const currentCategoryBadgeClass = computed(() => {
  if (!currentTool.value) return ''
  const category = currentTool.value.meta.category
  return {
    document: 'bg-blue-50 text-blue-600',
    develop: 'bg-violet-50 text-violet-600',
    data: 'bg-emerald-50 text-emerald-600',
  }[category] || ''
})

const categories = computed(() => {
  const catOrder: ToolCategory[] = ['document', 'develop', 'data']
  return catOrder.map((key) => ({
    key,
    tools: getToolsByCategory(key),
  })).filter((c) => c.tools.length > 0)
})
</script>
