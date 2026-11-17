import request from './request'

export function getFavorites() {
  return request.get('/favorites')
}

export function toggleFavorite(data) {
  return request.post('/favorites/toggle', data)
}

export function removeFavorite(id) {
  return request.delete(`/favorites/${id}`)
}

export function clearFavorites() {
  return request.delete('/favorites')
}
