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
        <el-radio-group v-model="layoutMode" size="small" @change="handleLayoutChange">
          <el-radio-button v-for="l in LAYOUTS" :key="l.value" :value="l.value">
            {{ l.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button-group size="small">
          <el-button @click="zoomBy(1.2)" :disabled="!hasData">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
          <el-button @click="zoomBy(1 / 1.2)" :disabled="!hasData">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <el-button @click="fitToView" :disabled="!hasData">重置</el-button>
        </el-button-group>
      </div>
    </div>

    <div
      ref="viewportRef"
      class="graph-viewport"
      :class="{ panning: isPanning }"
      v-loading="loading"
      @mousedown="startPan"
      @mousemove="onPointerMove"
      @mouseup="endPointer"
      @mouseleave="endPointer"
      @wheel.prevent="onWheel"
    >
      <el-empty v-if="!loading && !hasData" description="暂无标签数据" />

      <svg
        v-else
        class="graph-svg"
        :width="svgSize.width * scale"
        :height="svgSize.height * scale"
        :viewBox="`0 0 ${svgSize.width} ${svgSize.height}`"
        :style="{ transform: `translate(${view.x}px, ${view.y}px)` }"
      >
        <g class="graph-links">
          <path
            v-for="link in links"
            :key="link.key"
            :d="link.path"
            :stroke="link.color"
            fill="none"
          />
        </g>

        <g class="graph-nodes">
          <g
            v-for="node in nodes"
            :key="node.id"
            :style="nodeStyle(node)"
            @mousedown.stop="startNodeDrag($event, node)"
          >
            <rect
              class="node-box"
              :class="`node-${node.type}`"
              :x="-node.width / 2"
              y="0"
              :width="node.width"
              :height="node.height"
              rx="8"
              :style="node.type === 'tag' ? { stroke: node.color } : {}"
            />
            <rect
              v-if="node.type === 'tag'"
              class="node-accent"
              :x="-node.width / 2"
              y="0"
              width="3"
              :height="node.height"
              rx="1.5"
              :fill="node.color"
            />
            <text
              class="node-label"
              x="0"
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
        <span class="legend-tip">拖动节点会弹回原位 · 空白处拖拽平移 · 滚轮缩放</span>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { getTagGroups } from '@/api/tag'
import { computeLayout, LAYOUTS } from '@/utils/tag-graph-layout'

const visible = ref(false)
const loading = ref(false)
const hasData = ref(false)
const stats = ref(null)
const viewportRef = ref(null)

const layoutMode = ref('snowflake')
const nodes = ref([])
const links = ref([])
const svgSize = ref({ width: 0, height: 0 })
const scale = ref(1)
const view = ref({ x: 0, y: 0 })

/** 节点拖动偏移: id -> { dx, dy, springing } */
const dragOffsets = ref({})

const isPanning = ref(false)
let panStart = null
let dragging = null

let rawGroups = []

/* ---------------- 数据 ---------------- */
function buildTree(groups) {
  let tagCount = 0
  const mapTag = (tag) => {
    tagCount++
    return {
      id: `tag-${tag.id}`,
      label: tag.name,
      type: 'tag',
      color: tag.color || '#909399',
      title: tag.name,
      children: (tag.children || []).map(mapTag)
    }
  }
  // 注意: 必须先构造 root(触发 mapTag 累加), 再读 tagCount。
  // 若写成 { tagCount, root: {...} } 字面量, tagCount 会在 root 构建前求值, 恒为 0。
  const root = {
    id: 'root',
    label: '原点',
    type: 'root',
    color: '#909399',
    title: '原点',
    children: groups.map((g) => ({
      id: `group-${g.id}`,
      label: g.name,
      type: 'group',
      color: '#909399',
      title: `${g.name}（${(g.tags || []).length} 个顶级标签）`,
      children: (g.tags || []).map(mapTag)
    }))
  }
  return { root, tagCount }
}

function applyLayout() {
  if (!rawGroups.length && !hasData.value) return
  const { root, tagCount } = buildTree(rawGroups)
  const result = computeLayout(root, layoutMode.value)
  nodes.value = result.nodes
  links.value = result.links
  svgSize.value = result.size
  hasData.value = result.nodes.length > 1
  stats.value = { groups: rawGroups.length, tags: tagCount }
  dragOffsets.value = {}
}

async function loadData() {
  loading.value = true
  try {
    rawGroups = (await getTagGroups()) || []
    applyLayout()
  } catch (e) {
    ElMessage.error('加载标签数据失败')
    rawGroups = []
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

function handleLayoutChange() {
  applyLayout()
  nextTick(fitToView)
}

/* ---------------- 节点样式(含回弹) ---------------- */
function nodeStyle(node) {
  const off = dragOffsets.value[node.id] || { dx: 0, dy: 0, springing: false }
  return {
    transform: `translate(${node.x + off.dx}px, ${node.y + off.dy}px)`,
    // 松手后回到原位: easeOutBack 会先冲过头再回落, 形成回弹
    transition: off.springing
      ? 'transform 0.55s cubic-bezier(0.34, 1.56, 0.64, 1)'
      : 'none'
  }
}

/* ---------------- 拖拽节点 ---------------- */
function startNodeDrag(e, node) {
  if (!hasData.value) return
  e.stopPropagation()
  const prev = dragOffsets.value[node.id] || { dx: 0, dy: 0 }
  dragging = {
    id: node.id,
    startX: e.clientX,
    startY: e.clientY,
    baseDx: prev.dx,
    baseDy: prev.dy
  }
  dragOffsets.value = {
    ...dragOffsets.value,
    [node.id]: { dx: prev.dx, dy: prev.dy, springing: false }
  }
}

/* ---------------- 平移画布 ---------------- */
function startPan(e) {
  if (!hasData.value) return
  isPanning.value = true
  panStart = { x: e.clientX, y: e.clientY, vx: view.value.x, vy: view.value.y }
}

function onPointerMove(e) {
  // 拖动节点优先
  if (dragging) {
    const dx = dragging.baseDx + (e.clientX - dragging.startX) / scale.value
    const dy = dragging.baseDy + (e.clientY - dragging.startY) / scale.value
    dragOffsets.value = {
      ...dragOffsets.value,
      [dragging.id]: { dx, dy, springing: false }
    }
    return
  }
  if (isPanning.value && panStart) {
    view.value = {
      x: panStart.vx + (e.clientX - panStart.x),
      y: panStart.vy + (e.clientY - panStart.y)
    }
  }
}

function endPointer() {
  // 松手: 给该节点开启回弹动画, 下一帧把偏移归零
  if (dragging) {
    const id = dragging.id
    const cur = dragOffsets.value[id] || { dx: 0, dy: 0 }
    dragOffsets.value = {
      ...dragOffsets.value,
      [id]: { dx: cur.dx, dy: cur.dy, springing: true }
    }
    const target = id
    // 下一帧置零, 触发 transition 回弹
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        dragOffsets.value = {
          ...dragOffsets.value,
          [target]: { dx: 0, dy: 0, springing: true }
        }
        // 动画结束后移除标记, 避免下次拖动被过渡影响
        setTimeout(() => {
          const o = dragOffsets.value[target]
          if (o && o.springing && o.dx === 0 && o.dy === 0) {
            dragOffsets.value = {
              ...dragOffsets.value,
              [target]: { dx: 0, dy: 0, springing: false }
            }
          }
        }, 600)
      })
    })
    dragging = null
  }
  isPanning.value = false
  panStart = null
}

