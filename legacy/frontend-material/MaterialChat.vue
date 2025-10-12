<template>
  <div class="material-chat-container">
    <div class="chat-layout">
      <div class="sessions-sidebar">
        <div class="sidebar-header">
          <h3>对话历史</h3>
          <a-button type="primary" size="small" @click="createNewSession">
            <template #icon><i class="fa fa-plus"></i></template>
            新对话
          </a-button>
        </div>
        <div class="sessions-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: currentSessionId === session.id }"
            @click="switchSession(session.id)"
          >
            <div class="session-meta">
              <div class="session-title">{{ session.title || '新对话' }}</div>
              <div class="session-time">{{ formatTime(session.updateTime) }}</div>
            </div>
            <a-button
              type="text"
              size="small"
              danger
              @click.stop="deleteSession(session.id)"
            >
              <i class="fa fa-trash"></i>
            </a-button>
          </div>
          <a-empty v-if="sessions.length === 0" :image="false" description="暂无历史会话" />
        </div>
      </div>

      <div class="chat-main">
        <div class="chat-header">
          <div class="header-text">
            <h2>资料对话</h2>
            <p v-if="currentMaterial">当前资料：{{ currentMaterial.title }}</p>
            <p v-else>未选择资料，将检索你的所有可访问资料</p>
          </div>
          <a-space>
            <a-button @click="openSelector">
              <template #icon><i class="fa fa-database"></i></template>
              {{ currentMaterial ? '切换资料' : '选择资料（可选）' }}
            </a-button>
            <a-button v-if="currentMaterial" type="link" @click="clearMaterial">
              清除选择
            </a-button>
            <a-button type="link" @click="goToDetail" :disabled="!currentMaterial">
              查看资料详情
            </a-button>
          </a-space>
        </div>

        <div class="chat-messages" ref="messagesContainer">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <img
                v-if="msg.role === 'user'"
                :src="userAvatar"
                alt="User"
              />
              <i v-else class="fa fa-robot"></i>
            </div>
            <div class="message-content">
              <div class="message-text" v-if="msg.streaming" style="white-space: pre-wrap;">{{ msg.content }}</div>
              <div class="message-text" v-else v-html="formatMessage(msg.content)"></div>

              <!-- Sources 展示 -->
              <div v-if="msg.sources && msg.sources.length > 0" class="message-sources">
                <div class="sources-header">
                  <i class="fa fa-link"></i>
                  <span>参考来源 ({{ msg.sources.length }})</span>
                </div>
                <div class="sources-list">
                  <div
                    v-for="(source, sIndex) in msg.sources"
                    :key="sIndex"
                    class="source-item"
                  >
                    <div class="source-header" @click="toggleSource(index, sIndex)">
                      <div class="source-info">
                        <span class="source-index">[{{ sIndex + 1 }}]</span>
                        <span class="source-material" @click.stop="goToMaterial(source.material_id)">
                          {{ getMaterialTitle(source.material_id) }}
                        </span>
                        <span class="source-score">{{ (source.relevance * 100).toFixed(0) }}%</span>
                      </div>
                      <i class="fa" :class="isSourceExpanded(index, sIndex) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                    </div>
                    <div v-if="isSourceExpanded(index, sIndex)" class="source-content">
                      {{ source.content }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- 推荐资料展示 -->
              <div v-if="msg.recommendations && msg.recommendations.length > 0" class="message-recommendations">
                <div class="recommendations-header">
                  <i class="fa fa-lightbulb"></i>
                  <span>相关推荐</span>
                </div>
                <div class="recommendations-list">
                  <div
                    v-for="rec in msg.recommendations"
                    :key="rec.id"
                    class="recommendation-card"
                    @click="goToMaterial(rec.id)"
                  >
                    <div v-if="rec.coverUrl" class="rec-cover">
                      <img :src="rec.coverUrl" :alt="rec.title" />
                    </div>
                    <div class="rec-info">
                      <div class="rec-title">{{ rec.title }}</div>
                      <div class="rec-desc">{{ rec.description }}</div>
                      <div class="rec-meta">
                        <span class="rec-reason">{{ rec.reason }}</span>
                        <span class="rec-price">{{ rec.price > 0 ? `${rec.price}积分` : '免费' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>
          </div>

          <div v-if="isLoading" class="message-item assistant">
            <div class="message-avatar">
              <i class="fa fa-robot"></i>
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <a-textarea
            v-model:value="inputMessage"
            placeholder="输入你的问题..."
            :rows="3"
            @keydown.enter.ctrl="handleSend"
          />
          <div class="input-actions">
            <span class="input-tip">Ctrl + Enter 发送</span>
            <a-space>
              <a-button @click="handleClear">清空</a-button>
              <a-button
                type="primary"
                @click="handleSend"
                :loading="isLoading"
                :disabled="!inputMessage.trim()"
              >
                <template #icon><i class="fa fa-paper-plane"></i></template>
                发送
              </a-button>
            </a-space>
          </div>
        </div>
      </div>
    </div>

    <a-drawer
      v-model:visible="selectorVisible"
      title="选择资料"
      width="420"
      :destroyOnClose="false"
    >
      <a-input-search
        v-model:value="materialSearchKeyword"
        placeholder="搜索资料标题"
        allow-clear
        style="margin-bottom: 12px"
      />

      <a-spin :spinning="materialLoading">
        <div class="material-select-list">
          <div
            v-for="material in filteredMaterials"
            :key="material.id"
            class="material-select-item"
            :class="{ selected: currentMaterialId === material.id }"
            @click="selectMaterial(material.id)"
          >
            <div class="material-select-text">
              <div class="material-select-title">{{ material.title }}</div>
              <div class="material-select-desc">{{ material.description || '暂无简介' }}</div>
            </div>
          </div>
          <a-empty
            v-if="!materialLoading && filteredMaterials.length === 0"
            :image="false"
            description="暂无可选资料"
          />
        </div>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script>
import { ref, onMounted, nextTick, computed, watch, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import ragService from '@/service/ragService'
import materialService from '@/service/materialService'
import orderService from '@/service/orderService'

const STORAGE_KEY_MATERIAL = 'material_chat_material_id'
const STORAGE_KEY_SESSION = 'material_chat_session_id'

export default {
  name: 'MaterialChat',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const store = useStore()
    const messagesContainer = ref(null)
    const inputMessage = ref('')
    const isLoading = ref(false)
    const messages = ref([])
    const sessions = ref([])
    const currentSessionId = ref(null)
    const userAvatar = computed(() => store.state.picture || '/default-avatar.png')

    const selectorVisible = ref(false)
    const materialLoading = ref(false)
    const materialSearchKeyword = ref('')
    const allMaterials = ref([])
    const currentMaterialId = ref(null)
    const expandedSources = ref(new Set())

    const currentMaterial = computed(() => {
      if (!currentMaterialId.value) return null
      return allMaterials.value.find(m => m.id === currentMaterialId.value) || null
    })

    const routeMaterialId = computed(() => {
      const raw = route.params.id
      if (raw === undefined || raw === null || raw === '') return null
      const id = Number(raw)
      return Number.isFinite(id) ? id : null
    })

    const formatMessage = (content) => marked(content || '')

    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      if (Number.isNaN(date.getTime())) return ''
      const now = Date.now()
      const diff = now - date.getTime()
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
      return date.toLocaleDateString('zh-CN')
    }

    const getMaterialTitle = (materialId) => {
      if (!materialId) return '未知资料'
      const material = allMaterials.value.find(m => m.id === materialId)
      return material ? material.title : `资料 #${materialId}`
    }

    const toggleSource = (msgIndex, sourceIndex) => {
      const key = `${msgIndex}-${sourceIndex}`
      if (expandedSources.value.has(key)) {
        expandedSources.value.delete(key)
      } else {
        expandedSources.value.add(key)
      }
    }

    const isSourceExpanded = (msgIndex, sourceIndex) => {
      return expandedSources.value.has(`${msgIndex}-${sourceIndex}`)
    }

    const goToMaterial = (materialId) => {
      if (materialId) {
        router.push(`/material/${materialId}`)
      }
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    const filteredMaterials = computed(() => {
      const keyword = materialSearchKeyword.value.trim().toLowerCase()
      if (!keyword) return allMaterials.value
      return allMaterials.value.filter((material) =>
        (material.title || '').toLowerCase().includes(keyword) ||
        (material.description || '').toLowerCase().includes(keyword)
      )
    })

    const normalizeMaterial = (item) => {
      const id = Number(item.id || item.materialId)
      if (!Number.isFinite(id)) return null
      return {
        id,
        title: item.title || item.materialTitle || `资料${id}`,
        description: item.description || item.materialDescription || ''
      }
    }

    const extractList = (data) => {
      if (Array.isArray(data)) return data
      if (Array.isArray(data?.list)) return data.list
      return []
    }

    const loadSelectableMaterials = async () => {
      materialLoading.value = true
      try {
        const [purchasedRes, myRes, orderRes] = await Promise.allSettled([
          materialService.purchasedMaterials(1, 200),
          materialService.myMaterials(1, 200),
          orderService.myOrders({ pageNum: 1, pageSize: 200, status: 1 })
        ])

        const materialMap = new Map()

        if (purchasedRes.status === 'fulfilled' && purchasedRes.value?.code === 0) {
          extractList(purchasedRes.value.data).forEach((item) => {
            const normalized = normalizeMaterial(item)
            if (normalized) materialMap.set(normalized.id, normalized)
          })
        }

        if (myRes.status === 'fulfilled' && myRes.value?.code === 0) {
          extractList(myRes.value.data).forEach((item) => {
            const normalized = normalizeMaterial(item)
            if (normalized) materialMap.set(normalized.id, normalized)
          })
        }

        if (orderRes.status === 'fulfilled' && orderRes.value?.code === 0) {
          extractList(orderRes.value.data).forEach((order) => {
            const normalized = normalizeMaterial({
              id: order.materialId,
              title: order.materialTitle,
              description: order.materialDescription
            })
            if (normalized) materialMap.set(normalized.id, normalized)
          })
        }

        allMaterials.value = Array.from(materialMap.values())
      } catch (error) {
        console.error('加载可选资料失败', error)
      } finally {
        materialLoading.value = false
      }
    }

    const loadSessions = async () => {
      // materialId 为 null 时不加载，为 0 时表示跨资料对话，需要加载
      if (currentMaterialId.value === null || currentMaterialId.value === undefined) {
        sessions.value = []
        return
      }

      try {
        // 使用 0 表示跨资料对话
        const materialId = currentMaterialId.value || 0
        const res = await ragService.getSessions(materialId)
        if (res.code === 0 && Array.isArray(res.data)) {
          sessions.value = res.data.map(s => ({
            id: s.session_id || s.sessionId || s.id,
            title: s.title || '新对话',
            updateTime: s.updated_at || s.updatedAt || s.updateTime || s.created_at
          }))
        } else {
          sessions.value = []
        }
      } catch (error) {
        console.error('加载会话列表失败', error)
        sessions.value = []
      }
    }

    const loadChatHistory = async () => {
      // materialId 可以是 0（跨资料对话），但必须有 sessionId
      if (currentMaterialId.value === null || currentMaterialId.value === undefined || !currentSessionId.value) {
        messages.value = []
        return
      }

      try {
        // 使用 0 表示跨资料对话
        const materialId = currentMaterialId.value
        const res = await ragService.getChatHistory(materialId, currentSessionId.value)
        if (res.code === 0 && Array.isArray(res.data)) {
          messages.value = res.data.map(msg => ({
            role: msg.role === 'user' ? 'user' : 'assistant',
            content: msg.content || msg.message || '',
            sources: msg.sources || [],
            timestamp: msg.timestamp || msg.created_at || new Date().toISOString()
          }))
        } else {
          messages.value = []
        }
        scrollToBottom()
      } catch (error) {
        console.error('加载对话历史失败', error)
        messages.value = []
      }
    }

    const selectMaterial = async (materialId) => {
      if (currentMaterialId.value === materialId) {
        selectorVisible.value = false
        return
      }

      currentMaterialId.value = materialId
      localStorage.setItem(STORAGE_KEY_MATERIAL, String(materialId))

      currentSessionId.value = null
      localStorage.removeItem(STORAGE_KEY_SESSION)
      messages.value = []

      await loadSessions()
      selectorVisible.value = false
      message.success('已切换资料')
    }

    const createNewSession = () => {
      currentSessionId.value = null
      localStorage.removeItem(STORAGE_KEY_SESSION)
      messages.value = []
    }

    const switchSession = async (sessionId) => {
      if (!sessionId) return
      currentSessionId.value = sessionId
      localStorage.setItem(STORAGE_KEY_SESSION, sessionId)
      await loadChatHistory()
    }

    const deleteSession = async (sessionId) => {
      // materialId 可以是 0（跨资料对话）
      if (currentMaterialId.value === null || currentMaterialId.value === undefined) return

      try {
        const materialId = currentMaterialId.value
        await ragService.deleteSession(materialId, sessionId)
        sessions.value = sessions.value.filter((session) => session.id !== sessionId)
        if (currentSessionId.value === sessionId) {
          createNewSession()
        }
        message.success('删除成功')
      } catch (error) {
        message.error('删除失败')
        console.error(error)
      }
    }

    const openSelector = () => {
      selectorVisible.value = true
    }

    const clearMaterial = async () => {
      currentMaterialId.value = 0  // 使用 0 表示跨资料对话
      localStorage.setItem(STORAGE_KEY_MATERIAL, '0')
      currentSessionId.value = null
      localStorage.removeItem(STORAGE_KEY_SESSION)
      messages.value = []
      await loadSessions()  // 加载跨资料对话的历史会话
      message.success('已清除资料选择')
    }

    const handleSend = async () => {
      if (!inputMessage.value.trim() || isLoading.value) return

      const question = inputMessage.value.trim()
      inputMessage.value = ''

      messages.value.push({
        role: 'user',
        content: question,
        timestamp: new Date().toISOString()
      })
      scrollToBottom()

      isLoading.value = true

      const assistantMessage = reactive({
        role: 'assistant',
        content: '',
        streaming: true,
        timestamp: new Date().toISOString()
      })
      messages.value.push(assistantMessage)

      try {
        const history = messages.value
          .slice(0, -1)
          .filter(m => m.role === 'user' || m.role === 'assistant')
          .map(m => ({ role: m.role, content: m.content }))

        const streamResult = ragService.chatStream(
          currentMaterialId.value || 0,
          {
            question,
            history,
            sessionId: currentSessionId.value
          },
          {
            onToken: (token) => {
              assistantMessage.content += token
              requestAnimationFrame(() => {
                nextTick(() => {
                  scrollToBottom()
                })
              })
            },
            onEvent: (event) => {
              if (event?.event === 'session') {
                const sessionId = event.data?.sessionId || event.data?.session_id
                if (sessionId) {
                  currentSessionId.value = sessionId
                  localStorage.setItem(STORAGE_KEY_SESSION, sessionId)
                }
              }
            },
            onComplete: (data) => {
              assistantMessage.streaming = false
              const sessionId = data?.sessionId || data?.session_id
              if (sessionId) {
                currentSessionId.value = sessionId
                localStorage.setItem(STORAGE_KEY_SESSION, sessionId)
                loadSessions()
              }
              // 保存 sources 到消息
              if (data?.sources && Array.isArray(data.sources)) {
                assistantMessage.sources = data.sources
              }
              // 保存 recommendations 到消息
              if (data?.recommendations && Array.isArray(data.recommendations)) {
                assistantMessage.recommendations = data.recommendations
              }
            },
            onError: (error) => {
              console.error('流式对话错误', error)
              assistantMessage.streaming = false
            }
          }
        )

        await streamResult.done

        assistantMessage.streaming = false

        if (!assistantMessage.content) {
          assistantMessage.content = '收到你的问题，但本次没有返回有效内容。'
        }
      } catch (error) {
        if (!assistantMessage.content) {
          messages.value = messages.value.filter((msg) => msg !== assistantMessage)
        }
        message.error(`对话失败：${error?.message || '未知错误'}`)
        console.error(error)
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    const handleClear = () => {
      inputMessage.value = ''
    }

    const goToDetail = () => {
      if (!currentMaterialId.value) return
      router.push(`/material/${currentMaterialId.value}`)
    }

    const initializeMaterial = async () => {
      if (routeMaterialId.value) {
        currentMaterialId.value = routeMaterialId.value
        localStorage.setItem(STORAGE_KEY_MATERIAL, String(routeMaterialId.value))
      } else {
        const stored = localStorage.getItem(STORAGE_KEY_MATERIAL)
        if (stored) {
          const id = Number(stored)
          if (Number.isFinite(id)) {
            currentMaterialId.value = id
          }
        } else {
          // 默认使用跨资料对话模式
          currentMaterialId.value = 0
        }
      }

      // 如果是跨资料对话（materialId=0），直接加载会话
      if (currentMaterialId.value === 0) {
        await loadSessions()
        const storedSession = localStorage.getItem(STORAGE_KEY_SESSION)
        if (storedSession) {
          const hasSession = sessions.value.some(s => s.id === storedSession)
          if (hasSession) {
            currentSessionId.value = storedSession
            await loadChatHistory()
          }
        }
        return
      }

      if (currentMaterialId.value) {
        const existed = allMaterials.value.some(m => m.id === currentMaterialId.value)
        if (!existed) {
          try {
            const res = await materialService.getMaterialDetail(currentMaterialId.value)
            if (res.code === 0 && res.data) {
              const normalized = normalizeMaterial(res.data)
              if (normalized) {
                allMaterials.value = [...allMaterials.value, normalized]
              }
            }
          } catch (error) {
            console.error('加载资料详情失败', error)
          }
        }

        await loadSessions()

        const storedSession = localStorage.getItem(STORAGE_KEY_SESSION)
        if (storedSession) {
          const hasSession = sessions.value.some(s => s.id === storedSession)
          if (hasSession) {
            currentSessionId.value = storedSession
            await loadChatHistory()
          }
        }
      }
    }

    watch(
      () => route.params.id,
      async (newId) => {
        if (newId) {
          const id = Number(newId)
          if (Number.isFinite(id) && id !== currentMaterialId.value) {
            await selectMaterial(id)
          }
        }
      }
    )

    onMounted(async () => {
      await loadSelectableMaterials()
      await initializeMaterial()
    })

    return {
      messagesContainer,
      inputMessage,
      isLoading,
      messages,
      sessions,
      currentSessionId,
      currentMaterialId,
      currentMaterial,
      userAvatar,
      selectorVisible,
      materialLoading,
      materialSearchKeyword,
      filteredMaterials,
      formatMessage,
      formatTime,
      getMaterialTitle,
      toggleSource,
      isSourceExpanded,
      goToMaterial,
      createNewSession,
      switchSession,
      deleteSession,
      openSelector,
      selectMaterial,
      clearMaterial,
      handleSend,
      handleClear,
      goToDetail
    }
  }
}
</script>

<style scoped>
.material-chat-container {
  height: calc(100vh - 60px);
  background: #f5f5f5;
}

.chat-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  height: 100%;
}

.sessions-sidebar {
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 18px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.session-item:hover {
  background: #f7fbff;
}

.session-item.active {
  background: #e6f7ff;
  border-color: #91d5ff;
}

.session-meta {
  min-width: 0;
}

.session-title {
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #999;
}

.chat-main {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.header-text h2 {
  margin: 0;
  font-size: 20px;
}

.header-text p {
  margin: 4px 0 0;
  color: #666;
  font-size: 13px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 22px 24px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  gap: 10px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1890ff;
  color: #fff;
  font-size: 18px;
}

.message-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-content {
  max-width: 72%;
}

.message-item.user .message-content {
  text-align: right;
}

.message-text {
  background: #f5f5f5;
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.65;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #1890ff;
  color: #fff;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-8px);
  }
}

.chat-input-area {
  padding: 16px 24px;
  border-top: 1px solid #e8e8e8;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tip {
  font-size: 12px;
  color: #999;
}

.message-sources {
  margin-top: 12px;
  padding: 10px;
  background: #f9f9f9;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #666;
  margin-bottom: 8px;
}

.sources-header i {
  color: #1890ff;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-item {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
}

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.source-header:hover {
  background: #f7fbff;
}

.source-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.source-index {
  font-weight: 600;
  color: #1890ff;
  font-size: 12px;
  flex-shrink: 0;
}

.source-material {
  color: #1890ff;
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.source-material:hover {
  text-decoration: underline;
}

.source-score {
  font-size: 12px;
  color: #52c41a;
  font-weight: 600;
  background: #f6ffed;
  padding: 2px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}

.source-content {
  padding: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #555;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.message-recommendations {
  margin-top: 16px;
  padding: 12px;
  background: #f0f9ff;
  border-radius: 8px;
  border: 1px solid #bae7ff;
}

.recommendations-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0050b3;
  margin-bottom: 12px;
}

.recommendations-header i {
  color: #faad14;
}

.recommendations-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommendation-card {
  display: flex;
  gap: 12px;
  padding: 10px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #d9d9d9;
  cursor: pointer;
  transition: all 0.2s;
}

.recommendation-card:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
  transform: translateY(-1px);
}

.rec-cover {
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f5f5;
}

.rec-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.rec-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rec-title {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-desc {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
}

.rec-reason {
  font-size: 11px;
  color: #1890ff;
  background: #e6f7ff;
  padding: 2px 6px;
  border-radius: 3px;
}

.rec-price {
  font-size: 12px;
  font-weight: 600;
  color: #ff4d4f;
}

.material-select-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.material-select-item {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.material-select-item:hover {
  border-color: #91d5ff;
  background: #f7fbff;
}

.material-select-item.selected {
  border-color: #1890ff;
  background: #e6f7ff;
}

.material-select-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.material-select-desc {
  color: #777;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .sessions-sidebar {
    display: none;
  }

  .chat-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .message-content {
    max-width: 86%;
  }
}
</style>
