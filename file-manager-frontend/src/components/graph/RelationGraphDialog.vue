<template>
  <el-dialog
    v-model="visible"
    :title="title"
    class="tag-graph-dialog"
    width="92%"
    top="4vh"
    destroy-on-close
    @opened="handleOpened"
  >
    <div class="graph-toolbar">
      <div class="graph-hint">
        {{ dynamicHint || hint }}
        <span v-if="statsText" class="graph-stats">（{{ statsText }}）</span>
      </div>
      <div class="graph-actions">
        <label class="graph-switch" title="关闭后, 节点拖到哪儿就停在哪儿">
          <span class="switch-label">拖动回弹</span>
          <el-switch
            v-model="springBack"
            size="small"
            :disabled="!hasData"
            @change="handleSpringBackChange"
          />
        </label>
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

      <svg v-else class="graph-svg">
        <!-- 缩放与平移放在内层 g 上, SVG 本身铺满视口 -->
        <g :transform="`translate(${view.x} ${view.y}) scale(${scale})`">
          <g class="graph-links">
            <path
              v-for="link in renderedLinks"
              :key="link.key"
              :d="link.d"
              :stroke="link.color"
              fill="none"
            />
          </g>

          <g class="graph-nodes">
            <g
              v-for="node in nodes"
              :key="node.id"
              :class="{ dragging: draggingId === node.id }"
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
                :style="node.type === 'leaf' || node.type === 'tag' ? { stroke: node.color } : {}"
              />
              <rect
                v-if="node.type === 'leaf' || node.type === 'tag'"
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
                :x="node.labelX || 0"
                :y="node.height / 2"
                text-anchor="middle"
                dominant-baseline="central"
              >
                {{ node.label }}
              </text>
              <!--
                右侧角标有两种含义:
                  可展开(目录节点): 加号/减号, 点击展开或收起
                  可新增(标签节点): 加号, 点击弹出"新增子标签"
              -->
              <g
                v-if="node.addable || node.expandable"
                class="node-expander"
                :class="{ expanded: node.expanded, adder: node.addable }"
                :transform="`translate(${node.width / 2 - (node.removable ? 25 : 9)}, ${node.height / 2})`"
                @mousedown.stop
                @click.stop="handleBadgeClick(node)"
              >
                <circle r="7" />
                <path v-if="node.addable || !node.expanded" d="M -3.2 0 H 3.2 M 0 -3.2 V 3.2" />
                <path v-else d="M -3.2 0 H 3.2" />
                <title v-if="node.addable">新增子标签</title>
              </g>
              <!-- 叶子节点: 右侧再挂一个减号用于删除 -->
              <g
                v-if="node.removable"
                class="node-remover"
                :transform="`translate(${node.width / 2 - 9}, ${node.height / 2})`"
                @mousedown.stop
                @click.stop="emit('remove-node', node)"
              >
                <circle r="7" />
                <path d="M -3.2 0 H 3.2" />
                <title>删除标签</title>
              </g>
              <title>{{ node.title }}</title>
            </g>
          </g>
        </g>
      </svg>
    </div>

    <template #footer>
      <div class="graph-legend">
        <span class="legend-item"><i class="dot dot-root"></i>{{ rootLabel }}</span>
        <span
          v-for="item in legendItems"
          :key="item.label"
          class="legend-item"
        >
          <i class="dot" :class="item.dotClass"></i>{{ item.label }}
        </span>
        <span class="legend-tip">
          {{ springBack ? '拖动节点会弹回原位' : '拖动节点停在原地' }} · 空白处拖拽平移 · 滚轮缩放
        </span>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { computeLayout, buildLinkPath, LAYOUTS } from '@/utils/tag-graph-layout'
import { springBack } from '@/utils/graph-prefs'

const emit = defineEmits(['add-child', 'remove-node'])

