import { NextRequest, NextResponse } from 'next/server'
import {
  PROXY_READ_TOTAL_TIMEOUT_MS,
  isReadOnlyHttpMethod,
  resolveConfiguredUpstreamTimeoutMs,
  resolveProxyAttemptTimeoutMs,
  resolveProxyCandidateLimit,
} from '@/lib/proxy-timeout'

const FALLBACK_BACKEND_BASE_URLS = [
  'https://heifam.onrender.com',
  'https://hei-backend.onrender.com',
  'https://heifam-backend.onrender.com',
]

const RETRYABLE_UPSTREAM_STATUSES = new Set([404, 502, 503, 504])

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'content-length',
  'host',
  'transfer-encoding',
  'x-forwarded-for',
  'x-forwarded-host',
  'x-forwarded-port',
  'x-forwarded-proto',
])
const BLOCKED_IDENTITY_HEADERS = new Set([
  'x-user-email',
  'x-user-nickname',
])
const UPSTREAM_RESPONSE_HEADER_ALLOWLIST = new Set([
  'cache-control',
  'content-type',
  'etag',
  'last-modified',
])

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'
// Keep this static literal aligned with PROXY_MAX_DURATION_MS for Vercel route analysis.
export const maxDuration = 60

function parseCsv(value: string | undefined): string[] {
  if (!value) {
    return []
  }
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
}

function normalizeBaseUrl(value: string): string {
  return value.trim().replace(/\/+$/, '')
}

function sanitizeBackendBaseUrl(value: string): string | null {
  try {
    const parsed = new URL(value)
    const normalizedPath = parsed.pathname.replace(/\/+$/, '')

    // Prevent proxy loops when a proxy URL is accidentally configured as upstream.
    if (normalizedPath.startsWith('/api/proxy')) {
      return null
    }

    // Common misconfiguration: setting upstream to `/api` or `/api/health`.
    if (normalizedPath === '/api' || normalizedPath === '/api/health') {
      parsed.pathname = '/'
    }

    parsed.search = ''
    parsed.hash = ''
    return parsed.toString().replace(/\/+$/, '')
  } catch {
    return null
  }
}

function resolveBackendBaseUrls(): string[] {
  const explicitCandidates = [
    ...parseCsv(process.env.BACKEND_API_BASE_URLS),
    ...parseCsv(process.env.NEXT_PUBLIC_API_BASE_URL),
  ]

  const deduped = new Set<string>()
  for (const candidate of explicitCandidates) {
    const normalized = normalizeBaseUrl(candidate)
    if (!normalized.startsWith('http://') && !normalized.startsWith('https://')) {
      continue
    }

    const sanitized = sanitizeBackendBaseUrl(normalized)
    if (!sanitized) {
      continue
    }
    deduped.add(sanitized)
  }

  if (deduped.size > 0) {
    return Array.from(deduped)
  }

  const fallbackDeduped = new Set<string>()
  for (const fallback of FALLBACK_BACKEND_BASE_URLS) {
    const sanitized = sanitizeBackendBaseUrl(normalizeBaseUrl(fallback))
    if (sanitized) {
      fallbackDeduped.add(sanitized)
    }
  }

  return Array.from(fallbackDeduped)
}

function resolveUpstreamTimeoutMs(): number {
  return resolveConfiguredUpstreamTimeoutMs(process.env.BACKEND_PROXY_UPSTREAM_TIMEOUT_MS)
}

function buildTargetUrl(baseUrl: string, path: string[], search: string): string {
  const encodedPath = path.map((segment) => encodeURIComponent(segment)).join('/')
  const normalizedPath = encodedPath.length > 0 ? `/${encodedPath}` : ''
  return `${baseUrl}${normalizedPath}${search}`
}

function buildForwardHeaders(request: NextRequest): Headers {
  const headers = new Headers()
  request.headers.forEach((value, key) => {
    const normalizedKey = key.toLowerCase()
    if (HOP_BY_HOP_HEADERS.has(normalizedKey)) {
      return
    }
    if (BLOCKED_IDENTITY_HEADERS.has(normalizedKey)) {
      return
    }
    if (normalizedKey === 'cookie') {
      return
    }
    if (normalizedKey === 'accept-encoding') {
      return
    }
    headers.set(key, value)
  })
  return headers
}

