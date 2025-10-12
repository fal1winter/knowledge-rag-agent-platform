<template>
  <div class="material-detail-container">
    <a-spin :spinning="loading">
      <div class="detail-content" v-if="material">
        <!-- 左侧：资料信息 -->
        <div class="left-section">
          <div class="material-header">
            <h1 class="material-title">{{ material.title }}</h1>
            <div class="material-meta">
              <a-tag color="blue">{{ material.categoryName }}</a-tag>
              <a-tag>{{ material.fileType }}</a-tag>
              <span class="meta-item">
                <i class="fa fa-user"></i> {{ material.sellerName }}
              </span>
              <span class="meta-item">
                <i class="fa fa-clock"></i> {{ formatDate(material.createTime) }}
              </span>
            </div>
          </div>

          <div class="material-cover">
            <img
              :src="getCoverUrl(material.coverUrl)"
              :alt="material.title"
              referrerpolicy="no-referrer"
              @error="handleCoverError"
            />
          </div>

          <div class="material-description">
            <h3>资料简介</h3>
            <div v-html="material.description"></div>
          </div>

          <div class="material-stats">
            <div class="stat-item">
              <div class="stat-value">{{ material.viewCount }}</div>
              <div class="stat-label">浏览次数</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ material.salesCount }}</div>
              <div class="stat-label">销售数量</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ material.rating || '暂无' }}</div>
              <div class="stat-label">评分</div>
            </div>
          </div>
        </div>

        <!-- 右侧：购买区域 -->
        <div class="right-section">
          <div class="purchase-card">
            <!-- 用户积分显示 -->
            <div class="user-credits-info" v-if="isLoggedIn">
              <i class="fa fa-coins"></i>
              <span>我的积分：</span>
              <span class="credits-amount">{{ userCredits }}</span>
            </div>

            <div class="price-section">
              <div class="price-label">价格</div>
              <div class="price-value">{{ material.price }} 积分</div>
            </div>

            <div class="action-buttons">
              <a-button
                v-if="!hasAccess"
                type="primary"
                size="large"
                block
                @click="handlePurchase"
                :loading="purchasing"
              >
                <template #icon><i class="fa fa-shopping-cart"></i></template>
                立即购买
              </a-button>
              <a-button
                v-else
                type="primary"
                size="large"
                block
                @click="goToChat"
              >
                <template #icon><i class="fa fa-comments"></i></template>
                开始对话
              </a-button>
            </div>

            <div class="seller-info">
              <h4>卖家信息</h4>
              <div class="seller-card">
                <img
                  :src="getAvatarUrl(material.sellerAvatar)"
                  class="seller-avatar"
                  referrerpolicy="no-referrer"
                  @error="handleAvatarError"
                />
                <div class="seller-details">
                  <div class="seller-name">{{ material.sellerName }}</div>
                  <div class="seller-stats">
                    <span>资料: {{ material.sellerMaterialCount }}</span>
                    <span>销量: {{ material.sellerSalesCount }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <!-- 购买弹窗 -->
    <a-modal
      v-model:visible="purchaseModalVisible"
      title="购买资料"
      @ok="confirmPurchase"
      :confirm-loading="purchasing"
    >
      <div class="purchase-modal-content">
        <p><strong>资料名称：</strong>{{ material?.title }}</p>
        <p><strong>价格：</strong>{{ material?.price }} 积分</p>
        <p><strong>当前积分：</strong>{{ userPoints === null ? '未知' : `${userPoints} 积分` }}</p>
        <a-divider />
        <div class="pay-type-section">
          <p><strong>支付方式：</strong></p>
          <a-radio-group v-model:value="payType">
            <a-radio :value="1">积分支付</a-radio>
            <a-radio :value="2">支付宝支付</a-radio>
            <a-radio :value="3">微信支付</a-radio>
          </a-radio-group>
        </div>
        <a-alert
          v-if="payType === 1 && userPoints !== null && userPoints < material?.price"
          message="积分不足，请充值或选择其他支付方式"
          type="warning"
          show-icon
          style="margin-top: 16px"
        />
      </div>
    </a-modal>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { message } from 'ant-design-vue'
import materialService from '@/service/materialService'
import orderService from '@/service/orderService'
import paymentService from '@/service/paymentService'
import { normalizeMediaUrl } from '@/utils/mediaUrl'

export default {
  name: 'MaterialDetail',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const store = useStore()
    const loading = ref(false)
    const material = ref(null)
    const hasAccess = ref(false)
    const purchaseModalVisible = ref(false)
    const purchasing = ref(false)
    const payType = ref(1)
    const userPoints = ref(null)
    const defaultCover = '/static/images/material-default.svg'
    const defaultAvatar = '/default-avatar.svg'

    const materialId = computed(() => route.params.id)
    const isLoggedIn = computed(() => store.state.isLogin)
    const userCredits = computed(() => store.state.credits || 10000)

    // 格式化日期
    const formatDate = (dateStr) => {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleDateString('zh-CN')
    }

    // 加载资料详情
    const loadMaterialDetail = async () => {
      loading.value = true
      try {
        const res = await materialService.getMaterialDetail(materialId.value)
        if (res.code === 0) {
          material.value = res.data
        }
      } catch (error) {
        message.error('加载资料详情失败')
        console.error(error)
      } finally {
        loading.value = false
      }
    }

    // 检查访问权限
    const checkAccess = async () => {
      try {
        const res = await materialService.checkAccess(materialId.value)
        if (res.code === 0) {
          hasAccess.value = res.data
        }
      } catch (error) {
        console.error('检查权限失败', error)
      }
    }

    // 加载用户积分
    const loadUserCredits = async () => {
      if (!isLoggedIn.value) return
      try {
        const res = await materialService.getUserCredits()
        if (res.code === 0) {
          const credits = res.data.credits || 10000
          userPoints.value = credits
          store.commit('updateCredits', credits)
        }
      } catch (error) {
        console.error('加载积分失败', error)
      }
    }

    // 购买
    const handlePurchase = async () => {
      await loadUserCredits()
      purchaseModalVisible.value = true
    }

    // 确认购买
    const confirmPurchase = async () => {
      if (
        payType.value === 1 &&
        userPoints.value !== null &&
        userPoints.value < material.value.price
      ) {
        message.warning('积分不足')
        return
      }

      purchasing.value = true
      try {
        // 创建订单
        const orderRes = await orderService.createOrder({
          materialId: Number(materialId.value),
          payType: payType.value
        })

        if (orderRes.code === 0) {
          const orderNo = orderRes.data.orderNo
          if (payType.value === 1) {
            const payRes = await orderService.payOrder(orderNo)
            if (payRes.code === 0) {
              message.success('购买成功！')
              purchaseModalVisible.value = false
              hasAccess.value = true
              await loadUserCredits()
            }
          } else {
            const payment = await paymentService.createPayment({
              userId: store.state.userId,
              tenantId: 'tenant-a',
              subject: `购买资料：${material.value.title}`,
              amountFen: Number(material.value.price || 0),
              channel: payType.value,
              relatedBizType: 'material_purchase',
              relatedBizId: orderNo
            })
            const result = paymentService.submitGatewayResult(payment)
            if (result === 'FORM_SUBMITTED') {
              message.info('正在跳转支付宝支付')
            } else if (result) {
              message.info(`微信支付链接：${result}`)
            } else {
              message.success('支付订单已创建')
            }
            purchaseModalVisible.value = false
          }
        }
      } catch (error) {
        const errorMsg = error.response?.data?.message || error.message || '未知错误'
        message.error('购买失败：' + errorMsg)
        console.error(error)
      } finally {
        purchasing.value = false
      }
    }

    // 跳转对话
    const goToChat = () => {
      router.push(`/material/${materialId.value}/chat`)
    }

    const getCoverUrl = (url) => normalizeMediaUrl(url, { fallback: defaultCover })
    const getAvatarUrl = (url) => normalizeMediaUrl(url, { fallback: defaultAvatar })

    const handleCoverError = (event) => {
      if (event?.target && event.target.src !== defaultCover) {
        event.target.src = defaultCover
      }
    }

    const handleAvatarError = (event) => {
      if (event?.target && event.target.src !== defaultAvatar) {
        event.target.src = defaultAvatar
      }
    }

    onMounted(() => {
      loadMaterialDetail()
      checkAccess()
      loadUserCredits()

      // 检查是否从列表页带了购买参数
      if (route.query.action === 'buy') {
        setTimeout(() => {
          handlePurchase()
        }, 500)
      }
    })

    return {
      loading,
      material,
      hasAccess,
      purchaseModalVisible,
      purchasing,
      payType,
      userPoints,
      isLoggedIn,
      userCredits,
      formatDate,
      handlePurchase,
      confirmPurchase,
      goToChat,
      getCoverUrl,
      getAvatarUrl,
      handleCoverError,
      handleAvatarError
    }
  }
}
</script>

