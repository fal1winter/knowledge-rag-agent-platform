import { createRouter, createWebHistory } from 'vue-router'
import MaterialList from '@/components/material/MaterialList.vue'
import MaterialDetail from '@/components/material/MaterialDetail.vue'
import MaterialUpload from '@/components/material/MaterialUpload.vue'
import MaterialChat from '@/components/material/MaterialChat.vue'
import OrderList from '@/components/material/OrderList.vue'

const routes = [
  { path: '/', redirect: '/materials' },
  { path: '/materials', component: MaterialList },
  { path: '/material/upload', component: MaterialUpload },
  { path: '/material/:id', component: MaterialDetail },
  { path: '/material/:id/chat', component: MaterialChat },
  { path: '/material/chat', component: MaterialChat },
  { path: '/orders', component: OrderList }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
