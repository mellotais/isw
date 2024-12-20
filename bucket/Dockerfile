# Usar uma imagem base com o JDK
FROM openjdk:17-jdk-slim

# Diretório de trabalho no contêiner
WORKDIR /app

COPY src/main/resources/ src/main/resources/

COPY wallet /app/wallet

# Copiar o JAR gerado para o contêiner
COPY target/projeto-1.0-SNAPSHOT.jar app.jar

ENV TNS_ADMIN=/app/wallet

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
