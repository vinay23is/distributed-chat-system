import { create } from 'zustand'
import type { Room, Message, Notification } from '../types'

interface ChatState {
  rooms: Room[]
  activeRoomId: string | null
  messages: Record<string, Message[]>
  onlineUsers: Set<string>
  typingUsers: Record<string, string[]>  // roomId -> userIds
  notifications: Notification[]
  unreadCount: number
  messageCursors: Record<string, string | null>  // roomId -> next cursor

  setRooms: (rooms: Room[]) => void
  addRoom: (room: Room) => void
  setActiveRoom: (roomId: string | null) => void
  setMessages: (roomId: string, messages: Message[]) => void
  prependMessages: (roomId: string, messages: Message[]) => void
  appendMessage: (roomId: string, message: Message) => void
  updateMessage: (roomId: string, message: Message) => void
  deleteMessage: (roomId: string, messageId: string) => void
  setOnlineUsers: (userIds: string[]) => void
  addOnlineUser: (userId: string) => void
  removeOnlineUser: (userId: string) => void
  setTyping: (roomId: string, userId: string, typing: boolean) => void
  setNotifications: (notifications: Notification[]) => void
  addNotification: (notification: Notification) => void
  setUnreadCount: (count: number) => void
  setCursor: (roomId: string, cursor: string | null) => void
}

export const useChatStore = create<ChatState>()((set) => ({
  rooms: [],
  activeRoomId: null,
  messages: {},
  onlineUsers: new Set(),
  typingUsers: {},
  notifications: [],
  unreadCount: 0,
  messageCursors: {},

  setRooms: (rooms) => set({ rooms }),

  addRoom: (room) =>
    set((state) => ({
      rooms: state.rooms.some((r) => r.id === room.id)
        ? state.rooms.map((r) => r.id === room.id ? room : r)
        : [room, ...state.rooms],
    })),

  setActiveRoom: (roomId) => set({ activeRoomId: roomId }),

  setMessages: (roomId, messages) =>
    set((state) => ({ messages: { ...state.messages, [roomId]: messages } })),

  prependMessages: (roomId, older) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [roomId]: [...older, ...(state.messages[roomId] || [])],
      },
    })),

  appendMessage: (roomId, message) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [roomId]: [...(state.messages[roomId] || []), message],
      },
    })),

  updateMessage: (roomId, updated) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [roomId]: (state.messages[roomId] || []).map((m) =>
          m.id === updated.id ? updated : m
        ),
      },
    })),

  deleteMessage: (roomId, messageId) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [roomId]: (state.messages[roomId] || []).map((m) =>
          m.id === messageId ? { ...m, deleted: true, content: '' } : m
        ),
      },
    })),

  setOnlineUsers: (userIds) => set({ onlineUsers: new Set(userIds) }),

  addOnlineUser: (userId) =>
    set((state) => ({ onlineUsers: new Set([...state.onlineUsers, userId]) })),

  removeOnlineUser: (userId) =>
    set((state) => {
      const next = new Set(state.onlineUsers)
      next.delete(userId)
      return { onlineUsers: next }
    }),

  setTyping: (roomId, userId, typing) =>
    set((state) => {
      const current = state.typingUsers[roomId] || []
      const updated = typing
        ? current.includes(userId) ? current : [...current, userId]
        : current.filter((id) => id !== userId)
      return { typingUsers: { ...state.typingUsers, [roomId]: updated } }
    }),

  setNotifications: (notifications) => set({ notifications }),

  addNotification: (notification) =>
    set((state) => ({
      notifications: [notification, ...state.notifications],
      unreadCount: state.unreadCount + 1,
    })),

  setUnreadCount: (count) => set({ unreadCount: count }),

  setCursor: (roomId, cursor) =>
    set((state) => ({
      messageCursors: { ...state.messageCursors, [roomId]: cursor },
    })),
}))
