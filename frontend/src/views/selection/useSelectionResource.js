import { shallowReactive } from 'vue'

// Each mounted student page keeps just its current course query and its current term's records.
const FRESH_FOR_MS = 30_000

export function useSelectionResource() {
  const state = shallowReactive({ data: null, updatedAt: 0, loading: false, error: '' })
  let currentKey = null
  let pending = null

  function clear() {
    currentKey = null
    pending = null
    Object.assign(state, { data: null, updatedAt: 0, loading: false, error: '' })
  }

  function load(key, fetchData, force = false) {
    if (key !== currentKey) {
      clear()
      currentKey = key
    }
    if (pending) return pending
    if (!force && state.data && !state.error && Date.now() - state.updatedAt < FRESH_FOR_MS) return

    state.loading = true
    state.error = ''
    const request = fetchData().then(({ data }) => {
      if (pending !== request) return
      state.data = data
      state.updatedAt = Date.now()
    }).catch(e => {
      if (pending === request) state.error = e.response?.data?.message || e.message || '加载失败，请刷新重试'
    }).finally(() => {
      if (pending !== request) return
      state.loading = false
      pending = null
    })
    pending = request
    return request
  }

  return { state, load, clear }
}
