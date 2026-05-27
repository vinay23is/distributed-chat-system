import client from './client'
import type { AuthResponse } from '../types'

export const authApi = {
  register: (name: string, email: string, password: string) =>
    client.post<AuthResponse>('/auth/register', { name, email, password }),

  login: (email: string, password: string) =>
    client.post<AuthResponse>('/auth/login', { email, password }),

  getMe: () => client.get('/users/me'),

  heartbeat: () => client.post('/users/me/heartbeat'),
}
