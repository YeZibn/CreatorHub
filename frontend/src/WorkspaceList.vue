<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ArrowUpRight, CircleAlert, LoaderCircle, Newspaper, RefreshCw } from 'lucide-vue-next'
import { errorMessage, request } from './identity'

const props = defineProps({
  view: { type: Object, required: true },
  draftHref: { type: String, required: true },
})
const emit = defineEmits(['expired'])
const rows = ref([])
const cursor = ref(null)
const loading = ref(false)
const failure = ref('')
const failedMore = ref(false)
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
let mounted = true
onUnmounted(() => { mounted = false })

function rowId(row) {
  return row.id ?? row.userId
}

async function load(more = false) {
  if (loading.value || (more && !cursor.value)) return
  loading.value = true
  failure.value = ''
  try {
    const query = more ? '?' + new URLSearchParams({ cursor: cursor.value }) : ''
    const page = await request(props.view.path + query)
    if (!mounted) return
    if (more) {
      const knownIds = new Set(rows.value.map(rowId))
      rows.value = [...rows.value, ...page.items.filter((row) => !knownIds.has(rowId(row)))]
    } else {
      rows.value = page.items
    }
    cursor.value = page.nextCursor
  } catch (error) {
    if (!mounted) return
    if (error.status === 401) emit('expired')
    else {
      failedMore.value = more
      failure.value = error.status === 0 ? '连接失败，请检查网络后重试。' : errorMessage(error)
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="workspace-page" aria-labelledby="workspace-list-title">
    <header class="workspace-heading">
      <div>
        <h1 id="workspace-list-title">{{ view.label }}</h1>
        <p>{{ view.description }}</p>
      </div>
      <button class="outline-button workspace-refresh" type="button" :disabled="loading" @click="load()">
        <RefreshCw :size="16" aria-hidden="true" /> 刷新
      </button>
    </header>
    <section class="workspace-panel" :aria-busy="loading" aria-labelledby="workspace-list-title">
      <div v-if="loading && !rows.length" class="workspace-empty" role="status">
        <LoaderCircle class="spinner" :size="24" aria-hidden="true" />
        <p>正在加载…</p>
      </div>
      <ul v-if="rows.length" class="workspace-rows">
        <li v-for="row in rows" :key="rowId(row)">
          <a v-if="view.id === 'published'" class="workspace-row workspace-row-link" :href="'#articles/' + row.id">
            <span class="workspace-icon tone-published"><Newspaper :size="19" aria-hidden="true" /></span>
            <span class="workspace-row-copy">
              <strong>{{ row.title }}</strong>
              <time :datetime="row.publishedAt">{{ dateFormat.format(new Date(row.publishedAt)) }}</time>
            </span>
            <ArrowUpRight :size="18" class="workspace-row-arrow" aria-hidden="true" />
          </a>
          <div v-else class="workspace-row">
            <span class="workspace-person" :class="'tone-' + view.id" aria-hidden="true">{{ [...row.displayName][0]?.toUpperCase() || 'C' }}</span>
            <span class="workspace-row-copy">
              <strong>{{ row.displayName }}</strong>
              <time :datetime="row.followedAt">{{ dateFormat.format(new Date(row.followedAt)) }} 关注</time>
            </span>
          </div>
        </li>
      </ul>
      <div v-if="failure" class="workspace-list-error">
        <p class="feedback failure" role="alert"><CircleAlert :size="17" aria-hidden="true" />{{ failure }}</p>
        <button class="outline-button" type="button" @click="load(failedMore)">重试</button>
      </div>
      <div v-else-if="!loading && !rows.length" class="workspace-empty">
        <span class="workspace-icon" :class="'tone-' + view.id"><component :is="view.icon" :size="23" aria-hidden="true" /></span>
        <p>{{ view.empty }}</p>
        <a v-if="view.id === 'published'" class="outline-button workspace-action" :href="draftHref">打开草稿箱</a>
      </div>
      <div v-if="cursor && !failure" class="workspace-pagination">
        <button class="outline-button" type="button" :disabled="loading" @click="load(true)">
          <LoaderCircle v-if="loading" class="spinner" :size="15" aria-hidden="true" />
          {{ loading ? '正在加载…' : '加载更多' }}
        </button>
      </div>
    </section>
  </main>
</template>
