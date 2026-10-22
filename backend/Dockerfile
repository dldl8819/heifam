# The JDK is Oracle JDK 25.0.5.0.2 (Oracle No-Fee Terms and Conditions). It fixes CVE-2026-91403
# (JSSE), which no OpenJDK build (Temurin, Corretto, Zulu) carried yet on 2026-10-23. It is pinned
# by version and checksum: a changed download fails the build. Moving back to an OpenJDK image is
# a FROM line once one has the fix.
FROM ubuntu:jammy AS jdk

ARG JDK_URL=https://download.oracle.com/java/25/archive/jdk-25.0.5.0.2_linux-x64_bin.tar.gz
ARG JDK_SHA256=d430d3f9e91435d2d58d366b1dabf14b23fcf8629ed4b041757c1be444055946

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "$JDK_URL" -o /tmp/jdk.tar.gz \
    && echo "$JDK_SHA256  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/jdk \
    && tar -xzf /tmp/jdk.tar.gz -C /opt/jdk --strip-components=1 \
    && rm /tmp/jdk.tar.gz \
    && /opt/jdk/bin/java -version

FROM ubuntu:jammy AS builder

COPY --from=jdk /opt/jdk /opt/jdk
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM ubuntu:jammy

COPY --from=jdk /opt/jdk /opt/jdk
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH
ENV LANG=C.UTF-8

WORKDIR /app

COPY --from=builder /workspace/build/libs/heifam-backend.jar /app/app.jar

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
