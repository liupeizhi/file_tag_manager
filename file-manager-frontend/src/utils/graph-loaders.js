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

/* ---------------- 标签关系图 ---------------- */
export async function loadTagGraph() {
  const groups = (await getTagGroups()) || []

  let tagCount = 0
  const mapTag = (tag) => {
    tagCount++
    return {
      id: `tag-${tag.id}`,
      label: tag.name,
      type: 'leaf',
      color: tag.color || '#909399',
      title: tag.name,
      children: (tag.children || []).map(mapTag)
    }
  }

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
      const sub = (await getDirectoryTree(serverId, path, 1, 500, true, 60)) || []
      return sub.map((d) => makeEntryNode(serverId, d))
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

/**
 * 目录图的根节点就是服务器本身 —— 目录有天然的根,
 * 再套一层"原点"只会让人困惑。
 */
export async function loadDirectoryGraph(serverId, serverName) {
  if (serverId == null) {
    return { root: null, statsText: '' }
  }

  // 只取第一层, 其余按需展开
  const entries = (await getDirectoryTree(serverId, '/', 1, 500, true, 60)) || []

  const root = {
    id: 'server',
    label: serverName || '当前服务器',
    type: 'root',
    color: '#909399',
    title: `${serverName || '当前服务器'} · 根目录`,
    children: entries.map((e) => makeEntryNode(serverId, e))
  }

  const dirCount = entries.filter((e) => e.isDirectory).length
  const fileCount = entries.length - dirCount

  return {
    root,
    statsText: `${dirCount} 个文件夹 · ${fileCount} 个文件 · 点击节点展开/收起`
  }
}
