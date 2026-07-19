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
        <!-- 置顶工具（文档助手等） -->
        <div v-for="tool in pinnedTools" :key="tool.meta.id" class="mb-2">
          <router-link :to="`/tools/${tool.meta.id}`"
            class="flex items-center gap-2.5 px-2 py-2.5 rounded-lg text-sm transition-all duration-150 group relative"
            :class="activeToolId === tool.meta.id ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm ring-1 ring-indigo-200/60' : 'hover:bg-slate-50'"
            :style="activeToolId === tool.meta.id ? {} : { color: 'var(--text-primary)' }"
          >
            <span v-if="activeToolId === tool.meta.id" class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-indigo-500 rounded-full"></span>
            <span class="text-lg flex-shrink-0">{{ tool.meta.icon || '🤖' }}</span>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-semibold truncate">{{ tool.meta.name }}</div>
              <div class="text-[11px] truncate" style="color: var(--text-muted)">{{ tool.meta.description }}</div>
            </div>
          </router-link>
        </div>
        <div v-if="pinnedTools.length > 0" class="border-t mb-2" style="border-color: var(--border-color)"></div>

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
              <!-- 有分组名：可折叠手风琴 -->
              <template v-if="group.name">
                <button
                  @click="toggleGroup(category.key + '/' + group.name)"
                  class="w-full flex items-center gap-2 pl-6 pr-2 py-1.5 mt-1 mb-0.5 rounded text-xs font-semibold hover:bg-slate-100/70 transition-colors"
                  :style="{ color: groupColor(group.name) }"
                >
                  <span class="transform transition-transform duration-200 text-[10px]" :class="{ 'rotate-90': !collapsedGroups.has(category.key + '/' + group.name) }">▶</span>
                  <span class="text-sm flex-shrink-0">{{ groupIcon(group.name) }}</span>
                  <span class="flex-1 text-left">{{ group.name }}</span>
                  <span class="text-[10px] font-normal" style="color: var(--text-muted)">{{ group.tools.length }} 个</span>
                </button>
                <div v-show="!collapsedGroups.has(category.key + '/' + group.name)">
                  <router-link
                    v-for="tool in group.tools" :key="tool.meta.id" :to="`/tools/${tool.meta.id}`"
                    class="flex items-center gap-2.5 pl-9 pr-3 py-2 rounded-lg text-sm transition-all duration-150 group relative"
                    :class="activeToolId === tool.meta.id ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm ring-1 ring-indigo-200/60' : 'hover:bg-slate-50 hover:text-slate-800'"
                    :style="activeToolId === tool.meta.id ? {} : { color: 'var(--text-secondary)' }"
                  >
                    <span v-if="activeToolId === tool.meta.id" class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-indigo-500 rounded-full"></span>
                    <span class="w-1.5 h-1.5 rounded-full flex-shrink-0" :class="{ 'bg-blue-400': tool.meta.category === 'file', 'bg-violet-400': tool.meta.category === 'develop', 'bg-emerald-400': tool.meta.category === 'data' }"></span>
                    <span class="truncate">{{ tool.meta.name }}</span>
                    <span v-if="tool.meta.requiresBackend" class="ml-auto w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" title="需要后端支持"></span>
                  </router-link>
                </div>
              </template>
              <!-- 无分组名：直接列出工具 -->
              <template v-else>
                <router-link
                  v-for="tool in group.tools" :key="tool.meta.id" :to="`/tools/${tool.meta.id}`"
                  class="flex items-center gap-2.5 pl-6 pr-3 py-2 rounded-lg text-sm transition-all duration-150 group relative"
                  :class="activeToolId === tool.meta.id ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm ring-1 ring-indigo-200/60' : 'hover:bg-slate-50 hover:text-slate-800'"
                  :style="activeToolId === tool.meta.id ? {} : { color: 'var(--text-secondary)' }"
                >
                  <span v-if="activeToolId === tool.meta.id" class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-indigo-500 rounded-full"></span>
                  <span class="w-1.5 h-1.5 rounded-full flex-shrink-0" :class="{ 'bg-blue-400': tool.meta.category === 'file', 'bg-violet-400': tool.meta.category === 'develop', 'bg-emerald-400': tool.meta.category === 'data' }"></span>
                  <span class="truncate">{{ tool.meta.name }}</span>
                  <span v-if="tool.meta.requiresBackend" class="ml-auto w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" title="需要后端支持"></span>
                </router-link>
              </template>
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
    <main class="flex-1 overflow-hidden flex flex-col">
      <!-- 顶栏（始终可见） -->
      <header class="px-6 py-2.5 backdrop-blur border-b flex items-center gap-3 flex-shrink-0 transition-colors duration-300"
        style="background: var(--bg-sidebar); border-color: var(--sidebar-border)">
        <button v-if="sidebarCollapsed" @click="sidebarCollapsed = false" class="w-6 h-6 rounded flex items-center justify-center hover:bg-slate-100 text-xs" style="color: var(--text-muted)" title="展开侧边栏">☰</button>
        <template v-if="currentTool">
          <div class="w-7 h-7 rounded-md flex items-center justify-center" :class="{ 'bg-blue-100': currentTool.meta.category === 'file', 'bg-violet-100': currentTool.meta.category === 'develop', 'bg-emerald-100': currentTool.meta.category === 'data' }">
            <span class="text-sm">{{ CATEGORY_CONFIG[currentTool.meta.category].emoji }}</span>
          </div>
          <div>
            <h2 class="text-sm font-semibold" style="color: var(--text-primary)">{{ currentTool.meta.name }}</h2>
            <p class="text-[11px]" style="color: var(--text-muted)">{{ currentTool.meta.description }}</p>
          </div>
        </template>
        <span v-else class="text-sm font-semibold" style="color: var(--text-muted)">选择一个工具开始使用</span>

        <!-- 右侧操作区 -->
        <div class="ml-auto flex items-center gap-2">
          <!-- 主题切换 -->
          <div class="relative">
            <button
              @click.stop="toggleThemeMenu"
              class="w-8 h-8 rounded-lg flex items-center justify-center text-sm transition-all border"
              style="background: var(--bg-card); border-color: var(--border-color)"
              title="切换主题"
            >{{ themeIcon }}</button>

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
        </div>
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
const expandedCategories = ref(new Set<ToolCategory>(['file', 'develop', 'data']))
const collapsedGroups = ref(new Set<string>())  // 折叠的工具包分组 key
function toggleCategory(key: ToolCategory) {
  if (expandedCategories.value.has(key)) expandedCategories.value.delete(key)
  else expandedCategories.value.add(key)
  expandedCategories.value = new Set(expandedCategories.value)
}
function toggleGroup(groupKey: string) {
  if (collapsedGroups.value.has(groupKey)) collapsedGroups.value.delete(groupKey)
  else collapsedGroups.value.add(groupKey)
  collapsedGroups.value = new Set(collapsedGroups.value)
}

