import { useEffect, useState } from 'react'
import { useChatStore } from '../../store/chatStore'
import { messagesApi } from '../../api/messages'
import { roomsApi } from '../../api/rooms'
import { useWS } from '../../context/WebSocketContext'
import MessageList from './MessageList'
import MessageInput from './MessageInput'
import PresenceList from './PresenceList'

export default function ChatWindow() {
  const { activeRoomId, rooms, messages, setMessages, prependMessages, setCursor, messageCursors } = useChatStore()
  const { subscribeToRoom, connected } = useWS()
  const [roomDetails, setRoomDetails] = useState<typeof rooms[0] | null>(null)
  const [loadingHistory, setLoadingHistory] = useState(false)

  const activeRoom = rooms.find((r) => r.id === activeRoomId) ?? null

  // Load history and room metadata whenever the active room changes (HTTP — no WS needed)
  useEffect(() => {
    if (!activeRoomId) return
    setLoadingHistory(true)
    messagesApi.getHistory(activeRoomId)
      .then(({ data }) => {
        setMessages(activeRoomId, [...data].reverse())
        setCursor(activeRoomId, data.length === 50 ? data[data.length - 1].createdAt : null)
      })
      .catch(console.error)
      .finally(() => setLoadingHistory(false))
    roomsApi.getRoomDetails(activeRoomId).then(({ data }) => setRoomDetails(data)).catch(console.error)
    messagesApi.markRead(activeRoomId).catch(console.error)
  }, [activeRoomId, setMessages, setCursor])

  // Subscribe to the room's STOMP topics — re-runs whenever the WS connects OR the room changes.
  // This handles the race condition where the user enters a room before the STOMP handshake finishes.
  useEffect(() => {
    if (!activeRoomId || !connected) return
    return subscribeToRoom(activeRoomId)
  }, [activeRoomId, connected, subscribeToRoom])

  async function loadMoreMessages() {
    if (!activeRoomId) return
    const cursor = messageCursors[activeRoomId]
    if (!cursor) return
    setLoadingHistory(true)
    try {
      const { data } = await messagesApi.getHistory(activeRoomId, cursor)
      prependMessages(activeRoomId, [...data].reverse())
      setCursor(activeRoomId, data.length === 50 ? data[data.length - 1].createdAt : null)
    } finally {
      setLoadingHistory(false)
    }
  }

  if (!activeRoomId) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-600">
        <div className="text-center">
          <p className="text-4xl mb-3">💬</p>
          <p className="text-lg font-medium text-gray-500">Select a room to start chatting</p>
          <p className="text-sm text-gray-600 mt-1">Or create a new room from the sidebar</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-1 flex flex-col">
        <div className="px-6 py-4 border-b border-gray-800 bg-gray-900 flex items-center justify-between">
          <div>
            <h2 className="font-semibold text-white">
              {activeRoom?.type === 'PRIVATE' ? '🔒' : '#'} {activeRoom?.name}
            </h2>
            {activeRoom?.description && (
              <p className="text-xs text-gray-500 mt-0.5">{activeRoom.description}</p>
            )}
          </div>
          <span className="text-xs text-gray-500">{activeRoom?.memberCount} members</span>
        </div>

        {messageCursors[activeRoomId] && (
          <div className="text-center py-2">
            <button
              onClick={loadMoreMessages}
              disabled={loadingHistory}
              className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
            >
              {loadingHistory ? 'Loading…' : 'Load older messages'}
            </button>
          </div>
        )}

        <MessageList
          messages={messages[activeRoomId] || []}
          loading={loadingHistory && !(messages[activeRoomId]?.length)}
        />

        <MessageInput roomId={activeRoomId} />
      </div>

      {roomDetails && <PresenceList members={roomDetails.members} />}
    </div>
  )
}
