import { useEffect, useRef } from 'react'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import { useAuthStore } from '../../store/authStore'
import { useChatStore } from '../../store/chatStore'
import type { Message } from '../../types'

dayjs.extend(relativeTime)

interface Props {
  messages: Message[]
  loading: boolean
}

export default function MessageList({ messages, loading }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)
  const { userId } = useAuthStore()
  const { activeRoomId, typingUsers } = useChatStore()
  const typingInRoom = activeRoomId ? (typingUsers[activeRoomId] || []) : []

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="animate-pulse text-gray-600">Loading messages…</div>
      </div>
    )
  }

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-600 text-sm">
        No messages yet. Be the first to say something!
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto scrollbar-thin px-4 py-4 space-y-1">
      {messages.map((msg, i) => {
        const isOwn = msg.senderId === userId
        const showHeader = i === 0 || messages[i - 1].senderId !== msg.senderId

        return (
          <div key={msg.id} className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[70%] ${isOwn ? 'items-end' : 'items-start'} flex flex-col`}>
              {showHeader && !isOwn && (
                <span className="text-xs text-gray-500 mb-1 ml-1">{msg.senderName}</span>
              )}
              <div
                className={`rounded-2xl px-4 py-2 text-sm leading-relaxed ${
                  msg.deleted
                    ? 'bg-gray-800 text-gray-600 italic'
                    : isOwn
                    ? 'bg-brand-600 text-white'
                    : 'bg-gray-800 text-gray-100'
                }`}
              >
                {msg.deleted ? 'Message deleted' : msg.content}
                {msg.pinned && !msg.deleted && (
                  <span className="ml-2 text-xs opacity-60">📌</span>
                )}
              </div>
              <div className="flex items-center gap-1 mt-0.5 px-1">
                <span className="text-xs text-gray-600">
                  {dayjs(msg.createdAt).fromNow()}
                </span>
                {msg.editedAt && (
                  <span className="text-xs text-gray-600">(edited)</span>
                )}
                {isOwn && !msg.deleted && (
                  <span className="text-xs text-gray-600">
                    {getReceiptIcon(msg.receipts.map((r) => r.status))}
                  </span>
                )}
              </div>
            </div>
          </div>
        )
      })}

      {typingInRoom.length > 0 && (
        <div className="text-xs text-gray-500 italic px-2">
          {typingInRoom.length === 1
            ? 'Someone is typing…'
            : `${typingInRoom.length} people are typing…`}
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  )
}

function getReceiptIcon(statuses: string[]): string {
  if (statuses.some((s) => s === 'READ')) return '✓✓'
  if (statuses.some((s) => s === 'DELIVERED')) return '✓✓'
  return '✓'
}
