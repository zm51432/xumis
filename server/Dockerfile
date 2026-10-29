# ============================================================================
#  shine星心 外贸独立站 · Docker 镜像
#  多阶段构建：maven 编译 → 精简 JRE 运行，镜像体积更小
#  构建： docker build -t shine-xumis:1.0 .
#  运行： docker run -d -p 8080:8080 -v shine-data:/app/data -v shine-uploads:/app/uploads shine-xumis:1.0
# ============================================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只复制 pom，利用 Docker 缓存加速依赖下载
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads \
    ADMIN_KEY=change-me

RUN apk add --no-cache tzdata curl \
    && mkdir -p /app/data /app/uploads /app/logs

COPY --from=builder /build/target/shine-xumis.jar /app/shine-xumis.jar

VOLUME ["/app/data", "/app/uploads", "/app/logs"]
EXPOSE 8080

# 容器健康检查：访问首页
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java -Dfile.encoding=UTF-8 -jar /app/shine-xumis.jar \
  --spring.datasource.url=jdbc:h2:file:/app/data/shinedb;DB_CLOSE_DELAY=-1;MODE=MySQL \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa \
  --spring.datasource.password= \
  ${JAVA_OPTS}"]
