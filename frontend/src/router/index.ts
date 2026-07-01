import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { loadTools } from '@/tools/registry'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          redirect: '/tools/md-to-html',
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
router.beforeEach(async (_to, _from, next) => {
  if (!initialized) {
    await loadTools()
    initialized = true
  }
  next()
})

export default router
