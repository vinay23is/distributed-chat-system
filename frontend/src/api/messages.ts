import client from './client'
import type { Message } from '../types'

export const messagesApi = {
  getHistory: (roomId: string, cursor?: string, limit = 50) =>
    client.get<Message[]>(`/rooms/${roomId}/messages`, {
      params: { cursor, limit },
    }),

  sendMessage: (roomId: string, content: string) =>
    client.post<Message>(`/rooms/${roomId}/messages`, { content }),

  searchMessages: (roomId: string, q: string) =>
    client.get<Message[]>(`/rooms/${roomId}/messages/search`, { params: { q } }),

  editMessage: (roomId: string, messageId: string, content: string) =>
    client.patch<Message>(`/rooms/${roomId}/messages/${messageId}`, { content }),

  deleteMessage: (roomId: string, messageId: string) =>
    client.delete(`/rooms/${roomId}/messages/${messageId}`),

  markRead: (roomId: string) =>
    client.post(`/rooms/${roomId}/messages/read`),
}
