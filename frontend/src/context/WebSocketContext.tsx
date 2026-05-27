import { createContext, useContext, useEffect, useRef, useCallback, useState, ReactNode } from 'react'
import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../store/authStore'
import { useChatStore } from '../store/chatStore'
import type { ChatEvent, Message } from '../types'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'
const HEARTBEAT_INTERVAL = 20_000

interface WebSocketContextValue {
  subscribeToRoom: (roomId: string) => () => void
  sendMessage: (roomId: string, content: string) => void
  sendTyping: (roomId: string, typing: boolean) => void
  isConnected: () => boolean
  connected: boolean
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null)

export function WebSocketProvider({ children }: { children: ReactNode }) {
  const clientRef = useRef<Client | null>(null)
  const [connected, setConnected] = useState(false)
  const { token } = useAuthStore()
  const store = useChatStore()

  useEffect(() => {
    if (!token) return

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: HEARTBEAT_INTERVAL,
      heartbeatOutgoing: HEARTBEAT_INTERVAL,
      reconnectDelay: 5000,
      onConnect: () => {
        console.debug('[WS] Connected')
        setConnected(true)
        stompClient.subscribe('/user/queue/notifications', (msg) => {
          store.addNotification(JSON.parse(msg.body))
        })
        stompClient.subscribe('/user/queue/errors', (msg) => {
          console.warn('[WS] Error:', msg.body)
        })
      },
      onDisconnect: () => {
        console.debug('[WS] Disconnected')
        setConnected(false)
      },
      onStompError: (frame) => console.error('[WS] STOMP error:', frame),
    })

    stompClient.activate()
    clientRef.current = stompClient

    const heartbeatInterval = setInterval(() => {
      stompClient.publish({ destination: '/app/heartbeat', body: '{}' })
    }, HEARTBEAT_INTERVAL)

    return () => {
      clearInterval(heartbeatInterval)
      setConnected(false)
      stompClient.deactivate()
      clientRef.current = null
    }
  }, [token])  // Only reconnect when token changes (login/logout)

  const subscribeToRoom = useCallback((roomId: string) => {
    const client = clientRef.current
    if (!client?.connected) return () => {}

    client.publish({ destination: `/app/room/${roomId}/join` })

    const msgSub = client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
      const event: ChatEvent = JSON.parse(msg.body)
      handleRoomEvent(roomId, event, useChatStore.getState())
    })

    const typingSub = client.subscribe(`/topic/room/${roomId}/typing`, (msg: IMessage) => {
      const event: ChatEvent = JSON.parse(msg.body)
      if (event.senderId) {
        useChatStore.getState().setTyping(roomId, event.senderId, event.eventType === 'TYPING_START')
      }
    })

    const presenceSub = client.subscribe(`/topic/room/${roomId}/presence`, (msg: IMessage) => {
      const event = JSON.parse(msg.body)
      if (event.event === 'JOINED') useChatStore.getState().addOnlineUser(event.userId)
      else if (event.event === 'LEFT') useChatStore.getState().removeOnlineUser(event.userId)
    })

    return () => {
      if (client.connected) {
        client.publish({ destination: `/app/room/${roomId}/leave` })
      }
      msgSub.unsubscribe()
      typingSub.unsubscribe()
      presenceSub.unsubscribe()
    }
  }, [])

  const sendMessage = useCallback((roomId: string, content: string) => {
    clientRef.current?.publish({
      destination: `/app/chat/${roomId}`,
      body: JSON.stringify({ content }),
    })
  }, [])

  const sendTyping = useCallback((roomId: string, typing: boolean) => {
    clientRef.current?.publish({
      destination: `/app/typing/${roomId}`,
      body: JSON.stringify({ typing }),
    })
  }, [])

  const isConnected = useCallback(() => !!clientRef.current?.connected, [])

  return (
    <WebSocketContext.Provider value={{ subscribeToRoom, sendMessage, sendTyping, isConnected, connected }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export function useWS() {
  const ctx = useContext(WebSocketContext)
  if (!ctx) throw new Error('useWS must be used inside WebSocketProvider')
  return ctx
}

function handleRoomEvent(roomId: string, event: ChatEvent, store: ReturnType<typeof useChatStore.getState>) {
  switch (event.eventType) {
    case 'MESSAGE_SENT': {
      const message: Message = {
        id: event.messageId!,
        roomId,
        senderId: event.senderId!,
        senderName: event.senderName!,
        senderAvatarUrl: event.senderAvatarUrl,
        content: event.content!,
        messageType: (event.messageType as Message['messageType']) ?? 'TEXT',
        createdAt: event.timestamp,
        deleted: false,
        pinned: false,
        receipts: [],
      }
      store.appendMessage(roomId, message)
      break
    }
    case 'MESSAGE_EDITED': {
      const existing = store.messages[roomId]?.find((m) => m.id === event.messageId)
      if (existing) {
        store.updateMessage(roomId, { ...existing, content: event.content ?? existing.content, editedAt: event.timestamp })
      }
      break
    }
    case 'MESSAGE_DELETED':
      if (event.messageId) store.deleteMessage(roomId, event.messageId)
      break
  }
}
