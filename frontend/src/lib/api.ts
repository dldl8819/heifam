import type {
  AccessAdminListResponse,
  AccessAllowedEmailListResponse,
  AccessMeResponse,
  AccessResultEditorListResponse,
  BalanceRequest,
  BalanceResponse,
  CreateGroupMatchResponse,
  CaptainDraftCreateRequest,
  CaptainDraftEntriesUpdateRequest,
  CaptainDraftPickRequest,
  CaptainDraftResponse,
  GroupDashboardResponse,
  GroupDormantPlayerItem,
  GroupPlayerLastParticipationResponse,
  GroupPlayerRaceStatsItem,
  GroupPlayerMmrUpdateRequest,
  GroupPlayerUpdateRequest,
  GroupPlayerTierBoardItem,
  GroupPlayerTeammateStats,
  HealthResponse,
  LedgerCategoriesResponse,
  LedgerDashboardResponse,
  LedgerExpenseEntry,
  LedgerExpenseEntryCreateRequest,
  LedgerExpenseEntryUpdateRequest,
  LedgerImportResponse,
  LedgerIncomeEntry,
  LedgerIncomeEntryCreateRequest,
  LedgerIncomeEntryUpdateRequest,
  LedgerServerCost,
  LedgerServerCostRequest,
  MatchHistoryFilters,
  MatchHistoryPage,
  MatchTeamSide,
  MultiBalanceRequest,
  MultiBalanceResponse,
  MatchResultRequest,
  MatchResultResponse,
  MatchResultUpdateRequest,
  ManualMatchCreateRequest,
  BoardKind,
  BoardPostDetail,
  BoardPostList,
  BoardSearchResult,
  NicknameRequestList,
  PrizeDrawList,
  PrizeDrawSaveRequest,
  NoticeCreateRequest,
  NoticeDetail,
  NoticeImageUpload,
  NoticeItem,
  NoticeList,
  NoticeTitle,
  NoticeUpdateRequest,
  OperationAuditLogFilters,
  OperationAuditLogItem,
  OperationAuditLogPage,
  PlayerRosterItem,
  PlayerLifecycleStatus,
  PlayerRace,
  PlayerTierStatus,
  PointMonthlyHistory,
  PointPolicyResponse,
  PointRankingResponse,
  PointSummaryResponse,
  LatestTeamTournamentResponse,
  BalanceSeriesLineup,
  BalanceSeriesList,
  NotificationList,
  PushConfig,
  TeamTournament,
  PredictionBoard,
  PredictionMatch,
  MatchConfirmationList,
  TeamScoreBoard,
  PrizeEvent,
  PrizeEventCreateRequest,
  PrizeEventConfirmRequest,
  RatingRecalculationRequest,
  RatingRecalculationResponse,
  RecentMatchItem,
  RankingResponse,
  TeamSide,
} from '@/types/api'
import { normalizeAssignedRace } from '@/lib/participant-races'
import { supabase } from '@/lib/supabase'
import {
  PROXY_MUTATION_CLIENT_TIMEOUT_MS,
  resolveDefaultApiRequestTimeoutMs,
} from '@/lib/proxy-timeout'

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
const ACCESS_API_REQUEST_TIMEOUT_MS = process.env.NODE_ENV === 'production' ? PROXY_MUTATION_CLIENT_TIMEOUT_MS : 15000
const MULTI_BALANCE_API_REQUEST_TIMEOUT_MS = PROXY_MUTATION_CLIENT_TIMEOUT_MS
const IMPORT_API_REQUEST_TIMEOUT_MS = 120000
const RATING_RECALCULATION_API_REQUEST_TIMEOUT_MS = 300000
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
let sessionIdentityCacheGeneration = 0

export function clearSessionIdentityCache(): void {
  sessionIdentityCacheGeneration += 1
  cachedSessionIdentity = null
  sessionIdentityPromise = null
}

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

export type ApiRequestOptions = {
  adminOnly?: boolean
  requireUserEmail?: boolean
  includeUserEmail?: boolean
  includeUserNickname?: boolean
  userEmail?: string
  userNickname?: string
  accessToken?: string
  timeoutMs?: number
  baseUrlOverride?: string
  // For a body that is not JSON, such as an image file.
  contentType?: string
  // The answer is a file: it is handed back as a Blob, not parsed.
  responseType?: 'blob'
}

export class ApiRequestError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function extractApiErrorMessage(status: number, rawText: string): string {
  const text = rawText.trim()
  if (text.length === 0) {
    return `API request failed (${status})`
  }

  try {
    const parsed = JSON.parse(text) as Record<string, unknown>
    const candidates = [parsed.message, parsed.reason, parsed.error]
    for (const candidate of candidates) {
      if (typeof candidate === 'string' && candidate.trim().length > 0) {
        return candidate.trim()
      }
    }
  } catch {
    return text
  }

  return text
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

