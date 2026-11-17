/**
 * 标签关系图的布局计算。
 *
 * 输入是一棵已建好的树:
 *   { id, label, type: 'root'|'group'|'tag', color, title, children: [...] }
 *
 * 输出统一为节点中心坐标 + 连线路径, 由组件负责绘制:
 *   { nodes: [{ id, type, label, color, width, height, title, x, y }],
 *     links: [{ key, path, color }],
 *     size: { width, height } }
 */

export const LAYOUTS = [
  { value: 'snowflake', label: '雪花型' },
  { value: 'force', label: '力导向' },
  { value: 'tree', label: '树形' },
  { value: 'horizontal', label: '横向' }
]

const NODE_HEIGHT = 32
const NODE_WIDTH = 116
const ROOT_WIDTH = 84
const GROUP_WIDTH = 124

const PADDING = 48
const H_GAP = 18        // 树形兄弟节点间距
const V_GAP = 64        // 树形层级间距
const RADIUS_STEP = 142 // 雪花型每层半径
const RING_GAP = 170    // 环形每层半径

export function nodeWidth(node) {
  if (node.type === 'root') return ROOT_WIDTH
  if (node.type === 'group') return GROUP_WIDTH
  return NODE_WIDTH
}

/* ============ 通用: 叶子数(用于瓜分角度) ============ */
function assignLeaves(node) {
  if (!node.children || node.children.length === 0) {
    node._leaves = 1
    return 1
  }
  node._leaves = node.children.reduce((sum, c) => sum + assignLeaves(c), 0)
  return node._leaves
}

function emptyNode(node, x, y) {
  return {
    id: node.id,
    type: node.type,
    label: node.label,
    color: node.color,
    title: node.title,
    width: nodeWidth(node),
    height: NODE_HEIGHT,
    x,
    y
  }
}

/* ============ 树形 / 横向 ============ */
function measureSubtree(node) {
  const w = nodeWidth(node)
  if (!node.children || node.children.length === 0) {
    node._w = w
    return w
  }
  let total = 0
  node.children.forEach((c, i) => {
    total += measureSubtree(c)
    if (i > 0) total += H_GAP
  })
  node._w = Math.max(w, total)
  return node._w
}

/**
 * @param horizontal true 时根在左、子级向右展开
 */
function layoutTree(root, horizontal) {
  measureSubtree(root)
  const nodes = []
  const links = []

  const place = (node, crossCenter, depth) => {
    // vertical 模式: 主轴先按深度向下, 交叉轴水平铺开
    const crossSize = horizontal ? NODE_HEIGHT : nodeWidth(node)
    void crossSize
    const pos = horizontal
      ? { x: PADDING + depth * (GROUP_WIDTH + V_GAP), y: crossCenter }
      : { x: crossCenter, y: PADDING + depth * (NODE_HEIGHT + V_GAP) }

    nodes.push(emptyNode(node, pos.x, pos.y))

    if (node.children && node.children.length) {
      let total = 0
      node.children.forEach((c, i) => {
        total += c._w
        if (i > 0) total += H_GAP
      })
      let cursor = crossCenter - total / 2
      node.children.forEach((child) => {
        const childCenter = cursor + child._w / 2
        const childPos = horizontal
          ? { x: PADDING + (depth + 1) * (GROUP_WIDTH + V_GAP), y: childCenter }
          : { x: childCenter, y: PADDING + (depth + 1) * (NODE_HEIGHT + V_GAP) }

        links.push({
          key: `${node.id}->${child.id}`,
          path: horizontal
            ? hCurve(pos.x, pos.y, childPos.x, childPos.y)
            : vCurve(pos.x, pos.y, childPos.x, childPos.y),
          color: child.type === 'tag' ? `${child.color}88` : '#c9c9c9'
        })

        place(child, childCenter, depth + 1)
        cursor += child._w + H_GAP
      })
    }
  }

  place(root, PADDING + root._w / 2, 0)

  const maxX = Math.max(...nodes.map((n) => n.x + n.width / 2))
  const maxY = Math.max(...nodes.map((n) => n.y + n.height))
  return {
    nodes,
    links,
    size: {
      width: horizontal ? maxX + PADDING : root._w + PADDING * 2,
      height: horizontal ? root._w + PADDING * 2 : maxY + PADDING
    }
  }
}

