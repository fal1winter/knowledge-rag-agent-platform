<template>
  <div class="order-list-container">
    <div class="order-header">
      <h2>我的订单</h2>
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane key="purchase" tab="购买订单">
          <template #tab>
            <i class="fa fa-shopping-cart"></i> 购买订单
          </template>
        </a-tab-pane>
        <a-tab-pane key="sales" tab="销售订单">
          <template #tab>
            <i class="fa fa-dollar-sign"></i> 销售订单
          </template>
        </a-tab-pane>
      </a-tabs>
    </div>

    <div class="filter-section">
      <a-space>
        <span>订单状态：</span>
        <a-select
          v-model:value="filterStatus"
          style="width: 150px"
          @change="handleFilterChange"
        >
          <a-select-option :value="null">全部</a-select-option>
          <a-select-option :value="0">待支付</a-select-option>
          <a-select-option :value="1">已支付</a-select-option>
          <a-select-option :value="2">已取消</a-select-option>
          <a-select-option :value="3">已退款</a-select-option>
        </a-select>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div class="orders-list">
        <div
          v-for="order in orders"
          :key="order.orderNo"
          class="order-card"
        >
          <div class="order-header-info">
            <div class="order-no">
              订单号：{{ order.orderNo }}
            </div>
            <div class="order-time">
              {{ formatDate(order.createTime) }}
            </div>
            <a-tag :color="getStatusColor(order.status)">
              {{ getStatusText(order.status) }}
            </a-tag>
          </div>

          <div class="order-content">
            <div class="material-info">
              <img
                :src="getCoverUrl(order.materialCoverUrl)"
                class="material-cover"
                referrerpolicy="no-referrer"
                @error="handleCoverError"
              />
              <div class="material-details">
                <h3 class="material-title">{{ order.materialTitle }}</h3>
                <p class="material-desc">{{ order.materialDescription || '暂无资料描述' }}</p>
                <div class="material-meta">
                  <span>文件类型：{{ order.fileType || '未知' }}</span>
                  <span v-if="activeTab === 'purchase'">
                    卖家：{{ order.sellerName || order.sellerId || '-' }}
                  </span>
                  <span v-else>
                    买家：{{ order.buyerName || order.buyerId || '-' }}
                  </span>
                </div>
              </div>
            </div>

            <div class="order-price">
              <div class="price-label">订单金额</div>
              <div class="price-value">{{ order.price }} 积分</div>
              <div class="pay-type">
                {{ order.payType === 1 ? '积分支付' : (order.payType === 3 ? '微信支付' : '支付宝支付') }}
              </div>
            </div>

            <div class="order-actions">
              <a-space direction="vertical">
                <a-button
                  v-if="order.status === 0 && activeTab === 'purchase'"
                  type="primary"
                  @click="handlePay(order)"
                >
                  立即支付
                </a-button>
                <a-button
                  v-if="order.status === 1 && activeTab === 'purchase'"
                  type="primary"
                  @click="goToChat(order.materialId)"
                >
                  开始对话
                </a-button>
                <a-button
                  @click="goToMaterialDetail(order.materialId)"
                >
                  查看资料
                </a-button>
                <a-button
                  v-if="order.status === 0"
                  danger
                  @click="handleCancel(order.orderNo)"
                >
                  取消订单
                </a-button>
              </a-space>
            </div>
          </div>
        </div>
      </div>

      <a-empty v-if="!loading && orders.length === 0" description="暂无订单" />

      <div class="pagination-wrapper" v-if="total > 0">
        <a-pagination
          v-model:current="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          @change="handlePageChange"
        />
      </div>
    </a-spin>

    <!-- 支付弹窗 -->
    <a-modal
      v-model:visible="payModalVisible"
      title="选择支付方式"
      @ok="confirmPay"
      :confirm-loading="paying"
    >
      <div class="pay-modal-content">
        <p><strong>订单号：</strong>{{ currentOrder?.orderNo }}</p>
        <p><strong>金额：</strong>{{ currentOrder?.price }} 积分</p>
        <a-divider />
        <a-radio-group v-model:value="payType">
          <a-radio :value="1">积分支付</a-radio>
          <a-radio :value="2">支付宝支付</a-radio>
          <a-radio :value="3">微信支付</a-radio>
        </a-radio-group>
      </div>
    </a-modal>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import orderService from '@/service/orderService'
import paymentService from '@/service/paymentService'
import { normalizeMediaUrl } from '@/utils/mediaUrl'

