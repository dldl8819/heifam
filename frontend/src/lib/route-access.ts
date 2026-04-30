export type RouteAccessContext = {
  isLoggedIn: boolean
  canAccess: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
}

export type RouteAccessDecision = {
  allowed: boolean
  redirectTo: string | null
  blocked: boolean
}

type RouteRequirement = 'public' | 'member' | 'admin' | 'super_admin'

const AUTH_PATH_PREFIX = '/auth'
const PUBLIC_PATHS = ['/', '/results', '/privacy', '/terms']
const MEMBER_PATHS = ['/balance', '/players']
const ADMIN_PATHS = ['/ranking', '/balance/multi', '/captain-draft', '/import', '/admin/access']
const SUPER_ADMIN_PATHS = ['/dashboard', '/players/import']

function getAuthenticatedDefaultPath(context: RouteAccessContext): string {
  if (context.isSuperAdmin) {
    return '/dashboard'
  }

  if (context.isAdmin) {
    return '/ranking'
  }

  return '/players'
}

function matchesPath(pathname: string, path: string): boolean {
  if (path === '/') {
    return pathname === '/'
  }

  return pathname === path || pathname.startsWith(`${path}/`)
}

function resolveRouteRequirement(pathname: string): RouteRequirement {
  if (pathname === AUTH_PATH_PREFIX || pathname.startsWith(`${AUTH_PATH_PREFIX}/`)) {
    return 'public'
  }

  if (SUPER_ADMIN_PATHS.some((path) => matchesPath(pathname, path))) {
    return 'super_admin'
  }

  if (ADMIN_PATHS.some((path) => matchesPath(pathname, path))) {
    return 'admin'
  }

  if (MEMBER_PATHS.some((path) => matchesPath(pathname, path))) {
    return 'member'
  }

  if (PUBLIC_PATHS.some((path) => matchesPath(pathname, path))) {
    return 'public'
  }

  return 'member'
}

export function getRouteAccessDecision(
  pathname: string,
  context: RouteAccessContext
): RouteAccessDecision {
  const requirement = resolveRouteRequirement(pathname)

  if (requirement === 'public') {
    if (pathname === '/' && context.isLoggedIn && context.canAccess) {
      return {
        allowed: false,
        redirectTo: getAuthenticatedDefaultPath(context),
        blocked: false,
      }
    }

    return {
      allowed: true,
      redirectTo: null,
      blocked: false,
    }
  }

  if (!context.isLoggedIn) {
    return {
      allowed: false,
      redirectTo: '/',
      blocked: false,
    }
  }

  if (!context.canAccess) {
    return {
      allowed: false,
      redirectTo: null,
      blocked: true,
    }
  }

  if (requirement === 'member') {
    return {
      allowed: true,
      redirectTo: null,
      blocked: false,
    }
  }

  if (requirement === 'admin') {
    return {
      allowed: context.isAdmin,
      redirectTo: context.isAdmin ? null : getAuthenticatedDefaultPath(context),
      blocked: false,
    }
  }

  return {
    allowed: context.isSuperAdmin,
    redirectTo: context.isSuperAdmin ? null : getAuthenticatedDefaultPath(context),
    blocked: false,
  }
}