/* ---------------- 缩放 ---------------- */
function zoomBy(factor) {
  scale.value = Math.min(3, Math.max(0.2, scale.value * factor))
}

function onWheel(e) {
  if (!hasData.value) return
  const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1
  scale.value = Math.min(3, Math.max(0.2, scale.value * factor))
}

function fitToView() {
  const vp = viewportRef.value
  if (!vp || !svgSize.value.width) return
  const vw = vp.clientWidth
  const vh = vp.clientHeight
  const sx = (vw - 24) / svgSize.value.width
  const sy = (vh - 24) / svgSize.value.height
  scale.value = Math.min(1, Math.max(0.2, Math.min(sx, sy)))
  // 内容缩放后居中显示, 否则会贴在视口左上角
  const scaledW = svgSize.value.width * scale.value
  const scaledH = svgSize.value.height * scale.value
  view.value = {
    x: Math.max(0, (vw - scaledW) / 2),
    y: Math.max(0, (vh - scaledH) / 2)
  }
  dragOffsets.value = {}
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
  flex-wrap: wrap;
}

.graph-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.graph-stats {
  color: var(--text-secondary);
}

.graph-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.graph-viewport {
  position: relative;
  height: 70vh;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-primary);
  background-image: radial-gradient(circle, var(--border-light) 1px, transparent 1px);
  background-size: 18px 18px;
  cursor: grab;
}

.graph-viewport.panning {
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

.graph-nodes g {
  cursor: move;
}

.node-box {
  fill: var(--bg-card);
  stroke: var(--border-color);
  stroke-width: 1;
}

/*
 * 原点不直接用 --text-primary 填充: 自定义主题下它可能是任意亮度,
 * 没法保证与文字的反色对比。改为卡片底色 + 加粗描边。
 */
.node-root {
  fill: var(--bg-card);
  stroke: var(--text-primary);
  stroke-width: 2;
}

.node-root + .node-accent + .node-label,
.node-root + .node-label {
  fill: var(--text-primary);
  font-weight: 600;
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

.graph-nodes g:hover .node-box {
  stroke: var(--text-primary);
  stroke-width: 1.6;
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
  background: var(--bg-card);
  border: 2px solid var(--text-primary);
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
