<template>
  <div id="app">
    <a-layout class="app-shell">
      <a-layout-header class="app-header">
        <div class="brand" @click="$router.push('/materials')">Knowledge RAG Agent</div>
        <a-menu mode="horizontal" theme="dark" :selectedKeys="selectedKeys">
          <a-menu-item key="materials" @click="$router.push('/materials')">资料库</a-menu-item>
          <a-menu-item key="upload" @click="$router.push('/material/upload')">上传资料</a-menu-item>
          <a-menu-item key="orders" @click="$router.push('/orders')">订单</a-menu-item>
          <a-menu-item key="chat" @click="$router.push('/material/chat')">资料对话</a-menu-item>
        </a-menu>
      </a-layout-header>
      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </div>
</template>

<script>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'App',
  setup() {
    const route = useRoute()
    const selectedKeys = computed(() => {
      if (route.path.startsWith('/orders')) return ['orders']
      if (route.path.includes('/chat')) return ['chat']
      if (route.path.includes('/upload')) return ['upload']
      return ['materials']
    })
    return { selectedKeys }
  }
}
</script>
