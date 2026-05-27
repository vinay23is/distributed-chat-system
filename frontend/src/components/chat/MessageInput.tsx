import { useState, useRef, useCallback } from 'react'
import { useWS } from '../../context/WebSocketContext'

interface Props {
  roomId: string
}

const TYPING_DEBOUNCE_MS = 1500

export default function MessageInput({ roomId }: Props) {
  const [content, setContent] = useState('')
  const { sendMessage, sendTyping } = useWS()
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isTypingRef = useRef(false)

  const handleTypingStart = useCallback(() => {
    if (!isTypingRef.current) {
      isTypingRef.current = true
      sendTyping(roomId, true)
    }
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    typingTimeoutRef.current = setTimeout(() => {
      isTypingRef.current = false
      sendTyping(roomId, false)
    }, TYPING_DEBOUNCE_MS)
  }, [roomId, sendTyping])

  function handleChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setContent(e.target.value)
    if (e.target.value) handleTypingStart()
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  function submit() {
    const trimmed = content.trim()
    if (!trimmed) return
    sendMessage(roomId, trimmed)
    setContent('')
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    if (isTypingRef.current) {
      isTypingRef.current = false
      sendTyping(roomId, false)
    }
  }

  return (
    <div className="px-4 pb-4 pt-2">
      <div className="flex items-end gap-2 bg-gray-800 border border-gray-700 rounded-xl px-4 py-2">
        <textarea
          value={content}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder="Send a message… (Enter to send, Shift+Enter for newline)"
          rows={1}
          className="flex-1 bg-transparent text-white text-sm placeholder-gray-500 resize-none focus:outline-none max-h-32 scrollbar-thin"
          style={{ minHeight: '24px' }}
        />
        <button
          onClick={submit}
          disabled={!content.trim()}
          className="text-brand-500 hover:text-brand-400 disabled:text-gray-700 transition-colors pb-0.5"
          title="Send"
        >
          ➤
        </button>
      </div>
    </div>
  )
}
