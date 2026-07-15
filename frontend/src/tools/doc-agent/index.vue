<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'doc-agent',
  name: '文档助手',
  description: 'AI 对话式文档处理，支持 PDF 切分/合并/压缩/转图片、文档转 PDF、Markdown 转 DOCX',
  icon: '🤖',
  category: 'file',
  pinned: true,
}
</script>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useAgentChat } from '@/composables/useAgentChat'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

function renderMarkdown(text: string): string {
  if (!text) return ''
  return marked.parse(text) as string
}

const {
  messages, state, inputDisabled,
  initChat, sendMessage, cancelProcessing, downloadUrl
} = useAgentChat()

const inputText = ref('')
const chatContainer = ref<HTMLElement | null>(null)
const pendingFiles = ref<File[]>([])
const textareaRef = ref<HTMLTextAreaElement | null>(null)

onMounted(() => initChat())

watch(messages, async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}, { deep: true })

// 自动调整 textarea 高度
function autoResize(): void {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function handleSend(): Promise<void> {
  const text = inputText.value.trim()
  const files = [...pendingFiles.value]
  if (!text && files.length === 0) return

  // 立即清空输入
  pendingFiles.value = []
  inputText.value = ''
  await nextTick()

  await sendMessage(text, files.length > 0 ? files : undefined)
  // 重置 textarea 高度
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
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
          <div v-if="msg.files && msg.files.length > 0" class="msg-files">
            <div v-for="f in msg.files" :key="f.name" class="msg-file-chip">
              <span class="msg-file-icon">📄</span>
              <span class="msg-file-name">{{ f.name }}</span>
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
          <!-- 助手消息 Markdown 渲染 -->
          <span v-else-if="msg.role === 'assistant'" class="msg-text markdown-body"
            v-html="renderMarkdown(msg.content)" />
          <!-- 用户消息纯文本 -->
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
        <span class="pending-name">📄 {{ f.name }}</span>
        <span class="pending-size">{{ formatSize(f.size) }}</span>
        <button class="pending-remove" @click="removeFile(idx)">×</button>
      </div>
    </div>

    <!-- 底部输入区 — DeepSeek 风格 -->
    <div class="agent-input-area"
      @dragover.prevent
      @drop.prevent="handleDrop">
      <div class="input-box">
        <label class="upload-btn" title="上传文件">
          <span class="upload-icon">📎</span>
          <input type="file" multiple hidden
            @change="handleFileSelect"
            accept=".pdf,.doc,.docx,.wps,.md" />
        </label>
        <textarea ref="textareaRef" v-model="inputText"
          @keydown="handleKeydown"
          @input="autoResize"
          :disabled="inputDisabled"
          :placeholder="state === 'processing' ? '处理中...' : '告诉文档助手你要做什么，比如：把 PDF 切成每 2 页一份'"
          rows="1" class="chat-input" />
        <button v-if="state === 'processing'" class="send-btn cancel-btn" @click="cancelProcessing">
          <span class="send-icon">■</span>
        </button>
        <button v-else class="send-btn" :class="{ 'send-btn--active': !!(inputText.trim() || pendingFiles.length) }"
          :disabled="!inputText.trim() && pendingFiles.length === 0"
          @click="handleSend">
          <span class="send-icon">↑</span>
        </button>
      </div>
      <p class="input-hint">Enter 发送，Shift+Enter 换行 · 支持 PDF / DOCX / WPS / MD 文件</p>
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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
}

/* ===== 消息列表 ===== */
.agent-chat {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.msg-row { display: flex; max-width: 100%; }
.msg-row--user { justify-content: flex-end; }
.msg-row--bot  { justify-content: flex-start; }

.msg-bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
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

/* ===== 文件附件（修复颜色） ===== */
.msg-files { margin-bottom: 8px; display: flex; flex-direction: column; gap: 4px; }
.msg-file-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 10px; border-radius: 8px;
  background: rgba(255,255,255,0.2);
  font-size: 12px; color: inherit;
}
.msg-bubble--bot .msg-file-chip {
  background: var(--bg-main);
  border: 1px solid var(--border-color);
}
.msg-file-icon { font-size: 15px; flex-shrink: 0; }
.msg-file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.msg-file-size { opacity: 0.7; flex-shrink: 0; font-size: 11px; }

/* ===== 处理中 ===== */
.msg-processing { display: flex; align-items: center; gap: 8px; }
.spinner { animation: spin 1s linear infinite; color: var(--accent-color); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 结果卡片 ===== */
.msg-result {
  margin-top: 10px; padding: 10px 12px; border-radius: 10px;
  background: rgba(255,255,255,0.15); border: 1px solid rgba(255,255,255,0.25);
}
.msg-bubble--bot .msg-result {
  background: var(--bg-main); border: 1px solid var(--border-color);
}
.msg-result-info { font-size: 13px; margin-bottom: 8px; }
.msg-result-dl {
  padding: 6px 16px; border-radius: 8px; font-size: 13px;
  background: var(--accent-color); color: #fff;
  text-decoration: none; display: inline-block; font-weight: 500;
}

/* ===== 待发送文件 ===== */
.agent-pending {
  padding: 8px 24px; display: flex; gap: 8px; flex-wrap: wrap;
  border-top: 1px solid var(--border-color); background: var(--bg-surface);
}
.pending-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: 20px;
  background: var(--bg-main); border: 1px solid var(--border-color);
  font-size: 13px; color: var(--text-primary);
}
.pending-name { font-weight: 500; }
.pending-size { color: var(--text-muted); font-size: 11px; }
.pending-remove { background: none; border: none; cursor: pointer; color: var(--text-muted); font-size: 18px; padding: 0 0 0 4px; line-height: 1; }

/* ===== 底部输入区 — DeepSeek 风格 ===== */
.agent-input-area {
  padding: 16px 24px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-surface);
  flex-shrink: 0;
}
.input-box {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 6px 6px 6px 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.input-box:focus-within {
  border-color: var(--accent-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-color) 15%, transparent);
}
.chat-input {
  flex: 1;
  padding: 6px 2px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.6;
  resize: none;
  outline: none;
  font-family: inherit;
  max-height: 160px;
  min-height: 26px;
}
.chat-input::placeholder {
  color: var(--text-muted);
}
.upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px; height: 34px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.upload-btn:hover { background: var(--bg-card-hover); }
.upload-icon { font-size: 18px; }
.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px; height: 34px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  font-size: 18px;
  background: var(--border-color);
  color: var(--text-muted);
  transition: all 0.2s;
}
.send-btn--active {
  background: var(--accent-color);
  color: #fff;
}
.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.cancel-btn {
  background: #e53e3e !important;
  color: #fff !important;
}
.send-icon {
  font-weight: 700;
  line-height: 1;
}
.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  margin: 6px 0 0;
  user-select: none;
}

/* ===== Markdown 渲染 ===== */
.markdown-body :deep(table) {
  border-collapse: collapse; width: 100%; margin: 8px 0;
  font-size: 13px;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--border-color); padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(th) { background: var(--bg-main); font-weight: 600; }
.markdown-body :deep(code) {
  background: var(--bg-main); padding: 2px 6px; border-radius: 4px;
  font-size: 12px; font-family: 'SF Mono', 'Fira Code', monospace;
}
.markdown-body :deep(pre) {
  background: var(--bg-main); padding: 12px; border-radius: 8px;
  overflow-x: auto; font-size: 13px; margin: 8px 0;
}
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(p) { margin: 4px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 4px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--border-color); margin: 10px 0; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin: 12px 0 6px; font-weight: 600;
}
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
</style>
