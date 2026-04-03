import type {
  AccessAdminListResponse,
  AccessAllowedEmailListResponse,
  AccessMeResponse,
  BalanceRequest,
  BalanceResponse,
  CreateGroupMatchResponse,
  CaptainDraftCreateRequest,
  CaptainDraftEntriesUpdateRequest,
  CaptainDraftPickRequest,
  CaptainDraftResponse,
  GroupDashboardResponse,
  GroupPlayerMmrUpdateRequest,
  HealthResponse,
  MatchTeamSide,
  MultiBalanceRequest,
  MultiBalanceResponse,
  MatchResultRequest,
  MatchResultResponse,
  PlayerRosterItem,
  PlayerRace,
  PlayerTierStatus,
  RecentMatchItem,
  RankingResponse,
  TeamSide,
} from '@/types/api'
import { supabase } from '@/lib/supabase'

const DEFAULT_DEV_API_BASE_URL = 'http://localhost:8080'
const DEFAULT_PROD_API_BASE_URL = '/api/proxy'
const RAW_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL?.trim() ?? ''
const RAW_ACCESS_API_BASE_URL = process.env.NEXT_PUBLIC_ACCESS_API_BASE_URL?.trim() ?? ''
const API_BASE_URL =
  process.env.NODE_ENV === 'production'
    ? DEFAULT_PROD_API_BASE_URL
    : RAW_API_BASE_URL.length > 0
      ? RAW_API_BASE_URL
      : DEFAULT_DEV_API_BASE_URL
const ACCESS_API_BASE_URL =
  RAW_ACCESS_API_BASE_URL.length > 0 ? RAW_ACCESS_API_BASE_URL : API_BASE_URL
const DEFAULT_API_REQUEST_TIMEOUT_MS = 10000
const ACCESS_API_REQUEST_TIMEOUT_MS = process.env.NODE_ENV === 'production' ? 45000 : 15000
const IMPORT_API_REQUEST_TIMEOUT_MS = 120000
const SESSION_IDENTITY_CACHE_TTL_MS = 5000
const USER_EMAIL_HEADER = 'X-USER-EMAIL'
const USER_NICKNAME_HEADER = 'X-USER-NICKNAME'

type SessionIdentity = {
  email: string
  nickname: string
  accessToken: string
}

let cachedSessionIdentity: (SessionIdentity & { resolvedAt: number }) | null = null
let sessionIdentityPromise: Promise<SessionIdentity> | null = null

