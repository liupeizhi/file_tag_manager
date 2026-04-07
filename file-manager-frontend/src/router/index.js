import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    redirect: '/admin/servers',
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'servers',
        name: 'ServerManage',
        component: () => import('@/views/admin/ServerManage.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  
  if (!userStore.initialized) {
    await userStore.fetchCurrentUser()
  }
  
  const isLoggedIn = userStore.isLoggedIn
  const isAdmin = userStore.isAdmin
  
  if (!isLoggedIn && !to.meta.public) {
    next('/login')
  } else if (isLoggedIn && to.meta.public) {
    next('/')
  } else if (!isAdmin && to.meta.requiresAdmin) {
    next('/')
  } else {
    next()
  }
})

export default router