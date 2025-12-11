package Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.crypto.Ed25519Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * Полный тестовый сценарий:
 *
 *  1) AddUser  — добавляем пользователя в локальную БД
 *     (loginKey и deviceKey разные).
 *
 *  2) AuthChallenge — запрашиваем одноразовый authNonce
 *     для подписи шаге 2.
 *
 *  3) CreateAuthSession — подтверждаем владение deviceKey,
 *     создаётся сессия, сервер возвращает:
 *       - sessionId (строка, base64-32 байта)
 *       - sessionPwd (секрет сессии, base64-32 байта)
 *
 *  4) Новое подключение:
 *       - отправляем RefreshSession с тем же sessionId,
 *         но заведомо неверным sessionPwd
 *         (ожидаем ОТРИЦАТЕЛЬНЫЙ ответ: status != 200,
 *          code = SESSION_PWD_MISMATCH).
 *
 *  5) Ещё одно новое подключение:
 *       - отправляем RefreshSession с sessionId
 *         и корректным sessionPwd
 *         (ожидаем УСПЕШНЫЙ ответ: status=200,
 *          storagePwd совпадает с тем, что отправляли на шаге 3).
 *
 *     В ЭТОМ ЖЕ подключении:
 *       - вызываем CloseActiveSession для этой sessionId;
 *         ждём 200 (успешное закрытие сессии).
 *
 *  6) Новое подключение:
 *       - снова пытаемся сделать RefreshSession по той же sessionId/sessionPwd;
 *         ожидаем ошибку: status != 200, code = SESSION_NOT_FOUND.
 */
public class Test_AddUser_and_Authorification {

    // Адрес сервера
    private static final String WS_URI = "ws://localhost:7070/ws";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Тестовые данные пользователя
    private static final String TEST_LOGIN = "anya24";
    private static final long TEST_LOGIN_ID = 1030120L;
    private static final long TEST_BCH_ID = 4222L;
    private static final int TEST_BCH_LIMIT = 1_000_000;

    // Краткая строка clientInfo, которую клиент шлёт на шаге CreateAuthSession и RefreshSession
    private static final String TEST_CLIENT_INFO = "JavaTestClient/1.0";

    // --- Тестовые пары ключей ---
    // loginKey — ключ аккаунта (например, "основной")
    // deviceKey — ключ устройства, которым подписываем авторизацию

    private static final byte[] LOGIN_PRIV_KEY;
    private static final String LOGIN_PUBKEY_B64;

    private static final byte[] DEVICE_PRIV_KEY;
    private static final String DEVICE_PUBKEY_B64;

    static {
        // Детерминированное "семя" для логин-ключа
        LOGIN_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-login-11" + TEST_LOGIN);
        byte[] loginPub = Ed25519Util.derivePublicKey(LOGIN_PRIV_KEY);
        LOGIN_PUBKEY_B64 = Ed25519Util.keyToBase64(loginPub);

        // Детерминированное "семя" для девайс-ключа
        DEVICE_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-device-" + TEST_LOGIN);
        byte[] devicePub = Ed25519Util.derivePublicKey(DEVICE_PRIV_KEY);
        DEVICE_PUBKEY_B64 = Ed25519Util.keyToBase64(devicePub);
    }

    // --- Глобальные переменные между сценариями ---

    /** authNonce, выданный на шаге AuthChallenge. */
    private static String GLOBAL_AUTH_NONCE;

    /** sessionId (строка, base64-32 байта), выданный на шаге CreateAuthSession. */
    private static String GLOBAL_SESSION_ID;

    /** sessionPwd (секрет сессии), выданный на шаге CreateAuthSession. */
    private static String GLOBAL_SESSION_PWD;

    /** storagePwd, который мы отправили при CreateAuthSession. */
    private static String GLOBAL_STORAGE_PWD_SENT;

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        // Сценарий 1: регистрация + первичная авторизация
        runScenario_AddUser_And_FirstAuth();

        // Сценарий 2: новое подключение, RefreshSession с неверным sessionPwd
        runScenario_RefreshSession_WrongPwd();

        // Сценарий 3: новое подключение, RefreshSession с корректным sessionPwd + CloseActiveSession
        runScenario_RefreshSession_CorrectPwd_And_Close();

        // Сценарий 4: новое подключение, RefreshSession после закрытия сессии
        runScenario_RefreshSession_AfterClose();

