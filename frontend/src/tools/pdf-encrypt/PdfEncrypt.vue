<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'

/**
 * 页面状态机:
 *   noFile     — 未选择文件
 *   ready      — 已选文件，等待加密
 *   processing — 加密中，禁止移除文件、禁止修改参数
 *   done       — 加密完成，展示结果
 *   error      — 加密失败
 */
type Stage = 'noFile' | 'ready' | 'processing' | 'done' | 'error'

interface Permission {
  key: string
  label: string
  value: boolean
}

const fileInputRef = ref<HTMLInputElement>()
const uploadedFile = ref<File | null>(null)
const dragOver = ref(false)
const stage = ref<Stage>('noFile')
const errorMsg = ref('')

// 密码
const userPassword = ref('')
const ownerPassword = ref('')
const showUserPwd = ref(false)
const showOwnerPwd = ref(false)

// 权限
const permissions = ref<Permission[]>([
  { key: 'canPrint', label: '允许打印', value: true },
  { key: 'canCopy', label: '允许复制/提取内容', value: true },
  { key: 'canModify', label: '允许修改文档内容', value: true },
  { key: 'canAnnotate', label: '允许编辑注释和填写表单', value: true },
  { key: 'canAssemble', label: '允许页面组装', value: true },
])

// 结果
const resultBlob = ref<Blob | null>(null)
const resultUrl = ref('')

// ===== 计算属性 =====

/** 权限面板是否可编辑（所有者密码满足强度要求后才可编辑） */
const permissionsEditable = computed(() => ownerPwdValid.value && ownerPassword.value.trim().length > 0)

/** 开启的权限数量 */
const enabledCount = computed(() => permissions.value.filter(p => p.value).length)

/** 是否所有权限都开启（需要至少关闭一个） */
const allPermissionsOpen = computed(() => enabledCount.value === permissions.value.length)

/** 密码强度校验 */
const PASSWORD_REGEX = /^(?=.*[0-9])(?=.*[a-zA-Z]).{6,}$/

const userPwdValid = computed(() => {
  if (!userPassword.value) return true // 可选
  return PASSWORD_REGEX.test(userPassword.value)
})

const ownerPwdValid = computed(() => {
  if (!ownerPassword.value) return true // 可选
  return PASSWORD_REGEX.test(ownerPassword.value)
})

const passwordsSame = computed(() => {
  return userPassword.value && ownerPassword.value && userPassword.value === ownerPassword.value
})

const hasAtLeastOnePassword = computed(() => {
  return userPassword.value.trim().length > 0 || ownerPassword.value.trim().length > 0
})

/** 是否可以提交 */
const canSubmit = computed(() => {
  if (!uploadedFile.value || stage.value === 'processing') return false
  if (!hasAtLeastOnePassword.value) return false
  if (!userPwdValid.value || !ownerPwdValid.value) return false
  if (passwordsSame.value) return false
  if (permissionsEditable.value && allPermissionsOpen.value) return false
  return true
})

/** 提交按钮的禁用原因 */
const submitDisabledReason = computed(() => {
  if (!uploadedFile.value) return '请先上传 PDF 文件'
  if (!hasAtLeastOnePassword.value) return '请至少填写一个密码'
  if (!userPwdValid.value) return '用户密码强度不足'
  if (!ownerPwdValid.value) return '所有者密码强度不足'
  if (passwordsSame.value) return '两个密码不能相同'
  if (permissionsEditable.value && allPermissionsOpen.value) return '至少需要关闭一项权限'
  return ''
})

// ===== 方法 =====

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) setFile(files[0])
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    const f = files[0]
    if (f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')) {
      setFile(f)
    }
  }
}

function setFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    errorMsg.value = '文件不能超过 50MB'
    stage.value = 'error'
    return
  }
  uploadedFile.value = file
  errorMsg.value = ''
  clearResult()
  stage.value = 'ready'
}

function clearFile() {
  uploadedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
  clearResult()
  stage.value = 'noFile'
}

function clearResult() {
  resultBlob.value = null
  if (resultUrl.value) {
    URL.revokeObjectURL(resultUrl.value)
    resultUrl.value = ''
  }
  errorMsg.value = ''
}

