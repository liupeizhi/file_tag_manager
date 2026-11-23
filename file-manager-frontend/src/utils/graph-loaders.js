/**
 * 关系图的数据装配。
 *
 * loader 返回统一的树结构:
 *   { root: { id, label, type:'root'|'branch'|'leaf', color, title,
 *             children: [], expandable?, expanded?, loadChildren? },
 *     statsText }
 *
 * 目录图采用懒加载: 初始只取一层, 节点上的 loadChildren 在展开时才拉取下一层。
 */

import { getTagGroups, getFileTags } from '@/api/tag'
import { getDirectoryTree } from '@/api/file'
import { getFileTypeInfo } from '@/utils/file-type'

const ROOT_ID = 'root'
/** 与后端 maxFilesPerDir 保持一致 */
const MAX_FILES_PER_DIR = 60

/* ---------------- 标签关系图 ---------------- */
function findTag(tags, id) {
  for (const tag of tags || []) {
    if (tag.id === id) return tag
    const found = findTag(tag.children, id)
    if (found) return found
  }
  return null
}

/**
 * 标签关系图。
 *
 * 只画标签层级, 不展示文件 —— 展开文件这件事交给文件视图去做。
 * 标签节点右侧的 "+" 用来新增子标签: 组件抛出 add-child,
 * 由调用方弹窗填写名称后调用创建接口, 再 reload 这张图。
 *
 * @param {object} opts
 *   tagId  可选; 传了就只画该标签及其下级(从标签面板点开时用),
 *          不传则画全部: 原点 → 标签组 → 标签层级
 */
export async function loadTagGraph(opts = {}) {
  const groups = (await getTagGroups()) || []
  const { tagId } = opts

  let tagCount = 0

  /** 新增子标签需要的上下文, 一并挂在节点上 */
  const addMeta = (tag, group) => ({
    addable: true,
    add: { tagId: tag.id, groupId: group ? group.id : null, parentName: tag.name }
  })

  /** 删除要的上下文; 只有叶子节点可删 */
  const removeMeta = (tag, group, isLeaf) => ({
    removable: isLeaf,
    remove: isLeaf
      ? { tagId: tag.id, groupId: group ? group.id : null, tagName: tag.name }
      : null
  })

  const mapTag = (tag, group) => {
    tagCount++
    const children = (tag.children || []).map((child) => mapTag(child, group))
    return {
      id: `tag-${tag.id}`,
      label: tag.name,
      type: 'tag',
      color: tag.color || '#909399',
      title: tag.name,
      children,
      ...addMeta(tag, group),
      // 只允许删叶子节点, 有下级标签的节点不给删
      ...removeMeta(tag, group, children.length === 0)
    }
  }

  // 指定标签 -> 以该标签为根(它自己也算可继续新增子标签)
  if (tagId != null) {
    const group = (groups || []).find((g) => findTag(g.tags, tagId))
    const target = group ? findTag(group.tags, tagId) : null
    if (!target) {
      return { root: null, statsText: '未找到该标签' }
    }
    const children = (target.children || []).map((child) => mapTag(child, group))
    const root = {
      id: `tag-${target.id}`,
      label: target.name,
      type: 'root',
      color: target.color || '#909399',
      title: target.name,
      children,
      ...addMeta(target, group),
      ...removeMeta(target, group, children.length === 0)
    }
    return {
      root,
      hint: `当前标签：${target.name}`,
      statsText: `${tagCount} 个下级标签`
    }
  }

  // 全部标签
  // 先构造 root(触发 mapTag 累加), 再读 tagCount
  const root = {
    id: ROOT_ID,
    label: '原点',
    type: 'root',
    color: '#909399',
    title: '原点',
    children: groups.map((g) => ({
      id: `group-${g.id}`,
      label: g.name,
      type: 'branch',
      color: '#909399',
      title: `${g.name}（${(g.tags || []).length} 个顶级标签）`,
      children: (g.tags || []).map((tag) => mapTag(tag, g))
    }))
  }

  return {
    root,
    hint: '原点 → 标签组 → 标签层级',
    statsText: `${groups.length} 个分组 · ${tagCount} 个标签`
  }
}

