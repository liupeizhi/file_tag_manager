import request from './request'

export function getHistory() {
  return request.get('/history')
}

export function recordHistory(data) {
  return request.post('/history', data)
}

export function removeHistory(id) {
  return request.delete(`/history/${id}`)
}

export function clearHistory() {
  return request.delete('/history')
}