const props = defineProps({
  title: { type: String, default: '关系图' },
  hint: { type: String, default: '' },
  rootLabel: { type: String, default: '原点' },
  legendItems: { type: Array, default: () => [] },
  /**
   * loader 需返回 { root, statsText }
   * root: { id, label, type:'root'|'branch'|'leaf', color, title, children: [] }
   */
  loader: { type: Function, required: true },
  /** 打开时使用的初始布局; 文件关系图这种纯层级树用树形更清楚 */
  defaultLayout: { type: String, default: 'snowflake' }
})

const visible = ref(false)
const openOptions = ref({})
const loading = ref(false)
const hasData = ref(false)
const statsText = ref('')
const dynamicHint = ref('')
const viewportRef = ref(null)

const layoutMode = ref('snowflake')
const nodes = ref([])
const links = ref([])
const svgSize = ref({ width: 0, height: 0 })
const scale = ref(1)
const view = ref({ x: 0, y: 0 })

/** 节点拖动偏移: id -> { dx, dy, springing } */
const dragOffsets = ref({})
const draggingId = ref(null)

/** 关掉回弹时如果正好在回弹, 就地停住, 别继续把节点拉回去 */
function handleSpringBackChange(val) {
  if (!val && springRaf) {
    cancelAnimationFrame(springRaf)
    springRaf = null
  }
}

/**
 * 连线按节点"当前"位置(布局位置 + 拖动偏移)实时计算,
 * 因此拖动节点时连线会跟着走。
 */
const renderedLinks = computed(() => {
  const nodeMap = new Map(nodes.value.map((n) => [n.id, n]))
  const offsets = dragOffsets.value
  const out = []
  for (const link of links.value) {
    const a = nodeMap.get(link.from)
    const b = nodeMap.get(link.to)
    if (!a || !b) continue
    const ao = offsets[a.id] || { dx: 0, dy: 0 }
    const bo = offsets[b.id] || { dx: 0, dy: 0 }
    const x1 = a.x + ao.dx
    const y1 = a.y + ao.dy + a.height / 2
    const x2 = b.x + bo.dx
    const y2 = b.y + bo.dy + b.height / 2
    out.push({ key: link.key, color: link.color, d: buildLinkPath(link.curve, x1, y1, x2, y2) })
  }
  return out
})

const isPanning = ref(false)
let panStart = null
let dragging = null

let currentRoot = null

/* ---------------- 数据 ---------------- */
function applyLayout() {
  if (!currentRoot) return
  const result = computeLayout(currentRoot, layoutMode.value)
  nodes.value = result.nodes
  links.value = result.links
  svgSize.value = result.size
  hasData.value = result.nodes.length > 1
  dragOffsets.value = {}
}

