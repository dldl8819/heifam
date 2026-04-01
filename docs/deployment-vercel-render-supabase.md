# Hei Deployment Guide (Vercel + Render + Supabase)

This guide keeps the current architecture:
- Frontend: Next.js on Vercel
- Backend: Spring Boot on Render
- Database: Supabase Postgres

Expected production URLs:
- Frontend: `https://hei.heifam.com`
- Backend API: `https://api.heifam.com`

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
NEXT_PUBLIC_API_BASE_URL=https://api.heifam.com
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
NEXTAUTH_SECRET=replace-with-32+-char-random-secret
NEXTAUTH_URL=https://hei.heifam.com
NEXT_PUBLIC_ADMIN_EMAILS=admin1@example.com,admin2@example.com
```

### 4) Domain
- Attach custom domain `hei.heifam.com` to the Vercel project.

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
ADMIN_API_KEY=replace-with-strong-admin-key
ADMIN_EMAILS=admin1@example.com,admin2@example.com
CORS_ALLOWED_ORIGINS=https://hei.heifam.com
```

Optional:
```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

`SERVER_PORT` is optional because the backend also supports Render's `PORT` environment variable.

### 5) Domain
- Attach custom domain `api.heifam.com` to the Render service.

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
  - `https://hei.heifam.com`

- Authorized redirect URIs:
  - `https://hei.heifam.com/api/auth/callback/google`

Ensure Vercel env vars `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` match the same OAuth client.

## E. Final Verification Checklist

- [ ] `https://hei.heifam.com` loads (Korean Hei UI visible)
- [ ] `https://api.heifam.com/api/health` returns healthy response
- [ ] Google login succeeds from frontend
- [ ] Frontend requests go to `https://api.heifam.com`
- [ ] CORS accepts only `https://hei.heifam.com`
- [ ] Admin emails can call admin-only actions
- [ ] Non-admin users are blocked with `403` on admin-only actions
- [ ] Ranking / balance / import / result flows work without API errors

