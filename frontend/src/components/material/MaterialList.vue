<template>
  <div class="material-list-container">
    <!-- 顶部搜索栏 -->
    <div class="search-header">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索资料标题、描述..."
        size="large"
        @search="handleSearch"
        style="max-width: 600px"
      >
        <template #enterButton>
          <a-button type="primary">搜索</a-button>
        </template>
      </a-input-search>

      <!-- 用户积分显示 -->
      <div class="user-credits" v-if="isLoggedIn">
        <i class="fa fa-coins"></i>
        <span class="credits-label">我的积分：</span>
        <span class="credits-value">{{ userCredits }}</span>
      </div>
    </div>

    <!-- 分类和筛选 -->
    <div class="filter-section">
      <a-space size="large">
        <div>
          <span class="filter-label">分类：</span>
          <a-select
            v-model:value="selectedCategory"
            style="width: 200px"
            @change="handleCategoryChange"
          >
            <a-select-option :value="null">全部分类</a-select-option>
            <a-select-option
              v-for="cat in categories"
              :key="cat.id"
              :value="cat.id"
            >
              {{ cat.name }}
            </a-select-option>
          </a-select>
        </div>
        <div>
          <span class="filter-label">排序：</span>
          <a-select
            v-model:value="sortType"
            style="width: 150px"
            @change="handleSortChange"
          >
            <a-select-option value="latest">最新上传</a-select-option>
            <a-select-option value="hot">最热门</a-select-option>
            <a-select-option value="price_asc">价格从低到高</a-select-option>
            <a-select-option value="price_desc">价格从高到低</a-select-option>
          </a-select>
        </div>
      </a-space>
      <a-button type="primary" @click="goToUpload">
        <template #icon><i class="fa fa-upload"></i></template>
        上传资料
      </a-button>
    </div>

    <!-- 资料列表 -->
    <a-spin :spinning="loading">
      <div class="material-grid">
        <div
          v-for="material in materials"
          :key="material.id"
          class="material-card"
          @click="goToDetail(material.id)"
        >
          <div class="material-cover">
            <img
              :src="getCoverUrl(material.coverUrl)"
              :alt="material.title"
              referrerpolicy="no-referrer"
              @error="handleCoverError"
            />
            <div class="material-type-badge">{{ material.fileType }}</div>
          </div>
          <div class="material-info">
            <h3 class="material-title">{{ material.title }}</h3>
            <p class="material-desc">{{ material.description }}</p>
            <div class="material-meta">
              <span class="meta-item">
                <i class="fa fa-eye"></i> {{ material.viewCount }}
              </span>
              <span class="meta-item">
                <i class="fa fa-shopping-cart"></i> {{ material.salesCount }}
              </span>
            </div>
            <div class="material-footer">
              <div class="price-section">
                <span class="price">{{ material.price }} 积分</span>
              </div>
              <a-button type="primary" size="small" @click.stop="handleBuy(material)">
                立即购买
              </a-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <a-empty v-if="!loading && materials.length === 0" description="暂无资料" />

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <a-pagination
          v-model:current="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          show-size-changer
          @change="handlePageChange"
        />
      </div>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { message } from 'ant-design-vue'
import materialService from '@/service/materialService'
import { normalizeMediaUrl } from '@/utils/mediaUrl'

export default {
  name: 'MaterialList',
  setup() {
    const router = useRouter()
    const store = useStore()
    const loading = ref(false)
    const materials = ref([])
    const categories = ref([])
    const searchKeyword = ref('')
    const selectedCategory = ref(null)
    const sortType = ref('latest')
    const pageNum = ref(1)
    const pageSize = ref(12)
    const total = ref(0)
    const defaultCover = '/static/images/material-default.svg'

    // 用户登录状态和积分
    const isLoggedIn = computed(() => store.state.isLogin)
    const userCredits = computed(() => store.state.credits || 10000)

    // 加载用户积分
    const loadUserCredits = async () => {
      if (!isLoggedIn.value) return
      try {
        const res = await materialService.getUserCredits()
        if (res.code === 0) {
          const credits = res.data.credits || 10000
          store.commit('updateCredits', credits)
        }
      } catch (error) {
        console.error('加载积分失败', error)
      }
    }

    // 加载分类
    const loadCategories = async () => {
      try {
        const res = await materialService.getCategories()
        if (res.code === 0) {
          categories.value = res.data || []
        }
      } catch (error) {
        console.error('加载分类失败', error)
      }
    }

    // 排序类型映射
    const sortTypeMap = {
      'latest': 'create_time',
      'hot': 'view_count',
      'price_asc': 'price',
      'price_desc': 'price'
    }

    // 加载资料列表
    const loadMaterials = async () => {
      loading.value = true
      try {
        const params = {
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          categoryId: selectedCategory.value,
          orderBy: sortTypeMap[sortType.value] || 'create_time',
          keyword: searchKeyword.value
        }
        const res = await materialService.listMaterials(params)
        if (res.code === 0) {
          materials.value = res.data.list || []
          total.value = res.data.total || 0
        }
      } catch (error) {
        message.error('加载资料列表失败')
        console.error(error)
      } finally {
        loading.value = false
      }
    }

    // 搜索
    const handleSearch = () => {
      pageNum.value = 1
      loadMaterials()
    }

    // 分类变化
    const handleCategoryChange = () => {
      pageNum.value = 1
      loadMaterials()
    }

    // 排序变化
    const handleSortChange = () => {
      pageNum.value = 1
      loadMaterials()
    }

    // 分页变化
    const handlePageChange = () => {
      loadMaterials()
    }

    // 跳转详情
    const goToDetail = (id) => {
      router.push(`/material/${id}`)
    }

    // 跳转上传
    const goToUpload = () => {
      router.push('/material/upload')
    }

    // 购买
    const handleBuy = (material) => {
      router.push(`/material/${material.id}?action=buy`)
    }

    const getCoverUrl = (url) => normalizeMediaUrl(url, { fallback: defaultCover })

    const handleCoverError = (event) => {
      if (event?.target && event.target.src !== defaultCover) {
        event.target.src = defaultCover
      }
    }

    onMounted(() => {
      loadCategories()
      loadMaterials()
      loadUserCredits()
    })

    return {
      loading,
      materials,
      categories,
      searchKeyword,
      selectedCategory,
      sortType,
      pageNum,
      pageSize,
      total,
      isLoggedIn,
      userCredits,
      handleSearch,
      handleCategoryChange,
      handleSortChange,
      handlePageChange,
      goToDetail,
      goToUpload,
      handleBuy,
      getCoverUrl,
      handleCoverError
    }
  }
}
</script>

<style scoped>
.material-list-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.search-header {
  margin-bottom: 30px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 30px;
  position: relative;
}

.user-credits {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 25px;
  color: #fff;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.user-credits i {
  font-size: 18px;
  color: #ffd700;
}

.credits-label {
  font-weight: 500;
}

.credits-value {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.filter-label {
  font-weight: 500;
  margin-right: 10px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 30px;
}

.material-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  transition: all 0.3s;
  cursor: pointer;
}

.material-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
}

.material-cover {
  position: relative;
  width: 100%;
  height: 180px;
  overflow: hidden;
  background: #f5f5f5;
}

.material-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.material-type-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0,0,0,0.6);
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
}

.material-info {
  padding: 16px;
}

.material-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-desc {
  font-size: 14px;
  color: #666;
  margin: 0 0 12px 0;
  height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.material-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #999;
}

.meta-item i {
  margin-right: 4px;
}

.material-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.price {
  font-size: 18px;
  font-weight: 600;
  color: #ff4d4f;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}
</style>
