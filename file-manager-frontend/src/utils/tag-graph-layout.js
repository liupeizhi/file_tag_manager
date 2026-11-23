/**
 * 标签关系图的布局计算。
 *
 * 输入是一棵已建好的树:
 *   { id, label, type: 'root'|'group'|'tag', color, title, children: [...] }
 *
 * 输出:
 *   nodes: [{ id, type, label, color, width, height, title, x, y }]
 *          x/y 为节点盒子的「顶部中心」, 绘制时盒子从 y 向下展开
 *   links: [{ key, from, to, color, curve }]
 *          curve 为 'v'(上下) | 'h'(左右) | 'line'(直线),
 *          由组件按节点实时位置生成路径, 这样拖动节点时连线能跟随
 *   size:  { width, height }
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
const RADIUS_STEP = 142 // 雪花型每层最小半径
const MIN_ARC = 140      // 同层每个节点需要的最小弧长(约等于盒子宽度+间距)

/** 每个角标(+/-)在盒子内部占的横向空间 */
const BADGE_SIZE = 16

/** 节点盒子的宽度上限 —— 文件名再长也不让它无限撑开 */
const MAX_NODE_WIDTH = 200
const LABEL_FONT_SIZE = 12
const LABEL_PADDING = 28 // 左右内边距 + 展开角标的位置

/** 估算一段文字在 12px 下占的宽度: 中日韩字符按全角, 其余按半角 */
function textWidth(text) {
  let w = 0
  for (const ch of String(text ?? '')) {
    w += /[\u1100-\u11ff\u2e80-\ua4cf\uac00-\ud7af\uf900-\ufaff\uff00-\uff60\uffe0-\uffe6]/.test(ch)
      ? LABEL_FONT_SIZE
      : LABEL_FONT_SIZE * 0.56
  }
  return w
}

/** 按可用宽度截断文字, 超出部分用省略号(完整名字放在 <title> 里) */
function fitLabel(text, maxWidth) {
  const chars = [...String(text ?? '')]
  if (textWidth(text) <= maxWidth) return chars.join('')
  let out = ''
  let w = 0
  for (const ch of chars) {
    const cw = textWidth(ch)
    if (w + cw + LABEL_FONT_SIZE > maxWidth) break
    w += cw
    out += ch
  }
  return out ? out + '…' : chars.slice(0, 1).join('') + '…'
}

/**
 * 节点右侧要画几个角标。
 * 标签节点: 新增(+)、展开文件、叶子再加一个删除(-);
 * 目录节点: 只有一个展开角标。
 * 与组件里的 badgeOrder() 保持一致。
 */
export function badgeCount(node) {
  if (node.addable || node.fileExpandable) {
    return (node.addable ? 1 : 0) + (node.fileExpandable ? 1 : 0) + (node.removable ? 1 : 0)
  }
  return node.expandable ? 1 : 0
}

/** 角标占掉的横向空间 */
export function badgeArea(node) {
  return badgeCount(node) * BADGE_SIZE
}

export function nodeWidth(node) {
  const base = node.type === 'root'
    ? ROOT_WIDTH
    : node.type === 'group'
      ? GROUP_WIDTH
      : NODE_WIDTH
  // 短名字保持统一样式, 长名字(常见于文件名)按文字宽度撑开, 上限封顶
  const need = textWidth(node.label) + LABEL_PADDING + badgeArea(node)
  return Math.min(Math.max(base, need), MAX_NODE_WIDTH + badgeArea(node))
}

/* ============ 通用 ============ */
function assignLeaves(node) {
  if (!node.children || node.children.length === 0) {
    node._leaves = 1
    return 1
  }
  node._leaves = node.children.reduce((sum, c) => sum + assignLeaves(c), 0)
  return node._leaves
}

function emptyNode(node, x, y) {
  const width = nodeWidth(node)
  const area = badgeArea(node)
  return {
    id: node.id,
    type: node.type,
    // 盒子宽度定了才知道能放多少字, 超出部分截断
    label: fitLabel(node.label, width - LABEL_PADDING - area),
    // 右侧要给角标留位置, 文字往左挪半个角标区, 保持"在剩余区域居中"
    labelX: -area / 2,
    color: node.color,
    title: node.title,
    width,
    height: NODE_HEIGHT,
    x,
    y,
    // 懒加载标记: 供组件画"可展开"角标
    expandable: node.expandable === true,
    expanded: node.expanded === true,
    // 新增子节点标记: 供组件画 "+" 并抛出 add-child
    addable: node.addable === true,
    add: node.add || null,
    // 删除标记(只有叶子节点有): 供组件画 "-" 并抛出 remove-node
    removable: node.removable === true,
    remove: node.remove || null,
    // 展开文件标记: 供组件画"纸条"角标, 懒加载出该标签下的文件
    fileExpandable: node.fileExpandable === true
  }
}

