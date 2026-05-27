import { useChatStore } from '../../store/chatStore'
import type { RoomMember } from '../../types'

interface Props {
  members: RoomMember[]
}

export default function PresenceList({ members }: Props) {
  const { onlineUsers } = useChatStore()

  const online = members.filter((m) => onlineUsers.has(m.userId))
  const offline = members.filter((m) => !onlineUsers.has(m.userId))

  return (
    <div className="w-48 bg-gray-900 border-l border-gray-800 flex flex-col overflow-y-auto scrollbar-thin">
      <div className="px-3 py-3 border-b border-gray-800">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
          Members — {members.length}
        </p>
      </div>

      {online.length > 0 && (
        <section className="px-3 pt-3">
          <p className="text-xs text-gray-600 mb-2">Online — {online.length}</p>
          {online.map((m) => (
            <MemberRow key={m.userId} member={m} online />
          ))}
        </section>
      )}

      {offline.length > 0 && (
        <section className="px-3 pt-3">
          <p className="text-xs text-gray-600 mb-2">Offline — {offline.length}</p>
          {offline.map((m) => (
            <MemberRow key={m.userId} member={m} online={false} />
          ))}
        </section>
      )}
    </div>
  )
}

function MemberRow({ member, online }: { member: RoomMember; online: boolean }) {
  const initials = member.name.split(' ').map((w) => w[0]).join('').slice(0, 2).toUpperCase()

  return (
    <div className="flex items-center gap-2 py-1.5 px-1 rounded-lg hover:bg-gray-800 transition-colors">
      <div className="relative flex-shrink-0">
        <div className="w-7 h-7 rounded-full bg-gray-700 flex items-center justify-center text-xs font-medium text-gray-300">
          {member.avatarUrl
            ? <img src={member.avatarUrl} alt={member.name} className="w-7 h-7 rounded-full object-cover" />
            : initials
          }
        </div>
        <span
          className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-gray-900 ${
            online ? 'bg-green-500' : 'bg-gray-600'
          }`}
        />
      </div>
      <div className="min-w-0">
        <p className={`text-xs truncate ${online ? 'text-gray-200' : 'text-gray-500'}`}>
          {member.name}
        </p>
        {member.role !== 'MEMBER' && (
          <p className="text-xs text-gray-600">{member.role.toLowerCase()}</p>
        )}
      </div>
    </div>
  )
}