async function buildProxyResponse(upstream: Response): Promise<NextResponse> {
  const responseHeaders = new Headers()
  upstream.headers.forEach((value, key) => {
    const normalizedKey = key.toLowerCase()
    if (!UPSTREAM_RESPONSE_HEADER_ALLOWLIST.has(normalizedKey)) {
      return
    }
    responseHeaders.set(key, value)
  })

  // Let runtime/platform negotiate transport encoding.
  responseHeaders.delete('content-encoding')
  responseHeaders.delete('content-length')
  responseHeaders.delete('transfer-encoding')
  responseHeaders.set('Cache-Control', 'no-store, max-age=0')

  const upstreamBodyText = upstream.status === 204 ? null : await upstream.text()
  return new NextResponse(upstreamBodyText, {
    status: upstream.status,
    headers: responseHeaders,
  })
}

function buildUnavailableResponse(): NextResponse {
  return NextResponse.json(
    {
      message: 'Backend service is temporarily unavailable.',
    },
    { status: 502 }
  )
}

async function proxyRequest(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  const { path } = await context.params
  const baseUrls = resolveBackendBaseUrls()

  if (baseUrls.length === 0) {
    return NextResponse.json(
      { message: 'No backend API base URLs configured' },
      { status: 500 }
    )
  }

  const search = request.nextUrl.search ?? ''
  const headers = buildForwardHeaders(request)
  const readOnlyRequest = isReadOnlyHttpMethod(request.method)
  const upstreamTimeoutMs = resolveUpstreamTimeoutMs()
  const requestBaseUrls = baseUrls.slice(0, resolveProxyCandidateLimit(request.method))
  const readDeadlineMs = readOnlyRequest ? Date.now() + PROXY_READ_TOTAL_TIMEOUT_MS : null
  let activeUpstreamController: AbortController | null = null
  let clientAborted = request.signal.aborted
  let clientAbortListenerAttached = false
  const handleClientAbort = (): void => {
    clientAborted = true
    activeUpstreamController?.abort()
  }

  if (!clientAborted) {
    request.signal.addEventListener('abort', handleClientAbort, { once: true })
    clientAbortListenerAttached = true
    if (request.signal.aborted) {
      handleClientAbort()
    }
  }

  try {
    const body = readOnlyRequest ? undefined : await request.arrayBuffer()
    if (clientAborted) {
      return buildUnavailableResponse()
    }

    for (const [index, baseUrl] of requestBaseUrls.entries()) {
      if (clientAborted) {
        break
      }

      const remainingCandidateCount = requestBaseUrls.length - index
      const remainingReadBudgetMs = readDeadlineMs === null
        ? undefined
        : Math.max(0, readDeadlineMs - Date.now())
      const attemptTimeoutMs = resolveProxyAttemptTimeoutMs({
        method: request.method,
        configuredTimeoutMs: upstreamTimeoutMs,
        remainingReadBudgetMs,
        remainingCandidateCount,
      })
      if (attemptTimeoutMs <= 0) {
        break
      }

      const targetUrl = buildTargetUrl(baseUrl, path, search)
      const controller = new AbortController()
      activeUpstreamController = controller
      const timeoutId = setTimeout(() => controller.abort(), attemptTimeoutMs)

      try {
        const upstream = await fetch(targetUrl, {
          method: request.method,
          headers,
          body,
          redirect: 'manual',
          cache: 'no-store',
          signal: controller.signal,
        })

        const hasNextCandidate = index < requestBaseUrls.length - 1
        if (RETRYABLE_UPSTREAM_STATUSES.has(upstream.status) && hasNextCandidate) {
          await upstream.body?.cancel()
          continue
        }

        return await buildProxyResponse(upstream)
      } catch {
        if (clientAborted) {
          break
        }
        // The response intentionally omits upstream addresses and raw network errors.
      } finally {
        clearTimeout(timeoutId)
        activeUpstreamController = null
      }
    }
  } catch {
    // The response intentionally omits request body and raw client abort errors.
  } finally {
    activeUpstreamController?.abort()
    if (clientAbortListenerAttached) {
      request.signal.removeEventListener('abort', handleClientAbort)
    }
  }

  return buildUnavailableResponse()
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  return proxyRequest(request, context)
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  return proxyRequest(request, context)
}

export async function PUT(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  return proxyRequest(request, context)
}

export async function PATCH(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  return proxyRequest(request, context)
}

export async function DELETE(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  return proxyRequest(request, context)
}