/** 分组标题配色（与分类标题 text-secondary 区分） */
const GROUP_COLORS: Record<string, string> = {
  'PDF 工具包': '#6366f1',   // indigo-500
  '图片工具包': '#10b981',   // emerald-500
  'JSON 工具箱': '#8b5cf6',  // violet-500
}
function groupColor(name: string): string {
  return GROUP_COLORS[name] || 'var(--accent-color)'
}

/** 分组图标 */
const GROUP_ICONS: Record<string, string> = {
  'PDF 工具包': '📑',
  '图片工具包': '🖼️',
  'JSON 工具箱': '📦',
}
function groupIcon(name: string): string {
  return GROUP_ICONS[name] || '📁'
}

const tools = computed(() => getTools())
const pinnedTools = computed(() => tools.value.filter(t => t.meta.pinned))
const currentTool = computed(() => tools.value.find((t) => t.meta.id === activeToolId.value))
const totalToolCount = computed(() => tools.value.length)
interface CategoryWithGroups { key: ToolCategory; groups: { name: string | null; tools: typeof tools.value }[] }
const categories = computed<CategoryWithGroups[]>(() => {
  const catOrder: ToolCategory[] = ['file', 'develop', 'data']
  return catOrder.map((key) => {
    const catTools = getToolsByCategory(key).filter(t => !t.meta.pinned)
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
