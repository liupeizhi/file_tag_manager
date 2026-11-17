<template>
  <el-dialog
    v-model="visible"
    title="标签节点关系图"
    class="tag-graph-dialog"
    width="92%"
    top="4vh"
    destroy-on-close
    @opened="handleOpened"
  >
    <div class="graph-toolbar">
      <div class="graph-hint">
        原点 → 标签组 → 标签层级
        <span v-if="stats" class="graph-stats">
          （{{ stats.groups }} 个分组 · {{ stats.tags }} 个标签）
        </span>
      </div>
      <div class="graph-actions">
        <el-button-group size="small">
          <el-button @click="zoomBy(1.2)" :disabled="!hasData">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
          <el-button @click="zoomBy(1 / 1.2)" :disabled="!hasData">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <el-button @click="resetView" :disabled="!hasData">重置</el-button>
        </el-button-group>
      </div>
    </div>

    <div
      ref="viewportRef"
      class="graph-viewport"
      :class="{ dragging: isDragging }"
      v-loading="loading"
      @mousedown="startDrag"
      @mousemove="onDrag"
      @mouseup="endDrag"
      @mouseleave="endDrag"
      @wheel.prevent="onWheel"
    >
      <el-empty v-if="!loading && !hasData" description="暂无标签数据" />

      <svg
        v-else
        class="graph-svg"
        :width="svgSize.width * scale"
        :height="svgSize.height * scale"
        :viewBox="`0 0 ${svgSize.width} ${svgSize.height}`"
        :style="{ transform: `translate(${offset.x}px, ${offset.y}px)` }"
      >
        <!-- 连线 -->
        <g class="graph-links">
          <path
            v-for="link in links"
            :key="link.key"
            :d="link.d"
            :stroke="link.color"
            fill="none"
          />
        </g>

        <!-- 节点 -->
        <g class="graph-nodes">
          <g
            v-for="node in nodes"
            :key="node.id"
            :transform="`translate(${node.x - node.width / 2}, ${node.y})`"
          >
            <rect
              class="node-box"
              :class="`node-${node.type}`"
              :width="node.width"
              :height="node.height"
              rx="8"
              :style="node.type === 'tag' ? { stroke: node.color } : {}"
            />
            <rect
              v-if="node.type === 'tag'"
              class="node-accent"
              x="0"
              y="0"
              width="3"
              :height="node.height"
              rx="1.5"
              :fill="node.color"
            />
            <text
              class="node-label"
              :x="node.width / 2 + (node.type === 'tag' ? 2 : 0)"
              :y="node.height / 2"
              text-anchor="middle"
              dominant-baseline="central"
            >
              {{ node.label }}
            </text>
            <title>{{ node.title }}</title>
          </g>
        </g>
      </svg>
    </div>

    <template #footer>
      <div class="graph-legend">
        <span class="legend-item"><i class="dot dot-root"></i>原点</span>
        <span class="legend-item"><i class="dot dot-group"></i>标签组</span>
        <span class="legend-item"><i class="dot dot-tag"></i>标签</span>
        <span class="legend-tip">滚轮缩放 · 拖拽平移</span>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { getTagGroups } from '@/api/tag'

const visible = ref(false)

/* ---------- 布局参数 ---------- */
const NODE_HEIGHT = 34
const NODE_WIDTH = 120
const ROOT_WIDTH = 90
const GROUP_WIDTH = 130
const H_GAP = 16
const V_GAP = 62
const PADDING = 40

const loading = ref(false)
const nodes = ref([])
const links = ref([])
const svgSize = ref({ width: 0, height: 0 })
const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const viewportRef = ref(null)
const hasData = ref(false)
const stats = ref(null)

const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })

/* ---------- 数据 -> 树 ---------- */
function buildTree(groups) {
  const root = {
    id: 'root',
    label: '原点',
    type: 'root',
    color: '#1a1a1a',
    width: ROOT_WIDTH,
    title: '原点',
    children: []
  }

  let tagCount = 0

  const mapTag = (tag, depth) => {
    tagCount++
    return {
      id: `tag-${tag.id}`,
      label: tag.name,
      type: 'tag',
      color: tag.color || '#909399',
      width: NODE_WIDTH,
      title: tag.name,
      children: (tag.children || []).map((c) => mapTag(c, depth + 1))
    }
  }

  groups.forEach((group) => {
    root.children.push({
      id: `group-${group.id}`,
      label: group.name,
      type: 'group',
      color: '#606266',
      width: GROUP_WIDTH,
      title: `${group.name}（${(group.tags || []).length} 个顶级标签）`,
      children: (group.tags || []).map((t) => mapTag(t, 2))
    })
  })

  return { root, tagCount }
}

/* ---------- 计算子树宽度 ---------- */
function measure(node) {
  if (!node.children || node.children.length === 0) {
    node.subtreeWidth = node.width
    return node.subtreeWidth
  }
  let total = 0
  node.children.forEach((child, i) => {
    total += measure(child)
    if (i > 0) total += H_GAP
  })
  node.subtreeWidth = Math.max(node.width, total)
  return node.subtreeWidth
}

