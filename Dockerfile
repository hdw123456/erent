# 第一阶段：使用 Maven 和 JDK 21 编译项目
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 先复制 pom.xml，利用 Docker 构建缓存下载依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再复制源码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests


# 第二阶段：只保留运行应用需要的 JRE
FROM eclipse-temurin:21-jre

WORKDIR /app

# 创建非 root 用户
RUN groupadd --system app \
    && useradd --system --gid app --uid 10001 app

# 从第一阶段复制 Spring Boot JAR
COPY --from=build --chown=app:app \
    /workspace/target/ai-gateway-*.jar \
    /app/app.jar

# application.yml 会向 logs 目录写日志
RUN mkdir -p /app/logs \
    && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]