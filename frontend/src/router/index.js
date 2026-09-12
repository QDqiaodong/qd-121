import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据概览' }
  },
  {
    path: '/pad',
    name: 'PadManagement',
    component: () => import('@/views/PadManagement.vue'),
    meta: { title: '垫板档案管理' }
  },
  {
    path: '/pad/import',
    name: 'PadImport',
    component: () => import('@/views/PadImport.vue'),
    meta: { title: '垫板档案批量导入' }
  },
  {
    path: '/shelf',
    name: 'ShelfManagement',
    component: () => import('@/views/ShelfManagement.vue'),
    meta: { title: '货架层位管理' }
  },
  {
    path: '/layer-block',
    name: 'LayerBlock',
    component: () => import('@/views/LayerBlock.vue'),
    meta: { title: '层位封锁管理' }
  },
  {
    path: '/borrow',
    name: 'BorrowReturn',
    component: () => import('@/views/BorrowReturn.vue'),
    meta: { title: '垫板领用归还' }
  },
  {
    path: '/maintenance',
    name: 'PadMaintenance',
    component: () => import('@/views/PadMaintenance.vue'),
    meta: { title: '垫板保养台账' }
  },
  {
    path: '/scrap',
    name: 'ScrapOutbound',
    component: () => import('@/views/ScrapOutbound.vue'),
    meta: { title: '垫板报废出库' }
  },
  {
    path: '/record',
    name: 'AdjustRecord',
    component: () => import('@/views/AdjustRecord.vue'),
    meta: { title: '调整记录台账' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 冲压车间垫板货架层位绑定管理系统`
    : '冲压车间垫板货架层位绑定管理系统'
  next()
})

export default router
