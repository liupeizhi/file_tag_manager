<template>
  <el-dialog
    v-model="visible"
    title="重复文件检测"
    width="92%"
    top="4vh"
    class="dup-dialog"
    destroy-on-close
    @closed="handleClosed"
  >
    <div class="dup-body">
      <!-- 目录选择 -->
      <div class="pick-row">
        <div class="pick-card">
          <div class="pick-title">
            左侧目录
            <span class="pick-tag">基准</span>
          </div>
          <el-select v-model="left.serverId" placeholder="选择服务器" class="server-select">
            <el-option v-for="s in serverStore.servers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <div class="path-line">
            <el-input v-model="left.path" placeholder="/" clearable />
            <el-button @click="openPicker('left')">浏览</el-button>
          </div>
        </div>

        <div class="pick-arrow">↔</div>

        <div class="pick-card">
          <div class="pick-title">右侧目录</div>
          <el-select v-model="right.serverId" placeholder="选择服务器" class="server-select">
            <el-option v-for="s in serverStore.servers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <div class="path-line">
            <el-input v-model="right.path" placeholder="/" clearable />
            <el-button @click="openPicker('right')">浏览</el-button>
          </div>
        </div>
      </div>

      <!-- 选项 -->
      <div class="option-row">
        <span class="opt-label">最小文件</span>
        <el-input-number
          v-model="minSizeMb"
          :min="0"
          :max="102400"
          :step="1"
          size="small"
          controls-position="right"
          class="num-input"
        />
        <span class="opt-unit">MB</span>
        <el-checkbox v-model="ignoreHidden">忽略隐藏/系统文件</el-checkbox>
        <el-tooltip content="对所有候选读完整文件计算哈希，最准但最慢；默认只读头尾各 4MB" placement="top">
          <el-checkbox v-model="strict">严格模式</el-checkbox>
        </el-tooltip>

        <div class="opt-right">
          <el-button v-if="running" @click="cancel">取消比较</el-button>
          <el-button type="primary" :loading="starting" :disabled="running" @click="start">
            开始比较
          </el-button>
        </div>
      </div>

      <!-- 进度 -->
      <div v-if="task" class="progress-box">
        <el-progress
          :percentage="task.percent || 0"
          :status="progressStatus"
          :stroke-width="10"
        />
        <div class="progress-meta">
          <span class="phase">{{ phaseText }}</span>
          <span v-if="task.scannedLeft || task.scannedRight">
            左 {{ task.scannedLeft }} · 右 {{ task.scannedRight }} 个文件
          </span>
          <span v-if="task.candidateCount">
            候选 {{ task.candidateCount }} · 已算 {{ task.hashedCount }}
          </span>
          <span v-if="task.totalBytes">
            已读 {{ formatFileSize(task.readBytes || 0) }} / {{ formatFileSize(task.totalBytes) }}
          </span>
          <span v-if="task.warningCount" class="warn">跳过 {{ task.warningCount }} 个读取失败的文件</span>
          <span v-if="task.status === 'FAILED'" class="error">{{ task.message }}</span>
          <span v-else-if="task.message && task.status !== 'RUNNING'" class="msg">{{ task.message }}</span>
        </div>
        <div class="progress-tip">
          {{ leftLabel }} ↔ {{ rightLabel }}
        </div>
      </div>

      <!-- 结果 -->
      <template v-if="task && task.status === 'DONE' && task.summary">
        <div class="summary">
          <div class="sum-item">
            <b>{{ task.summary.groupCount }}</b><span>重复组</span>
          </div>
          <div class="sum-item">
            <b>{{ task.summary.fileCount }}</b><span>涉及文件</span>
          </div>
          <div class="sum-item highlight">
            <b>{{ formatFileSize(task.summary.reclaimableBytes) }}</b><span>可回收（潜力值）</span>
          </div>
          <div class="sum-item">
            <b>{{ task.summary.crossGroupCount }}</b><span>两边之间</span>
          </div>
          <div class="sum-item">
            <b>{{ task.summary.sameSideGroupCount }}</b><span>各自内部</span>
          </div>
          <div class="sum-item">
            <b>{{ task.summary.verifiedGroupCount }}</b><span>已确认</span>
          </div>
          <div class="sum-actions">
            <el-button size="small" :disabled="!groupTotal" @click="exportCsv">导出 CSV</el-button>
          </div>
        </div>

        <div class="filter-row">
          <el-radio-group v-model="filters.scope" size="small" @change="applyFilters">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="cross">两边之间</el-radio-button>
            <el-radio-button value="same">各自内部</el-radio-button>
          </el-radio-group>
          <span class="opt-label">最小</span>
          <el-input-number
            v-model="filters.minSizeMb"
            :min="0"
            size="small"
            controls-position="right"
            class="num-input"
            @change="applyFilters"
          />
          <span class="opt-unit">MB</span>
          <el-radio-group v-model="filters.verified" size="small" @change="applyFilters">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="yes">已确认</el-radio-button>
            <el-radio-button value="no">未确认</el-radio-button>
          </el-radio-group>
          <span class="filter-count">共 {{ groupTotal }} 组</span>
        </div>

        <div v-if="hintText" class="result-hint">{{ hintText }}</div>

        <el-empty v-if="!groupTotal" :description="emptyText" />

        <div v-else class="group-list">
          <div v-for="g in groups" :key="g.id" class="dup-group">
            <div class="group-head">
              <span class="g-title">{{ g.members.length }} 个副本 · {{ formatFileSize(g.size) }}</span>
              <span class="loc-chip" :class="locationOf(g).cls">{{ locationOf(g).text }}</span>
              <span class="g-sample" :title="g.members[0]?.path">{{ g.members[0]?.relativePath }}</span>
              <el-tag v-if="g.verified" size="small" type="success" effect="plain">已确认</el-tag>
              <span class="g-reclaim">可回收 {{ formatFileSize(g.reclaimableBytes) }}</span>
              <div class="g-actions">
                <el-button link size="small" :loading="verifyingId === g.id" @click="verify(g)">
                  复核
                </el-button>
                <el-button link size="small" @click="copyPaths(g)">复制路径</el-button>
                <el-button link size="small" @click="toggleGroup(g.id)">
                  {{ expanded[g.id] ? '收起' : '展开' }}
                </el-button>
              </div>
            </div>

            <div v-show="expanded[g.id]" class="members">
              <div v-for="m in g.members" :key="m.path" class="member">
                <span class="side" :class="m.side === 'LEFT' ? 'left' : 'right'">
                  {{ m.side === 'LEFT' ? '左' : '右' }}
                </span>
                <span class="m-path" :title="m.path">{{ m.relativePath }}</span>
                <span class="m-time">{{ m.lastModified || '' }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-pagination
          v-if="groupTotal > pageSize"
          class="pager"
          layout="prev, pager, next"
          :total="groupTotal"
          :page-size="pageSize"
          :current-page="page + 1"
          @current-change="handlePageChange"
        />
      </template>
    </div>
  </el-dialog>

  <!-- 目录选择 -->
  <el-dialog v-model="pickerVisible" title="选择目录" width="460px" append-to-body class="dup-picker">
    <div class="picker-server">{{ pickerServerName }}</div>
    <el-tree
      ref="pickerTreeRef"
      lazy
      :load="loadPickerNode"
      :props="treeProps"
      node-key="path"
      highlight-current
      class="picker-tree"
      @node-click="onPickerNodeClick"
    />
    <template #footer>
      <div class="picker-footer">
        <span class="picker-hint">{{ pickerTarget?.path || '/' }}</span>
        <div>
          <el-button @click="pickerVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmPicker">选择此目录</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useServerStore } from '@/store/server'
