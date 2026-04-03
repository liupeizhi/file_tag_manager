import request from './request'

export function getTagTree() {
  return request.get('/tags/tree')
}

export function getTagGroups() {
  return request.get('/tags/groups')
}

export function createTagGroup(data) {
  return request.post('/tags/groups', data)
}

export function updateTagGroup(id, data) {
  return request.put(`/tags/groups/${id}`, data)
}

export function deleteTagGroup(id) {
  return request.delete(`/tags/groups/${id}`)
}

export function createTag(data) {
  return request.post('/tags', data)
}

export function updateTag(id, data) {
  return request.put(`/tags/${id}`, data)
}

export function deleteTag(id) {
  return request.delete(`/tags/${id}`)
}

export function addTagToFile(filePath, serverId, tagId) {
  return request.post('/tags/file', null, {
    params: { filePath, serverId, tagId }
  })
}

export function removeTagFromFile(filePath, serverId, tagId) {
  return request.delete('/tags/file', {
    params: { filePath, serverId, tagId }
  })
}

export function setFileTags(filePath, serverId, tagIds) {
  return request.post('/tags/file/batch', tagIds, {
    params: { filePath, serverId }
  })
}

export function getFileTags(filePath, serverId) {
  return request.get('/tags/file', {
    params: { filePath, serverId }
  })
}

export function getFilesByTag(tagId) {
  return request.get(`/tags/${tagId}/files`)
}

export function getFilesByTagWithDetails(tagId) {
  return request.get(`/tags/${tagId}/files/detail`)
}