function vCurve(x1, y1, x2, y2) {
  const mid = (y1 + y2) / 2
  return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`
}

function hCurve(x1, y1, x2, y2) {
  const mid = (x1 + x2) / 2
  return `M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`
}

/* ============ 雪花型 (径向) ============ */
function layoutSnowflake(root) {
  assignLeaves(root)

  const nodes = []
  const links = []
  const maxDepth = depthOf(root)
  const radius = {
    total: maxDepth * RADIUS_STEP + PADDING,
    cx: 0,
    cy: 0
  }
  radius.cx = radius.total
  radius.cy = radius.total

  const place = (node, a0, a1, depth) => {
    const angle = (a0 + a1) / 2
    const r = depth * RADIUS_STEP
    // 根节点居中; 其余按角度铺开
    const x = depth === 0 ? radius.cx : radius.cx + r * Math.cos(angle)
    const y = depth === 0 ? radius.cy : radius.cy + r * Math.sin(angle)
    nodes.push(emptyNode(node, x, y))

    if (node.children && node.children.length) {
      const totalLeaves = node.children.reduce((s, c) => s + c._leaves, 0) || 1
      let cursor = a0
      node.children.forEach((child) => {
        const span = (a1 - a0) * (child._leaves / totalLeaves)
        const childAngle = cursor + span / 2
        const childR = (depth + 1) * RADIUS_STEP
        links.push({
          key: `${node.id}->${child.id}`,
          path: `M ${x} ${y} L ${radius.cx + childR * Math.cos(childAngle)} ${radius.cy + childR * Math.sin(childAngle)}`,
          color: child.type === 'tag' ? `${child.color}88` : '#c9c9c9'
        })
        place(child, cursor, cursor + span, depth + 1)
        cursor += span
      })
    }
  }

  // 根节点占满整个圆周; 第一个子节点从 -90° 开始, 视觉上更像"向上开花"
  place(root, -Math.PI / 2, Math.PI * 1.5, 0)

  const size = radius.total * 2
  return { nodes, links, size: { width: size, height: size } }
}

function depthOf(node) {
  if (!node.children || node.children.length === 0) return 0
  return 1 + Math.max(...node.children.map(depthOf))
}

/* ============ 力导向 ============ */
/**
 * 以雪花型结果为初始位置, 跑少量迭代的力导向松弛:
 * 全节点斥力 + 连线弹簧 + 向心引力。
 * 固定迭代次数(非持续模拟), 布局结果稳定且可复现。
 */
function layoutForce(root) {
  const seed = layoutSnowflake(root)
  const pts = seed.nodes.map((n) => ({ node: n, x: n.x, y: n.y, vx: 0, vy: 0 }))
  const index = new Map(pts.map((p) => [p.node.id, p]))

  const edges = []
  const walk = (n) => (n.children || []).forEach((c) => {
    const a = index.get(n.id)
    const b = index.get(c.id)
    if (a && b) edges.push([a, b])
    walk(c)
  })
  walk(root)

  const ITER = 260
  const REPULSE = 26000     // 斥力强度
  const SPRING = 0.055      // 弹簧刚度
  const REST = 132          // 弹簧自然长度
  const GRAVITY = 0.012     // 向心引力

  for (let iter = 0; iter < ITER; iter++) {
    // 斥力
    for (let i = 0; i < pts.length; i++) {
      for (let j = i + 1; j < pts.length; j++) {
        const a = pts[i]
        const b = pts[j]
        let dx = a.x - b.x
        let dy = a.y - b.y
        let d2 = dx * dx + dy * dy
        if (d2 < 1) { dx = Math.random() - 0.5; dy = Math.random() - 0.5; d2 = 1 }
        const d = Math.sqrt(d2)
        const f = REPULSE / d2
        const fx = (dx / d) * f
        const fy = (dy / d) * f
        a.vx += fx; a.vy += fy
        b.vx -= fx; b.vy -= fy
      }
    }
    // 弹簧
    for (const [a, b] of edges) {
      const dx = b.x - a.x
      const dy = b.y - a.y
      const d = Math.hypot(dx, dy) || 1
      const f = (d - REST) * SPRING
      const fx = (dx / d) * f
      const fy = (dy / d) * f
      a.vx += fx; a.vy += fy
      b.vx -= fx; b.vy -= fy
    }
    // 向心 + 阻尼 + 位移
    const cool = 1 - iter / ITER
    for (const p of pts) {
      p.vx += -p.x * GRAVITY
      p.vy += -p.y * GRAVITY
      p.x += p.vx * cool
      p.y += p.vy * cool
      p.vx *= 0.72
      p.vy *= 0.72
    }
  }

  // 归一化到正区间并留边距
  const xs = pts.map((p) => p.x)
  const ys = pts.map((p) => p.y)
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  const w = Math.max(...xs) - minX
  const h = Math.max(...ys) - minY
  const offsetX = PADDING - minX
  const offsetY = PADDING - minY

  const nodes = pts.map((p) => ({
    ...p.node,
    x: p.x + offsetX,
    y: p.y + offsetY
  }))
  const posOf = new Map(nodes.map((n) => [n.id, n]))

  const links = []
  const addLinks = (n) => (n.children || []).forEach((c) => {
    const p = posOf.get(n.id)
    const q = posOf.get(c.id)
    if (p && q) {
      links.push({
        key: `${n.id}->${c.id}`,
        path: `M ${p.x} ${p.y} L ${q.x} ${q.y}`,
        color: c.type === 'tag' ? `${c.color}88` : '#c9c9c9'
      })
    }
    addLinks(c)
  })
  addLinks(root)

  return {
    nodes,
    links,
    size: { width: w + PADDING * 2, height: h + PADDING * 2 }
  }
}

/* ============ 入口 ============ */
export function computeLayout(root, mode) {
  switch (mode) {
    case 'horizontal':
      return layoutTree(root, true)
    case 'force':
      return layoutForce(root)
    case 'snowflake':
      return layoutSnowflake(root)
    case 'tree':
    default:
      return layoutTree(root, false)
  }
}