  const cacheGeneration = sessionIdentityCacheGeneration
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
      if (cacheGeneration === sessionIdentityCacheGeneration) {
        cachedSessionIdentity = { ...identity, resolvedAt: Date.now() }
      }
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
  headers.set('Content-Type', options?.contentType ?? 'application/json')

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

function appendOptionalSearchParam(params: URLSearchParams, key: string, value: string | undefined): void {
  const normalized = value?.trim() ?? ''
  if (normalized.length > 0) {
    params.set(key, normalized)
  }
}

export async function apiRequest<T>(
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

  const requestMethod = (init?.method ?? 'GET').trim().toUpperCase()
  const baseUrlOverride = options?.baseUrlOverride?.trim() ?? ''
  const effectiveBaseUrl = baseUrlOverride.length > 0 ? baseUrlOverride : API_BASE_URL
  const usesProductionProxy = process.env.NODE_ENV === 'production'
    && effectiveBaseUrl.replace(/\/+$/, '') === DEFAULT_PROD_API_BASE_URL
  const defaultTimeoutMs = resolveDefaultApiRequestTimeoutMs({
    method: requestMethod,
    usesProductionProxy,
  })
  const controller = new AbortController()
  const timeoutMs = Number.isFinite(options?.timeoutMs) && (options?.timeoutMs ?? 0) > 0
    ? (options?.timeoutMs as number)
    : defaultTimeoutMs
  let requestTimedOut = false
  let callerAborted = init?.signal?.aborted ?? false
  const handleCallerAbort = (): void => {
    callerAborted = true
    controller.abort()
  }
  const shouldRemoveCallerAbortListener = Boolean(init?.signal && !callerAborted)

  if (callerAborted) {
    controller.abort()
  } else if (init?.signal) {
    init.signal.addEventListener('abort', handleCallerAbort, { once: true })
    if (init.signal.aborted) {
      handleCallerAbort()
    }
  }

  const timeoutId = setTimeout(() => {
    if (!callerAborted) {
      requestTimedOut = true
      controller.abort()
    }
  }, timeoutMs)

  try {
    const response = await fetch(createUrl(path, options?.baseUrlOverride), {
      ...init,
      headers: buildHeaders(init, options, userEmail, userNickname, accessToken),
      signal: controller.signal,
    })

    if (!response.ok) {
      const message = (await response.text()).trim()
      throw new ApiRequestError(
        response.status,
        extractApiErrorMessage(response.status, message)
      )
    }

    if (response.status === 204) {
      return undefined as T
    }

    if (options?.responseType === 'blob') {
      return (await response.blob()) as T
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
  } catch (error) {
    if (requestTimedOut && controller.signal.aborted) {
      throw new ApiRequestError(
        408,
        `API request timed out (${timeoutMs}ms)`
      )
    }

    throw error
  } finally {
    clearTimeout(timeoutId)
    if (shouldRemoveCallerAbortListener && init?.signal) {
      init.signal.removeEventListener('abort', handleCallerAbort)
    }
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

function normalizeRace(value: unknown): 'P' | 'T' | 'Z' | 'PT' | 'PZ' | 'TZ' | 'PTZ' {
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
    normalized === 'PTZ'
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
    case 'S+':
    case 'S':
    case 'S-':
    case 'A+':
    case 'A':
    case 'A-':
    case 'B+':
    case 'B':
    case 'B-':
    case 'C+':
    case 'C':
    case 'C-':
    case 'D+':
    case 'D':
    case 'D-':
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
  if (nickname === null) {
    return null
  }

  return {
    playerId,
    nickname,
    team: normalizeMatchTeam(source.team),
    mmr: mmr ?? undefined,
    assignedRace: normalizeAssignedRace(source.assignedRace),
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
  const homeRaceComposition =
    typeof source.homeRaceComposition === 'string' ? source.homeRaceComposition : null
  const awayRaceComposition =
    typeof source.awayRaceComposition === 'string' ? source.awayRaceComposition : null
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
    homeRaceComposition,
    awayRaceComposition,
    homeTeam,
    awayTeam,
    homeMmr: homeMmr ?? undefined,
    awayMmr: awayMmr ?? undefined,
    mmrDiff: mmrDiff ?? undefined,
    canEditRaceComposition: source.canEditRaceComposition === true,
    racesRecorded: source.racesRecorded === true,
  }
}

export function normalizePlayerRosterItem(value: unknown, index = 0): PlayerRosterItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const responseId = toNumber(source.id)
  const nickname =
    typeof source.nickname === 'string'
      ? source.nickname
      : typeof source.name === 'string'
        ? source.name
        : null
  const currentMmr = toNumber(source.currentMmr ?? source.mmr)
  const baseMmr = toNumber(source.baseMmr)
  const baseTier = normalizeTier(source.baseTier)
  const lastTierSnapshotMmr = toNumber(source.lastTierSnapshotMmr)
  const lastTierSnapshotTier = normalizeTier(source.lastTierSnapshotTier)
  const liveTier = normalizeTier(source.liveTier)
  const wins = toNumber(source.wins) ?? 0
  const losses = toNumber(source.losses) ?? 0
  const games = toNumber(source.games) ?? wins + losses
  const hasExplicitActiveStatus = typeof source.active === 'boolean'
  const active = source.active === true
  const rawLifecycleStatus =
    typeof source.lifecycleStatus === 'string' ? source.lifecycleStatus.toUpperCase() : null
  const lifecycleStatus = (
    ['ACTIVE', 'INACTIVE', 'WITHDRAWN', 'ANONYMIZED'] as PlayerLifecycleStatus[]
  ).includes(rawLifecycleStatus as PlayerLifecycleStatus)
    ? (rawLifecycleStatus as PlayerLifecycleStatus)
    : null
  const rawIdentityRetainedUntil =
    typeof source.identityRetainedUntil === 'string' ? source.identityRetainedUntil : null
  const rawInactiveAt = typeof source.chatLeftAt === 'string' ? source.chatLeftAt : null
  const rawInactiveReason =
    typeof source.chatLeftReason === 'string' ? source.chatLeftReason.trim() : null
  const identityRetainedUntilMillis =
    rawIdentityRetainedUntil === null ? Number.NaN : Date.parse(rawIdentityRetainedUntil)
  const inactiveAtMillis = rawInactiveAt === null ? Number.NaN : Date.parse(rawInactiveAt)
  const maximumRetainedUntilMillis =
    rawInactiveAt === null ? Number.NaN : addOneCalendarYear(rawInactiveAt)
  const now = Date.now()
  const allowedInactiveReason =
    rawInactiveReason !== null &&
    ['장기 미참여', '본인 요청', '운영 정책', '기타'].includes(rawInactiveReason)
  const retainedNickname =
    nickname !== null && nickname.trim().length > 0 && nickname !== '탈퇴한 회원'
  const retainedInactiveIdentity =
    hasExplicitActiveStatus &&
    active === false &&
    lifecycleStatus === 'INACTIVE' &&
    retainedNickname &&
    Number.isFinite(inactiveAtMillis) &&
    inactiveAtMillis <= now &&
    allowedInactiveReason &&
    Number.isFinite(identityRetainedUntilMillis) &&
    identityRetainedUntilMillis > now &&
    Number.isFinite(maximumRetainedUntilMillis) &&
    identityRetainedUntilMillis <= maximumRetainedUntilMillis
  const unsupportedLifecycleStatus = rawLifecycleStatus !== null && lifecycleStatus === null
  const inconsistentActiveLifecycle =
    active && lifecycleStatus !== null && lifecycleStatus !== 'ACTIVE'
  const identityHidden =
    !hasExplicitActiveStatus ||
    lifecycleStatus === 'ANONYMIZED' ||
    lifecycleStatus === 'WITHDRAWN' ||
    unsupportedLifecycleStatus ||
    inconsistentActiveLifecycle ||
    (active === false && !retainedInactiveIdentity)
  const normalizedLifecycleStatus: PlayerLifecycleStatus =
    lifecycleStatus ?? (active ? 'ACTIVE' : identityHidden ? 'ANONYMIZED' : 'INACTIVE')
  const retainedInactive = retainedInactiveIdentity && !identityHidden
  const id = identityHidden
    ? -(index + 1)
    : responseId ?? (active ? null : -(index + 1))

  if (id === null || nickname === null) {
    return null
  }

  return {
    id,
    nickname: identityHidden ? '탈퇴한 회원' : nickname,
    race: identityHidden ? 'P' : normalizeRace(source.race),
    tier: identityHidden || retainedInactive ? 'UNASSIGNED' : normalizeTier(source.tier) ?? 'UNASSIGNED',
    baseMmr: identityHidden || retainedInactive ? undefined : baseMmr ?? undefined,
    baseTier: identityHidden || retainedInactive ? undefined : baseTier ?? undefined,
    currentMmr: identityHidden || retainedInactive ? undefined : currentMmr ?? undefined,
    lastTierSnapshotAt:
      !identityHidden && !retainedInactive && typeof source.lastTierSnapshotAt === 'string'
        ? source.lastTierSnapshotAt
        : undefined,
    lastTierSnapshotMmr:
      identityHidden || retainedInactive ? undefined : lastTierSnapshotMmr ?? undefined,
    lastTierSnapshotTier:
      identityHidden || retainedInactive ? undefined : lastTierSnapshotTier ?? undefined,
    liveTier: identityHidden || retainedInactive ? undefined : liveTier ?? undefined,
    wins: identityHidden ? 0 : wins,
    losses: identityHidden ? 0 : losses,
    games: identityHidden ? 0 : games,
    active,
    identityHidden,
    lifecycleStatus: normalizedLifecycleStatus,
    identityRetainedUntil:
      !identityHidden && rawIdentityRetainedUntil !== null ? rawIdentityRetainedUntil : undefined,
    chatLeftAt: !identityHidden && rawInactiveAt !== null ? rawInactiveAt : undefined,
    chatLeftReason: !identityHidden && allowedInactiveReason ? rawInactiveReason ?? undefined : undefined,
    chatRejoinedAt:
      !identityHidden && !retainedInactive && typeof source.chatRejoinedAt === 'string'
        ? source.chatRejoinedAt
        : undefined,
    isOwnPlayer: !identityHidden && source.isOwnPlayer === true,
    dormantAt: !identityHidden && active && typeof source.dormantAt === 'string' ? source.dormantAt : undefined,
  }
}

function addOneCalendarYear(value: string): number {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})(T.*)$/)
  if (match === null) {
    return Number.NaN
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (!Number.isInteger(year) || month < 1 || month > 12 || day < 1) {
    return Number.NaN
  }
  const targetYear = year + 1
  const lastDay = new Date(Date.UTC(targetYear, month, 0)).getUTCDate()
  const targetDay = String(Math.min(day, lastDay)).padStart(2, '0')
  return Date.parse(`${String(targetYear).padStart(4, '0')}-${match[2]}-${targetDay}${match[4]}`)
}

function normalizeGroupDormantPlayerItem(value: unknown): GroupDormantPlayerItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const playerId = toNumber(source.playerId)
  const nickname = typeof source.nickname === 'string' ? source.nickname : null
  if (playerId === null || nickname === null) {
    return null
  }

