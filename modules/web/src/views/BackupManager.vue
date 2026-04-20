<template>
  <div :class="{ 'backup-page': true, dark: isNight }">
    <div class="backup-card">
      <h1 class="title">数据备份</h1>
      <p class="subtitle">一键备份您的所有阅读数据</p>

      <button
        class="download-btn"
        :class="{ loading: isBackingUp }"
        :disabled="isBackingUp"
        @click="handleBackup"
      >
        <span v-if="!isBackingUp">点击下载备份压缩包</span>
        <span v-else>正在备份...</span>
      </button>

      <transition name="fade">
        <div v-if="backupOverview" class="result-section">
          <div class="result-header">
            <span class="success-icon">✓</span>
            <span>备份成功</span>
            <span class="time">{{ formatTime(backupOverview.createTime) }}</span>
          </div>

          <div class="result-info">
            <div class="info-item">
              <span class="label">文件名</span>
              <span class="value">{{ backupOverview.fileName }}</span>
            </div>
            <div class="info-item">
              <span class="label">大小</span>
              <span class="value">{{ formatSize(backupOverview.totalSize) }}</span>
            </div>
          </div>

          <div class="file-list">
            <div class="list-title">包含内容</div>
            <div class="list-content">
              <div
                v-for="item in backupOverview.items"
                :key="item.fileName"
                class="file-item"
              >
                <span class="file-name">{{ item.displayName }}</span>
                <span class="file-count">{{ item.count }} 条</span>
                <span class="file-size">{{ formatSize(item.size) }}</span>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useBookStore } from '@/store'
import { legado_http_entry_point } from '@/api'

const store = useBookStore()
const isNight = computed(() => store.isNight)

interface BackupItemInfo {
  fileName: string
  displayName: string
  description: string
  count: number
  size: number
}

interface BackupOverview {
  fileName: string
  totalSize: number
  createTime: number
  items: BackupItemInfo[]
}

const isBackingUp = ref(false)
const backupOverview = ref<BackupOverview | null>(null)
const errorMsg = ref('')

const formatSize = (size: number): string => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

const formatTime = (timestamp: number): string => {
  return new Date(timestamp).toLocaleString('zh-CN')
}

const handleBackup = async () => {
  isBackingUp.value = true
  errorMsg.value = ''
  backupOverview.value = null

  try {
    const response = await fetch(`${legado_http_entry_point}backup`, {
      method: 'GET',
    })

    if (!response.ok) {
      throw new Error(`备份失败: ${response.statusText}`)
    }

    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'backup.zip'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)

    const previewResponse = await fetch(`${legado_http_entry_point}backupPreview`)
    const previewData = await previewResponse.json()

    if (previewData.isSuccess) {
      backupOverview.value = previewData.data
    }
  } catch (e: any) {
    errorMsg.value = e.message || '备份过程中发生错误'
  } finally {
    isBackingUp.value = false
  }
}
</script>

<style lang="scss" scoped>
.backup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;

  &.dark {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }
}

.backup-card {
  background: #fff;
  border-radius: 16px;
  padding: 48px;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  text-align: center;

  .dark & {
    background: #2d2d2d;
  }
}

.title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 8px 0;

  .dark & {
    color: #e5eaf3;
  }
}

.subtitle {
  font-size: 14px;
  color: #666;
  margin: 0 0 32px 0;

  .dark & {
    color: #999;
  }
}

.download-btn {
  width: 100%;
  padding: 16px 32px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  &.loading {
    background: #999;
  }
}

.result-section {
  margin-top: 32px;
  text-align: left;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
  font-size: 16px;
  font-weight: 500;
  color: #1a1a2e;

  .dark & {
    border-bottom-color: #444;
    color: #e5eaf3;
  }

  .success-icon {
    color: #52c41a;
    font-size: 18px;
  }

  .time {
    margin-left: auto;
    font-size: 12px;
    font-weight: 400;
    color: #999;
  }
}

.result-info {
  display: flex;
  gap: 24px;
  padding: 16px 0;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .label {
      font-size: 12px;
      color: #999;
    }

    .value {
      font-size: 14px;
      font-weight: 500;
      color: #1a1a2e;

      .dark & {
        color: #e5eaf3;
      }
    }
  }
}

.file-list {
  .list-title {
    font-size: 13px;
    color: #999;
    margin-bottom: 12px;
  }

  .list-content {
    max-height: 200px;
    overflow-y: auto;
  }

  .file-item {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    background: #f8f9fa;
    border-radius: 6px;
    margin-bottom: 8px;
    font-size: 13px;

    .dark & {
      background: #363636;
    }

    .file-name {
      flex: 1;
      color: #1a1a2e;

      .dark & {
        color: #e5eaf3;
      }
    }

    .file-count {
      color: #667eea;
      margin-right: 16px;
    }

    .file-size {
      color: #999;
      font-size: 12px;
    }
  }
}

.error-msg {
  margin-top: 16px;
  padding: 12px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 6px;
  color: #ff4d4f;
  font-size: 13px;

  .dark & {
    background: #2a1215;
    border-color: #58181c;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media screen and (max-width: 520px) {
  .backup-card {
    padding: 32px 24px;
  }

  .title {
    font-size: 24px;
  }

  .result-info {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
