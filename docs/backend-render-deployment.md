# Backend Render Deployment Notes

## Build and Start
- Build Command: `./gradlew clean bootJar`
- Start Command: `java -jar build/libs/heifam-backend.jar`
- Health Check Path: `/api/health`

## Required Render Environment Variables
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=replace-with-supabase-db-password
ADMIN_API_KEY=replace-with-strong-admin-key
ADMIN_EMAILS=admin1@example.com,admin2@example.com
CORS_ALLOWED_ORIGINS=https://hei.heifam.com
SERVER_PORT=10000
```

Optional:
```env
SPRING_DATASOURCE_SSLMODE=require
SPRING_PROFILES_ACTIVE=prod
```

## Notes
- Backend supports `PORT` and `SERVER_PORT`; Render `PORT` is used first.
- Keep `CORS_ALLOWED_ORIGINS` restricted to frontend production origin.
- Do not hardcode datasource credentials in source code.

