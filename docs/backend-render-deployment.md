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
ADMIN_EMAILS=admin1@example.com,admin2@example.com
SUPER_ADMIN_EMAILS=super-admin@example.com
ALLOWED_USER_EMAILS=member1@example.com,member2@example.com
SUPABASE_URL=https://your-project-ref.supabase.co
CORS_ALLOWED_ORIGINS=https://heifam.co.kr,https://www.heifam.co.kr
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

