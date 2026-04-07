import axios from 'axios'

const authRequest = axios.create({
  baseURL: '',
  timeout: 30000,
  withCredentials: true
})

authRequest.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    return Promise.reject(error)
  }
)

export const authApi = {
  login(data) {
    return authRequest({
      url: '/api/auth/login',
      method: 'post',
      data
    })
  },

  register(data) {
    return authRequest({
      url: '/api/auth/register',
      method: 'post',
      data
    })
  },

  logout() {
    return authRequest({
      url: '/api/auth/logout',
      method: 'post'
    })
  },

  getCurrentUser() {
    return authRequest({
      url: '/api/auth/me',
      method: 'get'
    })
  }
}