<style scoped>
.material-detail-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.detail-content {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 30px;
}

.left-section {
  background: #fff;
  padding: 30px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.material-header {
  margin-bottom: 24px;
}

.material-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 16px 0;
}

.material-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  color: #666;
  font-size: 14px;
}

.meta-item i {
  margin-right: 4px;
}

.material-cover {
  width: 100%;
  height: 400px;
  margin-bottom: 30px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f5f5;
}

.material-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.material-description {
  margin-bottom: 30px;
}

.material-description h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
}

.material-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  padding: 20px;
  background: #f9f9f9;
  border-radius: 8px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1890ff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.right-section {
  position: sticky;
  top: 20px;
  height: fit-content;
}

.purchase-card {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.user-credits-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
}

.user-credits-info i {
  font-size: 16px;
  color: #ffd700;
}

.user-credits-info .credits-amount {
  font-size: 18px;
  font-weight: 700;
  margin-left: auto;
}

.price-section {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.price-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.price-value {
  font-size: 32px;
  font-weight: 600;
  color: #ff4d4f;
}

.action-buttons {
  margin-bottom: 24px;
}

.seller-info h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}

.seller-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}

.seller-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
}

.seller-details {
  flex: 1;
}

.seller-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.seller-stats {
  font-size: 12px;
  color: #666;
  display: flex;
  gap: 12px;
}

.purchase-modal-content p {
  margin-bottom: 12px;
}

.pay-type-section {
  margin-top: 16px;
}
</style>
