import axios from './axios'

const ANONYMOUS_ID_KEY = 'material_chat_anonymous_id'

const getAnonymousId = () => {
  let anonymousId = localStorage.getItem(ANONYMOUS_ID_KEY)
  if (!anonymousId) {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      anonymousId = crypto.randomUUID()
    } else {
      anonymousId = `anon_${Date.now()}_${Math.random().toString(36).slice(2)}`
    }
    localStorage.setItem(ANONYMOUS_ID_KEY, anonymousId)
  }
  return anonymousId
}

const buildAnonymousHeaders = () => ({
  'X-Anonymous-Id': getAnonymousId()
})

const parseSseEvent = (eventText) => {
  const lines = eventText.split('\n')
  let eventName = 'message'
  const dataLines = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message'
    } else if (line.startsWith('data:')) {
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  const raw = dataLines.join('\n')
  try {
    return { event: eventName, data: JSON.parse(raw), raw }
  } catch (e) {
    return { event: eventName, data: raw, raw }
  }
}

export default {
  /**
   * 向量化资料文件
   */
  vectorizeMaterial(materialId, fileUrl, fileType) {
    return axios.post(`/api/bbs/material/rag/${materialId}/vectorize`, {
      fileUrl,
      fileType
    })
  },

  /**
   * 获取对话历史
   */
  getChatHistory(materialId, sessionId = null) {
    return axios.get(`/api/bbs/material/rag/${materialId}/history`, {
      params: { sessionId },
      headers: buildAnonymousHeaders()
    })
  },

  /**
   * 获取会话列表
   */
  getSessions(materialId) {
    return axios.get(`/api/bbs/material/rag/${materialId}/sessions`, {
      headers: buildAnonymousHeaders()
    })
  },

  /**
   * 删除会话
   */
  deleteSession(materialId, sessionId) {
    return axios.delete(`/api/bbs/material/rag/${materialId}/sessions/${sessionId}`, {
      headers: buildAnonymousHeaders()
    })
  },

  /**
   * 非流式对话
   */
  chat(materialId, question, history = [], sessionId = null) {
    return axios.post(`/api/bbs/material/rag/${materialId}/chat`, {
      question,
      history,
      sessionId
    }, {
      headers: buildAnonymousHeaders()
    })
  },

  /**
   * POST + SSE 流式对话
   */
  chatStream(materialId, payload, handlers = {}) {
    const { question, history = [], sessionId = null } = payload || {}
    const { onToken, onComplete, onError, onEvent } = handlers
    const controller = new AbortController()

    const done = (async () => {
      let completed = false
      let failed = false
      const response = await fetch(`/api/bbs/material/rag/${materialId}/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...buildAnonymousHeaders()
        },
        credentials: 'include',
        body: JSON.stringify({ question, history, sessionId }),
        signal: controller.signal
      })

      if (!response.ok || !response.body) {
        throw new Error(`流式请求失败: HTTP ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done: streamDone, value } = await reader.read()
        if (streamDone) {
          break
        }

        buffer += decoder.decode(value, { stream: true })

        let splitIndex = buffer.indexOf('\n\n')
        while (splitIndex >= 0) {
          const eventText = buffer.slice(0, splitIndex)
          buffer = buffer.slice(splitIndex + 2)
          const parsedEvent = parseSseEvent(eventText)

          if (parsedEvent) {
            if (onEvent) onEvent(parsedEvent)

            const eventData = parsedEvent.data
            const eventName = parsedEvent.event
            if (eventName === 'token' && onToken) {
              onToken(typeof eventData === 'string' ? eventData : (eventData?.content || ''))
            } else if (eventName === 'done' && onComplete) {
              completed = true
              onComplete(eventData || {})
            } else if (eventName === 'session') {
              // 会话事件交给上层 onEvent / onComplete 处理
            } else if (eventName === 'error' && onError) {
              failed = true
              onError(new Error(eventData?.error || eventData?.message || eventData || '流式对话失败'))
            } else if (eventData && typeof eventData === 'object') {
              if (eventData.type === 'token' && onToken) {
                onToken(eventData.content || '')
              } else if (eventData.type === 'complete' && onComplete) {
                completed = true
                onComplete(eventData.data || eventData)
              } else if (eventData.type === 'error' && onError) {
                failed = true
                onError(new Error(eventData.message || '流式对话失败'))
              }
            } else if (eventData === '[DONE]' && onComplete) {
              completed = true
              onComplete({})
            }
          }

          splitIndex = buffer.indexOf('\n\n')
        }
      }

      if (buffer.trim()) {
        const parsedEvent = parseSseEvent(buffer.trim())
        if (parsedEvent && onEvent) {
          onEvent(parsedEvent)
        }
        if (parsedEvent) {
          if (parsedEvent.event === 'token' && onToken) {
            onToken(typeof parsedEvent.data === 'string' ? parsedEvent.data : (parsedEvent.data?.content || ''))
          } else if (parsedEvent.event === 'done' && onComplete) {
            completed = true
            onComplete(parsedEvent.data || {})
          } else if (parsedEvent.event === 'error' && onError) {
            failed = true
            onError(new Error(parsedEvent.data?.error || parsedEvent.data?.message || parsedEvent.data || '流式对话失败'))
          } else if (parsedEvent.data && typeof parsedEvent.data === 'object') {
            if (parsedEvent.data.type === 'complete' && onComplete) {
              completed = true
              onComplete(parsedEvent.data.data || parsedEvent.data)
            }
          }
        }
      }

      if (!completed && !failed && onComplete) {
        onComplete({})
      }
    })().catch((error) => {
      if (controller.signal.aborted) {
        return
      }
      if (onError) {
        onError(error)
      } else {
        throw error
      }
    })

    return {
      cancel: () => controller.abort(),
      done
    }
  },

  /**
   * 语义检索
   */
  findSimilarChunks(materialId, query, topK = 5) {
    return axios.post(`/api/bbs/material/rag/${materialId}/search`, {
      query,
      topK
    })
  },

  /**
   * 获取资料统计信息
   */
  getMaterialStats(materialId) {
    return axios.get(`/api/bbs/material/rag/${materialId}/stats`)
  }
}
