package com.tais;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

public class Main {
    public static void main(String[] args) {
        // Configurações gerais
        String namespaceName = "graz51z7jyn1";
        String bucketName = "projeto";
        String arquivoClasspath = "teste.txt";
        String nomeObjeto = "teste.txt";

        // Configurações do AutonomousDB
        String jdbcUrl = "jdbc:oracle:thin:@projeto_high?TNS_ADMIN=wallet";
        String dbUser = "ADMIN";
        String dbPassword = "yUPBKK4Jqh3Le-P";

        try {
            // 1. Carregar arquivo no Object Storage
            ConfigFileAuthenticationDetailsProvider authProvider =
                    new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "DEFAULT");
            ObjectStorage objectStorageClient = new ObjectStorageClient(authProvider);

            System.out.println("Iniciando upload do arquivo...");
            PutObjectResponse response = carregarArquivo(objectStorageClient, namespaceName, bucketName, arquivoClasspath, nomeObjeto);
            System.out.println("Upload concluído! ETag: " + response.getETag());

            // 2. Criação da tabela no AutonomousDB
            criarTabelaNoBanco(jdbcUrl, dbUser, dbPassword);

            // 3. Salvar metadados no AutonomousDB
            salvarMetadadosNoBanco(jdbcUrl, dbUser, dbPassword, nomeObjeto, bucketName);
            System.out.println("Metadados salvos no AutonomousDB!");

            // Fechar o cliente do Object Storage
            objectStorageClient.close();

        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para carregar o arquivo no Object Storage
    private static PutObjectResponse carregarArquivo(ObjectStorage objectStorageClient, String namespaceName,
                                                     String bucketName, String arquivoClasspath, String nomeObjeto) throws Exception {
        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(arquivoClasspath)) {
            if (inputStream == null) {
                throw new RuntimeException("Arquivo não encontrado no classpath: " + arquivoClasspath);
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(nomeObjeto)
                    .putObjectBody(inputStream)
                    .build();

            return objectStorageClient.putObject(putObjectRequest);
        }
    }

    // Método para criar a tabela no AutonomousDB (com DROP TABLE)
    private static void criarTabelaNoBanco(String jdbcUrl, String user, String password) {
        String dropTableSql = "BEGIN EXECUTE IMMEDIATE 'DROP TABLE FILE_METADATA PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;";
        String createTableSql = "CREATE TABLE FILE_METADATA (" +
                                "file_id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                                "file_name VARCHAR2(255) NOT NULL, " +
                                "bucket_name VARCHAR2(100), " +
                                "upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                ")";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = connection.createStatement()) {

            // Executar DROP TABLE
            stmt.execute(dropTableSql);
            System.out.println("Tabela FILE_METADATA (se existia) foi removida.");

            // Executar CREATE TABLE
            stmt.execute(createTableSql);
            System.out.println("Tabela FILE_METADATA criada com sucesso!");

        } catch (SQLException e) {
            System.err.println("Erro ao criar a tabela: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para salvar os metadados no AutonomousDB
    private static void salvarMetadadosNoBanco(String jdbcUrl, String user, String password, String fileName, String bucketName) {
        String insertSql = "INSERT INTO FILE_METADATA (file_name, bucket_name, upload_date) VALUES (?, ?, SYSDATE)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement stmt = connection.prepareStatement(insertSql)) {

            stmt.setString(1, fileName);
            stmt.setString(2, bucketName);
            stmt.executeUpdate();
            System.out.println("Metadados do arquivo '" + fileName + "' salvos no banco de dados.");

        } catch (SQLException e) {
            System.err.println("Erro ao salvar metadados no banco: " + e.getMessage());
            e.printStackTrace();
        }
    }
}