import { getDirectoryTree } from '@/api/file'
import {
  startDuplicateCompare,
  getDuplicateTask,
  getDuplicateGroups,
  verifyDuplicateGroup,
  cancelDuplicateTask,
  exportDuplicateCsv
} from '@/api/duplicate'
import { formatFileSize } from '@/utils/file-type'

const serverStore = useServerStore()

const visible = ref(false)
const left = reactive({ serverId: null, path: '/' })
const right = reactive({ serverId: null, path: '/' })
const minSizeMb = ref(0)
const ignoreHidden = ref(true)
const strict = ref(false)

const starting = ref(false)
const task = ref(null)
const leftLabel = ref('')
const rightLabel = ref('')

const groups = ref([])
const groupTotal = ref(0)
const page = ref(0)
const pageSize = 20
const filters = reactive({ scope: 'all', minSizeMb: 0, verified: 'all' })
const expanded = reactive({})
const verifyingId = ref('')

let timer = null

const running = computed(() => task.value?.status === 'RUNNING')

/** 一组是「跨两边」还是「某一边内部」 */
function locationOf(group) {
  if (group.cross) return { text: '跨左右', cls: 'cross' }
  const sides = new Set((group.members || []).map((m) => m.side))
  if (sides.size === 1 && sides.has('LEFT')) return { text: '左侧内部', cls: 'left' }
  if (sides.size === 1 && sides.has('RIGHT')) return { text: '右侧内部', cls: 'right' }
  return { text: '同边', cls: 'left' }
}

