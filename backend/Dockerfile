# The JDK is Oracle JDK 25.0.5.1 (Oracle No-Fee Terms and Conditions). It fixes CVE-2026-114938
# ("Java4Shell", a heap overflow in the x86-64 Base64 decoder intrinsic, fixed 2026-11-17), on top
# of CVE-2026-91403 (JSSE), for which Oracle's 25.0.5.0.2 was taken on 2026-10-23 because no OpenJDK
# build (Temurin, Corretto, Zulu) carried that fix yet. It is pinned by version and checksum (from
# Oracle's .sha256 file): a changed download fails the build. Moving back to an OpenJDK image is a
# FROM line once one has both fixes.
FROM ubuntu:jammy AS jdk

ARG JDK_URL=https://download.oracle.com/java/25/archive/jdk-25.0.5.1_linux-x64_bin.tar.gz
ARG JDK_SHA256=3d40d69461200594380ac84c17be6224539022028730cffe043ac70c41ffd1d5

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

# The backend runs as a user of its own, not root: a flaw that let someone run code in it would not
# hand them the whole container. It writes nothing but its console log and the JVM's temporary files
# (/tmp, open to every user), and the port it listens on is above 1024. The jar stays root's and
# read-only to it.
RUN groupadd --system --gid 10001 heifam \
    && useradd --system --uid 10001 --gid heifam --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin heifam
USER 10001:10001

EXPOSE 10000

# Memory, sized so the backend fits Render's 0.5c-512mb plan (half a CPU, 512 MB) with room to spare,
# and fine on bigger plans. Measured on 2026-11-21 with --memory=512m --cpus=0.5 under up to 120
# users at once (page cache aside): without these the backend peaked at 430-440 MB, with them (and
# Tomcat's thread limit in application.yml) at about 360 MB, flat over a 20-minute run.
# - A serial collector and a 160 MB heap: what is still in use after a full collection is 55-70 MB.
# - TieredStopAtLevel=1 compiles with C1 only: compiled code takes 32 MB instead of 70, and starting
#   on half a CPU takes about 20 s instead of 36-42. The backend uses well under 1% of a CPU, so C2's
#   faster code is not missed (at 18 requests a second the median answer took 0.6 ms longer).
# - ExitOnOutOfMemoryError: a full heap ends the process, and Render starts it again, instead of
#   leaving it running half-broken.
# JAVA_OPTS set on the Render service replaces these.
ENV JAVA_OPTS="-XX:+UseSerialGC -Xmx160m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
