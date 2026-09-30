import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', () => {
  const navigationCollapsed = ref(false)
  function toggleNavigation() {
    navigationCollapsed.value = !navigationCollapsed.value
  }
  return { navigationCollapsed, toggleNavigation }
})
