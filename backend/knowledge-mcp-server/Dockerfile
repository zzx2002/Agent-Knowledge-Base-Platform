# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd -m -u 10002 kb && chown -R kb:kb /app
COPY --from=build /build/target/knowledge-mcp-server-*.jar /app/app.jar
USER kb
EXPOSE 8081
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