        System.out.println("Все тесты завершены, выходим.");
    }

    // ==========================================================
    //                 SCENARIO 1: AddUser + Auth
    // ==========================================================

    private static void runScenario_AddUser_And_FirstAuth() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 1: AddUser + AuthChallenge + CreateAuthSession ===");

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AddUser, 1 - AuthStep1, 2 - AuthStep2

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S1] WebSocket подключен");
                        webSocket.request(1);
                        sendNextRequest(webSocket);
                        Listener.super.onOpen(webSocket);
                    }

                    private void sendNextRequest(WebSocket webSocket) {
                        switch (step) {
                            case 0 -> {
                                String json = buildAddUserJson();
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 1] Отправляем AddUser:");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            case 1 -> {
                                String json = buildAuthStep1Json();
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 2] Отправляем AuthChallenge:");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            case 2 -> {
                                GLOBAL_STORAGE_PWD_SENT = generateFakeStoragePwd();
                                String json = buildAuthStep2Json(GLOBAL_AUTH_NONCE, GLOBAL_STORAGE_PWD_SENT);
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 3] Отправляем CreateAuthSession (подпись deviceKey):");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            default -> {
                                System.out.println("✅ [S1] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario1 done");
                            }
                        }
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S1] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        int status = extractStatus(message);
                        switch (step) {
                            case 0 -> {
                                // AddUser: ждём status=200
                                if (status == 200) {
                                    printOk("[S1] AddUser", "Пользователь успешно добавлен (status=200)");
                                } else {
                                    String code = extractErrorCode(message);
                                    printFail("[S1] AddUser", "Ожидали status=200, получили status=" + status + ", code=" + code);
                                }
                            }
                            case 1 -> {
                                // AuthChallenge: статус 200 + authNonce
                                String nonce = extractAuthNonce(message);
                                GLOBAL_AUTH_NONCE = nonce;
                                if (status == 200 && nonce != null && !nonce.isBlank()) {
                                    printOk("[S1] AuthChallenge", "status=200, получен authNonce=" + nonce);
                                } else {
                                    String code = extractErrorCode(message);
                                    printFail("[S1] AuthChallenge",
                                            "Ожидали status=200 + непустой authNonce, получили status="
                                                    + status + ", nonce=" + nonce + ", code=" + code);
                                }
                            }
                            case 2 -> {
                                // CreateAuthSession: статус 200 + sessionId & sessionPwd
                                String sid = extractSessionId(message);
                                String spwd = extractSessionPwd(message);
                                GLOBAL_SESSION_ID = sid;
                                GLOBAL_SESSION_PWD = spwd;
                                if (status == 200 && sid != null && !sid.isBlank()
                                        && spwd != null && !spwd.isBlank()) {
                                    printOk("[S1] CreateAuthSession",
                                            "status=200, sessionId и sessionPwd получены");
                                } else {
                                    String code = extractErrorCode(message);
                                    printFail("[S1] CreateAuthSession",
                                            "Ожидали status=200 + непустые sessionId/sessionPwd, получили status="
                                                    + status + ", sid=" + sid + ", code=" + code);
                                }
                            }
                            default -> {
                                // не должно сюда попадать
                            }
                        }

                        step++;
                        sendNextRequest(webSocket);
                        webSocket.request(1);

                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S1] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S1] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 1 завершён ===");
    }

    // ==========================================================
    //         SCENARIO 2: RefreshSession с неправильным паролем
    // ==========================================================

    private static void runScenario_RefreshSession_WrongPwd() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 2: RefreshSession с НЕВЕРНЫМ sessionPwd ===");
        System.out.println("Ожидаем ОТРИЦАТЕЛЬНЫЙ ответ сервера: status != 200, code = SESSION_PWD_MISMATCH");

        if (GLOBAL_SESSION_ID == null || GLOBAL_SESSION_PWD == null) {
            System.out.println("⚠️ Нет sessionId или sessionPwd из сценария 1, пропускаем сценарий 2.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        // Специально подменяем пароль, чтобы сервер его НЕ принял
        String wrongPwd = GLOBAL_SESSION_PWD + "_WRONG";

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S2] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(GLOBAL_SESSION_ID, wrongPwd, "test-refresh-wrong-1");
                        System.out.println();
                        System.out.println("📤 [S2] Отправляем RefreshSession с НЕВЕРНЫМ sessionPwd:");
                        System.out.println(json);
                        webSocket.sendText(json, true);
                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S2] Ответ сервера (ожидаем ошибку):");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        int status = extractStatus(message);
                        String code = extractErrorCode(message);

                        if (status != 200 && "SESSION_PWD_MISMATCH".equals(code)) {
                            printOk("[S2] RefreshSession (wrong pwd)",
                                    "Получена ожидаемая ошибка: status=" + status + ", code=" + code);
                        } else {
                            printFail("[S2] RefreshSession (wrong pwd)",
                                    "Ожидали status!=200 + code=SESSION_PWD_MISMATCH, получили status="
                                            + status + ", code=" + code);
                        }

                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario2 done");
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S2] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S2] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 2 завершён ===");
    }

    // ==========================================================
    //   SCENARIO 3: RefreshSession OK + CloseActiveSession
    // ==========================================================

    private static void runScenario_RefreshSession_CorrectPwd_And_Close() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 3: RefreshSession с КОРРЕКТНЫМ sessionPwd + CloseActiveSession ===");
        System.out.println("1) Ожидаем: status=200 и корректный storagePwd");
        System.out.println("2) Затем в этом же подключении вызываем CloseActiveSession для той же sessionId и ждём status=200.");

        if (GLOBAL_SESSION_ID == null || GLOBAL_SESSION_PWD == null || GLOBAL_STORAGE_PWD_SENT == null) {
            System.out.println("⚠️ Нет необходимых данных из сценария 1, пропускаем сценарий 3.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - RefreshSession OK, 1 - CloseActiveSession

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S3] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(GLOBAL_SESSION_ID, GLOBAL_SESSION_PWD, "test-refresh-ok-1");
                        System.out.println();
                        System.out.println("📤 [S3 / Шаг 1] Отправляем RefreshSession с КОРРЕКТНЫМ sessionPwd:");
                        System.out.println(json);
                        webSocket.sendText(json, true);
                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S3] Ответ сервера (step=" + step + "):");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        if (step == 0) {
                            // Ответ на RefreshSession
                            int status = extractStatus(message);
                            String storagePwdFromServer = extractStoragePwd(message);

                            if (status == 200 && GLOBAL_STORAGE_PWD_SENT.equals(storagePwdFromServer)) {
                                printOk("[S3] RefreshSession (correct pwd)",
                                        "status=200, storagePwd совпадает с отправленным ранее");
                            } else {
                                String code = extractErrorCode(message);
                                printFail("[S3] RefreshSession (correct pwd)",
                                        "Ожидали status=200 + storagePwd="
                                                + GLOBAL_STORAGE_PWD_SENT
                                                + ", получили status=" + status
                                                + ", storagePwd=" + storagePwdFromServer
                                                + ", code=" + code);
                            }

                            // Теперь отправляем CloseActiveSession для этой же sessionId
                            String closeJson = buildCloseActiveSessionJson(GLOBAL_SESSION_ID, "test-close-1");
                            System.out.println();
                            System.out.println("📤 [S3 / Шаг 2] Отправляем CloseActiveSession для sessionId=" + GLOBAL_SESSION_ID);
                            System.out.println(closeJson);
                            webSocket.sendText(closeJson, true);
                            step = 1;
                        } else if (step == 1) {
                            // Ответ на CloseActiveSession
                            int status = extractStatus(message);
                            String code = extractErrorCode(message);

                            if (status == 200) {
                                printOk("[S3] CloseActiveSession",
                                        "status=200, сессия закрыта (запись в БД удалена, другие подключения при наличии закрыты)");
                            } else {
                                printFail("[S3] CloseActiveSession",
                                        "Ожидали status=200, получили status=" + status + ", code=" + code);
                            }

                            // Сервер может сам закрыть WebSocket, но мы тоже корректно закрываем
                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario3 done");
                        }

                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S3] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S3] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 3 завершён ===");
    }

    // ==========================================================
    //   SCENARIO 4: RefreshSession после закрытия сессии
    // ==========================================================

    private static void runScenario_RefreshSession_AfterClose() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 4: RefreshSession после CloseActiveSession ===");
        System.out.println("Ожидаем: status != 200, code = SESSION_NOT_FOUND");

        if (GLOBAL_SESSION_ID == null || GLOBAL_SESSION_PWD == null) {
            System.out.println("⚠️ Нет sessionId или sessionPwd, пропускаем сценарий 4.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S4] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(GLOBAL_SESSION_ID, GLOBAL_SESSION_PWD, "test-refresh-after-close-1");
                        System.out.println();
                        System.out.println("📤 [S4] Отправляем RefreshSession ПОСЛЕ закрытия сессии:");
                        System.out.println(json);
                        webSocket.sendText(json, true);
                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S4] Ответ сервера:");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        int status = extractStatus(message);
                        String code = extractErrorCode(message);

                        if (status != 200 && "SESSION_NOT_FOUND".equals(code)) {
                            printOk("[S4] RefreshSession after Close",
                                    "Получена ожидаемая ошибка: status=" + status + ", code=" + code);
                        } else {
                            printFail("[S4] RefreshSession after Close",
                                    "Ожидали status!=200 + code=SESSION_NOT_FOUND, получили status="
                                            + status + ", code=" + code);
                        }

                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario4 done");
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S4] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S4] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 4 завершён ===");
    }

    // ==========================================================
    //                     JSON BUILDERS
    // ==========================================================

    // 1) AddUser с payload (loginKey != deviceKey)
    private static String buildAddUserJson() {
        return """
                {
                  "op": "AddUser",
                  "requestId": "test-add-1",
                  "payload": {
                    "login": "%s",
                    "loginId": %d,
                    "bchId": %d,
                    "loginKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                TEST_LOGIN,
                TEST_LOGIN_ID,
                TEST_BCH_ID,
                LOGIN_PUBKEY_B64,    // loginKey
                DEVICE_PUBKEY_B64,   // deviceKey
                TEST_BCH_LIMIT
        );
    }

    // 2) Шаг 1 авторизации: запрос authNonce
    private static String buildAuthStep1Json() {
        return """
                {
                  "op": "AuthChallenge",
                  "requestId": "test-auth-1",
                  "payload": {
                    "login": "%s"
                  }
                }
                """.formatted(TEST_LOGIN);
    }

    /**
     * 3) Шаг 2 авторизации: подтверждение подписью.
     *
     * @param authNonce  одноразовый nonce с шага 1
     * @param storagePwd клиентский storagePwd
     */
    private static String buildAuthStep2Json(String authNonce, String storagePwd) {
        if (authNonce == null) {
            authNonce = "";
        }
        if (storagePwd == null || storagePwd.isBlank()) {
            storagePwd = generateFakeStoragePwd();
        }

        long timeMs = System.currentTimeMillis();

        // preimage = "AUTHORIFICATED:" + timeMs + authNonce
        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        // Подписываем приватным ключом устройства (deviceKey)
        byte[] sig = Ed25519Util.sign(preimage, DEVICE_PRIV_KEY);
        String sigB64 = Base64.getEncoder().encodeToString(sig);

        return """
                {
                  "op": "CreateAuthSession",
                  "requestId": "test-auth-2",
                  "payload": {
                    "storagePwd": "%s",
                    "timeMs": %d,
                    "signatureB64": "%s",
                    "clientInfo": "%s"
                  }
                }
                """.formatted(
                storagePwd,
                timeMs,
                sigB64,
                TEST_CLIENT_INFO
        );
    }

    // 4) RefreshSession: всё в payload
    private static String buildRefreshSessionJson(String sessionId, String sessionPwd, String requestId) {
        return """
            {
              "op": "RefreshSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "sessionPwd": "%s",
                "clientInfo": "%s"
              }
            }
            """.formatted(
                requestId,
                sessionId,
                sessionPwd,
                TEST_CLIENT_INFO
        );
    }

    // 5) CloseActiveSession: можно передать sessionId, timeMs и signatureB64
    // В нашем случае уже есть авторизованная сессия, поэтому timeMs и signatureB64
    // можно задать нулями/пустыми — сервер их игнорирует в AUTH_STATUS_USER.
    private static String buildCloseActiveSessionJson(String sessionId, String requestId) {
        return """
            {
              "op": "CloseActiveSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "timeMs": 0,
                "signatureB64": ""
              }
            }
            """.formatted(
                requestId,
                sessionId
        );
    }

    // просто для теста: base64 от 32 байт "storage" ключа
    private static String generateFakeStoragePwd() {
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(data);
    }

    // ==========================================================
    //                     JSON HELPERS
    // ==========================================================

    private static String extractAuthNonce(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("authNonce")) {
                return payload.get("authNonce").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить authNonce из ответа: " + e.getMessage());
        }
        return null;
    }

    private static String extractSessionPwd(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionPwd")) {
                return payload.get("sessionPwd").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить sessionPwd из ответа: " + e.getMessage());
        }
        return null;
    }

    private static String extractSessionId(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionId")) {
                return payload.get("sessionId").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить sessionId из ответа: " + e.getMessage());
        }
        return null;
    }

    private static String extractStoragePwd(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("storagePwd")) {
                return payload.get("storagePwd").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить storagePwd из ответа: " + e.getMessage());
        }
        return null;
    }

    private static int extractStatus(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            if (root.has("status")) {
                return root.get("status").asInt();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить status из ответа: " + e.getMessage());
        }
        return -1;
    }

    private static String extractErrorCode(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("code") && !payload.get("code").isNull()) {
                return payload.get("code").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить code из ответа: " + e.getMessage());
        }
        return null;
    }

    // ==========================================================
    //                     PRINT HELPERS
    // ==========================================================

    private static void printOk(String testName, String details) {
        System.out.println("✅ " + testName + " — " + details);
    }

    private static void printFail(String testName, String details) {
        System.out.println("❌ " + testName + " — " + details);
    }
}