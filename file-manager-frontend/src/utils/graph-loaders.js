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

import { getTagGroups } from '@/api/tag'
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
 * @param {object} opts
 *   tagId  可选; 传了就只画该标签及其下级(从标签面板点开时用),
 *          不传则画全部: 原点 → 标签组 → 标签层级
 */
export async function loadTagGraph(opts = {}) {
  const groups = (await getTagGroups()) || []
  const { tagId } = opts

  let tagCount = 0

  /**
   * 标签节点: 子标签直接作为层级展示, 点击节点再按需加载
   * "该标签下的文件"(展开/收起)。文件是真正的叶子, 不可再展开。
   */
  const mapTag = (tag) => {
    tagCount++
    return {
      id: `tag-${tag.id}`,
      label: tag.name,
      type: 'tag',
      color: tag.color || '#909399',
      title: tag.name,
      children: (tag.children || []).map(mapTag),
      expandable: true,
      expanded: false,
      loadChildren: async () => {
        const files = (await getFilesByTagWithDetails(tag.id, opts.serverId, false)) || []
        return files.map(makeFileNode)
      }
    }
  }

  // 指定标签 -> 以该标签为根
  if (tagId != null) {
    const target = findTag(
      groups.flatMap((g) => g.tags || []),
      tagId
    )
    if (!target) {
      return { root: null, statsText: '未找到该标签' }
    }
    const root = {
      id: `tag-${target.id}`,
      label: target.name,
      type: 'root',
      color: target.color || '#909399',
      title: target.name,
      children: (target.children || []).map(mapTag),
      expandable: true,
      expanded: false,
      loadChildren: async () => {
        const files = (await getFilesByTagWithDetails(target.id, opts.serverId, false)) || []
        return files.map(makeFileNode)
      }
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
      children: (g.tags || []).map(mapTag)
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

function makeFileNode(file) {
  const info = getFileTypeInfo(file.name)
  return {
    id: `file-${file.path}`,
    label: file.name,
    type: 'leaf',
    color: info.color || '#909399',
    title: file.path,
    children: []
  }
}

function makeEntryNode(serverId, entry) {
  return entry.isDirectory
    ? makeDirectoryNode(serverId, entry)
    : makeFileNode(entry)
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