/* ---------- 递归放置 ---------- */
function place(node, centerX, depth, out) {
  const y = PADDING + depth * (NODE_HEIGHT + V_GAP)
  out.nodes.push({
    id: node.id,
    label: node.label,
    type: node.type,
    color: node.color,
    width: node.width,
    height: NODE_HEIGHT,
    title: node.title,
    x: centerX,
    y
  })

  if (node.children && node.children.length) {
    let total = 0
    node.children.forEach((child, i) => {
      total += child.subtreeWidth
      if (i > 0) total += H_GAP
    })
    let cursor = centerX - total / 2
    node.children.forEach((child) => {
      const childCenter = cursor + child.subtreeWidth / 2
      out.links.push({
        key: `${node.id}->${child.id}`,
        d: curve(centerX, y + NODE_HEIGHT, childCenter, PADDING + (depth + 1) * (NODE_HEIGHT + V_GAP)),
        color: child.type === 'tag' ? `${child.color}88` : '#d9d9d9'
      })
      place(child, childCenter, depth + 1, out)
      cursor += child.subtreeWidth + H_GAP
    })
  }
}

function curve(x1, y1, x2, y2) {
  const mid = (y1 + y2) / 2
  return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`
}

function layout(groups) {
  const { root, tagCount } = buildTree(groups)
  measure(root)
  const out = { nodes: [], links: [] }
  place(root, PADDING + root.subtreeWidth / 2, 0, out)

  const maxY = out.nodes.reduce((m, n) => Math.max(m, n.y + n.height), 0)
  nodes.value = out.nodes
  links.value = out.links
  svgSize.value = {
    width: root.subtreeWidth + PADDING * 2,
    height: maxY + PADDING
  }
  hasData.value = out.nodes.length > 1
  stats.value = { groups: groups.length, tags: tagCount }
}

/* ---------- 交互 ---------- */
async function loadData() {
  loading.value = true
  try {
    const groups = (await getTagGroups()) || []
    layout(groups)
  } catch (e) {
    ElMessage.error('加载标签数据失败')
    nodes.value = []
    links.value = []
    hasData.value = false
  } finally {
    loading.value = false
  }
}

function handleOpened() {
  loadData().then(() => nextTick(fitToView))
}

function fitToView() {
  const vp = viewportRef.value
  if (!vp || !svgSize.value.width) return
  const vw = vp.clientWidth
  const vh = vp.clientHeight
  const sx = (vw - 24) / svgSize.value.width
  const sy = (vh - 24) / svgSize.value.height
  // 内容比视口大时缩小以整体可见, 否则保持 1:1
  scale.value = Math.min(1, Math.max(0.25, Math.min(sx, sy)))
  offset.value = { x: 0, y: 0 }
}

function resetView() {
  fitToView()
}

function zoomBy(factor) {
  scale.value = Math.min(3, Math.max(0.2, scale.value * factor))
}

function onWheel(e) {
  if (!hasData.value) return
  const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1
  scale.value = Math.min(3, Math.max(0.2, scale.value * factor))
}

function startDrag(e) {
  if (!hasData.value) return
  isDragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY, ox: offset.value.x, oy: offset.value.y }
}

function onDrag(e) {
  if (!isDragging.value) return
  offset.value = {
    x: dragStart.value.ox + (e.clientX - dragStart.value.x),
    y: dragStart.value.oy + (e.clientY - dragStart.value.y)
  }
}

function endDrag() {
  isDragging.value = false
}

function open() {
  visible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.tag-graph-dialog :deep(.el-dialog__body) {
  padding: 0 16px 8px;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 10px;
  gap: 12px;
}

.graph-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.graph-stats {
  color: var(--text-secondary);
}

.graph-viewport {
  position: relative;
  height: 68vh;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-primary);
  background-image: radial-gradient(circle, var(--border-light) 1px, transparent 1px);
  background-size: 18px 18px;
  cursor: grab;
}

.graph-viewport.dragging {
  cursor: grabbing;
}

.graph-svg {
  display: block;
  transform-origin: 0 0;
}

.graph-links path {
  stroke-width: 1.4;
  stroke-linecap: round;
}

.node-box {
  fill: var(--bg-card);
  stroke: var(--border-color);
  stroke-width: 1;
}

/*
 * 原点不直接用 --text-primary 填充: 自定义主题下它可能是任意亮度,
 * 没法保证与文字的反色对比。改为卡片底色 + 加粗描边, 任何主题都清晰。
 */
.node-root {
  fill: var(--bg-card);
  stroke: var(--text-primary);
  stroke-width: 2;
}

.node-group {
  fill: var(--bg-secondary);
  stroke: var(--border-color);
}

.node-tag {
  stroke-width: 1.4;
}

.node-label {
  font-family: var(--font-ui);
  font-size: 12px;
  fill: var(--text-primary);
  pointer-events: none;
}

/* 原点加粗, 与普通标签区分 */
.node-root + .node-label {
  fill: var(--text-primary);
  font-weight: 600;
}

.graph-nodes g:hover .node-box {
  stroke: var(--text-primary);
  stroke-width: 1.6;
}

.graph-nodes g:hover .node-label {
  font-weight: 600;
}

.graph-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--text-muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  display: inline-block;
}

.dot-root {
  background: var(--text-primary);
}

.dot-group {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.dot-tag {
  background: var(--bg-card);
  border: 1px solid var(--text-secondary);
}

.legend-tip {
  margin-left: auto;
}
</style>