function linkColor(child) {
  return child.type === 'tag' ? `${child.color}88` : '#c9c9c9'
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

function layoutTree(root, horizontal) {
  measureSubtree(root)
  const nodes = []
  const links = []
  const curve = horizontal ? 'h' : 'v'

  const place = (node, crossCenter, depth) => {
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
        links.push({
          key: `${node.id}->${child.id}`,
          from: node.id,
          to: child.id,
          color: linkColor(child),
          curve
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

/* ============ 雪花型 (径向) ============ */
function layoutSnowflake(root) {
  assignLeaves(root)

  // 统计每层节点数: 同层节点过多时按弧长要求放大半径, 否则盒子会互相重叠
  const perDepth = new Map()
  const countDepth = (node, d) => {
    perDepth.set(d, (perDepth.get(d) || 0) + 1)
    ;(node.children || []).forEach((c) => countDepth(c, d + 1))
  }
  countDepth(root, 0)

  const radiusAt = (d) => {
    const count = perDepth.get(d) || 1
    const byArc = (count * MIN_ARC) / (2 * Math.PI)
    return Math.max(d * RADIUS_STEP, byArc)
  }

  const nodes = []
  const links = []

  const maxDepth = depthOf(root)
  const center = radiusAt(maxDepth) + PADDING
  const total = center * 2

  const place = (node, a0, a1, depth) => {
    const angle = (a0 + a1) / 2
    const r = radiusAt(depth)
    const cx = depth === 0 ? center : center + r * Math.cos(angle)
    const cy = depth === 0 ? center : center + r * Math.sin(angle)
    // 记录的是盒子顶部中心, 所以把圆心对齐到节点中线
    nodes.push(emptyNode(node, cx, cy - NODE_HEIGHT / 2))

    if (node.children && node.children.length) {
      const totalLeaves = node.children.reduce((s, c) => s + c._leaves, 0) || 1
      let cursor = a0
      node.children.forEach((child) => {
        const span = (a1 - a0) * (child._leaves / totalLeaves)
        links.push({
          key: `${node.id}->${child.id}`,
          from: node.id,
          to: child.id,
          color: linkColor(child),
          curve: 'line'
        })
        place(child, cursor, cursor + span, depth + 1)
        cursor += span
      })
    }
  }

  // 根节点占满整个圆周, 从 -90° 开始, 视觉上"向上开花"
  place(root, -Math.PI / 2, Math.PI * 1.5, 0)

  return { nodes, links, size: { width: total, height: total } }
}

function depthOf(node) {
  if (!node.children || node.children.length === 0) return 0
  return 1 + Math.max(...node.children.map(depthOf))
}

/* ============ 力导向 ============ */
/**
 * 以雪花型结果为初始位置, 跑固定次数的力导向松弛:
 * 全节点斥力 + 连线弹簧 + 向心引力。结果稳定可复现。
 */
function layoutForce(root) {
  const seed = layoutSnowflake(root)
  const pts = seed.nodes.map((n) => ({
    node: n,
    x: n.x + n.width / 2,
    y: n.y + n.height / 2,
    vx: 0,
    vy: 0
  }))
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
  const REPULSE = 26000
  const SPRING = 0.055
  const REST = 132
  const GRAVITY = 0.012

  for (let iter = 0; iter < ITER; iter++) {
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
    y: p.y + offsetY - NODE_HEIGHT / 2
  }))

  const links = []
  const addLinks = (n) => (n.children || []).forEach((c) => {
    links.push({
      key: `${n.id}->${c.id}`,
      from: n.id,
      to: c.id,
      color: linkColor(c),
      curve: 'line'
    })
    addLinks(c)
  })
  addLinks(root)

  return {
    nodes,
    links,
    size: { width: w + PADDING * 2, height: h + PADDING * 2 + NODE_HEIGHT }
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

/* ============ 连线路径 ============ */
/**
 * 按两个节点的实时中心点生成路径。
 * 拖动节点时组件会重新调用本函数, 因此连线会跟着走。
 */
export function buildLinkPath(curve, x1, y1, x2, y2) {
  if (curve === 'v') {
    const mid = (y1 + y2) / 2
    return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`
  }
  if (curve === 'h') {
    const mid = (x1 + x2) / 2
    return `M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`
  }
  return `M ${x1} ${y1} L ${x2} ${y2}`
}
