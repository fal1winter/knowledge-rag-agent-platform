import axios from './axios'

export default {
  upload(file) {
    const form = new FormData()
    form.append('file', file)
    return axios.post('/api/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  async uploadTextFile(fileName, content) {
    const file = new File([content], fileName, { type: 'text/plain' })
    const res = await this.upload(file)
    if (res.code === 0 && res.data?.url) return res.data.url
    throw new Error('文本资料上传失败')
  }
}
