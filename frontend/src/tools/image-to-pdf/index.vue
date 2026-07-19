<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'image-to-pdf',
  name: '图片转 PDF',
  description: '将多张图片合并为 PDF 文件',
  icon: 'image',
  category: 'file',
  group: '图片工具包',
  requiresBackend: true,
}
</script>

<script setup lang="ts">
import ImageToPdf from './ImageToPdf.vue'
</script>

<template>
  <ImageToPdf />
</template>
