import { createWebHashHistory, createRouter } from 'vue-router'

import { bookRoutes } from './bookRouter'
import { sourceRoutes } from './sourceRouter'
import { backupRoutes } from './backupRouter'

const router = createRouter({
  //   history: createWebHistory(process.env.BASE_URL),
  history: createWebHashHistory(),
  routes: ([] as any[]).concat(bookRoutes, sourceRoutes, backupRoutes),
})

router.afterEach(to => {
  if (to.name == 'shelf') document.title = '书架'
  if (to.name == 'backup') document.title = '数据备份'
})

export default router
