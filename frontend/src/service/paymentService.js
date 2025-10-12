import axios from './axios'

const PAY_CHANNELS = {
  1: 'CREDITS',
  2: 'ALIPAY',
  3: 'WECHAT'
}

export default {
  createPayment({ userId, tenantId, subject, amountFen, channel, relatedBizType, relatedBizId }) {
    return axios.post('/api/payments/create', {
      userId,
      tenantId,
      subject,
      amountFen,
      channel: PAY_CHANNELS[channel] || channel,
      relatedBizType,
      relatedBizId
    })
  },

  submitGatewayResult(result) {
    if (result?.payForm) {
      const div = document.createElement('div')
      div.innerHTML = result.payForm
      document.body.appendChild(div)
      const form = div.querySelector('form')
      if (form) form.submit()
      return 'FORM_SUBMITTED'
    }
    if (result?.codeUrl) {
      return result.codeUrl
    }
    return null
  }
}
