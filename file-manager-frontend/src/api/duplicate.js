import axios from 'axios'
import request from './request'

/** 发起一次目录重复检测; 返回 { taskId } */
export function startDuplicateCompare(data) {
  return request.post('/duplicates/compare', data)
}

/** 查询任务状态与进度 */
export function getDuplicateTask(taskId) {
  return request.get(`/duplicates/tasks/${taskId}`)
}

/** 分页取重复组 */
export function getDuplicateGroups(taskId, params) {
  return request.get(`/duplicates/tasks/${taskId}/groups`, { params })
}

/** 用全量哈希复核一组 */
export function verifyDuplicateGroup(taskId, groupId) {
  return request.post(`/duplicates/tasks/${taskId}/groups/${groupId}/verify`)
}

export function cancelDuplicateTask(taskId) {
  return request.post(`/duplicates/tasks/${taskId}/cancel`)
}

/**
 * 导出 CSV。
 * 走原始 axios: 全局拦截器会把 blob 当成业务响应解析。
 */
export function exportDuplicateCsv(taskId) {
  return axios.get(`/api/duplicates/tasks/${taskId}/export`, { responseType: 'blob' })
}
