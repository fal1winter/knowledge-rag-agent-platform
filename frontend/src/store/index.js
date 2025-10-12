import { createStore } from 'vuex'

export default createStore({
  state: {
    isLogin: true,
    userId: 1,
    userName: 'demo-user',
    picture: '/default-avatar.svg',
    credits: 10000,
    systemNotifyCount: 0,
    taskNotifyCount: 0,
    loginVisible: false
  },
  mutations: {
    updateCredits(state, credits) {
      state.credits = credits
    },
    setLogin(state, value) {
      state.isLogin = value
    },
    setUser(state, user) {
      state.userId = user.userId || user.id || state.userId
      state.userName = user.name || user.userName || state.userName
      state.picture = user.picture || state.picture
    }
  }
})