/** 两边之间没有重复、但各自内部有重复时，明确说出来 */
const hintText = computed(() => {
  const s = task.value?.summary
  if (!s || task.value?.status !== 'DONE') return ''
  if (filters.scope !== 'all') return ''
  if (s.groupCount > 0 && s.crossGroupCount === 0) {
    return '两个目录之间没有相同的文件；下面这些是各自目录内部的重复。'
  }
  if (s.crossGroupCount === 0) return ''
  return ''
})

const emptyText = computed(() => {
  if (filters.scope === 'cross') return '两个目录之间没有相同的文件'
  if (filters.scope === 'same') return '两边各自目录内部都没有重复文件'
  return '没有发现重复文件'
})
const progressStatus = computed(() => {
  const s = task.value?.status
  if (s === 'DONE') return 'success'
  if (s === 'FAILED') return 'exception'
  if (s === 'CANCELLED') return 'warning'
  return ''
})

const phaseText = computed(() => {
  switch (task.value?.phase) {
    case 'SCAN_LEFT': return '正在扫描左侧目录…'
    case 'SCAN_RIGHT': return '正在扫描右侧目录…'
    case 'HASHING': return '正在计算文件指纹（只读，不会改动任何文件）'
    case 'GROUPING': return '正在整理重复组…'
    case 'DONE': return '比较完成'
    default: return ''
  }
})

/* ---------------- 打开 / 关闭 ---------------- */

function open(options = {}) {
  const server = serverStore.currentServer
  const serverId = options.serverId ?? server?.id ?? null
  left.serverId = serverId
  right.serverId = serverId
  left.path = options.path || '/'
  right.path = '/'
  // 每次打开都回到"全部", 免得上次的筛选状态让人以为结果不对
  filters.scope = 'all'
  filters.minSizeMb = 0
  filters.verified = 'all'
  resetResult()
  visible.value = true
}

function resetResult() {
  stopTimer()
  task.value = null
  groups.value = []
  groupTotal.value = 0
  page.value = 0
  verifyingId.value = ''
  Object.keys(expanded).forEach((k) => delete expanded[k])
}

function handleClosed() {
  stopTimer()
  if (task.value?.status === 'RUNNING' && task.value?.id) {
    cancelDuplicateTask(task.value.id).catch(() => {})
  }
}

/* ---------------- 启动与轮询 ---------------- */

