<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Check, CircleAlert, FileText, Plus } from 'lucide-vue-next'
import { drafts } from './drafts'
import { errorMessage } from './identity'

const props = defineProps({ initialId: { type: String, default: null } })
const emit = defineEmits(['opened', 'published', 'expired'])
const items = ref([])
const nextCursor = ref(null)
const active = ref(null)
const title = ref('')
const body = ref('')
const busy = ref('')
const listError = ref('')
const editorError = ref('')
const notice = ref('')
const conflict = ref(false)
const pendingPublish = ref(null)
let mounted = true
onUnmounted(() => { mounted = false })

const titleLength = computed(() => Array.from(title.value.trim()).length)
const bodyLength = computed(() => Array.from(body.value).length)
const hasUnsavedChanges = computed(() => title.value !== active.value?.title || body.value !== active.value?.body)
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

function describe(failure) {
  if (!mounted) return ''
  if (failure.status === 401) {
    emit('expired')
    return ''
  }
  if (failure.code === 'DRAFT_NOT_FOUND') return '草稿不存在或无法访问。'
  if (failure.code === 'NETWORK_UNAVAILABLE') return '无法连接草稿服务，请检查连接后重试。'
  return errorMessage(failure)
}

async function loadList(append = false) {
  listError.value = ''
  try {
    const page = await drafts.list(append ? nextCursor.value : null)
    items.value = append ? [...items.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
  } catch (failure) {
    listError.value = describe(failure)
  }
}

async function refreshList(append = false) {
  if (busy.value) return
  busy.value = 'list'
  try {
    await loadList(append)
  } finally {
    busy.value = ''
  }
}

function useDraft(draft) {
  if (!mounted) return
  active.value = draft
  title.value = draft.title
  body.value = draft.body
  conflict.value = false
  pendingPublish.value = null
  editorError.value = ''
  notice.value = ''
  emit('opened', draft.id)
}

function newDraft() {
  if (busy.value) return
  useDraft({ id: null, title: '', body: '' })
}

async function openDraft(id) {
  if (busy.value) return
  busy.value = 'open'
  editorError.value = ''
  try {
    useDraft(await drafts.get(id))
  } catch (failure) {
    editorError.value = describe(failure)
    if (!active.value && failure.status === 404) emit('opened', null)
  } finally {
    busy.value = ''
  }
}

async function save() {
  if (busy.value || conflict.value || pendingPublish.value || !active.value) return
  editorError.value = ''
  notice.value = ''
  if ((!title.value.trim() && !body.value.trim()) || titleLength.value > 200 || bodyLength.value > 20_000) {
    editorError.value = '请至少填写标题或正文，并检查字数上限。'
    return
  }

  busy.value = 'save'
  const input = { title: title.value, body: body.value }
  try {
    const saved = active.value.id
      ? await drafts.update(active.value.id, { ...input, version: active.value.version })
      : await drafts.create(input)
    useDraft(saved)
    notice.value = '草稿已保存。'
    await loadList()
  } catch (failure) {
    if (failure.code === 'CONTENT_VERSION_CONFLICT') {
      conflict.value = true
      editorError.value = '草稿已在其他页面更新。当前输入仍保留，重新加载将以服务器版本替换它。'
    } else {
      editorError.value = describe(failure)
    }
  } finally {
    busy.value = ''
  }
}

async function publish() {
  if (busy.value || conflict.value || !active.value?.id) return
  editorError.value = ''
  notice.value = ''
  if (!pendingPublish.value && hasUnsavedChanges.value) {
    editorError.value = '请先保存当前修改，再发布草稿。'
    return
  }

  const attempt = pendingPublish.value ?? { id: active.value.id, version: active.value.version }
  busy.value = 'publish'
  try {
    const article = await drafts.publish(attempt.id, attempt.version)
    if (!mounted) return
    pendingPublish.value = null
    active.value = null
    items.value = items.value.filter((item) => item.id !== article.id)
    emit('published', article.id)
    await loadList()
  } catch (failure) {
    if (!mounted) return
    if (failure.status === 0 || failure.status >= 500 || failure.code === 'INVALID_RESPONSE') {
      pendingPublish.value = attempt
      editorError.value = '暂未确认发布结果。可用同一版本重试发布。'
    } else if (failure.code === 'CONTENT_VERSION_CONFLICT') {
      pendingPublish.value = null
      conflict.value = true
      editorError.value = '草稿版本已变化。当前输入仍保留，请重新加载确认最新状态。'
    } else if (failure.code === 'INVALID_REQUEST') {
      pendingPublish.value = null
      editorError.value = '发布前，请确认已保存的标题和正文都包含非空白字符。'
    } else {
      editorError.value = describe(failure)
    }
  } finally {
    busy.value = ''
  }
}

onMounted(async () => {
  const id = props.initialId
  if (id) await openDraft(id)
  await refreshList()
})
</script>

<template>
  <main class="drafts-main" aria-labelledby="drafts-title">
    <div class="drafts-heading">
      <h1 id="drafts-title">我的草稿箱</h1>
      <button class="primary-button" type="button" :disabled="!!busy" @click="newDraft">
        <Plus :size="17" aria-hidden="true" />新建草稿
      </button>
    </div>

    <div class="drafts-grid">
      <section class="drafts-list" aria-labelledby="draft-list-title" :aria-busy="busy === 'list'">
        <h2 id="draft-list-title">最近修改</h2>
        <div v-if="listError" class="drafts-list-feedback">
          <p class="feedback failure" role="alert">{{ listError }}</p>
          <button type="button" class="text-button" :disabled="!!busy" @click="refreshList()">重试</button>
        </div>
        <ul v-if="items.length">
          <li v-for="item in items" :key="item.id">
            <button type="button" class="draft-row" :aria-current="active?.id === item.id ? 'true' : undefined"
              :disabled="!!busy" @click="openDraft(item.id)">
              <FileText :size="18" aria-hidden="true" />
              <span>
                <strong>{{ item.title || '无标题草稿' }}</strong>
                <time :datetime="item.updatedAt">{{ dateFormat.format(new Date(item.updatedAt)) }}</time>
              </span>
            </button>
          </li>
        </ul>
        <p v-else-if="!listError" class="drafts-list-feedback muted">
          {{ busy === 'list' ? '正在加载…' : '还没有草稿。' }}
        </p>
        <button v-if="nextCursor" class="outline-button draft-more" type="button" :disabled="!!busy"
          @click="refreshList(true)">加载更多</button>
      </section>

      <section class="draft-editor" aria-labelledby="draft-editor-title">
        <h2 id="draft-editor-title">{{ active?.id ? '编辑草稿' : active ? '新草稿' : '草稿编辑器' }}</h2>
        <p v-if="editorError" class="feedback failure" role="alert"><CircleAlert :size="18" aria-hidden="true" />{{ editorError }}</p>
        <p v-if="notice" class="feedback success" role="status"><Check :size="18" aria-hidden="true" />{{ notice }}</p>
        <button v-if="conflict" class="outline-button" type="button" :disabled="!!busy"
          @click="openDraft(active.id)">重新加载最新版本</button>
        <form v-if="active" class="draft-form" :aria-busy="busy === 'save' || busy === 'publish'" @submit.prevent="save">
          <div class="field">
            <div class="draft-field-heading">
              <label for="draft-title">标题</label>
              <span id="draft-title-count" :class="{ 'over-limit': titleLength > 200 }">{{ titleLength }} / 200</span>
            </div>
            <input id="draft-title" v-model="title" aria-describedby="draft-title-count" :disabled="!!busy || !!pendingPublish"
              placeholder="给草稿取个标题" />
          </div>
          <div class="field">
            <div class="draft-field-heading">
              <label for="draft-body">正文（Markdown）</label>
              <span id="draft-body-count" :class="{ 'over-limit': bodyLength > 20_000 }">{{ bodyLength }} / 20,000</span>
            </div>
            <textarea id="draft-body" v-model="body" rows="14" aria-describedby="draft-body-count"
              :disabled="!!busy || !!pendingPublish" placeholder="在这里开始写作"></textarea>
          </div>
          <div class="draft-actions">
            <button class="primary-button" type="submit" :disabled="!!busy || conflict || !!pendingPublish">
              {{ busy === 'save' ? '正在保存…' : '保存草稿' }}
            </button>
            <button v-if="active.id" class="outline-button" type="button" :disabled="!!busy || conflict" @click="publish">
              {{ busy === 'publish' ? '正在发布…' : pendingPublish ? '重试发布' : '发布' }}
            </button>
          </div>
        </form>
        <p v-else class="muted">{{ busy === 'open' ? '正在打开草稿…' : '选择一篇草稿，或新建草稿开始写作。' }}</p>
      </section>
    </div>
  </main>
</template>
