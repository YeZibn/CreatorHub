<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { request, errorMessage } from './identity'

const props = defineProps({ id: { type: String, required: true } })
const emit = defineEmits(['expired'])
const article = ref(null)
const failure = ref(null)
const loading = ref(true)
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
let mounted = true
onUnmounted(() => { mounted = false })

async function load() {
  loading.value = true
  failure.value = null
  try {
    article.value = await request('/content/articles/' + props.id)
  } catch (error) {
    if (!mounted) return
    if (error.status === 401) emit('expired')
    else failure.value = error
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="article-main" :aria-busy="loading">
    <p v-if="loading" class="muted" role="status">正在读取文章…</p>
    <article v-else-if="article" class="article-card" aria-labelledby="article-title">
      <header>
        <h1 id="article-title">{{ article.title }}</h1>
        <time :datetime="article.publishedAt">{{ dateFormat.format(new Date(article.publishedAt)) }}</time>
      </header>
      <div class="article-body">{{ article.body }}</div>
    </article>
    <section v-else-if="failure" class="article-card" aria-labelledby="article-error">
      <h1 id="article-error">{{ failure.status === 404 ? '找不到这篇文章' : '暂时无法读取文章' }}</h1>
      <p v-if="failure.status === 404" role="alert">这篇文章不存在或尚未发布。</p>
      <template v-else>
        <p role="alert">{{ failure.status === 0 ? '连接失败，请检查网络后重试。' : errorMessage(failure) }}</p>
        <button type="button" class="outline-button" @click="load">重新加载</button>
      </template>
    </section>
  </main>
</template>
