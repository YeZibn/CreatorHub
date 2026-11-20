<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ArrowRight, Check, CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, LogOut,
  Mail, PenLine, UserRound,
} from 'lucide-vue-next'
import { errorMessage, identity } from './identity'
import DraftsWorkspace from './DraftsWorkspace.vue'
import ArticleDetail from './ArticleDetail.vue'

const phase = ref('loading')
const section = ref('account')
const articleId = ref(null)
const draftId = ref(null)
const draftsOpened = ref(false)
const draftsKey = ref(0)
const mode = ref('login')
const busy = ref('')
const user = ref(null)
const email = ref('')
const password = ref('')
const nickname = ref('')
const profileName = ref('')
const showPassword = ref(false)
const error = ref('')
const notice = ref('')

const isRegister = computed(() => mode.value === 'register')
const profileLength = computed(() => [...profileName.value.trim()].length)
const profileChanged = computed(() => profileName.value.trim() !== user.value?.displayName)
const initial = computed(() => [...(user.value?.displayName || '')][0]?.toUpperCase() || 'C')

function setUser(nextUser) {
  user.value = nextUser
  profileName.value = nextUser.displayName
  phase.value = 'account'
}

function setGuest(message = '') {
  user.value = null
  profileName.value = ''
  mode.value = 'login'
  password.value = ''
  showPassword.value = false
  phase.value = 'guest'
  error.value = ''
  notice.value = message
}

function setDraftId(id) {
  draftId.value = id
  if (section.value === 'drafts') window.history.replaceState(null, '', id ? '#drafts/' + id : '#drafts')
}

function showSection(next) {
  section.value = next
  if (next === 'drafts') {
    draftsOpened.value = true
    setDraftId(draftId.value)
  } else {
    window.history.replaceState(null, '', window.location.pathname + window.location.search)
  }
}

function syncRoute() {
  const article = /^#articles\/([1-9][0-9]*)$/.exec(window.location.hash)
  const draft = /^#drafts(?:\/([1-9][0-9]*))?$/.exec(window.location.hash)
  if (article) {
    articleId.value = article[1]
    section.value = 'article'
  } else if (draft) {
    const id = draft[1] ?? null
    if (id !== draftId.value) draftsKey.value++
    draftId.value = id
    draftsOpened.value = true
    section.value = 'drafts'
  } else {
    section.value = 'account'
  }
}

function showPublishedArticle(id) {
  draftId.value = null
  if (phase.value !== 'account' || section.value !== 'drafts') return
  articleId.value = id
  section.value = 'article'
  window.history.replaceState(null, '', '#articles/' + id)
}

async function loadSession() {
  phase.value = 'loading'
  error.value = ''
  try {
    setUser(await identity.current())
  } catch (failure) {
    if (failure.status === 401) {
      setGuest()
    } else {
      error.value = errorMessage(failure)
      phase.value = 'unavailable'
    }
  }
}

function switchMode() {
  if (busy.value) return
  mode.value = isRegister.value ? 'login' : 'register'
  error.value = ''
  notice.value = ''
  password.value = ''
  showPassword.value = false
}

async function submitAuth() {
  if (busy.value) return
  error.value = ''
  notice.value = ''
  const credentials = { email: email.value.trim(), password: password.value }

  if (isRegister.value) {
    const displayName = nickname.value.trim()
    if ([...displayName].length < 1 || [...displayName].length > 50) {
      error.value = '昵称需要在 1～50 个字符之间。'
      return
    }
    const length = [...credentials.password].length
    if (length < 15 || length > 128) {
      error.value = '密码需要在 15～128 个字符之间。'
      return
    }
    busy.value = 'register'
    try {
      await identity.register({ ...credentials, displayName })
      nickname.value = ''
      setGuest('账号已创建，请使用刚才的邮箱和密码登录。')
    } catch (failure) {
      error.value = errorMessage(failure)
    } finally {
      busy.value = ''
    }
    return
  }

  busy.value = 'login'
  try {
    await identity.login(credentials)
    password.value = ''
    showPassword.value = false
    await loadSession()
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = ''
  }
}

async function saveProfile() {
  if (busy.value || !profileChanged.value) return
  error.value = ''
  notice.value = ''
  if (profileLength.value < 1 || profileLength.value > 50) {
    error.value = '昵称需要在 1～50 个字符之间。'
    return
  }
  busy.value = 'profile'
  try {
    const profile = await identity.updateProfile(profileName.value.trim())
    setUser({ ...user.value, ...profile })
    notice.value = '昵称已保存。'
  } catch (failure) {
    if (failure.status === 401) setGuest('登录已过期，请重新登录。')
    else error.value = errorMessage(failure)
  } finally {
    busy.value = ''
  }
}