function togglePermission(key: string) {
  // 权限不可编辑时禁止切换
  if (!permissionsEditable.value) return

  const perm = permissions.value.find(p => p.key === key)
  if (!perm) return

  // 如果只剩一个开启，且点击的是这个开启的，禁止关闭
  if (perm.value && enabledCount.value === 1) return

  perm.value = !perm.value
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(2) + ' MB'
}

async function startEncrypt() {
  if (!uploadedFile.value || !canSubmit.value) return

  clearResult()
  stage.value = 'processing'

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value)
    formData.append('userPassword', userPassword.value)
    formData.append('ownerPassword', ownerPassword.value)

    for (const perm of permissions.value) {
      formData.append(perm.key, String(perm.value))
    }

    const baseUrl = window.location.origin
    const resp = await fetch(`${baseUrl}/api/pdf/encrypt`, {
      method: 'POST',
      body: formData,
    })

    if (!resp.ok) {
      const errText = await resp.text()
      throw new Error(errText || `HTTP ${resp.status}`)
    }

    const blob = await resp.blob()
    resultBlob.value = blob
    resultUrl.value = URL.createObjectURL(blob)
    stage.value = 'done'
  } catch (e: any) {
    errorMsg.value = e.message || '加密失败'
    stage.value = 'error'
  }
}

function downloadResult() {
  if (!resultBlob.value) return
  const url = URL.createObjectURL(resultBlob.value)
  const a = document.createElement('a')
  a.href = url
  a.download = uploadedFile.value
    ? uploadedFile.value.name.replace(/\.pdf$/i, '') + '_encrypted.pdf'
    : 'encrypted.pdf'
  a.click()
  URL.revokeObjectURL(url)
}

onUnmounted(() => {
  if (resultUrl.value) URL.revokeObjectURL(resultUrl.value)
})
</script>