  return { playerId, nickname }
}

function normalizeGroupPlayerLastParticipationResponse(
  value: unknown
): GroupPlayerLastParticipationResponse | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  if (source.lastPlayedAt === null) {
    return { lastPlayedAt: null }
  }
  if (
    typeof source.lastPlayedAt !== 'string' ||
    Number.isNaN(new Date(source.lastPlayedAt).getTime())
  ) {
    return null
  }

  return { lastPlayedAt: source.lastPlayedAt }
}

function normalizePlayerTierBoardItem(value: unknown): GroupPlayerTierBoardItem | null {
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
  const tier = normalizeTier(source.tier) ?? 'UNASSIGNED'
  const liveTier = normalizeTier(source.liveTier) ?? tier

  if (id === null || nickname === null) {
    return null
  }

  return {
    id,
    nickname,
    race: normalizeRace(source.race),
    tier,
    liveTier,
    active: typeof source.active === 'boolean' ? source.active : true,
  }
}

function normalizeOperationAuditLogItem(value: unknown): OperationAuditLogItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const id = toNumber(source.id)
  const targetId = toNumber(source.targetId)
  const groupId = toNumber(source.groupId)
  const action = typeof source.action === 'string' ? source.action : null
  const targetType = typeof source.targetType === 'string' ? source.targetType : null
  const summary = typeof source.summary === 'string' ? source.summary : null
  const createdAt = typeof source.createdAt === 'string' ? source.createdAt : null

  if (id === null || action === null || targetType === null || summary === null || createdAt === null) {
    return null
  }

  return {
    id,
    action,
    actorNickname: typeof source.actorNickname === 'string' ? source.actorNickname : undefined,
    targetType,
    targetId: targetId ?? undefined,
    targetLabel: typeof source.targetLabel === 'string' ? source.targetLabel : undefined,
    groupId: groupId ?? undefined,
    summary,
    details: typeof source.details === 'string' ? source.details : undefined,
    createdAt,
  }
}

