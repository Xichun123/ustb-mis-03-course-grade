import { reactive } from 'vue'
import { systemApi } from '../api/modules'
import { getDemoUserKey, setDemoUserKey } from '../api/request'

export const userState = reactive({
  currentKey: getDemoUserKey(),
  currentUser: null,
  accounts: [],
  loaded: false
})

export async function initUserStore() {
  try {
    const [accRes, meRes] = await Promise.all([
      systemApi.listDemoAccounts(),
      systemApi.me()
    ])
    userState.accounts = accRes.data || []
    userState.currentUser = meRes.data || null
    userState.loaded = true
  } catch {
    userState.loaded = true
  }
}

export async function switchDemoUser(key) {
  setDemoUserKey(key)
  userState.currentKey = key
  const meRes = await systemApi.me()
  userState.currentUser = meRes.data
}
