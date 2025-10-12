import axios from 'axios'

const instance = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  timeout: 20000,
  withCredentials: true
})

instance.interceptors.response.use(
  response => {
    if (response.data && typeof response.data.code !== 'undefined') {
      if (response.data.code === 0) return response.data
      return Promise.reject(response.data)
    }
    return response.data
  },
  error => Promise.reject(error)
)

export default instance
