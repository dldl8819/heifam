# Hei Deployment Guide (Vercel + Render + Supabase)

This guide keeps the current architecture:
- Frontend: Next.js on Vercel
- Backend: Spring Boot on Render
- Database: Supabase Postgres

Expected production URLs:
- Frontend: `https://www.heifam.co.kr` (or `https://heifam.co.kr`)
- Backend API: `https://heifam.onrender.com` (custom domain optional)

## A. Frontend Deploy on Vercel

### 1) Project import
- Import the `frontend` directory as a Vercel project.
- Framework preset: Next.js

### 2) Build settings
- Install command: `npm install`
- Build command: `npm run build`
- Output: default Next.js output

### 3) Vercel environment variables
Set these in Vercel Project Settings > Environment Variables:

```env
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_ACCESS_API_BASE_URL=https://heifam.onrender.com
BACKEND_API_BASE_URLS=https://heifam.onrender.com,https://api.heifam.com
BACKEND_PROXY_UPSTREAM_TIMEOUT_MS=25000
NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-supabase-anon-key
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
NEXTAUTH_SECRET=replace-with-32+-char-random-secret
NEXTAUTH_URL=https://www.heifam.co.kr
ALLOWED_SIGNIN_EMAILS=member1@example.com,member2@example.com
ALLOWED_SIGNIN_DOMAINS=
```

### 4) Domain
- Attach custom domain `www.heifam.co.kr` (and optional apex redirect `heifam.co.kr`) to the Vercel project.

## B. Backend Deploy on Render

### 1) Service type
- Create a Render Web Service from this repository.
- Root directory: `backend`
- Runtime: Java 21

### 2) Build/Start commands
- Build command: `./gradlew clean bootJar`
- Start command: `java -jar build/libs/heifam-backend.jar`

### 3) Health check
- Health path: `/api/health`

### 4) Render environment variables
Set these in Render service environment:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=replace-with-supabase-db-password
SPRING_DATASOURCE_SSLMODE=require
ADMIN_EMAILS=admin1@example.com,admin2@example.com
SUPER_ADMIN_EMAILS=super-admin@example.com
ALLOWED_USER_EMAILS=member1@example.com,member2@example.com
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=replace-with-supabase-anon-key
CORS_ALLOWED_ORIGINS=https://heifam.co.kr,https://www.heifam.co.kr
SUPABASE_SERVICE_ROLE_KEY=replace-with-supabase-service-role-key
```

Optional:
```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

`SERVER_PORT` is optional because the backend also supports Render's `PORT` environment variable.

### 5) Domain
- Optional: attach custom domain `api.heifam.com` to the Render service.

## C. Supabase Postgres Connection Setup

Use Supabase Database settings and copy:
- Host / Port
- Database name
- User
- Password

Then paste as:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Supported URL styles:
- Direct connection JDBC URL
- Supavisor session pooler JDBC URL

Use SSL (`sslmode=require`) in production.

For persistent servers:
- Prefer direct connection when network path is available.
- Use Supavisor session pooler when direct path is constrained (for example IPv4-only routing requirements).

## D. Google OAuth Production Setup

In Google Cloud Console (OAuth Client):

- Authorized JavaScript origins:
  - `https://www.heifam.co.kr`
  - `https://heifam.co.kr`

- Authorized redirect URIs:
  - `https://www.heifam.co.kr/api/auth/callback/google`
  - `https://heifam.co.kr/api/auth/callback/google`

Ensure Vercel env vars `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` match the same OAuth client.

## E. Final Verification Checklist

- [ ] `https://www.heifam.co.kr` loads (Korean Hei UI visible)
- [ ] `https://heifam.onrender.com/api/health` returns healthy response
- [ ] Google login succeeds from frontend
- [ ] `https://www.heifam.co.kr/api/proxy/api/health` returns healthy response
- [ ] CORS allows only `https://heifam.co.kr` and `https://www.heifam.co.kr`
- [ ] Admin emails can call admin-only actions
- [ ] Non-admin users are blocked with `403` on admin-only actions
- [ ] Ranking / balance / import / result flows work without API errors

