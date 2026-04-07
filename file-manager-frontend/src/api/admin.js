import axios from 'axios'
import { ElMessage } from 'element-plus'

const adminRequest = axios.create({
  baseURL: '/api/admin',
  timeout: 30000,
  withCredentials: true
})

adminRequest.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export const adminApi = {
  getUsers(page = 0, size = 10, status = null) {
    const params = { page, size }
    if (status) params.status = status
    return adminRequest({
      url: '/users',
      method: 'get',
      params
    })
  },

  createUser(data) {
    return adminRequest({
      url: '/users',
      method: 'post',
      data
    })
  },

  approveUser(id) {
    return adminRequest({
      url: `/users/${id}/approve`,
      method: 'put'
    })
  },

  disableUser(id) {
    return adminRequest({
      url: `/users/${id}/disable`,
      method: 'put'
    })
  },

  enableUser(id) {
    return adminRequest({
      url: `/users/${id}/enable`,
      method: 'put'
    })
  },

  deleteUser(id) {
    return adminRequest({
      url: `/users/${id}`,
      method: 'delete'
    })
  }
}