/* ---------------- 目录关系图(懒加载) ---------------- */
function makeDirectoryNode(serverId, dir) {
  const path = dir.path
  return {
    id: `dir-${path}`,
    label: dir.name,
    type: 'branch',
    color: '#909399',
    title: path,
    children: [],
    // 目录默认视为可展开; 展开后若为空会自动去掉角标
    expandable: true,
    expanded: false,
    loadChildren: async () => {
      const sub = (await getDirectoryTree(serverId, path, 1, 500, true, MAX_FILES_PER_DIR)) || []
      return sub.map((e) => makeEntryNode(serverId, e))
    }
  }
}

function makeFileNode(file, serverId) {
  const info = getFileTypeInfo(file.name)
  return {
    id: `file-${file.path}`,
    label: file.name,
    type: 'leaf',
    color: info.color || '#909399',
    title: file.path,
    children: [],
    // 带上文件本身的元信息: 点节点时用它打开预览
    file: {
      serverId: file.serverId ?? serverId,
      path: file.path,
      name: file.name,
      size: file.size,
      contentType: file.contentType,
      lastModified: file.lastModified,
      isDirectory: false
    }
  }
}

function makeEntryNode(serverId, entry) {
  return entry.isDirectory
    ? makeDirectoryNode(serverId, entry)
    : makeFileNode(entry, serverId)
}

function nameOfPath(path) {
  if (!path || path === '/') return '/'
  const parts = path.split('/').filter(Boolean)
  return parts.length ? parts[parts.length - 1] : '/'
}

/**
 * 目录图。
 *
 * @param {object} opts
 *   serverId   服务器 id
 *   serverName 服务器名
 *   basePath   起始目录, 默认根目录; 从文件列表点开时传当前路径
 *
 * 根节点直接用该目录本身(根目录时用服务器名) —— 目录天然有根,
 * 再套一层"原点"只会让人困惑。
 */
export async function loadDirectoryGraph(opts = {}) {
  const { serverId, serverName, basePath = '/' } = opts
  if (serverId == null) {
    return { root: null, statsText: '' }
  }

  const isRoot = !basePath || basePath === '/'
  // 只取第一层, 其余按需展开
  const entries = (await getDirectoryTree(serverId, basePath, 1, 500, true, MAX_FILES_PER_DIR)) || []

  const rootLabel = isRoot ? (serverName || '当前服务器') : nameOfPath(basePath)
  const root = {
    id: isRoot ? 'server' : `dir-${basePath}`,
    label: rootLabel,
    type: 'root',
    color: '#909399',
    title: isRoot ? `${rootLabel} · 根目录` : basePath,
    children: entries.map((e) => makeEntryNode(serverId, e))
  }

  const dirCount = entries.filter((e) => e.isDirectory).length
  const fileCount = entries.length - dirCount
  // 文件数触及上限时说明一下, 避免以为目录里只有这么多文件
  const fileCapped = fileCount >= MAX_FILES_PER_DIR

  return {
    root,
    hint: isRoot
      ? '服务器根目录 → 目录与文件'
      : `当前目录：${basePath.replace(/\/+$/, '') || '/'}`,
    statsText: `${dirCount} 个文件夹 · ${fileCount} 个文件${fileCapped ? '+' : ''} · 点击节点展开/收起`
  }
}

/* ---------------- 文件关系图 ---------------- */
/**
 * 文件关系图: 以文件为根节点, 向下挂两棵"所属"子树。
 * 层级一律「由近及远」—— 离文件最近的一层先出现, 越往外越远:
 *
 *   文件
 *   ├── 所在目录: 文件所在目录 → 上一级 → … → 服务器根
 *   └── 所属标签: 文件直接标签 → 上级标签 → … → 标签分组
 *
 * 只看层级, 不在图里展开兄弟文件, 避免和上下级重复。
 *
 * @param {object} opts
 *   filePath   文件完整路径
 *   fileName   文件名(不给就从路径里取)
 *   serverId   服务器 id
 *   serverName 服务器名
 */