async function start() {
  if (left.serverId == null || right.serverId == null) {
    ElMessage.warning('请先选择服务器')
    return
  }
  if (!String(left.path || '').trim() || !String(right.path || '').trim()) {
    ElMessage.warning('请填写目录路径')
    return
  }

  starting.value = true
  resetResult()
  try {
    const res = await startDuplicateCompare({
      leftServerId: left.serverId,
      rightServerId: right.serverId,
      leftPath: left.path.trim(),
      rightPath: right.path.trim(),
      minSize: Math.max(0, Math.round(minSizeMb.value * 1024 * 1024)),
      ignoreHidden: ignoreHidden.value,
      strict: strict.value
    })
    task.value = {
      id: res.taskId,
      status: 'RUNNING',
      phase: 'SCAN_LEFT',
      percent: 0,
      scannedLeft: 0,
      scannedRight: 0,
      candidateCount: 0,
      hashedCount: 0,
      readBytes: 0,
      totalBytes: 0,
      warningCount: 0
    }
    leftLabel.value = `${serverName(left.serverId)} · ${left.path.trim()}`
    rightLabel.value = `${serverName(right.serverId)} · ${right.path.trim()}`
    poll()
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    starting.value = false
  }
}

function serverName(id) {
  return serverStore.servers.find((s) => s.id === id)?.name || '服务器'
}

function poll() {
  stopTimer()
  timer = setInterval(async () => {
    if (!task.value?.id) return
    try {
      const t = await getDuplicateTask(task.value.id)
      task.value = t
      if (t.status !== 'RUNNING') {
        stopTimer()
        if (t.status === 'DONE') {
          page.value = 0
          loadGroups()
        }
      }
    } catch (e) {
      stopTimer()
    }
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function cancel() {
  if (!task.value?.id) return
  try {
    await cancelDuplicateTask(task.value.id)
  } catch (e) {
    // 已提示
  }
}

/* ---------------- 结果 ---------------- */

async function loadGroups() {
  if (!task.value?.id) return
  try {
    const res = await getDuplicateGroups(task.value.id, {
      page: page.value,
      size: pageSize,
      scope: filters.scope === 'all' ? undefined : filters.scope,
      minSize: filters.minSizeMb > 0 ? Math.round(filters.minSizeMb * 1024 * 1024) : undefined,
      verified: filters.verified === 'all' ? undefined : filters.verified
    })
    groups.value = res.items || []
    groupTotal.value = res.total || 0
  } catch (e) {
    groups.value = []
    groupTotal.value = 0
  }
}

function applyFilters() {
  page.value = 0
  loadGroups()
}

function handlePageChange(p) {
  page.value = p - 1
  loadGroups()
}

function toggleGroup(id) {
  expanded[id] = !expanded[id]
}

async function verify(group) {
  verifyingId.value = group.id
  try {
    const res = await verifyDuplicateGroup(task.value.id, group.id)
    if (res.mismatch) {
      ElMessage.warning(res.message || '复核后发现内容并不相同')
    } else {
      ElMessage.success('已确认：内容一致')
      Object.assign(group, res)
    }
    // 复核可能改变统计(误判会被移出结果)
    task.value = await getDuplicateTask(task.value.id)
    await loadGroups()
  } catch (e) {
    // 已提示
  } finally {
    verifyingId.value = ''
  }
}

async function copyPaths(group) {
  const text = group.members.map((m) => m.path).join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制 ${group.members.length} 条路径`)
  } catch (e) {
    // 退回到 textarea 方案
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制路径')
  }
}

async function exportCsv() {
  try {
    const res = await exportDuplicateCsv(task.value.id)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `duplicates-${task.value.id}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

/* ---------------- 目录选择 ---------------- */

const pickerVisible = ref(false)
const pickerSide = ref('left')
const pickerTarget = ref(null)
const pickerTreeRef = ref(null)
const treeProps = { label: 'name', isLeaf: () => false }

const pickerServerName = computed(() => {
  const id = pickerSide.value === 'left' ? left.serverId : right.serverId
  return serverName(id)
})

function openPicker(side) {
  const serverId = side === 'left' ? left.serverId : right.serverId
  if (serverId == null) {
    ElMessage.warning('请先选择服务器')
    return
  }
  pickerSide.value = side
  pickerTarget.value = { path: '/' }
  pickerVisible.value = true
}

async function loadPickerNode(node, resolve) {
  const serverId = pickerSide.value === 'left' ? left.serverId : right.serverId
  const path = node.level === 0 ? '/' : node.data.path
  try {
    const list = await getDirectoryTree(serverId, path, 1, 500, false, 0)
    resolve(
      (list || [])
        .filter((e) => e.isDirectory)
        .map((e) => ({ name: e.name, path: e.path }))
    )
  } catch (e) {
    resolve([])
  }
}

function onPickerNodeClick(data) {
  pickerTarget.value = data
}

function confirmPicker() {
  const target = pickerSide.value === 'left' ? left : right
  target.path = pickerTarget.value?.path || '/'
  pickerVisible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.dup-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}

/* 目录选择 */
.pick-row {
  display: flex;
  align-items: stretch;
  gap: 12px;
}

.pick-card {
  flex: 1;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-secondary);
}

.pick-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.pick-tag {
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  border-radius: 4px;
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.server-select {
  width: 100%;
  margin-bottom: 8px;
}

.path-line {
  display: flex;
  gap: 8px;
}

.path-line .el-input {
  flex: 1;
}

.pick-arrow {
  display: flex;
  align-items: center;
  color: var(--text-muted);
  font-size: 18px;
}

/* 选项 */
.option-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.opt-label {
  font-size: 12px;
  color: var(--text-muted);
}

.opt-unit {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: -4px;
}

.num-input {
  width: 108px;
}

.opt-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* 进度 */
.progress-box {
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-secondary);
}

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.progress-meta .phase {
  color: var(--text-primary);
}

.progress-meta .warn {
  color: #e6a23c;
}

.progress-meta .error {
  color: #f56c6c;
}

.progress-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
  word-break: break-all;
}

/* 汇总 */
.summary {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-secondary);
}

.sum-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sum-item b {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.sum-item span {
  font-size: 12px;
  color: var(--text-muted);
}

.sum-item.highlight b {
  color: #e6a23c;
}

.sum-actions {
  margin-left: auto;
}

/* 过滤 */
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-muted);
}

