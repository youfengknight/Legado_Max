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
            <div class="info-item">
              <span class="label">项目</span>
              <span class="value">{{ backupOverview.items.length }} 项</span>
            </div>
          </div>

          <div class="category-list">
            <div
              v-for="cat in categories"
              :key="cat.name"
              class="category-item"
            >
              <div class="category-header" @click="toggleCategory(cat.name)">
                <span class="category-icon">{{ cat.icon }}</span>
                <span class="category-name">{{ cat.name }}</span>
                <span class="category-count">{{ cat.items.length }} 项</span>
                <span class="category-size">{{ formatSize(cat.totalSize) }}</span>
                <span class="category-arrow" :class="{ expanded: expandedCategories[cat.name] }">›</span>
              </div>
              <transition name="expand">
                <div v-if="expandedCategories[cat.name]" class="category-detail">
                  <div
                    v-for="item in cat.items"
                    :key="item.fileName"
                    class="detail-item"
                  >
                    <div class="detail-main">
                      <span class="detail-icon">{{ getFileIcon(item.fileName) }}</span>
                      <div class="detail-info">
                        <span class="detail-name">{{ item.displayName }}</span>
                        <span class="detail-desc">{{ item.description }}</span>
                      </div>
                      <div class="detail-meta">
                        <span v-if="item.count > 0" class="detail-count">{{ item.count }} 条</span>
                        <span class="detail-size">{{ formatSize(item.size) }}</span>
                      </div>
                    </div>
                    <div class="detail-file-row">
                      <span class="detail-filename">{{ item.fileName }}</span>
                      <span class="detail-filesize">{{ formatSize(item.size) }}</span>
                    </div>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>
      </transition>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
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

interface CategoryGroup {
  name: string
  icon: string
  items: BackupItemInfo[]
  totalSize: number
}

const isBackingUp = ref(false)
const backupOverview = ref<BackupOverview | null>(null)
const errorMsg = ref('')
const expandedCategories = reactive<Record<string, boolean>>({})

const categoryConfig = [
  { name: '书籍相关', icon: '📚', keywords: ['bookshelf', 'bookmark', 'bookGroup', 'readRecord'] },
  { name: '源相关', icon: '📡', keywords: ['bookSource', 'rssSource', 'rssStar', 'sourceSub'] },
  { name: '规则相关', icon: '🔧', keywords: ['replaceRule', 'txtTocRule', 'dictRule', 'keyboardAssist'] },
  { name: '语音相关', icon: '🔊', keywords: ['httpTTS'] },
  { name: '配置相关', icon: '⚙️', keywords: ['config', 'videoConfig', 'readConfig', 'shareConfig', 'themeConfig', 'coverConfig', 'servers'] },
  { name: '其他', icon: '📁', keywords: ['searchHistory', 'DirectLinkUpload'] },
]

const categories = computed<CategoryGroup[]>(() => {
  if (!backupOverview.value) return []
  const items = backupOverview.value.items
  const result: CategoryGroup[] = []

  for (const cfg of categoryConfig) {
    const matched = items.filter(item =>
      cfg.keywords.some(kw => item.fileName.toLowerCase().includes(kw.toLowerCase()))
    )
    if (matched.length > 0) {
      result.push({
        name: cfg.name,
        icon: cfg.icon,
        items: matched,
        totalSize: matched.reduce((sum, i) => sum + i.size, 0),
      })
    }
  }

  const assigned = new Set(result.flatMap(c => c.items.map(i => i.fileName)))
  const remaining = items.filter(i => !assigned.has(i.fileName))
  if (remaining.length > 0) {
    result.push({
      name: '其他',
      icon: '📁',
      items: remaining,
      totalSize: remaining.reduce((sum, i) => sum + i.size, 0),
    })
  }

  return result
})

const toggleCategory = (name: string) => {
  expandedCategories[name] = !expandedCategories[name]
}

