import request from './request'

export function getTheme() {
  return request.get('/theme')
}

export function updateTheme(data) {
  return request.put('/theme', data)
}

export function uploadThemeBackground(file) {
  const formData = new FormData()
  formData.append('background', file)
  return request.post('/theme/background', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function removeThemeBackground() {
  return request.delete('/theme/background')
}
