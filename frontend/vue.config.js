module.exports = {
  publicPath: '/',
  devServer: {
    port: 8098,
    proxy: {
      '/api': {
        target: process.env.VUE_APP_GATEWAY_BASE_URL || 'http://127.0.0.1:8088',
        changeOrigin: true
      }
    }
  }
}