/* 重复组 */
.group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 46vh;
  overflow: auto;
  padding-right: 4px;
}

.dup-group {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-card);
  overflow: hidden;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  flex-wrap: wrap;
}

.g-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.loc-chip {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  border-radius: 4px;
  border: 1px solid transparent;
}

.loc-chip.cross {
  color: #e6a23c;
  border-color: rgba(230, 162, 60, 0.4);
  background: rgba(230, 162, 60, 0.12);
}

.loc-chip.left {
  color: #409eff;
  border-color: rgba(64, 158, 255, 0.4);
  background: rgba(64, 158, 255, 0.12);
}

.loc-chip.right {
  color: #67c23a;
  border-color: rgba(103, 194, 58, 0.4);
  background: rgba(103, 194, 58, 0.12);
}

.result-hint {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-secondary);
}

.g-sample {
  flex: 1;
  min-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-muted);
}

.g-reclaim {
  font-size: 12px;
  color: #e6a23c;
}

.g-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.members {
  border-top: 1px dashed var(--border-color);
  padding: 6px 12px 10px;
}

.member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  font-size: 12px;
}

.side {
  flex-shrink: 0;
  width: 20px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 4px;
  font-size: 11px;
}

.side.left {
  color: #409eff;
  background: rgba(64, 158, 255, 0.12);
}

.side.right {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.12);
}

.m-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.m-time {
  flex-shrink: 0;
  color: var(--text-muted);
}

.pager {
  justify-content: center;
}

/* 目录选择器 */
.picker-server {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.picker-tree {
  max-height: 46vh;
  overflow: auto;
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.picker-hint {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
