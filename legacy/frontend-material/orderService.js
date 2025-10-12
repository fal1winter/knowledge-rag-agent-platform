// 前端服务层 - 订单管理
// 路径: vue3web/src/service/orderService.js

import axios from './axios'

const toParams = (pageNumOrParams, pageSize = 10, status = null) => {
  if (typeof pageNumOrParams === 'object' && pageNumOrParams !== null) {
    return {
      pageNum: pageNumOrParams.pageNum,
      pageSize: pageNumOrParams.pageSize,
      status: pageNumOrParams.status
    }
  }

  return {
    pageNum: pageNumOrParams,
    pageSize,
    status
  }
}

export default {
  /**
   * 创建订单
   */
  createOrder(data) {
    return axios.post('/api/bbs/material/order/create', data)
  },

  /**
   * 支付订单
   */
  payOrder(orderNo) {
    return axios.post(`/api/bbs/material/order/pay/${orderNo}`)
  },

  /**
   * 支付宝支付（兼容旧调用，后端统一使用payOrder）
   */
  payWithAlipay(orderNo) {
    return this.payOrder(orderNo)
  },

  /**
   * 积分支付（兼容旧调用，后端统一使用payOrder）
   */
  payWithPoints(orderNo) {
    return this.payOrder(orderNo)
  },

  /**
   * 查询订单
   */
  getOrder(orderNo) {
    return axios.get(`/api/bbs/material/order/${orderNo}`)
  },

  /**
   * 我的订单
   */
  myOrders(pageNum, pageSize = 10, status = null) {
    const params = toParams(pageNum, pageSize, status)
    return axios.get('/api/bbs/material/order/my-purchase', {
      params
    })
  },

  /**
   * 我的销售订单
   */
  mySales(pageNum, pageSize = 10, status = null) {
    const params = toParams(pageNum, pageSize, status)
    return axios.get('/api/bbs/material/order/my-sales', {
      params
    })
  },

  /**
   * 取消订单
   */
  cancelOrder(orderNo) {
    return axios.post(`/api/bbs/material/order/cancel/${orderNo}`)
  },

  /**
   * 订单统计
   */
  getStatistics() {
    return axios.get('/api/bbs/material/order/statistics')
  }
}
