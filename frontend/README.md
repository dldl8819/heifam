# Hei Frontend (Vercel)

## Build Commands
- Install: `npm install`
- Build: `npm run build`
- Start (production local check): `npm run start`
- Accessibility check (axe-core in Chrome, after a build): `npm run test:a11y`. Build and run it with `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` set to placeholders, as CI does (`.github/workflows/ci.yml`); the backend and Supabase are answered inside the browser.

## Vercel Environment Variables
Set all values in Vercel Project Settings > Environment Variables:

```env
# Keep empty in production: the browser calls /api/proxy on the site, which Vercel forwards to the backend
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_ACCESS_API_BASE_URL=https://heifam.onrender.com
# Where /api/proxy goes. Read when the site is built (next.config.js); only the first usable address is used
BACKEND_API_BASE_URLS=https://heifam.onrender.com
NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-supabase-anon-key
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
NEXTAUTH_SECRET=replace-with-a-long-random-secret-at-least-32-characters
NEXTAUTH_URL=https://www.heifam.co.kr
ALLOWED_SIGNIN_EMAILS=member1@example.com,member2@example.com
ALLOWED_SIGNIN_DOMAINS=
```

## OAuth Compatibility
- Authorized JavaScript origin:
  - `https://www.heifam.co.kr`
  - `https://heifam.co.kr`
- Authorized redirect URI:
  - `https://www.heifam.co.kr/api/auth/callback/google`
  - `https://heifam.co.kr/api/auth/callback/google`

## Notes
- `NEXTAUTH_URL` must match the deployed canonical frontend domain.
- `NEXT_PUBLIC_ACCESS_API_BASE_URL` should point to reachable backend base URL.
- On the live site `/api/proxy/<path>` is an external rewrite (`api-proxy-rewrite.cjs`): Vercel's CDN forwards it to
  `BACKEND_API_BASE_URLS` with no function in between, so it costs no Function CPU. A changed backend address needs
  a new deployment. Vercel waits up to 120 seconds for the backend.
- 운영진/최상위 관리자/접속 허용 이메일은 백엔드 권한 관리 화면(`/admin/access`)에서 관리합니다.
- `ALLOWED_SIGNIN_EMAILS` / `ALLOWED_SIGNIN_DOMAINS` can block non-member Google accounts at sign-in callback.
