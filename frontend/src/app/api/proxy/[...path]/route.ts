import { NextRequest, NextResponse } from 'next/server'

const FALLBACK_BACKEND_BASE_URLS = [
  'https://hei-backend.onrender.com',
  'https://heifam-backend.onrender.com',
]

const RETRYABLE_UPSTREAM_STATUSES = new Set([404, 502, 503, 504])
const DEFAULT_UPSTREAM_TIMEOUT_MS = 5000

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

function resolveBackendBaseUrls(): string[] {
  const candidates = [
    ...parseCsv(process.env.BACKEND_API_BASE_URLS),
    ...parseCsv(process.env.NEXT_PUBLIC_API_BASE_URL),
    ...FALLBACK_BACKEND_BASE_URLS,
  ]

  const deduped = new Set<string>()
  for (const candidate of candidates) {
    const normalized = normalizeBaseUrl(candidate)
    if (!normalized.startsWith('http://') && !normalized.startsWith('https://')) {
      continue
    }
    deduped.add(normalized)
  }

  return Array.from(deduped)
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
    headers.set(key, value)
  })
  return headers
}

function buildProxyResponse(upstream: Response, baseUrl: string): NextResponse {
  const responseHeaders = new Headers(upstream.headers)
  responseHeaders.delete('transfer-encoding')
  responseHeaders.set('x-proxy-upstream', baseUrl)
  responseHeaders.set('x-proxy-upstream-status', String(upstream.status))

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  })
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

      return buildProxyResponse(upstream, baseUrl)
    } catch (error) {
      lastError = error
    } finally {
      clearTimeout(timeoutId)
    }
  }

  const reason = lastError instanceof Error ? lastError.message : 'Unknown network error'
  return NextResponse.json(
    {
      message:
        'Failed to connect to all configured backend endpoints. Check backend DNS and deployment status.',
      reason,
    },
    { status: 502 }
  )
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
