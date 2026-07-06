import { ref, computed, type Ref, type ComputedRef } from 'vue'

export type Theme = 'default' | 'green' | 'warm' | 'dark' | 'gray'

const THEME_LABELS: Record<Theme, string> = {
  default: '默认白',
  green: '护眼绿',
  warm: '暖色奶油',
  dark: '深色暗夜',
  gray: '浅灰柔白',
}

const THEME_ICONS: Record<Theme, string> = {
  default: '🌞',
  green: '🌿',
  warm: '☕',
  dark: '🌙',
  gray: '🩶',
}

const STORAGE_KEY = 'toolbox-theme'

function loadTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && Object.keys(THEME_LABELS).includes(stored)) return stored as Theme
  } catch { /* localStorage 不可用 */ }
  return 'default'
}

function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}

function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch { /* localStorage 不可用 */ }
}

// 单例状态
const theme = ref<Theme>(loadTheme())

// 初始化时应用主题
applyTheme(theme.value)

export function useTheme(): {
  theme: Ref<Theme>
  themeLabel: ComputedRef<string>
  themeIcon: ComputedRef<string>
  setTheme: (t: Theme) => void
} {
  const themeLabel = computed(() => THEME_LABELS[theme.value])
  const themeIcon = computed(() => THEME_ICONS[theme.value])

  function setTheme(t: Theme): void {
    theme.value = t
    applyTheme(t)
    saveTheme(t)
  }

  return { theme, themeLabel, themeIcon, setTheme }
}
