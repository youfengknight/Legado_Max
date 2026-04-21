import { createWebHashHistory, createRouter } from 'vue-router'

export const backupRoutes = [
  {
    path: '/backup',
    name: 'backup',
    component: () => import('../views/BackupManager.vue'),
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: backupRoutes,
})

export default router
