import { describe, expect, it } from 'vitest'
import { getRouteAccessDecision, isPublicRoute } from '@/lib/route-access'

describe('route access', () => {
  it('allows ads page without login', () => {
    const decision = getRouteAccessDecision('/ads', {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('allows events archive without login', () => {
    const decision = getRouteAccessDecision('/events', {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('redirects admins away from temporarily disabled dashboard', () => {
    const decision = getRouteAccessDecision('/dashboard', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
  })

  it('lets a regular member view notices', () => {
    const context = {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    }

    expect(getRouteAccessDecision('/notices', context)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
    expect(getRouteAccessDecision('/notices/5', context)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('shows the notice list to everyone but keeps notices themselves to members', () => {
    const withoutAccess = {
      isLoggedIn: true,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    }
    const visitor = {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    }

    expect(isPublicRoute('/notices')).toBe(true)
    expect(getRouteAccessDecision('/notices', withoutAccess)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
    expect(getRouteAccessDecision('/notices', visitor)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
    expect(getRouteAccessDecision('/notices/5', withoutAccess)).toEqual({
      allowed: false,
      redirectTo: null,
      blocked: true,
    })
    expect(getRouteAccessDecision('/notices/5', visitor)).toEqual({
      allowed: false,
      redirectTo: '/',
      blocked: false,
    })
  })

  it('allows a regular admin to view notices', () => {
    const context = {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    }

    expect(getRouteAccessDecision('/notices', context)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
    expect(getRouteAccessDecision('/notices/5', context)).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('allows a super admin to view notices', () => {
    const decision = getRouteAccessDecision('/notices', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: true,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('opens multi-balance, points and predictions to members but not to visitors', () => {
    const member = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }
    const pending = { ...member, canAccess: false }

    for (const path of ['/balance/multi', '/points', '/predictions']) {
      expect(getRouteAccessDecision(path, member)).toEqual({
        allowed: true,
        redirectTo: null,
        blocked: false,
      })
      expect(getRouteAccessDecision(path, pending).allowed).toBe(false)
      expect(getRouteAccessDecision(path, { ...member, isLoggedIn: false, canAccess: false }).allowed).toBe(false)
    }
  })

  it('opens the cannon draw to members but not to visitors or applicants without access', () => {
    const member = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }

    expect(getRouteAccessDecision('/cannon', member)).toEqual({ allowed: true, redirectTo: null, blocked: false })
    expect(getRouteAccessDecision('/cannon', { ...member, canAccess: false })).toEqual({
      allowed: false,
      redirectTo: null,
      blocked: true,
    })
    expect(getRouteAccessDecision('/cannon', { ...member, isLoggedIn: false, canAccess: false })).toEqual({
      allowed: false,
      redirectTo: '/',
      blocked: false,
    })
    expect(isPublicRoute('/cannon')).toBe(false)
  })

  it('opens a member\'s points and the monthly ranking to members and keeps the prize events with admins', () => {
    const member = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }

    for (const path of ['/points', '/points/ranking', '/points/policy']) {
      expect(getRouteAccessDecision(path, member).allowed).toBe(true)
    }
    expect(getRouteAccessDecision('/points/events', member)).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
    expect(getRouteAccessDecision('/points/events', { ...member, isAdmin: true }).allowed).toBe(true)
  })

  it('keeps every one of the three access pages with super admins', () => {
    const admin = { isLoggedIn: true, canAccess: true, isAdmin: true, isSuperAdmin: false }

    for (const path of ['/admin/access/admins', '/admin/access/result-editors', '/admin/access/allowed']) {
      expect(getRouteAccessDecision(path, admin)).toEqual({ allowed: false, redirectTo: '/players', blocked: false })
      expect(getRouteAccessDecision(path, { ...admin, isSuperAdmin: true }).allowed).toBe(true)
    }
  })

  it('keeps the tournament page with admins', () => {
    const member = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }

    expect(getRouteAccessDecision('/tournaments', member)).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
    expect(getRouteAccessDecision('/tournaments', { ...member, isAdmin: true })).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('keeps super admin routes restricted to super admins', () => {
    const decision = getRouteAccessDecision('/admin/access', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(decision.allowed).toBe(false)
    expect(decision.redirectTo).toBe('/players')
  })

  it('lets a regular admin open the audit log', () => {
    const decision = getRouteAccessDecision('/admin/audit', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('keeps the audit log away from regular members', () => {
    const decision = getRouteAccessDecision('/admin/audit', {
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: false,
      redirectTo: '/players',
      blocked: false,
    })
  })

  it('shows the access notice on home to a signed-in applicant without access', () => {
    const decision = getRouteAccessDecision('/', {
      isLoggedIn: true,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: false,
      redirectTo: null,
      blocked: true,
    })
  })

  it('keeps home public for visitors who are not signed in', () => {
    const decision = getRouteAccessDecision('/', {
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })

  it('keeps other public pages readable for a signed-in applicant without access', () => {
    const decision = getRouteAccessDecision('/results', {
      isLoggedIn: true,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(decision).toEqual({
      allowed: true,
      redirectTo: null,
      blocked: false,
    })
  })
})
