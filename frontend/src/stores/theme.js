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
  return 'light'
}

export const themeState = reactive({
  mode: readSavedMode(),
  resolved: 'light'
})

function applyTheme() {
  const dark = themeState.mode === 'dark'
    || (themeState.mode === 'system' && !!mediaQuery?.matches)
  themeState.resolved = dark ? 'dark' : 'light'
  const root = document.documentElement
  root.setAttribute('data-theme', themeState.resolved)
  root.classList.toggle('dark', dark)
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

export function initTheme() {
  applyTheme()
  if (mediaQuery && mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', () => {
      if (themeState.mode === 'system') applyTheme()
    })
  }
}