function normalizeOperationAuditLogPage(value: unknown): OperationAuditLogPage {
  if (Array.isArray(value)) {
    const items = value
      .map(normalizeOperationAuditLogItem)
      .filter((item): item is OperationAuditLogItem => item !== null)
    return {
      items,
      page: 0,
      size: items.length,
      totalElements: items.length,
      totalPages: items.length > 0 ? 1 : 0,
      first: true,
      last: true,
    }
  }

  if (value === null || typeof value !== 'object') {
    throw new Error('Invalid audit logs response format')
  }

  const source = value as Record<string, unknown>
  const rawItems = Array.isArray(source.items) ? source.items : []
  const items = rawItems
    .map(normalizeOperationAuditLogItem)
    .filter((item): item is OperationAuditLogItem => item !== null)
  const page = Math.max(0, Math.floor(toNumber(source.page) ?? 0))
  const fallbackSize = items.length > 0 ? items.length : 1
  const size = Math.max(1, Math.floor(toNumber(source.size) ?? fallbackSize))
  const totalElements = Math.max(0, Math.floor(toNumber(source.totalElements) ?? items.length))
  const totalPages = Math.max(0, Math.floor(toNumber(source.totalPages) ?? (totalElements === 0 ? 0 : Math.ceil(totalElements / size))))

  return {
    items,
    page,
    size,
    totalElements,
    totalPages,
    first: typeof source.first === 'boolean' ? source.first : page <= 0,
    last: typeof source.last === 'boolean' ? source.last : totalPages === 0 || page >= totalPages - 1,
  }
}

function normalizeMatchHistoryPage(value: unknown): MatchHistoryPage {
  if (value === null || typeof value !== 'object') {
    throw new Error('Invalid match history response format')
  }

  const source = value as Record<string, unknown>
  const rawItems = Array.isArray(source.items) ? source.items : []
  const items = rawItems
    .map(normalizeRecentMatchItem)
    .filter((item): item is RecentMatchItem => item !== null)
  const page = Math.max(0, Math.floor(toNumber(source.page) ?? 0))
  const fallbackSize = items.length > 0 ? items.length : 1
  const size = Math.max(1, Math.floor(toNumber(source.size) ?? fallbackSize))
  const totalElements = Math.max(0, Math.floor(toNumber(source.totalElements) ?? items.length))
  const totalPages = Math.max(
    0,
    Math.floor(toNumber(source.totalPages) ?? (totalElements === 0 ? 0 : Math.ceil(totalElements / size))),
  )

  return {
    items,
    page,
    size,
    totalElements,
    totalPages,
    first: typeof source.first === 'boolean' ? source.first : page <= 0,
    last: typeof source.last === 'boolean' ? source.last : totalPages === 0 || page >= totalPages - 1,
  }
}

function normalizePlayerRaceStat(value: unknown) {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const wins = Math.max(0, Math.floor(toNumber(source.wins) ?? 0))
  const losses = Math.max(0, Math.floor(toNumber(source.losses) ?? 0))
  const games = Math.max(0, Math.floor(toNumber(source.games) ?? wins + losses))
  const winRate = toNumber(source.winRate)

  return {
    race: normalizeRace(source.race),
    wins,
    losses,
    games,
    winRate: winRate ?? (games > 0 ? Math.round((wins * 10000) / games) / 100 : 0),
  }
}

function normalizePlayerGameTypeStat(value: unknown) {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const gameType = typeof source.gameType === 'string' ? source.gameType.trim().toUpperCase() : ''
  const wins = Math.max(0, Math.floor(toNumber(source.wins) ?? 0))
  const losses = Math.max(0, Math.floor(toNumber(source.losses) ?? 0))
  const games = Math.max(0, Math.floor(toNumber(source.games) ?? wins + losses))
  const winRate = toNumber(source.winRate)

  if (gameType.length === 0) {
    return null
  }

  return {
    gameType,
    wins,
    losses,
    games,
    winRate: winRate ?? (games > 0 ? Math.round((wins * 10000) / games) / 100 : 0),
  }
}

