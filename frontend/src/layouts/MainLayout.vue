<template>
  <div class="flex h-screen overflow-hidden transition-colors duration-300" style="background: var(--bg-main)">
    <!-- 侧边栏 -->
    <aside
      class="backdrop-blur border-r flex flex-col flex-shrink-0 transition-all duration-300"
      style="background: var(--bg-sidebar); border-color: var(--sidebar-border); box-shadow: var(--shadow-sm)"
      :class="sidebarCollapsed ? 'w-12' : 'w-60'"
    >
      <!-- Logo / 展开按钮 -->
      <div class="px-3 py-4 border-b flex items-center" :class="sidebarCollapsed ? 'justify-center' : ''" style="border-color: var(--border-color)">
        <button
          @click="sidebarCollapsed = !sidebarCollapsed"
          class="w-8 h-8 rounded-lg flex items-center justify-center transition-colors hover:bg-slate-100 flex-shrink-0"
          :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        >
          <span class="text-sm">{{ sidebarCollapsed ? '☰' : '◀' }}</span>
        </button>
        <template v-if="!sidebarCollapsed">
          <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center shadow-sm ml-2.5">
            <span class="text-white text-sm">🔧</span>
          </div>
          <div class="ml-2.5">
            <h1 class="text-base font-bold leading-tight" style="color: var(--text-primary)">工具箱</h1>
            <p class="text-[10px]" style="color: var(--text-muted)">Dev Utils</p>
          </div>
        </template>
      </div>

      <!-- 导航菜单（收起时隐藏） -->
      <nav v-if="!sidebarCollapsed" class="flex-1 overflow-y-auto px-3 py-3 space-y-1">
        <div v-for="category in categories" :key="category.key">
          <button
            @click="toggleCategory(category.key)"
            class="w-full flex items-center gap-2 px-2 py-1.5 mb-0.5 rounded-md text-xs font-semibold transition-colors hover:bg-slate-100/70 group"
          >
            <span class="transform transition-transform duration-200 group-hover:opacity-80" style="color: var(--text-muted)" :class="{ 'rotate-90': expandedCategories.has(category.key) }">▶</span>
            <span class="flex-1 text-left uppercase tracking-wider" style="color: var(--text-secondary)">{{ CATEGORY_CONFIG[category.key].emoji }} {{ CATEGORY_CONFIG[category.key].label }}</span>
            <span class="text-[10px]" style="color: var(--text-muted)">{{ category.groups.reduce((s: number, g: any) => s + g.tools.length, 0) }}</span>
          </button>

          <div v-show="expandedCategories.has(category.key)" class="mb-2 overflow-hidden transition-all duration-200">
            <template v-for="group in category.groups" :key="group.name || '_ungrouped'">
              <div v-if="group.name" class="flex items-center gap-1.5 px-3 py-1 mt-1 mb-0.5 text-[10px] font-semibold uppercase tracking-wider" style="color: var(--text-muted)">
                <span class="w-0.5 h-3 rounded-full" style="background: var(--text-muted); opacity: 0.5"></span>{{ group.name }}
              </div>
              <router-link
                v-for="tool in group.tools" :key="tool.meta.id" :to="`/tools/${tool.meta.id}`"
                class="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-all duration-150 group relative"
                :class="activeToolId === tool.meta.id ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm ring-1 ring-indigo-200/60' : 'hover:bg-slate-50 hover:text-slate-800'"
                :style="[activeToolId === tool.meta.id ? {} : { color: 'var(--text-secondary)' }, group.name ? { marginLeft: '12px' } : {}]"
              >
                <span v-if="activeToolId === tool.meta.id" class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-indigo-500 rounded-full"></span>
                <span class="w-1.5 h-1.5 rounded-full flex-shrink-0" :class="{ 'bg-blue-400': tool.meta.category === 'document', 'bg-violet-400': tool.meta.category === 'develop', 'bg-emerald-400': tool.meta.category === 'data' }"></span>
                <span class="truncate">{{ tool.meta.name }}</span>
                <span v-if="tool.meta.requiresBackend" class="ml-auto w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" title="需要后端支持"></span>
              </router-link>
            </template>
          </div>
        </div>
      </nav>

      <!-- 底部信息（收起时隐藏） -->
      <div v-if="!sidebarCollapsed" class="px-4 py-3 border-t transition-colors duration-300"
        style="background: var(--bg-card-hover); border-color: var(--border-color)">
        <p class="text-[10px] text-center" style="color: var(--text-muted)">Toolbox v1.0 · {{ totalToolCount }} tools</p>
      </div>
    </aside>

    <!-- 内容区 -->
    <main class="flex-1 overflow-hidden flex flex-col relative">
      <!-- 主题切换（始终可见） -->
      <div class="absolute top-3 right-4 z-40">
        <button
          @click="toggleThemeMenu"
          class="w-8 h-8 rounded-lg flex items-center justify-center text-sm transition-all shadow-sm border"
          style="background: var(--bg-card); border-color: var(--border-color)"
          title="切换主题"
        >{{ themeIcon }}</button>

        <!-- 下拉菜单 -->
        <div
          v-if="themeMenuOpen"
          class="absolute right-0 top-full mt-1.5 w-44 rounded-lg shadow-lg border z-50 overflow-hidden transition-colors duration-300"
          style="background: var(--bg-card); border-color: var(--border-color); box-shadow: 0 4px 16px rgba(0,0,0,0.12)"
        >
          <button
            v-for="opt in themeOptions"
            :key="opt.key"
            @click="selectTheme(opt.key)"
            class="w-full flex items-center gap-2.5 px-3 py-2 text-xs transition-colors hover:bg-slate-50"
            :class="theme === opt.key ? 'font-semibold' : ''"
            :style="{ color: 'var(--text-primary)' }"
          >
            <span class="w-4 h-4 rounded-full border flex-shrink-0" :style="{ background: opt.color, borderColor: 'var(--border-color)' }"></span>
            <span>{{ opt.icon }} {{ opt.label }}</span>
            <span v-if="theme === opt.key" class="ml-auto text-xs" style="color: var(--accent-color)">✓</span>
          </button>
        </div>
      </div>
      <header v-if="currentTool" class="px-6 py-3.5 backdrop-blur border-b flex items-center gap-3 flex-shrink-0 transition-colors duration-300"
        style="background: var(--bg-sidebar); border-color: var(--sidebar-border)">
        <button v-if="sidebarCollapsed" @click="sidebarCollapsed = false" class="w-6 h-6 rounded flex items-center justify-center hover:bg-slate-100 text-xs" style="color: var(--text-muted)" title="展开侧边栏">☰</button>
        <div class="w-7 h-7 rounded-md flex items-center justify-center" :class="{ 'bg-blue-100': currentTool.meta.category === 'document', 'bg-violet-100': currentTool.meta.category === 'develop', 'bg-emerald-100': currentTool.meta.category === 'data' }">
          <span class="text-sm">{{ CATEGORY_CONFIG[currentTool.meta.category].emoji }}</span>
        </div>
        <div>
          <h2 class="text-sm font-semibold" style="color: var(--text-primary)">{{ currentTool.meta.name }}</h2>
          <p class="text-[11px]" style="color: var(--text-muted)">{{ currentTool.meta.description }}</p>
        </div>
        <span class="ml-auto px-2 py-0.5 text-[10px] rounded-full font-medium" :class="currentCategoryBadgeClass">{{ CATEGORY_CONFIG[currentTool.meta.category].label }}</span>
      </header>

      <div v-if="!currentTool" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-indigo-100 to-violet-100 flex items-center justify-center"><span class="text-2xl">🧰</span></div>
          <h2 class="text-lg font-semibold mb-1" style="color: var(--text-primary)">欢迎使用工具箱</h2>
          <p class="text-sm" style="color: var(--text-muted)">从左侧选择一个工具开始使用</p>
        </div>
      </div>

      <div v-else class="flex-1 overflow-hidden p-6">
        <router-view />
      </div>
    </main>

    <ToastNotification />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTools, getToolsByCategory } from '@/tools/registry'