export default {
  name: 'OrderList',
  setup() {
    const router = useRouter()
    const loading = ref(false)
    const orders = ref([])
    const allOrders = ref([])
    const activeTab = ref('purchase')
    const filterStatus = ref(null)
    const pageNum = ref(1)
    const pageSize = ref(10)
    const total = ref(0)
    const payModalVisible = ref(false)
    const paying = ref(false)
    const payType = ref(1)
    const currentOrder = ref(null)
    const defaultCover = '/static/images/material-default.svg'

    // 格式化日期
    const formatDate = (dateStr) => {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleString('zh-CN')
    }

    // 获取状态颜色
    const getStatusColor = (status) => {
      const colors = {
        0: 'orange',
        1: 'green',
        2: 'default',
        3: 'red'
      }
      return colors[status] || 'default'
    }

    // 获取状态文本
    const getStatusText = (status) => {
      const texts = {
        0: '待支付',
        1: '已支付',
        2: '已取消',
        3: '已退款'
      }
      return texts[status] || '未知'
    }

    // 加载订单列表
    const loadOrders = async () => {
      loading.value = true
      try {
        const params = {
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          status: filterStatus.value
        }

        let res
        if (activeTab.value === 'purchase') {
          res = await orderService.myOrders(params)
        } else {
          res = await orderService.mySales(params)
        }

        if (res.code === 0) {
          const orderList = Array.isArray(res.data)
            ? res.data
            : (res.data?.list || [])

          let filteredOrders = orderList
          if (filterStatus.value !== null && filterStatus.value !== undefined) {
            filteredOrders = orderList.filter(order => order.status === filterStatus.value)
          }

          allOrders.value = filteredOrders
          total.value = filteredOrders.length

          const start = (pageNum.value - 1) * pageSize.value
          const end = start + pageSize.value
          orders.value = filteredOrders.slice(start, end)
        }
      } catch (error) {
        message.error('加载订单列表失败')
        console.error(error)
      } finally {
        loading.value = false
      }
    }

    // 切换标签
    const handleTabChange = () => {
      pageNum.value = 1
      filterStatus.value = null
      loadOrders()
    }

    // 筛选变化
    const handleFilterChange = () => {
      pageNum.value = 1
      loadOrders()
    }

    // 分页变化
    const handlePageChange = () => {
      loadOrders()
    }

    // 支付
    const handlePay = (order) => {
      currentOrder.value = order
      payType.value = order.payType
      payModalVisible.value = true
    }

    // 确认支付
    const confirmPay = async () => {
      paying.value = true
      try {
        const orderNo = currentOrder.value.orderNo
        if (payType.value === 1) {
          const res = await orderService.payOrder(orderNo)
          if (res.code === 0) {
            message.success('支付成功')
            payModalVisible.value = false
            loadOrders()
          }
        } else {
          const payment = await paymentService.createPayment({
            userId: 1,
            tenantId: 'tenant-a',
            subject: `资料订单：${currentOrder.value.materialTitle || orderNo}`,
            amountFen: Number(currentOrder.value.price || 0),
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
          payModalVisible.value = false
        }
      } catch (error) {
        message.error('支付失败：' + (error.message || '未知错误'))
        console.error(error)
      } finally {
        paying.value = false
      }
    }

    // 取消订单
    const handleCancel = (orderNo) => {
      Modal.confirm({
        title: '确认取消订单？',
        content: '取消后订单将无法恢复',
        onOk: async () => {
          try {
            const res = await orderService.cancelOrder(orderNo)
            if (res.code === 0) {
              message.success('订单已取消')
              loadOrders()
            }
          } catch (error) {
            message.error('取消失败')
            console.error(error)
          }
        }
      })
    }

    // 跳转对话
    const goToChat = (materialId) => {
      router.push(`/material/${materialId}/chat`)
    }

    // 跳转资料详情
    const goToMaterialDetail = (materialId) => {
      router.push(`/material/${materialId}`)
    }

    const getCoverUrl = (url) => normalizeMediaUrl(url, { fallback: defaultCover })

    const handleCoverError = (event) => {
      if (event?.target && event.target.src !== defaultCover) {
        event.target.src = defaultCover
      }
    }

    onMounted(() => {
      loadOrders()
    })

    return {
      loading,
      orders,
      allOrders,
      activeTab,
      filterStatus,
      pageNum,
      pageSize,
      total,
      payModalVisible,
      paying,
      payType,
      currentOrder,
      formatDate,
      getStatusColor,
      getStatusText,
      handleTabChange,
      handleFilterChange,
      handlePageChange,
      handlePay,
      confirmPay,
      handleCancel,
      goToChat,
      goToMaterialDetail,
      getCoverUrl,
      handleCoverError
    }
  }
}
</script>

<style scoped>
.order-list-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.order-header {
  background: #fff;
  padding: 20px 30px 0;
  border-radius: 8px;
  margin-bottom: 20px;
}

.order-header h2 {
  margin: 0 0 20px 0;
  font-size: 24px;
}

.filter-section {
  background: #fff;
  padding: 16px 30px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.order-header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.order-no {
  font-weight: 500;
  color: #666;
}

.order-time {
  color: #999;
  font-size: 14px;
}

.order-content {
  display: grid;
  grid-template-columns: 1fr 200px 150px;
  gap: 20px;
  align-items: center;
}

.material-info {
  display: flex;
  gap: 16px;
}

.material-cover {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.material-details {
  flex: 1;
}

.material-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-desc {
  font-size: 14px;
  color: #666;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
}

.order-price {
  text-align: center;
}

.price-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.price-value {
  font-size: 24px;
  font-weight: 600;
  color: #ff4d4f;
  margin-bottom: 4px;
}

.pay-type {
  font-size: 12px;
  color: #999;
}

.order-actions {
  text-align: center;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

.pay-modal-content p {
  margin-bottom: 12px;
}
</style>
