# heifam
Automatic team balancing and ELO-based league management system for game communities.

## Monorepo Structure
- `frontend`: Next.js + TypeScript + Tailwind CSS
- `backend`: Spring Boot 3.x + Gradle + Java 21
- `docs`: Architecture and project docs
- `docker-compose.yml`: PostgreSQL for local development

## Run Locally
1. Configure environment variables
```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local
cp backend/.env.example backend/.env
```

2. Start PostgreSQL
```bash
docker-compose up -d
```
3. Start backend
```bash
cd backend
./gradlew bootRun
```
4. Start frontend
```bash
cd frontend
npm install
npm run dev
```

## Environment Notes
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: backend datasource connection
- `NEXT_PUBLIC_ADMIN_EMAILS`: comma-separated frontend admin emails (UI/guard)
- `ADMIN_EMAILS`: comma-separated backend admin emails (API authorization)
- `ALLOWED_SIGNIN_EMAILS`: comma-separated Google account allowlist for member-only sign-in
- `ALLOWED_SIGNIN_DOMAINS`: optional Google account domain allowlist for member-only sign-in
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `NEXTAUTH_SECRET`, `NEXTAUTH_URL`: Google login / NextAuth setup
- Supabase/local backend datasource guide: [`docs/backend-datasource-supabase.md`](docs/backend-datasource-supabase.md)

## Production Deployment
- Frontend domain: `https://hei.heifam.com`
- Backend domain: `https://api.heifam.com`
- Detailed guide: [`docs/deployment-production.md`](docs/deployment-production.md)
- Vercel + Render + Supabase guide: [`docs/deployment-vercel-render-supabase.md`](docs/deployment-vercel-render-supabase.md)
- Backend Render notes: [`docs/backend-render-deployment.md`](docs/backend-render-deployment.md)
- Frontend Vercel quick notes: [`frontend/README.md`](frontend/README.md)
- Render blueprint: [`render.yaml`](render.yaml)
- Deployment assets:
  - Nginx bootstrap(HTTP): [`deploy/nginx/balancify-http.conf`](deploy/nginx/balancify-http.conf)
  - Nginx config: [`deploy/nginx/balancify.conf`](deploy/nginx/balancify.conf)
  - systemd backend: [`deploy/systemd/balancify-backend.service`](deploy/systemd/balancify-backend.service)
  - systemd frontend: [`deploy/systemd/balancify-frontend.service`](deploy/systemd/balancify-frontend.service)

