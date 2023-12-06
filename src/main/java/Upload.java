import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;

public final class Upload {
    private static final HttpClient HTTP_CLIENT;
    private static final ObjectMapper MAPPER;
    private static final X509ExtendedTrustManager TRUST_MANAGER;

    static {
        MAPPER = new ObjectMapper();
        TRUST_MANAGER = new X509ExtendedTrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }
        };

        try {
            final var sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{TRUST_MANAGER}, new SecureRandom());

            HTTP_CLIENT = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception ignored) {
            throw new RuntimeException();
        }

    }

    @SneakyThrows
    private static Map<String, String> readFile(
            @NonNull final Path path) {

        return Map.of(
                "name", path.toString(),
                "content", Files.readString(path)
        );
    }

    @SneakyThrows
    public static void main(String[] args) {
        @Cleanup final var sc = new Scanner(System.in);
        @Cleanup final var paths = Files.find(Paths.get("src/main/java"),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> fileAttr.isRegularFile());
        final var payload = paths
                .filter(path -> !path.endsWith("Upload.java"))
                .map(Upload::readFile)
                .toList();

        System.out.println("\nThe following files will be uploaded:");
        payload.stream()
                .map(entry -> String.format("* %s", entry.get("name")))
                .forEach(System.out::println);

        System.out.println("\nPlease enter your token:");
        final var sessionId = sc.nextLine();

        final var response = MAPPER.readTree(HTTP_CLIENT.send(
                        HttpRequest.newBuilder()
                                .uri(new URI("https://codexe.inf.unideb.hu/offline-exam/api/exam/" + sessionId + "/file"))
                                .header("Content-type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString((MAPPER.writeValueAsString(payload))))
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body());

        try {
            final var buffer = new StringJoiner("\n")
                    .add("Successfully uploaded the files on behalf of the following student:")
                    .add("* ID: " + response.get("studentId").asText())
                    .add("* Neptun: " + response.get("studentNeptun").asText())
                    .add("* Name: " + response.get("studentName").asText())
                    .add("* IP: " + response.get("studentIp").asText())
                    .add("* token: " + response.get("sessionId").asText());
            System.out.println(buffer);
        } catch (Exception e) {
            System.err.println("Could not upload the solutions. Check the availability of the exam.");
        }
    }
}