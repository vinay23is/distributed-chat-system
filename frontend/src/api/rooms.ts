import client from './client'
import type { Room } from '../types'

export const roomsApi = {
  getPublicRooms: () => client.get<Room[]>('/rooms'),

  getMyRooms: () => client.get<Room[]>('/rooms/me'),

  getRoomDetails: (roomId: string) => client.get<Room>(`/rooms/${roomId}`),

  createRoom: (name: string, description: string, type: string) =>
    client.post<Room>('/rooms', { name, description, type }),

  joinRoom: (roomId: string) => client.post<Room>(`/rooms/${roomId}/join`),

  leaveRoom: (roomId: string) => client.post(`/rooms/${roomId}/leave`),

  kickMember: (roomId: string, userId: string) =>
    client.delete(`/rooms/${roomId}/members/${userId}`),

  muteUser: (roomId: string, userId: string, minutes: number) =>
    client.post(`/rooms/${roomId}/members/${userId}/mute?minutes=${minutes}`),
}