async function loadData() {
  loading.value = true
  try {
    const payload = await props.loader(openOptions.value)
    currentRoot = payload ? payload.root : null
    statsText.value = payload ? payload.statsText || '' : ''
    dynamicHint.value = payload ? payload.hint || '' : ''
    if (currentRoot) applyLayout()
  } catch (e) {
    ElMessage.error('加载数据失败')
    currentRoot = null
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

/* ---------------- 节点样式 ---------------- */
function nodeStyle(node) {
  const off = dragOffsets.value[node.id] || { dx: 0, dy: 0 }
  return {
    transform: `translate(${node.x + off.dx}px, ${node.y + off.dy}px)`
  }
}

function setOffset(id, dx, dy) {
  dragOffsets.value = { ...dragOffsets.value, [id]: { dx, dy } }
}

/* ---------------- 回弹弹簧 ---------------- */
let springRaf = null

/**
 * 松手后的回弹。用 rAF 逐帧跑弹簧, 而不是 CSS transition ——
 * 因为连线是按 dragOffsets 实时算的, 只有逐帧更新偏移量,
 * 节点和连线才能一起"弹"回原位。
 */
function startSpring(id, fromDx, fromDy) {
  if (springRaf) {
    cancelAnimationFrame(springRaf)
    springRaf = null
  }
  const STIFFNESS = 0.09
  const DAMPING = 0.84
  const REST = 0.4

  let dx = fromDx
  let dy = fromDy
  let vx = 0
  let vy = 0

  const step = () => {
    vx = (vx - dx * STIFFNESS) * DAMPING
    vy = (vy - dy * STIFFNESS) * DAMPING
    dx += vx
    dy += vy

    if (
      Math.abs(dx) <= REST && Math.abs(dy) <= REST &&
      Math.abs(vx) <= REST && Math.abs(vy) <= REST
    ) {
      setOffset(id, 0, 0)
      springRaf = null
      return
    }
    setOffset(id, dx, dy)
    springRaf = requestAnimationFrame(step)
  }
  springRaf = requestAnimationFrame(step)
}

/* ---------------- 拖拽节点 ---------------- */
function startNodeDrag(e, node) {
  if (!hasData.value) return
  e.stopPropagation()
  if (springRaf) {
    cancelAnimationFrame(springRaf)
    springRaf = null
  }
  const prev = dragOffsets.value[node.id] || { dx: 0, dy: 0 }
  dragging = {
    id: node.id,
    startX: e.clientX,
    startY: e.clientY,
    moved: 0,
    baseDx: prev.dx,
    baseDy: prev.dy
  }
  draggingId.value = node.id
  setOffset(node.id, prev.dx, prev.dy)
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
    const movedX = e.clientX - dragging.startX
    const movedY = e.clientY - dragging.startY
    dragging.moved = Math.max(dragging.moved, Math.hypot(movedX, movedY))
    const dx = dragging.baseDx + movedX / scale.value
    const dy = dragging.baseDy + movedY / scale.value
    setOffset(dragging.id, dx, dy)
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
  if (dragging) {
    const { id, moved } = dragging
    const cur = dragOffsets.value[id] || { dx: 0, dy: 0 }
    dragging = null
    draggingId.value = null

    if (moved < 4) {
      // 几乎没动 -> 视为点击: 清掉偏移并尝试展开
      setOffset(id, 0, 0)
      handleNodeActivate(id)
    } else if (springBack.value) {
      // 松手后弹簧回弹: 节点与连线一起回到布局位置
      startSpring(id, cur.dx, cur.dy)
    }
    // 关闭回弹时不做事: 保留当前偏移, 节点停在松手的位置
  }
  isPanning.value = false
  panStart = null
}

/** 角标点击: 新增型抛事件给调用方(由它弹窗填名字), 展开型走展开逻辑 */
function handleBadgeClick(node) {
  if (node.addable) {
    emit('add-child', node)
    return
  }
  handleNodeActivate(node.id)
}

/* ---------------- 懒加载展开 ---------------- */
const expandingId = ref(null)

/**
 * 展开/收起后布局会变大或变小。
 * 能放下就只重新居中(保持缩放, 避免每次点击都缩放); 放不下才整体适配。
 */
function recenterAfterLayout() {
  const vp = viewportRef.value
  const b = contentBounds()
  if (!vp || !b) return
  const vw = vp.clientWidth
  const vh = vp.clientHeight
  const needW = b.w * scale.value
  const needH = b.h * scale.value
  if (needW > vw - 24 || needH > vh - 24) {
    fitToView()
    return
  }
  view.value = {
    x: (vw - needW) / 2 - b.minX * scale.value,
    y: (vh - needH) / 2 - b.minY * scale.value
  }
}

/**
 * 点击节点: 未展开则展开, 已展开则收起。
 * 收起时不丢弃已加载的子节点, 缓存起来下次直接复原, 避免重复请求。
 */
async function handleNodeActivate(nodeId) {
  const node = findRawNode(currentRoot, nodeId)
  if (!node || !node.expandable || expandingId.value) return

  // 已展开 -> 收起(只移除追加出来的子节点, 保留原有层级)
  if (node.expanded) {
    if (node._baseChildren) node.children = node._baseChildren
    node.expanded = false
    applyLayout()
    recenterAfterLayout()
    return
  }

  // 有缓存 -> 直接复原
  if (node._extraChildren) {
    node.children = [...(node._baseChildren || []), ...node._extraChildren]
    node.expanded = true
    applyLayout()
    recenterAfterLayout()
    return
  }

  // 首次展开 -> 拉取追加内容(目录图是子目录/文件, 标签图是标签下的文件)
  expandingId.value = nodeId
  try {
    const extra = node.loadChildren ? await node.loadChildren() : []
    node._baseChildren = [...(node.children || [])]
    node._extraChildren = extra
    node.children = [...node._baseChildren, ...extra]
    node.expanded = true
    // 追加内容为空且本来没有层级 -> 视为叶子, 去掉角标
    if (!node.children.length) {
      node.expandable = false
    }
    applyLayout()
    recenterAfterLayout()
  } catch (e) {
    ElMessage.error('展开失败: ' + (e.message || '未知错误'))
  } finally {
    expandingId.value = null
  }
}

function findRawNode(node, id) {
  if (!node) return null
  if (node.id === id) return node
  for (const child of node.children || []) {
    const found = findRawNode(child, id)
    if (found) return found
  }
  return null
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

/** 所有节点的实际包围盒(不含画布留白), 用于真正居中内容 */
function contentBounds() {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const n of nodes.value) {
    minX = Math.min(minX, n.x - n.width / 2)
    maxX = Math.max(maxX, n.x + n.width / 2)
    minY = Math.min(minY, n.y)
    maxY = Math.max(maxY, n.y + n.height)
  }
  if (!Number.isFinite(minX)) return null
  return { minX, minY, w: maxX - minX, h: maxY - minY }
}

function fitToView() {
  const vp = viewportRef.value
  const b = contentBounds()
  if (!vp || !b) return
  if (springRaf) {
    cancelAnimationFrame(springRaf)
    springRaf = null
  }
  const vw = vp.clientWidth
  const vh = vp.clientHeight
  const sx = (vw - 24) / b.w
  const sy = (vh - 24) / b.h
  scale.value = Math.min(1, Math.max(0.2, Math.min(sx, sy)))
  // 按内容包围盒居中(雪花型的节点分布并非四周对称, 只按画布居中会偏)
  view.value = {
    x: (vw - b.w * scale.value) / 2 - b.minX * scale.value,
    y: (vh - b.h * scale.value) / 2 - b.minY * scale.value
  }
  dragOffsets.value = {}
  draggingId.value = null
}

function open(options = {}) {
  openOptions.value = options
  layoutMode.value = props.defaultLayout
  visible.value = true
}

/** 重新加载数据(新增子节点后刷新用); 顺带重新适配视图 */
async function reload() {
  if (!visible.value) return
  await loadData()
  await nextTick()
  fitToView()
}

defineExpose({ open, reload })
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

.graph-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
}

.graph-switch .switch-label {
  font-size: 12px;
  color: var(--text-muted);
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
  width: 100%;
  height: 100%;
}

.graph-links path {
  stroke-width: 1.4;
  stroke-linecap: round;
}

.graph-nodes g {
  cursor: pointer;
}

.graph-nodes g.dragging {
  cursor: grabbing;
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

.node-leaf,
.node-tag {
  stroke-width: 1.4;
}

/* 懒加载角标: 未展开显示加号, 展开后变为减号且淡化 */
.node-expander circle {
  fill: var(--bg-card);
  stroke: var(--text-secondary);
  stroke-width: 1;
}

.node-expander path {
  stroke: var(--text-secondary);
  stroke-width: 1.4;
  stroke-linecap: round;
}

.node-expander.expanded path {
  opacity: 0.45;
}

/* 删除按钮: 用危险色, 和"+ 新增"区分开 */
.node-remover {
  cursor: pointer;
}

.node-remover circle {
  fill: var(--bg-card);
  stroke: #f56c6c;
  stroke-width: 1;
  transition: fill 0.12s;
}

.node-remover path {
  stroke: #f56c6c;
  stroke-width: 1.4;
  stroke-linecap: round;
}

.node-remover:hover circle {
  fill: #f56c6c;
}

.node-remover:hover path {
  stroke: var(--bg-card);
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