export async function loadFileGraph(opts = {}) {
  const { filePath, fileName, serverId, serverName } = opts
  if (!filePath || serverId == null) {
    return { root: null, statsText: '' }
  }

  const [fileTags, groups] = await Promise.all([
    getFileTags(filePath, serverId).catch(() => []),
    getTagGroups().catch(() => [])
  ])
  const tags = Array.isArray(fileTags) ? fileTags : []

  const root = {
    id: 'file-root',
    label: fileName || nameOfPath(filePath),
    type: 'root',
    color: '#909399',
    title: filePath,
    children: []
  }

  /* --- 目录层级: 文件所在目录 → 逐级向上 → 服务器根 --- */
  const segments = String(filePath).split('/').filter(Boolean)
  const dirSegments = segments.slice(0, -1) // 最后一段是文件名

  // 链从"最近的一层"开始: 文件所在目录 → 上一级 → …
  const dirChain = []
  for (let i = dirSegments.length; i >= 1; i--) {
    const path = '/' + dirSegments.slice(0, i).join('/')
    dirChain.push({
      id: `dir-${path}`,
      label: dirSegments[i - 1],
      type: 'branch',
      color: '#909399',
      title: path,
      children: []
    })
  }
  // 服务器根是整条链的最远端
  dirChain.push({
    id: 'dir-server',
    label: serverName || '/',
    type: 'branch',
    color: '#909399',
    title: serverName ? `${serverName} · 根目录` : '服务器根目录',
    children: []
  })

  // 数组是"近 → 远", 逐层串起来: 近的当父节点, 远的当它的子节点
  let dirTail = null
  for (let i = dirChain.length - 1; i >= 0; i--) {
    dirChain[i].children = dirTail ? [dirTail] : []
    dirTail = dirChain[i]
  }

  root.children.push({
    id: 'branch-dir',
    label: '所在目录',
    type: 'branch',
    color: '#909399',
    title: `${dirChain.length} 层目录 · 由近及远`,
    children: [dirTail]
  })

  /* --- 标签层级: 文件直接标签 → 逐级向上 → 标签分组 --- */
  // 先建好父子索引, 才能从标签一路走到分组
  const parentOf = new Map()
  const groupOf = new Map()
  for (const group of groups || []) {
    const walk = (nodes, parent) => {
      for (const tag of nodes || []) {
        if (parent) parentOf.set(tag.id, parent)
        groupOf.set(tag.id, group)
        walk(tag.children, tag)
      }
    }
    walk(group.tags, null)
  }

  /**
   * 单个标签的链路: [自己, 父, 祖父, …, 分组]。
   * 同一个上级可能出现在多条链路里(文件打了兄弟标签时),
   * 因为一棵树表达不了"一个节点多个父", 宁可重复展示也不合并。
   * 节点 id 带上序号, 避免多条链路里的同名祖先撞 key。
   */
  const buildTagChain = (tag, seq) => {
    const chain = []
    const seen = new Set()
    let cur = tag
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id)
      chain.push({
        id: `tag-${cur.id}-${seq}`,
        label: cur.name,
        type: 'tag',
        color: cur.color || '#909399',
        title: cur.name,
        children: []
      })
      cur = parentOf.get(cur.id)
    }
    const group = groupOf.get(tag.id)
    if (group) {
      chain.push({
        id: `group-${group.id}-${seq}`,
        label: group.name,
        type: 'branch',
        color: '#909399',
        title: `${group.name} · 标签分组`,
        children: []
      })
    }

    let tail = null
    for (let i = chain.length - 1; i >= 0; i--) {
      chain[i].children = tail ? [tail] : []
      tail = chain[i]
    }
    return tail
  }

  const tagChains = tags.map((tag, i) => buildTagChain(tag, i)).filter(Boolean)
  if (!tagChains.length) {
    tagChains.push({
      id: 'no-tags',
      label: '（未打标签）',
      type: 'leaf',
      color: '#909399',
      title: '该文件还没有标签',
      children: []
    })
  }

  root.children.push({
    id: 'branch-tag',
    label: '所属标签',
    type: 'branch',
    color: '#909399',
    title: `${tags.length} 个标签 · 由近及远`,
    children: tagChains
  })

  return {
    root,
    hint: '文件 → 由近及远：所在目录 / 所属标签',
    statsText: `${dirChain.length} 层目录 · ${tags.length} 个标签`
  }
}
