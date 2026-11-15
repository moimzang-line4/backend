# 미리 빌드된 jar를 담기만 한다. 이미지 안에서 Gradle을 돌리면 러너에 레이어 캐시가 없어
# 매 배포마다 Gradle 배포판과 전체 의존성을 다시 받게 되므로, 빌드는 CI 잡에 맡긴다.
# 로컬에서 이미지를 만들려면 ./gradlew bootJar 를 먼저 실행해야 한다.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/*-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

# 같은 인스턴스에 nginx와 OS가 함께 올라가므로 컨테이너 메모리를 전부 heap으로 쓰지 않는다
CMD ["sh", "-c", "java -XX:MaxRAMPercentage=65 -Dserver.port=${PORT} -jar app.jar"]
