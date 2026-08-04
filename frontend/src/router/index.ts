import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { loadTools, getTools } from '@/tools/registry'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          redirect: '/tools/doc-agent',
        },
        {
          path: 'tools/:toolId',
          name: 'tool',
          component: () => import('@/tools/ToolPage.vue'),
          props: true,
        },
      ],
    },
  ],
})

// 守卫：启动时预加载工具注册表
let initialized = false
router.beforeEach(async (to, _from, next) => {
  if (!initialized) {
    await loadTools()
    initialized = true
  }
  // 目标工具不在已启用列表（如文档助手被功能开关关闭，或工具不存在）时，重定向到第一个可用工具
  if (to.path.startsWith('/tools/')) {
    const toolId = to.path.replace('/tools/', '').replace(/\/$/, '')
    const tools = getTools()
    if (!tools.some((t) => t.meta.id === toolId)) {
      const fallback = tools[0]
      if (fallback) {
        next({ path: '/tools/' + fallback.meta.id, replace: true })
        return
      }
    }
  }
  next()
})

export default router
