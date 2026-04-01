import { NextRequest, NextResponse } from 'next/server'

const FALLBACK_BACKEND_BASE_URLS = [
  'https://heifam.onrender.com',
  'https://hei-backend.onrender.com',
  'https://heifam-backend.onrender.com',
]

const RETRYABLE_UPSTREAM_STATUSES = new Set([404, 502, 503, 504])
const DEFAULT_UPSTREAM_TIMEOUT_MS = 25000

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
const UPSTREAM_RESPONSE_HEADER_ALLOWLIST = new Set([
  'cache-control',
  'content-type',
  'etag',
  'last-modified',
])

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

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
  const raw = process.env.BACKEND_PROXY_UPSTREAM_TIMEOUT_MS?.trim()
  if (!raw) {
    return DEFAULT_UPSTREAM_TIMEOUT_MS
  }

  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed < 1000 || parsed > 60000) {
    return DEFAULT_UPSTREAM_TIMEOUT_MS
  }

  return Math.floor(parsed)
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

async function buildProxyResponse(upstream: Response, baseUrl: string): Promise<NextResponse> {
  const responseHeaders = new Headers()
  upstream.headers.forEach((value, key) => {
    const normalizedKey = key.toLowerCase()
    if (!UPSTREAM_RESPONSE_HEADER_ALLOWLIST.has(normalizedKey)) {
      return
    }
    responseHeaders.set(key, value)
  })

  // Always let platform/runtime set transport encoding headers.
  responseHeaders.delete('content-encoding')
  responseHeaders.delete('content-length')
  responseHeaders.delete('transfer-encoding')
  responseHeaders.set('x-proxy-upstream', baseUrl)
  responseHeaders.set('x-proxy-upstream-status', String(upstream.status))

  const upstreamBody = upstream.status === 204 ? null : await upstream.arrayBuffer()
  return new NextResponse(upstreamBody, {
    status: upstream.status,
    headers: responseHeaders,
  })
}

function buildUnavailableResponse(baseUrls: string[], reason: string): NextResponse {
  return NextResponse.json(
    {
      message:
        'Failed to connect to all configured backend endpoints. Check backend DNS and deployment status.',
      reason,
      attemptedBaseUrls: baseUrls,
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
  const upstreamTimeoutMs = resolveUpstreamTimeoutMs()
  const body =
    request.method === 'GET' || request.method === 'HEAD'
      ? undefined
      : await request.arrayBuffer()

  let lastError: unknown = null

  for (const [index, baseUrl] of baseUrls.entries()) {
    const targetUrl = buildTargetUrl(baseUrl, path, search)
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), upstreamTimeoutMs)

    try {
      const upstream = await fetch(targetUrl, {
        method: request.method,
        headers,
        body,
        redirect: 'manual',
        cache: 'no-store',
        signal: controller.signal,
      })

      const hasNextCandidate = index < baseUrls.length - 1
      if (RETRYABLE_UPSTREAM_STATUSES.has(upstream.status) && hasNextCandidate) {
        await upstream.body?.cancel()
        continue
      }

      return await buildProxyResponse(upstream, baseUrl)
    } catch (error) {
      lastError = error
    } finally {
      clearTimeout(timeoutId)
    }
  }

  const reason = lastError instanceof Error ? lastError.message : 'Unknown network error'
  return buildUnavailableResponse(baseUrls, reason)
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
