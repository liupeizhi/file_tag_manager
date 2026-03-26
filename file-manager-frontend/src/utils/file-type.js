const FILE_TYPE_MAP = {
  pdf: { icon: 'Document', color: '#E74C3C', type: 'document' },
  doc: { icon: 'Document', color: '#3498DB', type: 'document' },
  docx: { icon: 'Document', color: '#3498DB', type: 'document' },
  xls: { icon: 'Document', color: '#27AE60', type: 'document' },
  xlsx: { icon: 'Document', color: '#27AE60', type: 'document' },
  jpg: { icon: 'Picture', color: '#9B59B6', type: 'image' },
  jpeg: { icon: 'Picture', color: '#9B59B6', type: 'image' },
  png: { icon: 'Picture', color: '#9B59B6', type: 'image' },
  gif: { icon: 'Picture', color: '#9B59B6', type: 'image' },
  mp4: { icon: 'VideoPlay', color: '#E91E63', type: 'video' },
  avi: { icon: 'VideoPlay', color: '#E91E63', type: 'video' },
  mp3: { icon: 'Headset', color: '#00BCD4', type: 'audio' },
  wav: { icon: 'Headset', color: '#00BCD4', type: 'audio' },
  zip: { icon: 'Files', color: '#FF9800', type: 'archive' },
  txt: { icon: 'Document', color: '#95A5A6', type: 'document' },
  md: { icon: 'Document', color: '#34495E', type: 'document' }
}

export function getFileTypeInfo(filename) {
  if (!filename) return { icon: 'Document', color: '#95A5A6', type: 'other' }
  
  const ext = filename.split('.').pop()?.toLowerCase()
  return FILE_TYPE_MAP[ext] || { icon: 'Document', color: '#95A5A6', type: 'other' }
}

export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}