function normalizePlayerRaceStatsItem(value: unknown): GroupPlayerRaceStatsItem | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const playerId = toNumber(source.playerId)
  const nickname = typeof source.nickname === 'string' ? source.nickname : null
  const wins = Math.max(0, Math.floor(toNumber(source.wins) ?? 0))
  const losses = Math.max(0, Math.floor(toNumber(source.losses) ?? 0))
  const games = Math.max(0, Math.floor(toNumber(source.games) ?? wins + losses))
  const winRate = toNumber(source.winRate)

  if (playerId === null || nickname === null) {
    return null
  }

  return {
    playerId,
    nickname,
    race: normalizeRace(source.race),
    wins,
    losses,
    games,
    winRate: winRate ?? (games > 0 ? Math.round((wins * 10000) / games) / 100 : 0),
    byRace: Array.isArray(source.byRace)
      ? source.byRace
          .map(normalizePlayerRaceStat)
          .filter((item): item is NonNullable<ReturnType<typeof normalizePlayerRaceStat>> => item !== null)
      : [],
    byGameType: Array.isArray(source.byGameType)
      ? source.byGameType
          .map(normalizePlayerGameTypeStat)
          .filter((item): item is NonNullable<ReturnType<typeof normalizePlayerGameTypeStat>> => item !== null)
      : [],
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
    }, { includeUserEmail: true, timeoutMs: MULTI_BALANCE_API_REQUEST_TIMEOUT_MS }),
  submitMatchResult: (matchId: number, payload: MatchResultRequest) =>
    apiRequest<MatchResultResponse>(`/api/matches/${matchId}/result`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { requireUserEmail: true, includeUserEmail: true, includeUserNickname: true }),
  createManualMatch: (payload: ManualMatchCreateRequest) =>
    apiRequest<MatchResultResponse>('/api/matches/manual', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, { requireUserEmail: true }),
  updateMatchResult: (matchId: number, payload: MatchResultUpdateRequest) =>
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
    payload: {
      homePlayerIds: number[]
      awayPlayerIds: number[]
      teamSize?: number
      raceComposition?: string
      // The result is entered right after: the match was never open for predictions, so none are announced.
      resultFollows?: boolean
    }
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
    payload: GroupPlayerUpdateRequest
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
  /** 휴면: sets a player aside (true) or wakes them (false). Admins only. */
  setGroupPlayerDormant: (groupId: number, playerId: number, dormant: boolean) =>
    apiRequest<void>(
      `/api/groups/${groupId}/players/${playerId}/dormant`,
      { method: dormant ? 'PUT' : 'DELETE' },
      { adminOnly: true }
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
  getGroupPlayers: async (
    groupId: number,
    options?: { includeInactive?: boolean; includeDormant?: boolean }
  ): Promise<PlayerRosterItem[]> => {
    const params = new URLSearchParams()
    if (options?.includeInactive) {
      params.set('includeInactive', 'true')
    }
    // Dormant players (휴면): the backend lists them for admins only.
    if (options?.includeDormant) {
      params.set('includeDormant', 'true')
    }
    const search = params.toString()
    const query = search.length > 0 ? `?${search}` : ''
    const payload = await apiRequest<unknown>(`/api/groups/${groupId}/players${query}`, undefined, {
      includeUserEmail: true,
    })
    if (!Array.isArray(payload)) {
      throw new Error('Invalid players response format')
    }

    return payload
      .map(normalizePlayerRosterItem)
      .filter((item): item is PlayerRosterItem => item !== null)
  },
  getGroupDormantPlayers: async (groupId: number): Promise<GroupDormantPlayerItem[]> => {
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/players/dormant`,
      undefined,
      { adminOnly: true }
    )
    if (!Array.isArray(payload)) {
      throw new Error('Invalid dormant players response format')
    }

    return payload
      .map(normalizeGroupDormantPlayerItem)
      .filter((item): item is GroupDormantPlayerItem => item !== null)
  },
  getGroupPlayerLastParticipation: async (
    groupId: number,
    playerId: number
  ): Promise<GroupPlayerLastParticipationResponse> => {
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/players/${playerId}/last-participation`,
      undefined,
      { adminOnly: true }
    )
    const item = normalizeGroupPlayerLastParticipationResponse(payload)
    if (item === null) {
      throw new Error('Invalid player last participation response format')
    }

    return item
  },
  getGroupPlayerTierBoard: async (groupId: number): Promise<GroupPlayerTierBoardItem[]> => {
    const payload = await apiRequest<unknown>(`/api/groups/${groupId}/players/tier-board`, undefined, {
      includeUserEmail: true,
    })
    if (!Array.isArray(payload)) {
      throw new Error('Invalid tier board response format')
    }

    return payload
      .map(normalizePlayerTierBoardItem)
      .filter((item): item is GroupPlayerTierBoardItem => item !== null)
  },
  getGroupPlayerRaceStatsForPlayer: async (
    groupId: number,
    playerId: number
  ): Promise<GroupPlayerRaceStatsItem> => {
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/players/${playerId}/race-stats`,
      undefined,
      {
        includeUserEmail: true,
      }
    )
    const item = normalizePlayerRaceStatsItem(payload)
    if (item === null) {
      throw new Error('Invalid player race stats response format')
    }

    return item
  },
  getGroupPlayerMonthlyRaceStatsForPlayer: async (
    groupId: number,
    playerId: number
  ): Promise<GroupPlayerRaceStatsItem> => {
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/players/${playerId}/race-stats/monthly`,
      undefined,
      {
        includeUserEmail: true,
      }
    )
    const item = normalizePlayerRaceStatsItem(payload)
    if (item === null) {
      throw new Error('Invalid player monthly race stats response format')
    }

    return item
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
  getRecentMatches: async (groupId: number, limit = 10, offset = 0): Promise<RecentMatchItem[]> => {
    const safeLimit = Math.max(1, Math.floor(Number.isFinite(limit) ? limit : 10))
    const safeOffset = Math.max(0, Math.floor(Number.isFinite(offset) ? offset : 0))
    const params = new URLSearchParams({ limit: String(safeLimit) })
    if (safeOffset > 0) {
      params.set('offset', String(safeOffset))
    }
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/matches/recent?${params.toString()}`,
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
  getMatchHistoryPage: async (
    groupId: number,
    options: ({ page?: number; size?: number } & MatchHistoryFilters) = {}
  ): Promise<MatchHistoryPage> => {
    const requestedPage = Number.isFinite(options.page) ? Math.floor(options.page ?? 0) : 0
    const requestedSize = Number.isFinite(options.size) ? Math.floor(options.size ?? 20) : 20
    const safePage = Math.max(0, requestedPage)
    const safeSize = Math.max(1, Math.min(200, requestedSize))
    const params = new URLSearchParams({
      page: String(safePage),
      size: String(safeSize),
    })
    appendOptionalSearchParam(params, 'fromDate', options.fromDate)
    appendOptionalSearchParam(params, 'toDate', options.toDate)
    const payload = await apiRequest<unknown>(
      `/api/groups/${groupId}/matches/history?${params.toString()}`,
      undefined,
      { requireUserEmail: true, includeUserEmail: true }
    )
    return normalizeMatchHistoryPage(payload)
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
  deleteMyAccount: (identity: { accessToken: string }) =>
    apiRequest<void>(
      '/api/access/me',
      {
        method: 'DELETE',
      },
      {
        accessToken: identity.accessToken,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
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
  updateAdminMmrAccess: (email: string, canViewMmr: boolean) =>
    apiRequest<AccessAdminListResponse>(
      `/api/access/admins/${encodeURIComponent(email)}/mmr-access`,
      {
        method: 'PUT',
        body: JSON.stringify({ canViewMmr }),
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
  updateAllowedEmailNickname: (email: string, nickname: string) =>
    apiRequest<AccessAllowedEmailListResponse>(
      `/api/access/allowed-users/${encodeURIComponent(email)}/nickname`,
      {
        method: 'PUT',
        body: JSON.stringify({ nickname }),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  getMatchResultEditorList: () =>
    apiRequest<AccessResultEditorListResponse>('/api/access/result-editors', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
      timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
      baseUrlOverride: ACCESS_API_BASE_URL,
    }),
  addMatchResultEditor: (email: string) =>
    apiRequest<AccessResultEditorListResponse>(
      '/api/access/result-editors',
      {
        method: 'POST',
        body: JSON.stringify({ email }),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: ACCESS_API_REQUEST_TIMEOUT_MS,
        baseUrlOverride: ACCESS_API_BASE_URL,
      }
    ),
  removeMatchResultEditor: (email: string) =>
    apiRequest<AccessResultEditorListResponse>(
      `/api/access/result-editors/${encodeURIComponent(email)}`,
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
  // The member boards: 'free' or 'anonymous'. Every call needs a signed-in member with access.
  getBoardPosts: (groupId: number, board: BoardKind, page: number) =>
    apiRequest<BoardPostList>(`/api/groups/${groupId}/boards/${board}/posts?page=${page}`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  // Opening a post counts the member once among those who have seen it.
  getBoardPost: (groupId: number, board: BoardKind, postId: number) =>
    apiRequest<BoardPostDetail>(`/api/groups/${groupId}/boards/${board}/posts/${postId}`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  // videoUrl: the YouTube link of a post on the video board.
  createBoardPost: (groupId: number, board: BoardKind, payload: { title: string; content: string; videoUrl?: string }) =>
    apiRequest<BoardPostDetail>(
      `/api/groups/${groupId}/boards/${board}/posts`,
      { method: 'POST', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  updateBoardPost: (
    groupId: number,
    board: BoardKind,
    postId: number,
    payload: { title: string; content: string; videoUrl?: string }
  ) =>
    apiRequest<BoardPostDetail>(
      `/api/groups/${groupId}/boards/${board}/posts/${postId}`,
      { method: 'PUT', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  deleteBoardPost: (groupId: number, board: BoardKind, postId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/boards/${board}/posts/${postId}`,
      { method: 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  addBoardComment: (groupId: number, board: BoardKind, postId: number, content: string) =>
    apiRequest<BoardPostDetail>(
      `/api/groups/${groupId}/boards/${board}/posts/${postId}/comments`,
      { method: 'POST', body: JSON.stringify({ content }) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  deleteBoardComment: (groupId: number, board: BoardKind, postId: number, commentId: number) =>
    apiRequest<BoardPostDetail>(
      `/api/groups/${groupId}/boards/${board}/posts/${postId}/comments/${commentId}`,
      { method: 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  setBoardPostLike: (groupId: number, board: BoardKind, postId: number, liked: boolean) =>
    apiRequest<BoardPostDetail>(
      `/api/groups/${groupId}/boards/${board}/posts/${postId}/like`,
      { method: liked ? 'PUT' : 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Posts with the word in their title or text, among the notices the member may read and the free board.
  searchBoards: (groupId: number, query: string, page: number) =>
    apiRequest<BoardSearchResult>(
      `/api/groups/${groupId}/boards/search?${new URLSearchParams({ q: query, page: String(page) }).toString()}`,
      undefined,
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // The records of prize draws; members read them. Saving and removing answer with the records as they now are.
  getPrizeDraws: (groupId: number) =>
    apiRequest<PrizeDrawList>(`/api/groups/${groupId}/prize-draws`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  // Admins only: the outcome of a draw that was run on the pinball page.
  savePrizeDraw: (groupId: number, payload: PrizeDrawSaveRequest) =>
    apiRequest<PrizeDrawList>(
      `/api/groups/${groupId}/prize-draws`,
      { method: 'POST', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Super admins only.
  deletePrizeDraw: (groupId: number, drawId: number) =>
    apiRequest<PrizeDrawList>(
      `/api/groups/${groupId}/prize-draws/${drawId}`,
      { method: 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Requests for a nickname change: a member's own, and for admins everyone's. Each change answers
  // with the lists as they now are.
  getNicknameRequests: (groupId: number) =>
    apiRequest<NicknameRequestList>(`/api/groups/${groupId}/nickname-requests`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  createNicknameRequest: (groupId: number, payload: { desiredNickname: string; reason?: string }) =>
    apiRequest<NicknameRequestList>(
      `/api/groups/${groupId}/nickname-requests`,
      { method: 'POST', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  cancelNicknameRequest: (groupId: number, requestId: number) =>
    apiRequest<NicknameRequestList>(
      `/api/groups/${groupId}/nickname-requests/${requestId}/cancel`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Admins only. It records the decision; the nickname itself is changed by hand.
  decideNicknameRequest: (
    groupId: number,
    requestId: number,
    payload: { status: 'APPROVED' | 'REJECTED'; note?: string }
  ) =>
    apiRequest<NicknameRequestList>(
      `/api/groups/${groupId}/nickname-requests/${requestId}/decision`,
      { method: 'PUT', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Open to visitors: titles and dates of the notices members may read.
  getNoticeTitles: (groupId: number) =>
    apiRequest<NoticeTitle[]>(`/api/groups/${groupId}/notice-titles`),
  getNotices: (groupId: number) =>
    apiRequest<NoticeList>(`/api/groups/${groupId}/notices`, undefined, {
      includeUserEmail: true,
    }),
  // Opening a notice marks it read for the member.
  getNotice: (groupId: number, noticeId: number) =>
    apiRequest<NoticeDetail>(`/api/groups/${groupId}/notices/${noticeId}`, undefined, {
      includeUserEmail: true,
    }),
  // With parentId the comment is a reply to that comment.
  addNoticeComment: (groupId: number, noticeId: number, content: string, parentId?: number) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/comments`,
      {
        method: 'POST',
        body: JSON.stringify(parentId === undefined ? { content } : { content, parentId }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  updateNoticeComment: (groupId: number, noticeId: number, commentId: number, content: string) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/comments/${commentId}`,
      {
        method: 'PUT',
        body: JSON.stringify({ content }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  setNoticeCommentLike: (groupId: number, noticeId: number, commentId: number, liked: boolean) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/comments/${commentId}/like`,
      { method: liked ? 'PUT' : 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  deleteNoticeComment: (groupId: number, noticeId: number, commentId: number) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/comments/${commentId}`,
      { method: 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  setNoticeLike: (groupId: number, noticeId: number, liked: boolean) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/like`,
      { method: liked ? 'PUT' : 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // An option casts or moves the member's vote; null takes it back.
  setNoticeVote: (groupId: number, noticeId: number, optionId: number | null) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/vote`,
      optionId === null ? { method: 'DELETE' } : { method: 'PUT', body: JSON.stringify({ optionId }) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // For admins, and for members when the vote allows additions.
  addNoticeVoteOption: (groupId: number, noticeId: number, label: string) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/vote/options`,
      { method: 'POST', body: JSON.stringify({ label }) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Admins only. The votes on the option go with it.
  removeNoticeVoteOption: (groupId: number, noticeId: number, optionId: number) =>
    apiRequest<NoticeDetail>(
      `/api/groups/${groupId}/notices/${noticeId}/vote/options/${optionId}`,
      { method: 'DELETE' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  createNotice: (groupId: number, payload: NoticeCreateRequest) =>
    apiRequest<NoticeItem>(
      `/api/groups/${groupId}/notices`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  updateNotice: (groupId: number, noticeId: number, payload: NoticeUpdateRequest) =>
    apiRequest<NoticeItem>(
      `/api/groups/${groupId}/notices/${noticeId}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  deleteNotice: (groupId: number, noticeId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/notices/${noticeId}`,
      { method: 'DELETE' },
      { adminOnly: true }
    ),
  // The body is the image file itself. The id that comes back is what the notice's text names it by.
  uploadNoticeImage: (groupId: number, image: Blob) =>
    apiRequest<NoticeImageUpload>(
      `/api/groups/${groupId}/notice-images`,
      { method: 'POST', body: image },
      { adminOnly: true, contentType: image.type }
    ),
  // Shown to whoever may open the notice the image is in; the token goes with the request, so an
  // <img> cannot load it by address and the page draws the Blob instead.
  getNoticeImage: (groupId: number, imageId: number, signal?: AbortSignal) =>
    apiRequest<Blob>(
      `/api/groups/${groupId}/notice-images/${imageId}`,
      { signal },
      { requireUserEmail: true, includeUserEmail: true, responseType: 'blob' }
    ),
  getLedgerIncome: (groupId: number) =>
    apiRequest<LedgerIncomeEntry[]>(`/api/groups/${groupId}/ledger/income`, undefined, {
      includeUserEmail: true,
    }),
  getLedgerIncomeCategories: (groupId: number) =>
    apiRequest<LedgerCategoriesResponse>(`/api/groups/${groupId}/ledger/income/categories`, undefined, {
      includeUserEmail: true,
    }),
  createLedgerIncomeEntry: (groupId: number, payload: LedgerIncomeEntryCreateRequest) =>
    apiRequest<LedgerIncomeEntry>(
      `/api/groups/${groupId}/ledger/income`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  updateLedgerIncomeEntry: (groupId: number, entryId: number, payload: LedgerIncomeEntryUpdateRequest) =>
    apiRequest<LedgerIncomeEntry>(
      `/api/groups/${groupId}/ledger/income/${entryId}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  deleteLedgerIncomeEntry: (groupId: number, entryId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/ledger/income/${entryId}`,
      { method: 'DELETE' },
      { adminOnly: true }
    ),
  importLedgerIncomeEntries: (groupId: number, csvContent: string) =>
    apiRequest<LedgerImportResponse>(
      `/api/groups/${groupId}/ledger/income/import`,
      {
        method: 'POST',
        body: JSON.stringify({ csvContent }),
      },
      { adminOnly: true }
    ),
  getLedgerExpense: (groupId: number, expenseType?: LedgerExpenseEntry['expenseType']) =>
    apiRequest<LedgerExpenseEntry[]>(
      `/api/groups/${groupId}/ledger/expense${expenseType ? `?expense_type=${expenseType}` : ''}`,
      undefined,
      { includeUserEmail: true }
    ),
  getLedgerExpenseCategories: (groupId: number, expenseType: LedgerExpenseEntry['expenseType']) =>
    apiRequest<LedgerCategoriesResponse>(
      `/api/groups/${groupId}/ledger/expense/categories?expense_type=${expenseType}`,
      undefined,
      { includeUserEmail: true }
    ),
  createLedgerExpenseEntry: (groupId: number, payload: LedgerExpenseEntryCreateRequest) =>
    apiRequest<LedgerExpenseEntry>(
      `/api/groups/${groupId}/ledger/expense`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  updateLedgerExpenseEntry: (groupId: number, entryId: number, payload: LedgerExpenseEntryUpdateRequest) =>
    apiRequest<LedgerExpenseEntry>(
      `/api/groups/${groupId}/ledger/expense/${entryId}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  deleteLedgerExpenseEntry: (groupId: number, entryId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/ledger/expense/${entryId}`,
      { method: 'DELETE' },
      { adminOnly: true }
    ),
  importLedgerExpenseEntries: (groupId: number, csvContent: string, expenseType: LedgerExpenseEntry['expenseType']) =>
    apiRequest<LedgerImportResponse>(
      `/api/groups/${groupId}/ledger/expense/import`,
      {
        method: 'POST',
        body: JSON.stringify({ csvContent, expenseType }),
      },
      { adminOnly: true }
    ),
  getGroupPlayerTeammateStats: (groupId: number, playerId: number) =>
    apiRequest<GroupPlayerTeammateStats>(
      `/api/groups/${groupId}/players/${playerId}/teammate-stats`,
      undefined,
      { includeUserEmail: true }
    ),
  getLedgerServerCosts: (groupId: number) =>
    apiRequest<LedgerServerCost[]>(
      `/api/groups/${groupId}/ledger/server-costs`,
      undefined,
      { includeUserEmail: true }
    ),
  createLedgerServerCost: (groupId: number, payload: LedgerServerCostRequest) =>
    apiRequest<LedgerServerCost>(
      `/api/groups/${groupId}/ledger/server-costs`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  updateLedgerServerCost: (groupId: number, costId: number, payload: LedgerServerCostRequest) =>
    apiRequest<LedgerServerCost>(
      `/api/groups/${groupId}/ledger/server-costs/${costId}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      { adminOnly: true }
    ),
  deleteLedgerServerCost: (groupId: number, costId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/ledger/server-costs/${costId}`,
      { method: 'DELETE' },
      { adminOnly: true }
    ),
  getLedgerDashboard: (groupId: number) =>
    apiRequest<LedgerDashboardResponse>(
      `/api/groups/${groupId}/ledger/dashboard`,
      undefined,
      { includeUserEmail: true }
    ),
  getMyPoints: () =>
    apiRequest<PointSummaryResponse>('/api/points/me', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  getPointPolicy: () =>
    apiRequest<PointPolicyResponse>('/api/points/policy', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  getPointRankingHistory: (accountId: number, month?: string) => {
    const params = new URLSearchParams()
    appendOptionalSearchParam(params, 'month', month)
    const query = params.toString()
    return apiRequest<PointMonthlyHistory>(`/api/points/ranking/${accountId}${query ? `?${query}` : ''}`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    })
  },
  getPointRanking: (month?: string) => {
    const params = new URLSearchParams()
    appendOptionalSearchParam(params, 'month', month)
    const query = params.toString()
    return apiRequest<PointRankingResponse>(`/api/points/ranking${query ? `?${query}` : ''}`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    })
  },
  getPrizeEvents: (groupId: number) =>
    apiRequest<{ events: PrizeEvent[] }>(`/api/groups/${groupId}/prize-events`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  createPrizeEvent: (groupId: number, payload: PrizeEventCreateRequest) =>
    apiRequest<PrizeEvent>(
      `/api/groups/${groupId}/prize-events`,
      { method: 'POST', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  confirmPrizeEvent: (groupId: number, eventId: number, payload: PrizeEventConfirmRequest) =>
    apiRequest<PrizeEvent>(
      `/api/groups/${groupId}/prize-events/${eventId}/confirm`,
      { method: 'POST', body: JSON.stringify(payload) },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  cancelPrizeEvent: (groupId: number, eventId: number) =>
    apiRequest<PrizeEvent>(
      `/api/groups/${groupId}/prize-events/${eventId}/cancel`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getTeamScores: (groupId: number) =>
    apiRequest<TeamScoreBoard>(`/api/groups/${groupId}/team-scores`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  getPredictionBoard: (groupId: number) =>
    apiRequest<PredictionBoard>(`/api/groups/${groupId}/predictions`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  submitPrediction: (groupId: number, matchId: number, team: TeamSide) =>
    apiRequest<PredictionMatch>(
      `/api/groups/${groupId}/predictions/${matchId}`,
      {
        method: 'PUT',
        body: JSON.stringify({ team }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  closePredictions: (groupId: number, matchId: number) =>
    apiRequest<void>(
      `/api/groups/${groupId}/predictions/${matchId}/close`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  // Calls off a match that was set up but not played; one with a result answers 409.
  cancelMatch: (matchId: number) =>
    apiRequest<void>(
      `/api/matches/${matchId}/cancel`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getMatchConfirmations: (groupId: number) =>
    apiRequest<MatchConfirmationList>(`/api/groups/${groupId}/match-confirmations`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  confirmMatchResult: (groupId: number, matchId: number) =>
    apiRequest<MatchConfirmationList>(
      `/api/groups/${groupId}/match-confirmations/${matchId}`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getLatestTeamTournament: (groupId: number) =>
    apiRequest<LatestTeamTournamentResponse>(`/api/groups/${groupId}/tournaments/latest`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  createTeamTournament: (groupId: number, playerIds: number[]) =>
    apiRequest<TeamTournament>(
      `/api/groups/${groupId}/tournaments`,
      {
        method: 'POST',
        body: JSON.stringify({ playerIds }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  cancelTeamTournament: (groupId: number, tournamentId: number) =>
    apiRequest<TeamTournament>(
      `/api/groups/${groupId}/tournaments/${tournamentId}/cancel`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getNotifications: (groupId: number) =>
    apiRequest<NotificationList>(`/api/groups/${groupId}/notifications`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  markNotificationsRead: (groupId: number) =>
    apiRequest<NotificationList>(
      `/api/groups/${groupId}/notifications/read`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getPushConfig: () =>
    apiRequest<PushConfig>('/api/notifications/push-config', undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  savePushSubscription: (subscription: PushSubscriptionJSON) =>
    apiRequest<void>(
      '/api/notifications/push-subscriptions',
      {
        method: 'POST',
        body: JSON.stringify({ endpoint: subscription.endpoint, keys: subscription.keys }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  removePushSubscription: (endpoint: string) =>
    apiRequest<void>(
      '/api/notifications/push-subscriptions/remove',
      {
        method: 'POST',
        body: JSON.stringify({ endpoint }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  getBalanceSeries: (groupId: number) =>
    apiRequest<BalanceSeriesList>(`/api/groups/${groupId}/balance-series`, undefined, {
      requireUserEmail: true,
      includeUserEmail: true,
    }),
  startBalanceSeries: (groupId: number, lineups: BalanceSeriesLineup[]) =>
    apiRequest<BalanceSeriesList>(
      `/api/groups/${groupId}/balance-series`,
      {
        method: 'POST',
        body: JSON.stringify({ lineups }),
      },
      { requireUserEmail: true, includeUserEmail: true }
    ),
  cancelBalanceSeries: (groupId: number, seriesId: number) =>
    apiRequest<BalanceSeriesList>(
      `/api/groups/${groupId}/balance-series/${seriesId}/cancel`,
      { method: 'POST' },
      { requireUserEmail: true, includeUserEmail: true }
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
  getOperationAuditLogPage: async (
    options: ({ page?: number; size?: number } & OperationAuditLogFilters) = {}
  ): Promise<OperationAuditLogPage> => {
    const requestedPage = Number.isFinite(options.page) ? Math.floor(options.page ?? 0) : 0
    const requestedSize = Number.isFinite(options.size) ? Math.floor(options.size ?? 20) : 20
    const safePage = Math.max(0, requestedPage)
    const safeSize = Math.max(1, Math.min(100, requestedSize))
    const params = new URLSearchParams({
      page: String(safePage),
      size: String(safeSize),
    })
    appendOptionalSearchParam(params, 'fromDate', options.fromDate)
    appendOptionalSearchParam(params, 'toDate', options.toDate)
    appendOptionalSearchParam(params, 'actor', options.actor)
    appendOptionalSearchParam(params, 'action', options.action)
    appendOptionalSearchParam(params, 'content', options.content)
    appendOptionalSearchParam(params, 'target', options.target)
    const payload = await apiRequest<unknown>(
      `/api/admin/audit-logs?${params.toString()}`,
      undefined,
      {
        requireUserEmail: true,
        includeUserEmail: true,
      }
    )
    return normalizeOperationAuditLogPage(payload)
  },
  getOperationAuditLogs: async (limit = 100): Promise<OperationAuditLogItem[]> => {
    const requestedLimit = Number.isFinite(limit) ? Math.floor(limit) : 100
    const safeLimit = Math.max(1, Math.min(100, requestedLimit))
    const response = await apiClient.getOperationAuditLogPage({ page: 0, size: safeLimit })
    return response.items
  },
  recalculateRatings: (payload: RatingRecalculationRequest) =>
    apiRequest<RatingRecalculationResponse>(
      '/api/admin/rating/recalculate',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      {
        requireUserEmail: true,
        includeUserEmail: true,
        timeoutMs: RATING_RECALCULATION_API_REQUEST_TIMEOUT_MS,
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

export function isApiBadRequestError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 400
}

export function isApiTooManyRequestsError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 429
}

export function isApiTimeoutError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 408
}
