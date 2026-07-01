<template>
  <div class="fixed top-4 right-4 z-50 flex flex-col gap-2">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="px-4 py-2.5 rounded-xl shadow-lg backdrop-blur text-sm font-medium flex items-center gap-2 animate-slide-in"
      :class="{
        'bg-emerald-500/90 text-white shadow-emerald-500/20': toast.type === 'success',
        'bg-red-500/90 text-white shadow-red-500/20': toast.type === 'error',
        'bg-indigo-600/90 text-white shadow-indigo-500/20': toast.type === 'info',
      }"
    >
      <span v-if="toast.type === 'success'">✓</span>
      <span v-else-if="toast.type === 'error'">✕</span>
      <span v-else>ℹ</span>
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()
</script>

<style scoped>
@keyframes slide-in {
  from { opacity: 0; transform: translateX(20px); }
  to { opacity: 1; transform: translateX(0); }
}
.animate-slide-in {
  animation: slide-in 0.3s ease-out;
}
</style>
