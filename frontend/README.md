# Hei Frontend (Vercel)

## Build Commands
- Install: `npm install`
- Build: `npm run build`
- Start (production local check): `npm run start`

## Vercel Environment Variables
Set all values in Vercel Project Settings > Environment Variables:

```env
NEXT_PUBLIC_API_BASE_URL=https://api.heifam.com
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
NEXTAUTH_SECRET=replace-with-a-long-random-secret-at-least-32-characters
NEXTAUTH_URL=https://hei.heifam.com
ALLOWED_SIGNIN_EMAILS=member1@example.com,member2@example.com
ALLOWED_SIGNIN_DOMAINS=
```

## OAuth Compatibility
- Authorized JavaScript origin:
  - `https://hei.heifam.com`
- Authorized redirect URI:
  - `https://hei.heifam.com/api/auth/callback/google`

## Notes
- Production requires `NEXT_PUBLIC_API_BASE_URL`.
- `NEXTAUTH_URL` must match the deployed frontend domain.
- 운영진/최상위 관리자/접속 허용 이메일은 백엔드 권한 관리 화면(`/admin/access`)에서 관리합니다.
- `ALLOWED_SIGNIN_EMAILS` / `ALLOWED_SIGNIN_DOMAINS` can block non-member Google accounts at sign-in callback.
