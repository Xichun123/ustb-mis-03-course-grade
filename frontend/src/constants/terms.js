export const DEFAULT_TERM = '2026-2027-1'

export const TERM_OPTIONS = [
  '2026-2027-2',
  '2026-2027-1',
  '2025-2026-2',
  '2025-2026-1',
  '2024-2025-2',
  '2024-2025-1',
  '2023-2024-2',
  '2023-2024-1'
]

/**
 * 合并预设学期与接口返回的动态学期列表，去重并按倒序排列
 */
export function mergeTermOptions(...lists) {
  const set = new Set(TERM_OPTIONS)
  for (const list of lists) {
    if (!Array.isArray(list)) continue
    for (const item of list) {
      if (item && String(item).trim()) {
        set.add(String(item).trim())
      }
    }
  }
  return Array.from(set).sort((a, b) => b.localeCompare(a))
}
