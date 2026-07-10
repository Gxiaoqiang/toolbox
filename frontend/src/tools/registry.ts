/// <reference types="vite/client" />

import type { ToolDefinition, ToolMeta, ToolCategory } from './types'

/**
 * 工具注册中心 — 使用 Vite glob 自动发现所有工具
 *
 * 约定：每个工具在 src/tools/<id>/index.vue 中，导出 defineComponent + meta 对象
 */
const toolModules = import.meta.glob<{ default: any; meta?: ToolMeta }>('./*/index.vue')

/** 已注册的工具列表（懒加载） */
let toolsCache: ToolDefinition[] | null = null

/** 加载并解析所有工具 */
export async function loadTools(): Promise<ToolDefinition[]> {
  if (toolsCache !== null) {
    return toolsCache
  }

  const tools: ToolDefinition[] = []

  for (const [path, loader] of Object.entries(toolModules)) {
    const pathParts = path.split('/')
    const toolId = pathParts[pathParts.length - 2]

    const mod = await loader()
    const component = mod.default

    if (!component) {
      console.warn(`[registry] 工具 ${toolId} 缺少 default 导出，跳过`)
      continue
    }

    // 优先读取模块级 export meta，其次读取 component.meta，最后用默认值
    const meta: ToolMeta = mod.meta || component.meta || {
      id: toolId,
      name: toolId,
      description: '',
      icon: 'wrench',
      category: 'develop',
    }

    tools.push({
      meta: { ...meta, id: toolId },
      component: () => loader(),
    })
  }

  toolsCache = tools
  return tools
}

/** 获取工具列表（同步，需先调用 loadTools） */
export function getTools(): ToolDefinition[] {
  return toolsCache || []
}

/** 按分类获取工具 */
export function getToolsByCategory(category: ToolCategory): ToolDefinition[] {
  return (toolsCache || []).filter((t) => t.meta.category === category)
}

/** 根据 id 获取工具 */
export function getToolById(id: string): ToolDefinition | undefined {
  return (toolsCache || []).find((t) => t.meta.id === id)
}
