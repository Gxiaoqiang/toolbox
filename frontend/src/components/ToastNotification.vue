<template>
  <div class="fixed top-4 right-4 z-50 flex flex-col gap-2">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="px-4 py-2 rounded-lg shadow-lg text-sm font-medium transition-all"
      :class="{
        'bg-emerald-500 text-white': toast.type === 'success',
        'bg-red-500 text-white': toast.type === 'error',
        'bg-slate-700 text-white': toast.type === 'info',
      }"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()
</script>
