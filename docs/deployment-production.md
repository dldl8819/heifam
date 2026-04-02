# Hei (Balancify) Production Deployment Guide

> 참고: 현재 Hei`Fam 운영 배포(Frontend=Vercel, Backend=Render)는 `docs/deployment-vercel-render-supabase.md`를 우선 기준으로 사용하세요.

## A. Server and Domain Preparation
- Target deployment: single Linux server
- Frontend domain: `https://hei.heifam.com`
- Backend domain: `https://api.heifam.com`
- Assumption: DNS A records already point both domains to this server.
- Suggested project path on server: `/opt/heifam`

## B. Environment Variable Setup

### 1) Root backend/server env: `/opt/heifam/.env`
```env
POSTGRES_DB=heifam
POSTGRES_USER=heifam_user
POSTGRES_PASSWORD=replace-with-strong-password
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/heifam
SPRING_DATASOURCE_USERNAME=heifam_user
SPRING_DATASOURCE_PASSWORD=replace-with-strong-password
SPRING_DATASOURCE_SSLMODE=prefer
ADMIN_API_KEY=replace-with-strong-admin-key
ADMIN_EMAILS=admin1@example.com,admin2@example.com
CORS_ALLOWED_ORIGINS=https://hei.heifam.com
```

If you use Supabase, replace datasource values with your Supabase JDBC URL/username/password (SSL enabled).

### 2) Frontend production env: `/opt/heifam/frontend/.env.production`
```env
NEXT_PUBLIC_API_BASE_URL=https://api.heifam.com
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
NEXTAUTH_SECRET=replace-with-32-char-random-string
NEXTAUTH_URL=https://hei.heifam.com
ALLOWED_SIGNIN_EMAILS=member1@example.com,member2@example.com
ALLOWED_SIGNIN_DOMAINS=
```

## C. Database Startup
```bash
cd /opt/heifam
docker compose up -d
```

## D. Backend Build and Startup

### 1) Build executable jar
```bash
cd /opt/heifam/backend
./gradlew clean bootJar
```

### 2) Local production-like run check
```bash
cd /opt/heifam/backend
set -a; source /opt/heifam/.env; set +a
java -jar build/libs/heifam-backend.jar --server.port=8080
```

## E. Frontend Build and Startup
```bash
cd /opt/heifam/frontend
npm install
set -a; source /opt/heifam/frontend/.env.production; set +a
npm run build
npm run start -- --hostname 127.0.0.1 --port 3000
```

## F. Reverse Proxy Setup (Nginx)

1. Install Nginx.
2. Create ACME webroot directory:
```bash
sudo mkdir -p /var/www/certbot
```
3. Copy temporary HTTP bootstrap config:
```bash
sudo cp /opt/heifam/deploy/nginx/balancify-http.conf /etc/nginx/conf.d/balancify.conf
```
4. Test and reload:
```bash
sudo nginx -t
sudo systemctl reload nginx
```

Routing in this config:
- temporary HTTP-only route for certificate issuance

## G. HTTPS (Let's Encrypt)

1. Install Certbot + nginx plugin.
2. Issue certificates (webroot, each domain):
```bash
sudo certbot certonly --webroot -w /var/www/certbot -d hei.heifam.com
sudo certbot certonly --webroot -w /var/www/certbot -d api.heifam.com
```
3. Enable final HTTPS reverse-proxy config:
```bash
sudo cp /opt/heifam/deploy/nginx/balancify.conf /etc/nginx/conf.d/balancify.conf
sudo nginx -t
sudo systemctl reload nginx
```
4. Verify auto-renew:
```bash
sudo certbot renew --dry-run
```
5. Reload nginx after certificate issuance/renew if needed:
```bash
sudo systemctl reload nginx
```

## H. Process Management with systemd

### 1) Install service files
```bash
sudo cp /opt/heifam/deploy/systemd/balancify-backend.service /etc/systemd/system/
sudo cp /opt/heifam/deploy/systemd/balancify-frontend.service /etc/systemd/system/
sudo systemctl daemon-reload
```

### 2) Enable and start
```bash
sudo systemctl enable --now balancify-backend
sudo systemctl enable --now balancify-frontend
```

### 3) Check status/logs
```bash
sudo systemctl status balancify-backend
sudo systemctl status balancify-frontend
journalctl -u balancify-backend -f
journalctl -u balancify-frontend -f
```

## Google OAuth Required Settings
- Authorized JavaScript origin:
  - `https://hei.heifam.com`
- Authorized redirect URI:
  - `https://hei.heifam.com/api/auth/callback/google`

## Production Validation Checklist
- [ ] `https://api.heifam.com/api/health` returns OK
- [ ] `https://hei.heifam.com` opens over HTTPS
- [ ] Google login succeeds
- [ ] Frontend requests target `https://api.heifam.com`
- [ ] CORS allows frontend domain and blocks unknown origins
- [ ] Admin email accounts can use protected actions
- [ ] Non-admin users are blocked (403) for admin-only APIs
- [ ] Ranking/balance/import/result flows all respond normally


