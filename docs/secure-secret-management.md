# Secure Secret Management

This project keeps all secrets outside source code and injects them through environment variables at runtime. The tracked files in the repository are safe to commit because they use placeholders only.

## Runtime Files

The backend reads its safe defaults from [application.yml](/c:/Users/이민식/Documents/heifam/backend/src/main/resources/application.yml) and binds secret-bearing settings through [UpbitProperties.java](/c:/Users/이민식/Documents/heifam/backend/src/main/java/com/balancify/backend/config/UpbitProperties.java) and [JwtProperties.java](/c:/Users/이민식/Documents/heifam/backend/src/main/java/com/balancify/backend/config/JwtProperties.java). Local example values live in [application-local.yml.example](/c:/Users/이민식/Documents/heifam/backend/src/main/resources/application-local.yml.example), backend environment variables live in [backend/.env.example](/c:/Users/이민식/Documents/heifam/backend/.env.example), and repository-level Docker variables live in [.env.example](/c:/Users/이민식/Documents/heifam/.env.example).

## Logging

Sensitive values must never be written to logs. The backend disables SQL value logging and request-body logging in [application.yml](/c:/Users/이민식/Documents/heifam/backend/src/main/resources/application.yml), and [logback-spring.xml](/c:/Users/이민식/Documents/heifam/backend/src/main/resources/logback-spring.xml) routes output through [SensitiveDataMaskingPatternLayout.java](/c:/Users/이민식/Documents/heifam/backend/src/main/java/com/balancify/backend/logging/SensitiveDataMaskingPatternLayout.java), which redacts common token, password, API key, and Authorization patterns.

## Docker

Use [deploy/docker-compose.backend.example.yml](/c:/Users/이민식/Documents/heifam/deploy/docker-compose.backend.example.yml) with environment-variable interpolation so secrets stay in the runtime environment instead of the compose file. The compose example passes only the variables that the backend actually needs.

## systemd

Use [deploy/systemd/balancify-backend.service](/c:/Users/이민식/Documents/heifam/deploy/systemd/balancify-backend.service) together with [deploy/systemd/backend.env.example](/c:/Users/이민식/Documents/heifam/deploy/systemd/backend.env.example). The service file expects the real environment file at `/etc/heifam/backend.env`, which keeps secrets outside the Git checkout.

## Wrong vs Right

Wrong:

```yaml
balancify:
  integrations:
    upbit:
      access-key: DO_NOT_HARDCODE
      secret-key: DO_NOT_HARDCODE
```

Right:

```yaml
balancify:
  integrations:
    upbit:
      access-key: ${UPBIT_ACCESS_KEY:}
      secret-key: ${UPBIT_SECRET_KEY:}
```

Wrong:

```java
log.info("JWT secret={}", jwtSecret);
```

Right:

```java
log.info("JWT enabled: {}", jwtProperties.isEnabled());
```

Wrong:

```yaml
environment:
  JWT_SECRET: DO_NOT_HARDCODE
```

Right:

```yaml
environment:
  JWT_SECRET: ${JWT_SECRET:-}
```
