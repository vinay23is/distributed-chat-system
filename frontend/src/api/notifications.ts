import client from './client'
import type { Notification } from '../types'

export const notificationsApi = {
  getNotifications: (page = 0) =>
    client.get<Notification[]>('/notifications', { params: { page } }),

  getUnreadCount: () => client.get<{ count: number }>('/notifications/count'),

  markAllRead: () => client.post('/notifications/read-all'),

  markRead: (notificationId: string) =>
    client.patch(`/notifications/${notificationId}/read`),
}
