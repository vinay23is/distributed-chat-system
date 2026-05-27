import { useEffect } from 'react'
import { roomsApi } from '../api/rooms'
import { notificationsApi } from '../api/notifications'
import { useChatStore } from '../store/chatStore'
import Sidebar from '../components/chat/Sidebar'
import ChatWindow from '../components/chat/ChatWindow'

export default function ChatPage() {
  const { setRooms, setUnreadCount } = useChatStore()

  useEffect(() => {
    roomsApi.getMyRooms().then(({ data }) => setRooms(data)).catch(console.error)
    notificationsApi.getUnreadCount().then(({ data }) => setUnreadCount(data.count)).catch(console.error)
  }, [setRooms, setUnreadCount])

  return (
    <div className="flex h-screen bg-gray-950">
      <Sidebar />
      <ChatWindow />
    </div>
  )
}