import { CATEGORY_CONFIG } from '@/tools/types'
import type { ToolCategory } from '@/tools/types'
import ToastNotification from '@/components/ToastNotification.vue'
import { useTheme } from '@/composables/useTheme'
import type { Theme } from '@/composables/useTheme'

const route = useRoute()
const sidebarCollapsed = ref(false)

const { theme, themeIcon, setTheme } = useTheme()
const themeMenuOpen = ref(false)

const themeOptions: { key: Theme; label: string; icon: string; color: string }[] = [
  { key: 'default', label: '默认白', icon: '🌞', color: '#f8fafc' },
  { key: 'green', label: '护眼绿', icon: '🌿', color: '#e8f0e8' },
  { key: 'warm', label: '暖色奶油', icon: '☕', color: '#fdf3e4' },
  { key: 'dark', label: '深色暗夜', icon: '🌙', color: '#1a1a2e' },
  { key: 'gray', label: '浅灰柔白', icon: '🩶', color: '#eeeeee' },
]

function toggleThemeMenu() {
  themeMenuOpen.value = !themeMenuOpen.value
}

function selectTheme(t: Theme) {
  setTheme(t)
  themeMenuOpen.value = false
}

function handleClickOutside() {
  if (themeMenuOpen.value) themeMenuOpen.value = false
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

const activeToolId = computed(() => { const id = route.params.toolId; return typeof id === 'string' ? id : id[0] })
const expandedCategories = ref(new Set<ToolCategory>(['document', 'develop', 'data']))
function toggleCategory(key: ToolCategory) {
  if (expandedCategories.value.has(key)) expandedCategories.value.delete(key)
  else expandedCategories.value.add(key)
  expandedCategories.value = new Set(expandedCategories.value)
}

const tools = computed(() => getTools())
const currentTool = computed(() => tools.value.find((t) => t.meta.id === activeToolId.value))
const totalToolCount = computed(() => tools.value.length)
const currentCategoryBadgeClass = computed(() => {
  if (!currentTool.value) return ''
  return { document: 'bg-blue-50 text-blue-600', develop: 'bg-violet-50 text-violet-600', data: 'bg-emerald-50 text-emerald-600' }[currentTool.value.meta.category] || ''
})

interface CategoryWithGroups { key: ToolCategory; groups: { name: string | null; tools: typeof tools.value }[] }
const categories = computed<CategoryWithGroups[]>(() => {
  const catOrder: ToolCategory[] = ['document', 'develop', 'data']
  return catOrder.map((key) => {
    const catTools = getToolsByCategory(key)
    const groupMap = new Map<string | null, typeof catTools>()
    for (const t of catTools) {
      const g = t.meta.group || null
      if (!groupMap.has(g)) groupMap.set(g, [])
      groupMap.get(g)!.push(t)
    }
    const groups = Array.from(groupMap.entries())
    groups.sort(([a], [b]) => { if (a === null) return 1; if (b === null) return -1; return a.localeCompare(b) })
    return { key, groups: groups.map(([name, tools]) => ({ name, tools })) }
  }).filter((c) => c.groups.length > 0 && c.groups.some(g => g.tools.length > 0))
})
</script>
