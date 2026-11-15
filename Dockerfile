FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# 테스트는 CI 잡에서 Testcontainers로 돌린다. 이미지 빌드 안에서 돌리려면
# 도커 인 도커가 필요해져서 여기서는 건너뛴다
RUN chmod +x gradlew && ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

# 같은 인스턴스에 nginx와 OS가 함께 올라가므로 컨테이너 메모리를 전부 heap으로 쓰지 않는다
CMD ["sh", "-c", "java -XX:MaxRAMPercentage=65 -Dserver.port=${PORT} -jar app.jar"]