<template>
  <div class="flex gap-4 h-full">
    <!-- ====== 左侧：上传区 + 设置 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">加密设置</label>
      <!-- 虚线外框包裹三个模块 -->
      <div class="flex-1 border-2 border-dashed rounded-2xl p-4 flex flex-col gap-3 overflow-y-auto"
        style="border-color: var(--border-color); background: var(--bg-card)">

        <!-- 上传区 -->
        <div
          class="border rounded-lg flex flex-col items-center justify-center gap-3 transition-colors flex-shrink-0 h-24"
          :class="stage === 'processing' ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30'"
          style="border-color: var(--border-color); background: var(--bg-main)"
          @click="stage !== 'processing' && triggerFileInput()"
          @dragover.prevent="stage !== 'processing' && (dragOver = true)"
          @dragleave.prevent="dragOver = false"
          @drop.prevent="stage !== 'processing' && handleDrop($event)"
        >
          <template v-if="stage === 'noFile'">
            <span class="text-3xl">🔒</span>
            <div class="text-center">
              <p class="text-sm font-medium" style="color: var(--text-primary)">拖拽 PDF 到此处</p>
              <p class="text-xs mt-1" style="color: var(--text-muted)">或点击选择文件 · 最大 50MB</p>
            </div>
          </template>
          <template v-else>
            <div class="flex items-center gap-3">
              <span class="text-2xl">📄</span>
              <div>
                <p class="text-sm font-medium" style="color: var(--text-primary)">{{ uploadedFile!.name }}</p>
                <p class="text-xs" style="color: var(--text-muted)">{{ formatSize(uploadedFile!.size) }}</p>
              </div>
              <button
                v-if="stage !== 'processing'"
                @click.stop="clearFile"
                class="text-xs underline hover:text-red-500 transition-colors"
                style="color: var(--text-muted)">移除</button>
            </div>
          </template>
        </div>

        <input ref="fileInputRef" type="file" accept=".pdf,application/pdf" class="hidden" @change="handleFileSelect" />

        <!-- 密码设置 -->
        <div class="border rounded-lg p-3 transition-opacity"
          :class="stage === 'processing' ? 'opacity-60 pointer-events-none' : ''"
          style="border-color: var(--border-color); background: var(--bg-main)">
        <p class="text-xs font-semibold mb-3" style="color: var(--text-secondary)">密码设置</p>

        <!-- 用户密码 -->
        <div class="mb-3">
          <label class="text-xs mb-1 block" style="color: var(--text-muted)">用户密码（打开密码）</label>
          <div class="relative">
            <input
              v-model="userPassword"
              :type="showUserPwd ? 'text' : 'password'"
              placeholder="≥6位，含数字+字母"
              class="w-full px-3 py-1.5 text-sm rounded-md border outline-none transition-colors"
              :style="{
                borderColor: userPassword && !userPwdValid ? '#ef4444' : 'var(--border-color)',
                background: 'var(--bg-main)',
                color: 'var(--text-primary)',
              }"
            />
            <button
              @click="showUserPwd = !showUserPwd"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-xs"
              style="color: var(--text-muted)">
              {{ showUserPwd ? '🙈' : '👁️' }}
            </button>
          </div>
          <p v-if="userPassword && !userPwdValid" class="text-[10px] mt-1" style="color: #ef4444">
            密码强度不足：至少6位，需包含数字和字母
          </p>
        </div>

        <!-- 所有者密码 -->
        <div>
          <label class="text-xs mb-1 block" style="color: var(--text-muted)">所有者密码（权限密码）</label>
          <div class="relative">
            <input
              v-model="ownerPassword"
              :type="showOwnerPwd ? 'text' : 'password'"
              placeholder="≥6位，含数字+字母"
              class="w-full px-3 py-1.5 text-sm rounded-md border outline-none transition-colors"
              :style="{
                borderColor: ownerPassword && !ownerPwdValid ? '#ef4444' : 'var(--border-color)',
                background: 'var(--bg-main)',
                color: 'var(--text-primary)',
              }"
            />
            <button
              @click="showOwnerPwd = !showOwnerPwd"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-xs"
              style="color: var(--text-muted)">
              {{ showOwnerPwd ? '🙈' : '👁️' }}
            </button>
          </div>
          <p v-if="ownerPassword && !ownerPwdValid" class="text-[10px] mt-1" style="color: #ef4444">
            密码强度不足：至少6位，需包含数字和字母
          </p>
          <p v-else-if="ownerPassword && ownerPwdValid" class="text-[10px] mt-1" style="color: #22c55e">
            ✓ 密码强度符合要求，可设置权限
          </p>
        </div>

        <!-- 密码相同提示 -->
        <p v-if="passwordsSame" class="text-[10px] mt-2" style="color: #ef4444">
          两个密码不能相同
        </p>

        <!-- 至少一个密码提示 -->
        <p v-if="!hasAtLeastOnePassword && (userPassword || ownerPassword)" class="text-[10px] mt-2" style="color: #ef4444">
          请至少填写一个密码
        </p>
      </div>

      <!-- 权限面板（始终展示，密码符合要求后可编辑） -->
      <div class="border rounded-lg p-3 transition-all"
        :class="[
          stage === 'processing' ? 'opacity-60 pointer-events-none' : '',
          !permissionsEditable ? 'opacity-50' : ''
        ]"
        :style="{
          borderColor: 'var(--border-color)',
          background: permissionsEditable ? 'var(--bg-main)' : 'var(--bg-card-hover)',
        }">
        <div class="flex items-center justify-between mb-2">
          <p class="text-xs font-semibold" style="color: var(--text-secondary)">操作权限</p>
          <span v-if="!permissionsEditable" class="text-[10px] px-2 py-0.5 rounded-full"
            style="background: var(--bg-card-hover); color: var(--text-muted)">
            请先填写所有者密码
          </span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-full"
            style="background: #dcfce7; color: #16a34a">
            ✓ 可编辑
          </span>
        </div>

        <div class="space-y-1.5">
          <div
            v-for="perm in permissions" :key="perm.key"
            @click="togglePermission(perm.key)"
            class="flex items-center gap-2 py-1 px-2 rounded transition-colors"
            :class="permissionsEditable
              ? (perm.value ? 'cursor-pointer hover:bg-green-50/30' : 'cursor-pointer hover:bg-red-50/30')
              : 'cursor-not-allowed'"
            :style="{ opacity: permissionsEditable ? (perm.value && enabledCount === 1 ? 0.6 : 1) : 0.5 }"
          >
            <!-- 开关 -->
            <div
              class="w-8 h-4 rounded-full flex items-center transition-all px-0.5"
              :style="{
                background: perm.value ? 'var(--accent-color)' : '#d1d5db',
                justifyContent: perm.value ? 'flex-end' : 'flex-start',
              }"
            >
              <div class="w-3 h-3 rounded-full bg-white shadow-sm"></div>
            </div>
            <span class="text-xs" style="color: var(--text-primary)">{{ perm.label }}</span>
            <span v-if="permissionsEditable && perm.value && enabledCount === 1" class="text-[10px] ml-auto" style="color: var(--text-muted)">
              （至少保留一项）
            </span>
          </div>
        </div>

        <p v-if="permissionsEditable && allPermissionsOpen" class="text-[10px] mt-2" style="color: #ef4444">
          ⚠ 至少需要关闭一项权限
        </p>
      </div>
      </div>
    </div>

    <!-- ====== 中间：加密按钮 ====== -->
    <div class="flex flex-col items-center justify-center flex-shrink-0" style="width: 80px">
      <button
        @click="startEncrypt"
        :disabled="!canSubmit"
        class="flex flex-col items-center gap-1 py-3 px-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
        :style="canSubmit
          ? { background: 'var(--accent-color)', color: '#fff' }
          : { background: 'var(--bg-card-hover)', color: 'var(--text-muted)', cursor: 'not-allowed' }"
        :title="!canSubmit ? submitDisabledReason : ''"
      >
        <svg v-if="stage === 'processing'" class="animate-spin" width="22" height="22" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity="0.2"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-else class="text-base">🔒</span>
        <span class="text-xs">{{ stage === 'processing' ? '加密中' : '加密' }}</span>
      </button>
    </div>

    <!-- ====== 右侧：结果区 ====== -->
    <div class="flex-1 flex flex-col min-w-0">
      <label class="text-xs font-semibold mb-2 flex-shrink-0" style="color: var(--text-secondary)">加密结果</label>
      <div class="flex-1 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-3"
        style="border-color: var(--border-color); background: var(--bg-card)">

        <!-- 空闲 / 等待 -->
        <template v-if="stage === 'noFile' || stage === 'ready'">
          <span class="text-4xl">🔒</span>
          <p class="text-sm" style="color: var(--text-muted)">
            {{ stage === 'noFile' ? '上传 PDF 后设置密码和权限' : '点击"加密"开始' }}
          </p>
        </template>

        <!-- 加密中 -->
        <template v-else-if="stage === 'processing'">
          <svg class="animate-spin" width="40" height="40" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-color)" stroke-width="2" opacity="0.15"/>
            <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent-color)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <p class="text-sm" style="color: var(--text-muted)">正在加密，请稍候...</p>
        </template>

        <!-- 完成 -->
        <template v-else-if="stage === 'done'">
          <span class="text-4xl">✅</span>
          <p class="text-sm font-semibold" style="color: var(--text-primary)">加密完成</p>

          <div class="w-full max-w-xs space-y-2 px-4">
            <div class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">文件名</span>
              <span class="font-medium truncate ml-2" style="color: var(--text-primary)">
                {{ uploadedFile?.name?.replace(/\.pdf$/i, '') + '_encrypted.pdf' }}
              </span>
            </div>
            <div v-if="userPassword" class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">用户密码</span>
              <span class="font-medium" style="color: var(--text-primary)">已设置</span>
            </div>
            <div v-if="ownerPassword" class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">所有者密码</span>
              <span class="font-medium" style="color: var(--text-primary)">已设置</span>
            </div>
            <div v-if="ownerPassword" class="flex justify-between text-xs">
              <span style="color: var(--text-muted)">权限限制</span>
              <span class="font-medium" style="color: var(--text-primary)">
                {{ permissions.filter(p => !p.value).map(p => p.label).join('、') || '无' }}
              </span>
            </div>
          </div>

          <button @click="downloadResult"
            class="px-4 py-2 text-sm rounded-lg text-white transition-colors hover:opacity-90"
            style="background: var(--accent-color)">下载加密文件</button>

          <p class="text-[10px]" style="color: var(--text-muted)">
            可修改密码或权限后重新加密
          </p>
        </template>

        <!-- 错误 -->
        <template v-else-if="stage === 'error'">
          <span class="text-4xl">⚠️</span>
          <p class="text-sm text-center px-4" style="color: #ef4444">{{ errorMsg }}</p>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hidden { display: none; }

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}
.slide-enter-from,
.slide-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: 0;
}
.slide-enter-to,
.slide-leave-from {
  max-height: 500px;
  opacity: 1;
}
</style>
