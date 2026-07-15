<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'doc-agent',
  name: '文档助手',
  description: 'AI 对话式文档处理，支持 PDF 切分/合并/压缩/转图片、文档转 PDF、Markdown 转 DOCX',
  icon: '🤖',
  category: 'file',
}
</script>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useAgentChat } from '@/composables/useAgentChat'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const {
  messages, state, inputDisabled,
  initChat, sendMessage, cancelProcessing, downloadUrl
} = useAgentChat()

const inputText = ref('')
const chatContainer = ref<HTMLElement | null>(null)
const pendingFiles = ref<File[]>([])

onMounted(() => initChat())

// 自动滚动到底部
watch(messages, async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}, { deep: true })

async function handleSend(): Promise<void> {
  const text = inputText.value.trim()
  const files = [...pendingFiles.value]
  pendingFiles.value = []
  inputText.value = ''
  await sendMessage(text, files.length > 0 ? files : undefined)
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleFileSelect(e: Event): void {
  const target = e.target as HTMLInputElement
  if (target.files) pendingFiles.value.push(...Array.from(target.files))
  target.value = ''
}

function handleDrop(e: DragEvent): void {
  e.preventDefault()
  if (e.dataTransfer?.files) pendingFiles.value.push(...Array.from(e.dataTransfer.files))
}

function removeFile(index: number): void {
  pendingFiles.value.splice(index, 1)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="doc-agent">
    <!-- 顶部 -->
    <header class="agent-header">
      <span class="agent-logo">🤖</span>
      <span class="agent-title">文档助手</span>
      <span class="agent-subtitle">AI 对话式文档处理</span>
    </header>

    <!-- 消息列表 -->
    <div ref="chatContainer" class="agent-chat">
      <div v-for="(msg, i) in messages" :key="i" class="msg-row"
        :class="msg.role === 'user' ? 'msg-row--user' : 'msg-row--bot'">
        <div class="msg-bubble" :class="{
          'msg-bubble--user': msg.role === 'user',
          'msg-bubble--bot': msg.role === 'assistant',
          'msg-bubble--error': msg.isError
        }">
          <!-- 文件附件 -->
          <div v-if="msg.files" class="msg-files">
            <div v-for="f in msg.files" :key="f.name" class="msg-file-chip">
              <span>📎</span>
              <span>{{ f.name }}</span>
              <span class="msg-file-size">{{ formatSize(f.size) }}</span>
            </div>
          </div>

          <!-- 处理中动画 -->
          <span v-if="msg.isProcessing" class="msg-processing">
            <svg width="18" height="18" viewBox="0 0 24 24" class="spinner">
              <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor"
                stroke-width="3" stroke-dasharray="32" stroke-linecap="round"/>
            </svg>
            {{ msg.content || '处理中...' }}
          </span>
          <span v-else class="msg-text">{{ msg.content }}</span>

          <!-- 结果卡片 -->
          <div v-if="msg.result" class="msg-result">
            <div class="msg-result-info">
              📦 {{ msg.result.fileName }} ({{ msg.result.size }})
            </div>
            <a :href="downloadUrl(msg.result.fileId)" class="msg-result-dl">⬇ 下载</a>
          </div>
        </div>
      </div>
    </div>

    <!-- 待发送文件 -->
    <div v-if="pendingFiles.length > 0" class="agent-pending">
      <div v-for="(f, idx) in pendingFiles" :key="f.name + idx" class="pending-chip">
        <span>📎 {{ f.name }}</span>
        <button class="pending-remove" @click="removeFile(idx)">×</button>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="agent-input-row"
      @dragover.prevent
      @drop.prevent="handleDrop">
      <label class="attach-btn" title="上传文件">
        📎
        <input type="file" multiple hidden
          @change="handleFileSelect"
          accept=".pdf,.doc,.docx,.wps,.md" />
      </label>

      <textarea v-model="inputText" @keydown="handleKeydown"
        :disabled="inputDisabled"
        :placeholder="state === 'processing' ? '处理中...' : '输入你的需求，或拖拽文件到此处...'"
        rows="1" class="chat-input" />

      <button v-if="state === 'processing'" class="btn-cancel" @click="cancelProcessing">
        取消
      </button>
      <button v-else class="btn-send"
        :disabled="!inputText.trim() && pendingFiles.length === 0"
        @click="handleSend">
        发送
      </button>
    </div>
  </div>
</template>

<style scoped>
/* ===== 布局 ===== */
.doc-agent {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  background: var(--bg-main);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* ===== 顶部 ===== */
.agent-header {
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-surface);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.agent-logo { font-size: 20px; }
.agent-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.agent-subtitle { font-size: 12px; color: var(--text-muted); margin-left: auto; }

/* ===== 消息列表 ===== */
.agent-chat {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row { display: flex; max-width: 100%; }
.msg-row--user { justify-content: flex-end; }
.msg-row--bot  { justify-content: flex-start; }

.msg-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-bubble--user {
  background: var(--accent-color);
  color: #fff;
}
.msg-bubble--bot {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}
.msg-bubble--error {
  border-color: #e53e3e;
}

/* ===== 文件附件 ===== */
.msg-files { margin-bottom: 6px; display: flex; flex-direction: column; gap: 4px; }
.msg-file-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px; border-radius: 6px;
  background: var(--bg-main); font-size: 12px;
}
.msg-file-size { color: var(--text-muted); }

/* ===== 处理中 ===== */
.msg-processing { display: flex; align-items: center; gap: 8px; }
.spinner { animation: spin 1s linear infinite; color: var(--accent-color); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 结果卡片 ===== */
.msg-result {
  margin-top: 8px; padding: 10px; border-radius: 8px;
  background: var(--bg-main); border: 1px solid var(--border-color);
}
.msg-result-info { font-size: 13px; margin-bottom: 6px; }
.msg-result-dl {
  padding: 4px 12px; border-radius: 6px; font-size: 13px;
  background: var(--accent-color); color: #fff;
  text-decoration: none; display: inline-block;
}

/* ===== 待发送文件 ===== */
.agent-pending {
  padding: 8px 20px; display: flex; gap: 8px; flex-wrap: wrap;
  border-top: 1px solid var(--border-color);
}
.pending-chip {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: 16px;
  background: var(--bg-surface); border: 1px solid var(--border-color);
  font-size: 12px;
}
.pending-remove { background: none; border: none; cursor: pointer; color: var(--text-muted); font-size: 16px; padding: 0; }

/* ===== 底部输入区 ===== */
.agent-input-row {
  padding: 12px 20px; border-top: 1px solid var(--border-color);
  background: var(--bg-surface);
  display: flex; gap: 8px; align-items: flex-end;
  flex-shrink: 0;
}
.attach-btn {
  padding: 8px; border-radius: 8px; cursor: pointer;
  color: var(--text-secondary); font-size: 20px;
  line-height: 1;
}
.chat-input {
  flex: 1; padding: 8px 12px; border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-main); color: var(--text-primary);
  font-size: 14px; resize: none; outline: none;
  font-family: inherit;
}
.chat-input:focus {
  border-color: var(--accent-color);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent-color) 20%, transparent);
}
.btn-send, .btn-cancel {
  padding: 8px 16px; border-radius: 8px; border: none;
  cursor: pointer; font-size: 14px; white-space: nowrap;
}
.btn-send {
  background: var(--accent-color); color: #fff;
}
.btn-send:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-cancel { background: #e53e3e; color: #fff; }
</style>
