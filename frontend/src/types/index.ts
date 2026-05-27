export interface User {
  id: string
  name: string
  email: string
  avatarUrl?: string
  lastSeenAt?: string
}

export interface AuthResponse {
  token: string
  userId: string
  name: string
  email: string
  avatarUrl?: string
  expiresAt: string
}

export interface Room {
  id: string
  name: string
  description?: string
  type: 'PUBLIC' | 'PRIVATE'
  createdById: string
  createdByName: string
  createdAt: string
  memberCount: number
  currentUserRole?: 'OWNER' | 'ADMIN' | 'MEMBER'
  members: RoomMember[]
}

export interface RoomMember {
  userId: string
  name: string
  avatarUrl?: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  online: boolean
}

export interface Message {
  id: string
  roomId: string
  senderId: string
  senderName: string
  senderAvatarUrl?: string
  content: string
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM'
  createdAt: string
  editedAt?: string
  deleted: boolean
  pinned: boolean
  receipts: ReceiptSummary[]
}

export interface ReceiptSummary {
  userId: string
  status: 'SENT' | 'DELIVERED' | 'READ'
}

export interface Notification {
  id: string
  userId: string
  roomId?: string
  messageId?: string
  type: 'NEW_MESSAGE' | 'MENTION' | 'ROOM_INVITE' | 'SYSTEM'
  body?: string
  read: boolean
  createdAt: string
}

export interface ChatEvent {
  eventType: string
  roomId?: string
  messageId?: string
  senderId?: string
  senderName?: string
  senderAvatarUrl?: string
  content?: string
  messageType?: string
  timestamp: string
  payload?: unknown
}

export interface TypingUser {
  userId: string
  userName: string
}

export interface PresenceEvent {
  userId: string
  event: 'JOINED' | 'LEFT'
}
