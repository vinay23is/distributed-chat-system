import { useState } from 'react'
import { useChatStore } from '../../store/chatStore'
import { useAuthStore } from '../../store/authStore'
import { roomsApi } from '../../api/rooms'
import RoomList from './RoomList'

export default function Sidebar() {
  const { rooms, setRooms, addRoom, unreadCount } = useChatStore()
  const { name, clearAuth } = useAuthStore()
  const [showCreate, setShowCreate] = useState(false)
  const [roomName, setRoomName] = useState('')
  const [roomDesc, setRoomDesc] = useState('')
  const [creating, setCreating] = useState(false)

  async function createRoom(e: React.FormEvent) {
    e.preventDefault()
    if (!roomName.trim()) return
    setCreating(true)
    try {
      const { data } = await roomsApi.createRoom(roomName.trim(), roomDesc.trim(), 'PUBLIC')
      addRoom(data)
      setRoomName('')
      setRoomDesc('')
      setShowCreate(false)
    } finally {
      setCreating(false)
    }
  }

  async function loadPublicRooms() {
    const { data } = await roomsApi.getPublicRooms()
    setRooms(data)
  }

  return (
    <div className="w-64 bg-gray-900 border-r border-gray-800 flex flex-col">
      {/* Header */}
      <div className="px-4 py-4 border-b border-gray-800">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-semibold text-white text-sm">{name}</p>
            <p className="text-xs text-gray-500">Online</p>
          </div>
          <div className="flex items-center gap-2">
            {unreadCount > 0 && (
              <span className="bg-brand-600 text-white text-xs font-bold rounded-full px-2 py-0.5">
                {unreadCount}
              </span>
            )}
            <button
              onClick={clearAuth}
              className="text-gray-500 hover:text-white text-xs"
              title="Sign out"
            >
              ⏻
            </button>
          </div>
        </div>
      </div>

      {/* Room actions */}
      <div className="px-4 pt-3 pb-2 flex items-center justify-between">
        <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Rooms</span>
        <div className="flex gap-1">
          <button
            onClick={loadPublicRooms}
            className="text-gray-500 hover:text-white text-xs px-1"
            title="Browse public rooms"
          >
            ⊕
          </button>
          <button
            onClick={() => setShowCreate(!showCreate)}
            className="text-gray-500 hover:text-white text-xs px-1"
            title="Create room"
          >
            +
          </button>
        </div>
      </div>

      {/* Create room form */}
      {showCreate && (
        <form onSubmit={createRoom} className="mx-3 mb-3 bg-gray-800 rounded-lg p-3 space-y-2">
          <input
            value={roomName}
            onChange={(e) => setRoomName(e.target.value)}
            placeholder="Room name"
            className="w-full bg-gray-700 rounded px-3 py-1.5 text-sm text-white placeholder-gray-500 focus:outline-none"
            required
          />
          <input
            value={roomDesc}
            onChange={(e) => setRoomDesc(e.target.value)}
            placeholder="Description (optional)"
            className="w-full bg-gray-700 rounded px-3 py-1.5 text-sm text-white placeholder-gray-500 focus:outline-none"
          />
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={creating}
              className="flex-1 bg-brand-600 hover:bg-brand-700 text-white text-xs py-1.5 rounded transition-colors"
            >
              {creating ? '…' : 'Create'}
            </button>
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="flex-1 bg-gray-700 hover:bg-gray-600 text-gray-300 text-xs py-1.5 rounded transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Room list */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        <RoomList rooms={rooms} />
      </div>
    </div>
  )
}
