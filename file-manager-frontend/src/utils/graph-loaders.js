/**
 * 关系图的数据装配。
 *
 * loader 返回统一的树结构:
 *   { root: { id, label, type:'root'|'branch'|'leaf', color, title,
 *             children: [], expandable?: boolean, expanded?: boolean,
 *             loadChildren?: async () => TreeNode[] },
 *     statsText }
 *
 * 目录图采用懒加载: 初始只取一层, 节点上的 loadChildren 在点击时
 * 才去拉取下一层子目录。
 */

import { getTagGroups } from '@/api/tag'
import { getDirectoryTree } from '@/api/file'

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
    // 目录都可能还有下级, 点击时再去确认; 若确实为空则展开后 badge 消失
    expandable: true,
    expanded: false,
    loadChildren: async () => {
      const sub = (await getDirectoryTree(serverId, path, 1, 500)) || []
      return sub.map((d) => makeDirectoryNode(serverId, d))
    }
  }
}

export async function loadDirectoryGraph(serverId, serverName) {
  if (serverId == null) {
    return { root: null, statsText: '' }
  }

  // 只取第一层, 其余按需展开
  const dirs = (await getDirectoryTree(serverId, '/', 1, 500)) || []

  const serverNode = {
    id: 'server',
    label: serverName || '当前服务器',
    type: 'branch',
    color: '#909399',
    title: serverName || '当前服务器',
    children: dirs.map((d) => makeDirectoryNode(serverId, d))
  }

  const root = {
    id: ROOT_ID,
    label: '原点',
    type: 'root',
    color: '#909399',
    title: '原点',
    children: [serverNode]
  }

  return {
    root,
    statsText: `${dirs.length} 个顶级文件夹 · 点击节点展开下一层`
  }
}
