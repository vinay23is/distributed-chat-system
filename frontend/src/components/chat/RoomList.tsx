import clsx from 'clsx'
import { useChatStore } from '../../store/chatStore'
import { roomsApi } from '../../api/rooms'
import type { Room } from '../../types'

interface Props {
  rooms: Room[]
}

export default function RoomList({ rooms }: Props) {
  const { activeRoomId, setActiveRoom, addRoom } = useChatStore()

  async function joinAndSelect(room: Room) {
    if (room.currentUserRole) {
      setActiveRoom(room.id)
    } else {
      const { data } = await roomsApi.joinRoom(room.id)
      addRoom(data)
      setActiveRoom(room.id)
    }
  }

  if (rooms.length === 0) {
    return (
      <p className="text-xs text-gray-600 px-4 py-3">
        No rooms yet. Create or browse to join one.
      </p>
    )
  }

  return (
    <ul className="space-y-0.5 px-2">
      {rooms.map((room) => (
        <li key={room.id}>
          <button
            onClick={() => joinAndSelect(room)}
            className={clsx(
              'w-full text-left px-3 py-2 rounded-lg transition-colors text-sm',
              activeRoomId === room.id
                ? 'bg-brand-600/20 text-brand-400'
                : 'text-gray-300 hover:bg-gray-800 hover:text-white'
            )}
          >
            <div className="flex items-center justify-between">
              <span className="truncate">
                {room.type === 'PRIVATE' ? '🔒' : '#'} {room.name}
              </span>
              <span className="text-xs text-gray-600 ml-1">{room.memberCount}</span>
            </div>
          </button>
        </li>
      ))}
    </ul>
  )
}
