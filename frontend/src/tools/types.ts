/** 工具分类 */
export type ToolCategory = 'document' | 'develop' | 'data'

/** 工具元信息 — 每个工具组件的导出规范 */
export interface ToolMeta {
  /** 唯一标识，自动取自文件夹名 */
  id: string
  /** 显示名称 */
  name: string
  /** 一句话描述 */
  description: string
  /** Lucide 图标名（如 'file-code', 'file-text'） */
  icon: string
  /** 所属分类 */
  category: ToolCategory
  /** 是否需要后端支持 */
  requiresBackend?: boolean
}

/** 完整工具定义（meta + 组件加载器） */
export interface ToolDefinition {
  meta: ToolMeta
  component: () => Promise<{ default: any }>
}

/** 分类显示配置 */
export const CATEGORY_CONFIG: Record<ToolCategory, { label: string; emoji: string }> = {
  document: { label: '文档工具', emoji: '📄' },
  develop: { label: '开发辅助', emoji: '💻' },
  data: { label: '数据处理', emoji: '📊' },
}
