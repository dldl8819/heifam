# Backend PostgreSQL Configuration (Local + Supabase)

This backend keeps using Spring Boot + JDBC/JPA + PostgreSQL.
Datasource values are loaded from environment variables:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## A. Local Docker Postgres Example

Use these environment variables:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/heifam
SPRING_DATASOURCE_USERNAME=heifam_user
SPRING_DATASOURCE_PASSWORD=replace-with-strong-password
SPRING_DATASOURCE_SSLMODE=prefer
```

If you use local `docker-compose.yml`, make sure DB container values match your local datasource values.

## B. Supabase Postgres Example

Set datasource variables in your backend runtime environment (`.env`, systemd `EnvironmentFile`, or hosting platform env):

```env
# Direct connection (example)
SPRING_DATASOURCE_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=replace-with-supabase-db-password
SPRING_DATASOURCE_SSLMODE=require
```

```env
# Session pooler connection (example)
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=replace-with-supabase-db-password
SPRING_DATASOURCE_SSLMODE=require
```

## C. Connection Mode Notes

- For persistent backend servers, Supabase generally recommends direct connection when network routing allows it.
- If your runtime needs IPv4-friendly managed pooling, Supavisor session pooler is a practical alternative.
- Use SSL for Supabase connections (`sslmode=require`).