const getFileIcon = (fileName: string): string => {
  if (fileName.endsWith('.xml')) return '📄'
  if (fileName.includes('bookshelf')) return '📖'
  if (fileName.includes('bookmark')) return '🔖'
  if (fileName.includes('bookGroup')) return '📂'
  if (fileName.includes('bookSource')) return '📡'
  if (fileName.includes('rssSource')) return '📰'
  if (fileName.includes('rssStar')) return '⭐'
  if (fileName.includes('replaceRule')) return '🔄'
  if (fileName.includes('readRecord')) return '📊'
  if (fileName.includes('searchHistory')) return '🔍'
  if (fileName.includes('sourceSub')) return '🔗'
  if (fileName.includes('txtTocRule')) return '📑'
  if (fileName.includes('httpTTS')) return '🔊'
  if (fileName.includes('keyboardAssist')) return '⌨️'
  if (fileName.includes('dictRule')) return '📝'
  if (fileName.includes('servers')) return '🖥️'
  if (fileName.includes('config')) return '⚙️'
  return '📄'
}

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
  Object.keys(expandedCategories).forEach(k => delete expandedCategories[k])

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
      categories.value.forEach(c => {
        expandedCategories[c.name] = true
      })
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
  align-items: flex-start;
  justify-content: center;
  background: #f0f2f5;
  padding: 40px 20px;

  &.dark {
    background: #141414;
  }
}

.backup-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  max-width: 560px;
  width: 100%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  text-align: center;

  .dark & {
    background: #1f1f1f;
  }
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 6px 0;

  .dark & {
    color: #e5eaf3;
  }
}

.subtitle {
  font-size: 13px;
  color: #888;
  margin: 0 0 28px 0;

  .dark & {
    color: #666;
  }
}

.download-btn {
  width: 100%;
  padding: 14px 32px;
  font-size: 15px;
  font-weight: 500;
  color: #fff;
  background: #4a7af5;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover:not(:disabled) {
    background: #3a6ae0;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &.loading {
    background: #999;
  }
}

.result-section {
  margin-top: 28px;
  text-align: left;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;

  .dark & {
    border-bottom-color: #333;
    color: #e5eaf3;
  }

  .success-icon {
    color: #52c41a;
    font-size: 16px;
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
  padding: 14px 0;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .label {
      font-size: 12px;
      color: #999;
    }

    .value {
      font-size: 13px;
      font-weight: 500;
      color: #1a1a1a;

      .dark & {
        color: #e5eaf3;
      }
    }
  }
}

.category-list {
  margin-top: 4px;
}

.category-item {
  margin-bottom: 6px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #eee;

  .dark & {
    border-color: #2e2e2e;
  }
}

.category-header {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: #fafafa;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;

  &:hover {
    background: #f5f5f5;
  }

  .dark & {
    background: #262626;

    &:hover {
      background: #2a2a2a;
    }
  }

  .category-icon {
    font-size: 18px;
    margin-right: 8px;
  }

  .category-name {
    flex: 1;
    font-size: 13px;
    font-weight: 600;
    color: #1a1a1a;

    .dark & {
      color: #e5eaf3;
    }
  }

  .category-count {
    font-size: 12px;
    color: #4a7af5;
    margin-right: 10px;
  }

  .category-size {
    font-size: 12px;
    color: #999;
    margin-right: 10px;
  }

  .category-arrow {
    font-size: 16px;
    color: #bbb;
    transition: transform 0.25s ease;

    &.expanded {
      transform: rotate(90deg);
    }
  }
}

.category-detail {
  padding: 2px 0;
  background: #fff;
  border-top: 1px solid #f0f0f0;

  .dark & {
    background: #1f1f1f;
    border-top-color: #2e2e2e;
  }
}

.detail-item {
  padding: 8px 14px;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .dark & {
    border-bottom-color: #262626;
  }
}

.detail-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.detail-info {
  flex: 1;
  min-width: 0;

  .detail-name {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: #1a1a1a;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    .dark & {
      color: #e5eaf3;
    }
  }

  .detail-desc {
    display: block;
    font-size: 11px;
    color: #999;
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.detail-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;

  .detail-count {
    font-size: 12px;
    color: #4a7af5;
    font-weight: 500;
  }

  .detail-size {
    font-size: 11px;
    color: #bbb;
  }
}

.detail-file-row {
  display: flex;
  align-items: center;
  margin-top: 5px;
  padding: 3px 24px;
  background: #fafafa;
  border-radius: 3px;

  .dark & {
    background: #262626;
  }

  .detail-filename {
    flex: 1;
    font-size: 11px;
    font-family: 'Courier New', monospace;
    color: #999;

    .dark & {
      color: #666;
    }
  }

  .detail-filesize {
    font-size: 11px;
    color: #bbb;
  }
}

.error-msg {
  margin-top: 14px;
  padding: 10px;
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
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  max-height: 600px;
}

@media screen and (max-width: 520px) {
  .backup-card {
    padding: 24px 18px;
  }

  .title {
    font-size: 20px;
  }

  .result-info {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 12px;

    .info-item {
      min-width: 80px;
    }
  }
}
</style>
