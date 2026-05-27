import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  userId: string | null
  name: string | null
  email: string | null
  avatarUrl?: string | null
  isAuthenticated: boolean
  setAuth: (token: string, userId: string, name: string, email: string, avatarUrl?: string) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      name: null,
      email: null,
      avatarUrl: null,
      isAuthenticated: false,

      setAuth: (token, userId, name, email, avatarUrl) => {
        localStorage.setItem('token', token)
        set({ token, userId, name, email, avatarUrl, isAuthenticated: true })
      },

      clearAuth: () => {
        localStorage.removeItem('token')
        set({ token: null, userId: null, name: null, email: null, avatarUrl: null, isAuthenticated: false })
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        token: state.token,
        userId: state.userId,
        name: state.name,
        email: state.email,
        avatarUrl: state.avatarUrl,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
