import axios from './axios'

export default {
  /**
   * 获取资料列表
   */
  listMaterials(params) {
    return axios.get('/api/bbs/material/list', { params })
  },

  /**
   * 获取资料详情
   */
  getMaterialDetail(id) {
    return axios.get(`/api/bbs/material/${id}`)
  },

  /**
   * 上传资料
   */
  uploadMaterial(data) {
    return axios.post('/api/bbs/material/upload', data)
  },

  /**
   * 更新资料
   */
  updateMaterial(id, data) {
    return axios.put(`/api/bbs/material/${id}`, data)
  },

  /**
   * 更新资料状态
   */
  updateStatus(id, status) {
    return axios.put(`/api/bbs/material/${id}/status`, null, {
      params: { status }
    })
  },

  /**
   * 删除资料
   */
  deleteMaterial(id) {
    return axios.delete(`/api/bbs/material/${id}`)
  },

  /**
   * 我的资料(卖家)
   */
  myMaterials(pageNum, pageSize = 10) {
    return axios.get('/api/bbs/material/my-materials', {
      params: { pageNum, pageSize }
    })
  },

  /**
   * 已购买的资料
   */
  purchasedMaterials(pageNum, pageSize = 10) {
    return axios.get('/api/bbs/material/purchased', {
      params: { pageNum, pageSize }
    })
  },

  /**
   * 搜索资料
   */
  searchMaterials(keyword, categoryId, pageNum, pageSize = 10) {
    return axios.get('/api/bbs/material/search', {
      params: { keyword, categoryId, pageNum, pageSize }
    })
  },

  /**
   * 获取分类列表
   */
  getCategories() {
    return axios.get('/api/bbs/material/categories')
  },

  /**
   * 检查访问权限
   */
  checkAccess(materialId) {
    return axios.get(`/api/bbs/material/${materialId}/check-access`)
  },

  /**
   * 获取用户积分
   */
  getUserCredits() {
    return axios.get('/api/user/credits')
  },

  /**
   * 获取评价列表
   */
  getReviews(params) {
    return axios.get('/api/bbs/material/review/list', { params })
  },

  /**
   * 提交评价
   */
  submitReview(data) {
    return axios.post('/api/bbs/material/review', data)
  },

  /**
   * 获取相似资料
   */
  getSimilarMaterials(materialId) {
    return axios.get(`/api/bbs/material/${materialId}/similar`)
  },

  /**
   * 获取热门资料
   */
  getHotMaterials(limit = 10) {
    return axios.get('/api/bbs/material/hot', {
      params: { limit }
    })
  },

  /**
   * 获取推荐资料
   */
  getRecommendMaterials(limit = 10) {
    return axios.get('/api/bbs/material/recommend', {
      params: { limit }
    })
  },

  /**
   * 增加浏览次数
   */
  incrementViewCount(materialId) {
    return axios.post(`/api/bbs/material/${materialId}/view`)
  }
}
