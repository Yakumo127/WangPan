import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    redirect: to => ({
      path: '/login',
      query: { mode: 'register' }
    }),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    meta: { title: '首页', requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页', requiresAuth: true }
      },
      {
        path: '/main',
        name: 'Main',
        component: () => import('@/views/Home.vue'),
        meta: { title: '主界面', requiresAuth: true }
      },
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', requiresAuth: true }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人资料', requiresAuth: true }
      },
      {
        path: '/files',
        name: 'Files',
        component: () => import('@/views/files/List.vue'),
        meta: { title: '文件管理', requiresAuth: true }
      },
      {
        path: '/folders',
        name: 'Folders',
        component: () => import('@/views/folders/List.vue'),
        meta: { title: '文件夹管理', requiresAuth: true }
      },
      {
        path: '/share',
        name: 'Share',
        component: () => import('@/views/Share.vue'),
        meta: { title: '我的分享', requiresAuth: true }
      },
      {
        path: '/recycle',
        name: 'Recycle',
        component: () => import('@/views/Recycle.vue'),
        meta: { title: '回收站', requiresAuth: true }
      },
      {
        path: '/admin/files',
        name: 'AdminFiles',
        component: () => import('@/views/admin/Files.vue'),
        meta: { title: '文件管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: '/admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: '/admin/system',
        name: 'System',
        component: () => import('@/views/admin/System.vue'),
        meta: { title: '系统设置', requiresAuth: true, requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 文件管理系统` : '文件管理系统'
  
  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    // 需要认证的页面
    if (!authStore.isAuthenticated) {
      // 未登录，重定向到登录页
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }
    
    // 如果有token但没有用户信息，尝试获取用户信息
    if (authStore.token && !authStore.user) {
      try {
        await authStore.fetchUserInfo()
      } catch (error) {
        // 获取用户信息失败，可能是token过期，清除token并跳转到登录页
        authStore.logout()
        next({
          path: '/login',
          query: { redirect: to.fullPath }
        })
        return
      }
    }
    
    // 检查是否需要管理员权限
    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      // 非管理员尝试访问管理员页面
      next({ path: '/' })
      return
    }
  } else {
    // 不需要认证的页面（登录页、注册页）
    // 如果已经登录，重定向到首页
    if (authStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
      next({ path: '/' })
      return
    }
  }
  
  next()
})

export default router