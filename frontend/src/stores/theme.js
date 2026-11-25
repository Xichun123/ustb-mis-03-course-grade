import { reactive } from 'vue'

const THEME_STORAGE_KEY = 'ustb_cg_theme_mode'

const mediaQuery = typeof window !== 'undefined'
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null

function readSavedMode() {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    if (saved === 'light' || saved === 'dark' || saved === 'system') return saved
  } catch {
    // ignore
  }
  return 'system'
}

function resolveTheme(mode) {
  const dark = mode === 'dark' || (mode === 'system' && !!mediaQuery?.matches)
  return dark ? 'dark' : 'light'
}

const initialMode = readSavedMode()

export const themeState = reactive({
  mode: initialMode,
  resolved: resolveTheme(initialMode)
})

function applyTheme() {
  const resolved = resolveTheme(themeState.mode)
  const dark = resolved === 'dark'
  themeState.resolved = resolved
  const root = document.documentElement
  root.setAttribute('data-theme', resolved)
  root.classList.toggle('dark', dark)
  root.style.colorScheme = resolved
}

export function setThemeMode(mode) {
  themeState.mode = mode
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode)
  } catch {
    // ignore
  }
  applyTheme()
}

let listenerBound = false

function handleSystemChange() {
  if (themeState.mode === 'system') applyTheme()
}

export function initTheme() {
  applyTheme()
  if (!listenerBound && mediaQuery) {
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleSystemChange)
      listenerBound = true
    } else if (mediaQuery.addListener) {
      mediaQuery.addListener(handleSystemChange)
      listenerBound = true
    }
  }
}
