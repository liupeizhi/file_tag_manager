import request from './request'

export function getFileTree(serverId, path = '/') {
  return request.get('/files/tree', { params: { serverId, path } })
}

export function getFileList(params) {
  return request.get('/files', { params })
}

export function downloadFile(serverId, path) {
  return `/api/files/${serverId}/download?path=${encodeURIComponent(path)}`
}

export function uploadFile(serverId, path, file, onProgress) {
  const formData = new FormData()
  formData.append('serverId', serverId)
  formData.append('path', path)
  formData.append('file', file)
  
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export function renameFile(serverId, oldPath, newPath) {
  return request.put(`/files/${serverId}/rename`, null, {
    params: { oldPath, newPath }
  })
}

export function deleteFile(serverId, path) {
  return request.delete(`/files/${serverId}`, { params: { path } })
}

export function createFolder(serverId, path) {
  return request.post('/files/create-folder', null, {
    params: { serverId, path }
  })
}

export function syncFiles(serverId, path = '/') {
  return request.post('/files/sync', null, {
    params: { serverId, path }
  })
}

export function exportDirectory(serverId, path = '/') {
  return `/api/files/export?serverId=${serverId}&path=${encodeURIComponent(path)}`
}
/** 递归目录树, 供目录关系图使用; includeFiles 控制是否返回文件叶子节点 */
export function getDirectoryTree(serverId, path = '/', maxDepth = 5, maxNodes = 180,
                                 includeFiles = true, maxFilesPerDir = 60) {
  return request.get('/files/directory-tree', {
    params: { serverId, path, maxDepth, maxNodes, includeFiles, maxFilesPerDir }
  })
}
