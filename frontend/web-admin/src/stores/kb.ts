import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listKnowledgeBases,
  type KnowledgeBase,
} from '@/api/knowledgeBase'

export const useKbStore = defineStore('kb', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentKbId = ref<string>('')
  const loading = ref(false)

  /** 拉取知识库列表并缓存 */
  async function fetchKnowledgeBases(keyword = '') {
    loading.value = true
    try {
      const res = await listKnowledgeBases({ keyword, page: 1, page_size: 100 })
      const data = (res as any).data ?? res
      knowledgeBases.value = data.records ?? []
      if (!currentKbId.value && knowledgeBases.value.length > 0) {
        currentKbId.value = knowledgeBases.value[0].id
      }
    } finally {
      loading.value = false
    }
  }

  function setCurrentKb(id: string) {
    currentKbId.value = id
  }

  return { knowledgeBases, currentKbId, loading, fetchKnowledgeBases, setCurrentKb }
})