function resetProfile() {
  profileName.value = user.value.displayName
  error.value = ''
  notice.value = ''
}

async function logout() {
  if (busy.value) return
  error.value = ''
  notice.value = ''
  busy.value = 'logout'
  try {
    await identity.logout()
    showSection('account')
    draftsOpened.value = false
    draftId.value = null
    setGuest('你已退出登录。')
  } catch (failure) {
    if (failure.status === 401) setGuest('你已退出登录。')
    else error.value = '暂未确认退出。' + errorMessage(failure)
  } finally {
    busy.value = ''
  }
}

syncRoute()
onMounted(() => {
  window.addEventListener('hashchange', syncRoute)
  loadSession()
})
onUnmounted(() => window.removeEventListener('hashchange', syncRoute))
</script>

<template>
  <div class="site">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="wordmark">
          <span class="brand-mark"><PenLine :size="19" :stroke-width="2.15" aria-hidden="true" /></span>
          <span>Creator<span class="wordmark-accent">Hub</span></span>
        </div>
        <button v-if="phase === 'account'" class="outline-button sign-out" type="button" :disabled="!!busy" @click="logout">
          <LoaderCircle v-if="busy === 'logout'" :size="16" class="spinner" aria-hidden="true" />
          <LogOut v-else :size="16" aria-hidden="true" />
          {{ busy === 'logout' ? '正在退出…' : '退出登录' }}
        </button>
      </div>
    </header>

    <main v-if="phase === 'guest'" class="auth-main">
      <div class="auth-layout">
        <aside class="editorial" aria-label="关于 CreatorHub">
          <div class="editorial-copy">
            <p class="eyebrow"><span></span> 创作者社区</p>
            <p class="hero-title">让好想法<br />被看见<span>。</span></p>
          </div>
          <div class="editorial-art" aria-hidden="true">
            <span class="art-orbit orbit-outer"></span>
            <span class="art-orbit orbit-inner"></span>
            <div class="sheet sheet-back"></div>
            <div class="sheet sheet-middle"></div>
            <div class="sheet sheet-front">
              <span class="art-seal"><PenLine :size="30" :stroke-width="1.65" /></span>
              <span class="ink ink-one"></span>
              <span class="ink ink-two"></span>
              <span class="ink ink-three"></span>
              <span class="ink ink-four"></span>
            </div>
            <span class="art-spark">✦</span>
          </div>
        </aside>

        <section class="auth-panel" aria-labelledby="auth-title">
          <div class="auth-content">
            <div class="form-heading">
              <h1 id="auth-title">{{ isRegister ? '创建账号' : '欢迎回来' }}</h1>
              <p>{{ isRegister ? '填写以下信息，开启你的创作账号。' : '登录 CreatorHub，管理你的创作者账号。' }}</p>
            </div>
            <p v-if="notice" class="feedback success" role="status"><Check :size="18" aria-hidden="true" />{{ notice }}</p>
            <p v-if="error" class="feedback failure" role="alert"><CircleAlert :size="18" aria-hidden="true" />{{ error }}</p>
            <form class="auth-form" :aria-busy="!!busy" @submit.prevent="submitAuth">
              <div v-if="isRegister" class="field">
                <label for="register-name">昵称</label>
                <div class="input-wrap">
                  <UserRound :size="17" aria-hidden="true" />
                  <input id="register-name" v-model="nickname" name="displayName" autocomplete="nickname" placeholder="你的创作者昵称" required :disabled="!!busy" />
                </div>
              </div>
              <div class="field">
                <label for="email">邮箱</label>
                <div class="input-wrap">
                  <Mail :size="17" aria-hidden="true" />
                  <input id="email" v-model="email" type="email" name="email" autocomplete="username" placeholder="name@example.com" maxlength="254" required :disabled="!!busy" />
                </div>
              </div>
              <div class="field">
                <label for="password">密码</label>
                <div class="input-wrap">
                  <LockKeyhole :size="17" aria-hidden="true" />
                  <input id="password" v-model="password" :type="showPassword ? 'text' : 'password'" name="password"
                    :autocomplete="isRegister ? 'new-password' : 'current-password'" :aria-describedby="isRegister ? 'password-help' : undefined"
                    :placeholder="isRegister ? '设置你的密码' : '请输入密码'" maxlength="256" required :disabled="!!busy" />
                  <button type="button" class="icon-button password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">
                    <EyeOff v-if="showPassword" :size="17" aria-hidden="true" />
                    <Eye v-else :size="17" aria-hidden="true" />
                  </button>
                </div>
                <p v-if="isRegister" id="password-help" class="field-hint">使用 15～128 个字符。</p>
              </div>
              <button class="primary-button auth-submit" type="submit" :disabled="!!busy">
                {{ busy ? (isRegister ? '正在创建…' : '正在登录…') : (isRegister ? '创建账号' : '登录') }}
                <LoaderCircle v-if="busy" :size="17" class="spinner" aria-hidden="true" />
                <ArrowRight v-else :size="17" aria-hidden="true" />
              </button>
            </form>
            <div class="auth-switch">
              <span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
              <button class="text-button" type="button" :disabled="!!busy" @click="switchMode">
                {{ isRegister ? '去登录' : '创建账号' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>

    <nav v-if="phase === 'account'" class="workspace-nav" aria-label="创作工作台">
      <button type="button" :aria-current="section === 'account' ? 'page' : undefined" @click="showSection('account')">账号设置</button>
      <button type="button" :aria-current="section === 'drafts' ? 'page' : undefined" @click="showSection('drafts')">我的草稿</button>
    </nav>
    <DraftsWorkspace v-if="phase === 'account' && draftsOpened" v-show="section === 'drafts'"
      :key="draftsKey" :initial-id="draftId" @opened="setDraftId" @published="showPublishedArticle"
      @expired="setGuest('登录已过期，请重新登录。')" />
    <ArticleDetail v-if="phase === 'account' && section === 'article'" :key="articleId" :id="articleId"
      @expired="setGuest('登录已过期，请重新登录。')" />
    <main v-if="phase === 'account' && section === 'account'" class="account-main" aria-labelledby="account-title">
      <div class="account-heading">
        <h1 id="account-title">账号设置</h1>
        <p>查看登录邮箱，管理展示给其他创作者的昵称。</p>
      </div>
      <div class="account-grid">
        <section class="identity-card" aria-label="账号概览">
          <div class="avatar" aria-hidden="true">{{ initial }}</div>
          <h2>{{ user.displayName }}</h2>
          <p>{{ user.email }}</p>
        </section>
        <section class="profile-card" aria-labelledby="profile-title">
          <div class="profile-heading"><span class="profile-icon"><UserRound :size="20" aria-hidden="true" /></span><h2 id="profile-title">个人资料</h2></div>
          <p v-if="notice" class="feedback success" role="status"><Check :size="18" aria-hidden="true" />{{ notice }}</p>
          <p v-if="error" class="feedback failure" role="alert"><CircleAlert :size="18" aria-hidden="true" />{{ error }}</p>
          <form class="profile-form" :aria-busy="!!busy" @submit.prevent="saveProfile">
            <div class="field">
              <label for="profile-name">昵称</label>
              <div class="input-wrap"><input id="profile-name" v-model="profileName" name="displayName" autocomplete="nickname" aria-describedby="nickname-limit" required :disabled="!!busy" /></div>
              <p id="nickname-limit" class="field-hint profile-hint">用于公开展示 <span :class="{ 'over-limit': profileLength > 50 }">{{ profileLength }} / 50</span></p>
            </div>
            <div class="field">
              <label for="profile-email">登录邮箱</label>
              <div class="input-wrap readonly"><Mail :size="17" aria-hidden="true" /><input id="profile-email" :value="user.email" type="email" readonly /></div>
            </div>
            <div class="profile-actions">
              <button v-if="profileChanged" class="outline-button" type="button" :disabled="!!busy" @click="resetProfile">取消</button>
              <button class="primary-button" type="submit" :disabled="!!busy || !profileChanged || profileLength < 1 || profileLength > 50">
                <LoaderCircle v-if="busy === 'profile'" :size="16" class="spinner" aria-hidden="true" />
                {{ busy === 'profile' ? '正在保存…' : '保存修改' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </main>

    <main v-else-if="phase !== 'account' && phase !== 'guest'" class="status-main">
      <div v-if="phase === 'loading'" class="status-card" role="status"><LoaderCircle :size="22" class="spinner" aria-hidden="true" />正在检查登录状态…</div>
      <div v-else class="status-card unavailable">
        <CircleAlert :size="28" aria-hidden="true" />
        <h1>暂时无法连接</h1>
        <p role="alert">{{ error }}</p>
        <button class="primary-button" type="button" @click="loadSession">重新连接</button>
      </div>
    </main>
  </div>
</template>