function createUrl(path: string, baseUrlOverride?: string): string {
  const baseUrl = baseUrlOverride && baseUrlOverride.trim().length > 0
    ? baseUrlOverride.trim()
    : API_BASE_URL

  if (baseUrl.length === 0) {
    throw new Error('API base URL is not configured')
  }

  const normalizedBase = baseUrl.replace(/\/+$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}

type ApiRequestOptions = {
  adminOnly?: boolean
  requireUserEmail?: boolean
  includeUserEmail?: boolean
  includeUserNickname?: boolean
  userEmail?: string
  userNickname?: string
  accessToken?: string
  timeoutMs?: number
  baseUrlOverride?: string
}

export class ApiRequestError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function toSafeNickname(value: unknown): string {
  if (typeof value !== 'string') {
    return ''
  }
  const normalized = value.trim()
  if (normalized.length === 0) {
    return ''
  }
  return normalized.length > 100 ? normalized.slice(0, 100) : normalized
}

function resolveSessionNickname(metadata: Record<string, unknown> | undefined): string {
  if (!metadata) {
    return ''
  }

  return toSafeNickname(metadata.nickname)
}

async function resolveSessionUserIdentity(): Promise<SessionIdentity> {
  const now = Date.now()
  if (cachedSessionIdentity && now - cachedSessionIdentity.resolvedAt < SESSION_IDENTITY_CACHE_TTL_MS) {
    return {
      email: cachedSessionIdentity.email,
      nickname: cachedSessionIdentity.nickname,
      accessToken: cachedSessionIdentity.accessToken,
    }
  }

  if (sessionIdentityPromise) {
    return sessionIdentityPromise
  }

  sessionIdentityPromise = (async () => {
    try {
      const { data } = await supabase.auth.getSession()
      const user = data.session?.user
      const accessToken = data.session?.access_token?.trim() ?? ''
      const email = user?.email?.trim() ?? ''
      const nickname = resolveSessionNickname(
        user?.user_metadata && typeof user.user_metadata === 'object'
          ? (user.user_metadata as Record<string, unknown>)
          : undefined
      )

      const identity = {
        email,
        nickname,
        accessToken,
      }
      cachedSessionIdentity = { ...identity, resolvedAt: Date.now() }
      return identity
    } catch {
      return { email: '', nickname: '', accessToken: '' }
    } finally {
      sessionIdentityPromise = null
    }
  })()

  return sessionIdentityPromise
}

function buildHeaders(
  init: RequestInit | undefined,
  options: ApiRequestOptions | undefined,
  userEmail: string,
  userNickname: string,
  accessToken: string
): HeadersInit {
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')

  if (accessToken.length > 0) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  if (userEmail.length > 0) {
    headers.set(USER_EMAIL_HEADER, userEmail)
  }

  if ((options?.adminOnly || options?.includeUserNickname) && userNickname.length > 0) {
    const encodedNickname = encodeURIComponent(userNickname)
    if (encodedNickname.length > 0) {
      headers.set(USER_NICKNAME_HEADER, encodedNickname)
    }
  }

  return headers
}

async function apiRequest<T>(
  path: string,
  init?: RequestInit,
  options?: ApiRequestOptions
): Promise<T> {
  const requiresUserEmail = options?.requireUserEmail ?? Boolean(options?.adminOnly)
  const shouldIncludeUserEmail = options?.includeUserEmail ?? false
  const explicitUserEmail = options?.userEmail?.trim() ?? ''
  const explicitUserNickname = options?.userNickname?.trim() ?? ''
  const explicitAccessToken = options?.accessToken?.trim() ?? ''
  const shouldResolveSessionIdentity =
    explicitAccessToken.length === 0 && (options?.adminOnly || requiresUserEmail || shouldIncludeUserEmail)
  const identity = shouldResolveSessionIdentity
    ? await resolveSessionUserIdentity()
    : { email: '', nickname: '', accessToken: '' }
  const userEmail = explicitUserEmail.length > 0 ? explicitUserEmail : identity.email
  const userNickname = explicitUserNickname.length > 0 ? explicitUserNickname : identity.nickname
  const accessToken = explicitAccessToken.length > 0 ? explicitAccessToken : identity.accessToken

  if (requiresUserEmail && (userEmail.length === 0 || accessToken.length === 0)) {
    throw new ApiRequestError(401, '로그인이 필요합니다.')
  }

  const controller = new AbortController()
  const timeoutMs = Number.isFinite(options?.timeoutMs) && (options?.timeoutMs ?? 0) > 0
    ? (options?.timeoutMs as number)
    : DEFAULT_API_REQUEST_TIMEOUT_MS
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

  if (init?.signal) {
    if (init.signal.aborted) {
      controller.abort()
    } else {
      init.signal.addEventListener(
        'abort',
        () => controller.abort(),
        { once: true }
      )
    }
  }

  let response: Response
  try {
    response = await fetch(createUrl(path, options?.baseUrlOverride), {
      ...init,
      headers: buildHeaders(init, options, userEmail, userNickname, accessToken),
      signal: controller.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiRequestError(
        408,
        `API request timed out (${timeoutMs}ms)`
      )
    }
    throw error
  } finally {
    clearTimeout(timeoutId)
  }

  if (!response.ok) {
    const message = (await response.text()).trim()
    throw new ApiRequestError(
      response.status,
      message.length > 0
        ? `API request failed (${response.status}): ${message}`
        : `API request failed (${response.status})`
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (text.trim().length === 0) {
    return undefined as T
  }

  try {
    return JSON.parse(text) as T
  } catch {
    return text as T
  }
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

function normalizeRace(value: unknown): 'P' | 'T' | 'Z' | 'PT' | 'PZ' | 'TZ' | 'R' {
  if (typeof value !== 'string') {
    return 'P'
  }

  const normalized = value.trim().toUpperCase()
  if (
    normalized === 'P' ||
    normalized === 'T' ||
    normalized === 'Z' ||
    normalized === 'PT' ||
    normalized === 'PZ' ||
    normalized === 'TZ' ||
    normalized === 'R'
  ) {
    return normalized
  }

  return 'P'
}

function normalizeTier(value: unknown): PlayerTierStatus | undefined {
  if (typeof value !== 'string' || value.trim().length === 0) {
    return undefined
  }

  const normalized = value.trim().toUpperCase()
  switch (normalized) {
    case 'S':
    case 'A+':
    case 'A':
    case 'A-':
    case 'B+':
    case 'B':
    case 'B-':
    case 'C+':
    case 'C':
    case 'C-':
      return normalized
    case 'UNASSIGNED':
    case 'PENDING':
    case 'TBD':
    case 'NONE':
      return 'UNASSIGNED'
    default:
      return undefined
  }
}

function normalizeMatchTeam(value: unknown): MatchTeamSide {
  if (typeof value !== 'string') {
    return 'UNKNOWN'
  }

  const normalized = value.trim().toUpperCase()
  if (normalized === 'HOME' || normalized === 'AWAY' || normalized === 'UNKNOWN') {
    return normalized
  }
  return 'UNKNOWN'
}

function normalizeWinnerTeam(value: unknown): TeamSide | null {
  if (typeof value !== 'string') {
    return null
  }

  const normalized = value.trim().toUpperCase()
  if (normalized === 'HOME' || normalized === 'AWAY') {
    return normalized
  }
  return null
}

function normalizeRecentMatchPlayer(value: unknown) {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const playerId = toNumber(source.playerId)
  const nickname = typeof source.nickname === 'string' ? source.nickname : null
  const mmr = toNumber(source.mmr)
  if (playerId === null || nickname === null) {
    return null
  }

  return {
    playerId,
    nickname,
    team: normalizeMatchTeam(source.team),
    mmr: mmr ?? undefined,
  }
}

function normalizeRecentMatchItem(value: unknown): RecentMatchItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const matchId = toNumber(source.matchId)
  const playedAt = typeof source.playedAt === 'string' ? source.playedAt : null
  const resultRecordedAt = typeof source.resultRecordedAt === 'string' ? source.resultRecordedAt : null
  const resultRecordedByNickname =
    typeof source.resultRecordedByNickname === 'string' ? source.resultRecordedByNickname : null
  const homeMmr = toNumber(source.homeMmr)
  const awayMmr = toNumber(source.awayMmr)
  const mmrDiff = toNumber(source.mmrDiff)
  const homeTeam = Array.isArray(source.homeTeam)
    ? source.homeTeam
        .map(normalizeRecentMatchPlayer)
        .filter((item): item is NonNullable<ReturnType<typeof normalizeRecentMatchPlayer>> => item !== null)
    : []
  const awayTeam = Array.isArray(source.awayTeam)
    ? source.awayTeam
        .map(normalizeRecentMatchPlayer)
        .filter((item): item is NonNullable<ReturnType<typeof normalizeRecentMatchPlayer>> => item !== null)
    : []

  if (matchId === null || playedAt === null) {
    return null
  }

  return {
    matchId,
    playedAt,
    status: typeof source.status === 'string' ? source.status : null,
    winningTeam: normalizeWinnerTeam(source.winningTeam),
    resultRecordedAt,
    resultRecordedByNickname,
    homeTeam,
    awayTeam,
    homeMmr: homeMmr ?? undefined,
    awayMmr: awayMmr ?? undefined,
    mmrDiff: mmrDiff ?? undefined,
  }
}

function normalizePlayerRosterItem(value: unknown): PlayerRosterItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const id = toNumber(source.id)
  const nickname =
    typeof source.nickname === 'string'
      ? source.nickname
      : typeof source.name === 'string'
        ? source.name
        : null
  const currentMmr = toNumber(source.currentMmr ?? source.mmr)
  const baseMmr = toNumber(source.baseMmr)
  const baseTier = normalizeTier(source.baseTier)
  const wins = toNumber(source.wins) ?? 0
  const losses = toNumber(source.losses) ?? 0
  const games = toNumber(source.games) ?? wins + losses

  if (id === null || nickname === null) {
    return null
  }

  return {
    id,
    nickname,
    race: normalizeRace(source.race),
    tier: normalizeTier(source.tier) ?? 'UNASSIGNED',
    baseMmr: baseMmr ?? undefined,
    baseTier: baseTier ?? undefined,
    currentMmr: currentMmr ?? undefined,
    wins,
    losses,
    games,
  }
}

export const apiClient = {
  getHealth: () => apiRequest<HealthResponse>('/api/health'),
  balanceMatch: (payload: BalanceRequest) =>
    apiRequest<BalanceResponse>('/api/matches/balance', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { includeUserEmail: true }),
  balanceMatchMulti: (payload: MultiBalanceRequest) =>
    apiRequest<MultiBalanceResponse>('/api/matches/balance/multi', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { includeUserEmail: true }),
  submitMatchResult: (matchId: number, payload: MatchResultRequest) =>
    apiRequest<MatchResultResponse>(`/api/matches/${matchId}/result`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { requireUserEmail: true, includeUserEmail: true, includeUserNickname: true }),
  updateMatchResult: (matchId: number, payload: MatchResultRequest) =>
    apiRequest<MatchResultResponse>(`/api/matches/${matchId}/result`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }, { adminOnly: true }),
  deleteMatch: (matchId: number) =>
    apiRequest<void>(`/api/matches/${matchId}`, {
      method: 'DELETE',
    }, { adminOnly: true }),
  createGroupMatch: (
    groupId: number,
    payload: { homePlayerIds: number[]; awayPlayerIds: number[] }
  ) =>
    apiRequest<CreateGroupMatchResponse>(
      `/api/groups/${groupId}/matches`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  importGroupPlayers: (groupId: number, payload: unknown) =>
    apiRequest<unknown>(
      `/api/groups/${groupId}/players/import`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true, timeoutMs: IMPORT_API_REQUEST_TIMEOUT_MS }
    ),
  updateGroupPlayer: (
    groupId: number,
    playerId: number,
    payload: { nickname?: string; race?: string }
  ) =>
    apiRequest<void>(
      `/api/groups/${groupId}/players/${playerId}`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  updateGroupPlayerMmr: (
    groupId: number,
    playerId: number,
    payload: GroupPlayerMmrUpdateRequest
  ) =>
    apiRequest<void>(
      `/api/groups/${groupId}/players/${playerId}/mmr`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  deleteGroupPlayer: (groupId: number, playerId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/players/${playerId}`,
      {
        method: 'DELETE',
      },
      { adminOnly: true }
    ),
  importMatches: (payload: unknown) =>
    apiRequest<unknown>(
      '/api/matches/import',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true, timeoutMs: IMPORT_API_REQUEST_TIMEOUT_MS }
    ),
  getGroupPlayers: async (groupId: number): Promise<PlayerRosterItem[]> => {
    const payload = await apiRequest<unknown>(`/api/groups/${groupId}/players`, undefined, {
      includeUserEmail: true,
    })
    if (!Array.isArray(payload)) {
      throw new Error('Invalid players response format')
    }

    return payload
      .map(normalizePlayerRosterItem)
      .filter((item): item is PlayerRosterItem => item !== null)
  },
  getGroupDashboard: (groupId: number) =>
    apiRequest<GroupDashboardResponse>(`/api/groups/${groupId}/dashboard`, undefined, {
      includeUserEmail: true,
      includeUserNickname: true,
    }),
  getRanking: (groupId: number) =>
    apiRequest<RankingResponse>(`/api/groups/${groupId}/ranking`, undefined, {
      includeUserEmail: true,
    }),
  getRecentMatches: async (groupId: number, limit = 10): Promise<RecentMatchItem[]> => {
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/matches/recent?limit=${limit}`,
      undefined,
      { includeUserEmail: true }
    )
    if (!Array.isArray(payload)) {
      throw new Error('Invalid recent matches response format')
    }

    return payload
      .map(normalizeRecentMatchItem)
      .filter((item): item is RecentMatchItem => item !== null)
  },
  createCaptainDraft: (groupId: number, payload: CaptainDraftCreateRequest) =>
    apiRequest<CaptainDraftResponse>(`/api/groups/${groupId}/captain-drafts`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { includeUserEmail: true }),
  getLatestCaptainDraft: (groupId: number) =>
    apiRequest<CaptainDraftResponse>(`/api/groups/${groupId}/captain-drafts/latest`, undefined, {
      includeUserEmail: true,
    }),
  getCaptainDraft: (groupId: number, draftId: number) =>
    apiRequest<CaptainDraftResponse>(`/api/groups/${groupId}/captain-drafts/${draftId}`, undefined, {
      includeUserEmail: true,
    }),
  pickCaptainDraftPlayer: (
    groupId: number,
    draftId: number,
    payload: CaptainDraftPickRequest
  ) =>
    apiRequest<CaptainDraftResponse>(
      `/api/groups/${groupId}/captain-drafts/${draftId}/pick`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { includeUserEmail: true }
    ),
  updateCaptainDraftEntries: (
    groupId: number,
    draftId: number,
    payload: CaptainDraftEntriesUpdateRequest
  ) =>
    apiRequest<CaptainDraftResponse>(
      `/api/groups/${groupId}/captain-drafts/${draftId}/entries`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      { includeUserEmail: true }
    ),
  getMyAccess: (identity?: { email: string; nickname?: string; accessToken?: string }) =>
    apiRequest<AccessMeResponse>('/api/access/me', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
      userEmail: identity?.email,
      userNickname: identity?.nickname,
      accessToken: identity?.accessToken,
      timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
      baseUrlOverride: ACCESS_API_BASE_URL,
    }),
  getAdminEmailList: () =>
    apiRequest<AccessAdminListResponse>('/api/access/admins', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
      timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
      baseUrlOverride: ACCESS_API_BASE_URL,
    }),
  addAdminEmail: (email: string, nickname: string) =>
    apiRequest<AccessAdminListResponse>(
      '/api/access/admins',
      {
        method: 'POST',
        body: JSON.stringify({ email, nickname }),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  removeAdminEmail: (email: string) =>
    apiRequest<AccessAdminListResponse>(
      `/api/access/admins/${encodeURIComponent(email)}`,
      {
        method: 'DELETE',
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  getAllowedEmailList: () =>
    apiRequest<AccessAllowedEmailListResponse>('/api/access/allowed-users', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
      timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
      baseUrlOverride: ACCESS_API_BASE_URL,
    }),
  addAllowedEmail: (email: string, nickname: string) =>
    apiRequest<AccessAllowedEmailListResponse>(
      '/api/access/allowed-users',
      {
        method: 'POST',
        body: JSON.stringify({ email, nickname }),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  removeAllowedEmail: (email: string) =>
    apiRequest<AccessAllowedEmailListResponse>(
      `/api/access/allowed-users/${encodeURIComponent(email)}`,
      {
        method: 'DELETE',
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  updateMyPreferredRace: (race: PlayerRace) =>
    apiRequest<AccessMeResponse>(
      '/api/access/me/race',
      {
        method: 'PUT',
        body: JSON.stringify({ race }),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
}

export function isApiForbiddenError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 403
}

export function isApiNotFoundError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 404
}

export function isApiUnauthorizedError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 401
}

export function isApiConflictError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 409
}

export function isApiTimeoutError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 408
}
