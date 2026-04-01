import type { NextAuthOptions } from 'next-auth'
import GoogleProvider from 'next-auth/providers/google'

const googleClientId = process.env.GOOGLE_CLIENT_ID
const googleClientSecret = process.env.GOOGLE_CLIENT_SECRET

function parseCsvValues(value: string | undefined): string[] {
  if (!value) {
    return []
  }

  return Array.from(
    new Set(
      value
        .split(',')
        .map((entry) => entry.trim().toLowerCase())
        .filter((entry) => entry.length > 0)
    )
  )
}

function normalizeDomain(value: string): string {
  return value.replace(/^@+/, '').trim().toLowerCase()
}

function resolveEmailDomain(email: string): string {
  const atIndex = email.lastIndexOf('@')
  if (atIndex <= 0 || atIndex >= email.length - 1) {
    return ''
  }

  return email.slice(atIndex + 1).toLowerCase()
}

const ALLOWED_SIGNIN_EMAILS = parseCsvValues(process.env.ALLOWED_SIGNIN_EMAILS)
const ALLOWED_SIGNIN_DOMAINS = parseCsvValues(process.env.ALLOWED_SIGNIN_DOMAINS).map(normalizeDomain)

function isAllowedGoogleAccount(email: string | null | undefined): boolean {
  const normalizedEmail = (email ?? '').trim().toLowerCase()
  if (normalizedEmail.length === 0) {
    return false
  }

  if (ALLOWED_SIGNIN_EMAILS.length === 0 && ALLOWED_SIGNIN_DOMAINS.length === 0) {
    return true
  }

  if (ALLOWED_SIGNIN_EMAILS.includes(normalizedEmail)) {
    return true
  }

  const domain = resolveEmailDomain(normalizedEmail)
  return domain.length > 0 && ALLOWED_SIGNIN_DOMAINS.includes(domain)
}

const providers =
  googleClientId && googleClientSecret
    ? [
        GoogleProvider({
          clientId: googleClientId,
          clientSecret: googleClientSecret,
        }),
      ]
    : []

export const authOptions: NextAuthOptions = {
  secret: process.env.NEXTAUTH_SECRET,
  providers,
  pages: {
    error: '/auth/error',
  },
  session: {
    strategy: 'jwt',
  },
  callbacks: {
    async signIn({ user, account }) {
      if (account?.provider !== 'google') {
        return false
      }

      return isAllowedGoogleAccount(user.email)
    },
  },
}
