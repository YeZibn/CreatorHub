<script setup>
import { computed } from 'vue'
import { ArrowDownRight, ArrowUpRight, Settings } from 'lucide-vue-next'

const props = defineProps({
  displayName: { type: String, required: true },
  links: { type: Array, required: true },
})
const initial = computed(() => [...props.displayName][0]?.toUpperCase() || 'C')
</script>

<template>
  <main class="workspace-page home-page" aria-labelledby="home-title">
    <section class="workspace-hero">
      <div class="hero-identity">
        <p class="workspace-caption">个人主页</p>
        <div class="hero-avatar" aria-hidden="true">{{ initial }}</div>
        <h1 id="home-title">{{ displayName }}</h1>
        <a href="#account" class="hero-profile-link"><Settings :size="15" aria-hidden="true" /> 编辑个人资料</a>
      </div>
      <div class="hero-monogram" aria-hidden="true">
        <span>{{ initial }}</span>
        <ArrowDownRight :size="28" :stroke-width="1.4" />
      </div>
    </section>
    <div class="workspace-shortcuts">
      <a v-for="link in links" :key="link.id" :href="link.href" class="workspace-shortcut">
        <span class="workspace-icon" :class="'tone-' + link.id"><component :is="link.icon" :size="21" aria-hidden="true" /></span>
        <span class="shortcut-copy">
          <strong>{{ link.label }}</strong>
          <span>{{ link.description }}</span>
        </span>
        <ArrowUpRight class="shortcut-arrow" :size="19" aria-hidden="true" />
      </a>
    </div>
  </main>
</template>
