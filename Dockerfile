# 백엔드 실행 이미지. jar는 먼저 호스트에서 빌드한다 (docs/deploy.md).
#   ./gradlew bootJar
#   docker build --platform linux/amd64 -t riftdata-backend:latest .
# jar(플랫폼 무관)만 복사하므로 Mac(arm64)에서 GCP VM(amd64)용 이미지를 빠르게 만들 수 있다.
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Seoul
WORKDIR /app
RUN useradd --system --uid 1001 --no-create-home app
COPY build/libs/riftdata-backend.jar /app/app.jar
USER app

EXPOSE 8080
# 컨테이너 메모리의 75%까지 힙으로 쓴다. 인자는 그대로 Spring에 넘어간다 (예: --spring.profiles.active=loader).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
