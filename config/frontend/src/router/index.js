import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import Layout from '@/layout/index.vue'

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
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Dashboard', requiresAuth: true }
      }
    ]
  },
  {
    path: '/files',
    component: Layout,
    redirect: '/files/list',
    children: [
      {
        path: 'list',
        name: 'FileList',
        component: () => import('@/views/files/List.vue'),
        meta: { title: '文件管理', icon: 'Files', requiresAuth: true }
      }
    ]
  },
  {
    path: '/folders',
    component: Layout,
    redirect: '/folders/list',
    children: [
      {
        path: 'list',
        name: 'FolderList',
        component: () => import('@/views/folders/List.vue'),
        meta: { title: '文件夹管理', icon: 'Folder', requiresAuth: true }
      }
    ]
  },
  {
    path: '/profile',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', icon: 'User', requiresAuth: true }
      }
    ]
  },
  {
    path: '/admin',
    component: Layout,
    redirect: '/admin/users',
    meta: { title: '管理后台', icon: 'Setting', requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'users',
        name: 'UserManagement',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', icon: 'User', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'files',
        name: 'FileManagement',
        component: () => import('@/views/admin/Files.vue'),
        meta: { title: '文件管理', icon: 'Files', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system',
        name: 'SystemSettings',
        component: () => import('@/views/admin/System.vue'),
        meta: { title: '系统设置', icon: 'Setting', requiresAuth: true, requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 文件管理系统` : '文件管理系统'
  
  // 检查是否需要认证
  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) {
      next('/login')
      return
    }
    
    // 检查是否需要管理员权限
    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      next('/dashboard')
      return
    }
  }
  
  // 如果已登录，访问登录页则跳转到首页
  if (authStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
    next('/dashboard')
    return
  }
  
  next()
})

export default router