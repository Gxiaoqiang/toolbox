<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'pdf-encrypt',
  name: 'PDF 加密',
  description: '对 PDF 设置密码和权限保护，控制打印/复制/修改等操作',
  icon: 'lock',
  category: 'file',
  group: 'PDF 工具包',
  requiresBackend: true,
}
</script>

<template>
  <PdfEncrypt />
</template>

<script setup lang="ts">
import PdfEncrypt from './PdfEncrypt.vue'
</script>
