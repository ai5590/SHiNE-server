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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * Большой сценарий тестирования авторизации и работы с сессиями:
 *
 *  1) AddUser — создаём пользователя в локальной БД.
 *
 *  2) Сессия 1:
 *     - AuthChallenge + CreateAuthSession → первая сессия (SESSION1_ID/SESSION1_PWD).
 *
 *  3) Сессия 2:
 *     - AuthChallenge + CreateAuthSession → вторая сессия (SESSION2_ID/SESSION2_PWD).
 *     - ListSessions (внутри второй сессии, AUTH_STATUS_USER).
 *
 *  4) Новое подключение:
 *     - AuthChallenge → AUTH_IN_PROGRESS.
 *     - ListSessions c timeMs + signatureB64 (подпись по authNonce).
 *
 *  5) Новое подключение:
 *     - RefreshSession по первой сессии.
 *     - CloseActiveSession по второй сессии (закрываем SESSION2_ID).
 *
 *  6) Новое подключение:
 *     - AuthChallenge → AUTH_IN_PROGRESS.
 *     - ListSessions (ожидаем, что вторая сессия исчезла, осталась только первая).
 *
 *  7) Новое подключение:
 *     - AuthChallenge → AUTH_IN_PROGRESS.
 *     - CloseActiveSession по первой сессии (SESSION1_ID) без Refresh.
 *
 *  8) Новое подключение:
 *     - AuthChallenge → AUTH_IN_PROGRESS.
 *     - ListSessions (ожидаем пустой список сессий).
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

    // Краткая строка clientInfo, которую клиент шлёт
    private static final String TEST_CLIENT_INFO = "JavaTestClient/1.0";

    // --- Тестовые пары ключей ---
    // loginKey — ключ аккаунта (например, "основной")
    // deviceKey — ключ устройства, которым подписываем авторизацию / управление сессиями

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

    /** Первая сессия (создана в сценарии 1). */
    private static String SESSION1_ID;
    private static String SESSION1_PWD;
    private static String SESSION1_STORAGE_PWD;

    /** Вторая сессия (создана в сценарии 2). */
    private static String SESSION2_ID;
    private static String SESSION2_PWD;
    private static String SESSION2_STORAGE_PWD;

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        scenario1_AddUser_And_CreateFirstSession();

        scenario2_CreateSecondSession_And_ListInside();

        scenario3_ListSessions_AuthInProgress("S3: ListSessions (AUTH_IN_PROGRESS, две сессии ожидаются)", true, true);

        scenario4_RefreshFirstSession_And_CloseSecond();

        scenario5_ListSessions_AuthInProgress_AfterClosingSecond();

        scenario6_CloseFirstSession_AuthInProgress();

        scenario7_ListSessions_AuthInProgress_NoSessions();

        System.out.println("\n\nВсе сценарии завершены, выходим.");
    }

    // ==========================================================
    //                        SCENARIO 1
    // ==========================================================

    private static void scenario1_AddUser_And_CreateFirstSession() throws Exception {
        printSection("СЦЕНАРИЙ 1: AddUser + AuthChallenge + CreateAuthSession (первая сессия)");

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AddUser, 1 - AuthChallenge, 2 - CreateAuthSession
                    private String authNonceLocal;

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S1] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildAddUserJson();
                        System.out.println("\n📤 [S1 / Шаг 1] Отправляем AddUser:");
                        System.out.println(json);
                        webSocket.sendText(json, true);

                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("\n📥 [S1] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        try {
                            if (step == 0) {
                                // Ответ на AddUser
                                int status = extractStatus(message);
                                boolean ok = (status == 200);
                                printTestResult(
                                        "S1/AddUser",
                                        ok,
                                        "status=" + status + (ok ? " (пользователь создан/добавлен)" : " (ожидали 200)")
                                );

                                // Переходим к AuthChallenge
                                step = 1;
                                String json = buildAuthStep1Json();
                                System.out.println("\n📤 [S1 / Шаг 2] Отправляем AuthChallenge:");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 1) {
                                // Ответ на AuthChallenge
                                int status = extractStatus(message);
                                String nonce = extractAuthNonce(message);
                                boolean ok = (status == 200 && nonce != null && !nonce.isBlank());
                                printTestResult(
                                        "S1/AuthChallenge",
                                        ok,
                                        "status=" + status + ", authNonce=" + nonce
                                );

                                authNonceLocal = nonce;

                                // Переходим к CreateAuthSession
                                step = 2;
                                SESSION1_STORAGE_PWD = generateFakeStoragePwd();
                                String json = buildAuthStep2Json(authNonceLocal, SESSION1_STORAGE_PWD);
                                System.out.println("\n📤 [S1 / Шаг 3] Отправляем CreateAuthSession (первая сессия):");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 2) {
                                // Ответ на CreateAuthSession — здесь мы получаем SESSION1_ID / SESSION1_PWD
                                int status = extractStatus(message);
                                String sessionId = extractSessionId(message);
                                String sessionPwd = extractSessionPwd(message);

                                boolean ok = (status == 200
                                        && sessionId != null && !sessionId.isBlank()
                                        && sessionPwd != null && !sessionPwd.isBlank());

                                SESSION1_ID = sessionId;
                                SESSION1_PWD = sessionPwd;

                                printTestResult(
                                        "S1/CreateAuthSession (первая сессия)",
                                        ok,
                                        "status=" + status +
                                                ", sessionId=" + sessionId +
                                                ", sessionPwd=" + (sessionPwd != null ? "[получен]" : "null")
                                );

                                System.out.println("🆔 [S1] SESSION1_ID=" + SESSION1_ID);
                                System.out.println("🔐 [S1] SESSION1_PWD=" + SESSION1_PWD);

                                step = 3;
                                System.out.println("✅ [S1] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario1 done");
                            }

                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }

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
    }

    // ==========================================================
    //                        SCENARIO 2
    // ==========================================================

    private static void scenario2_CreateSecondSession_And_ListInside() throws Exception {
        printSection("СЦЕНАРИЙ 2: Создать вторую сессию и внутри неё вызвать ListSessions");

        if (SESSION1_ID == null || SESSION1_PWD == null) {
            System.out.println("⚠️ [S2] Первая сессия не создана, пропускаем сценарий 2.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AuthChallenge, 1 - CreateAuthSession(вторая), 2 - ListSessions
                    private String authNonceLocal;

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S2] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildAuthStep1Json();
                        System.out.println("\n📤 [S2 / Шаг 1] Отправляем AuthChallenge:");
                        System.out.println(json);
                        webSocket.sendText(json, true);

                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("\n📥 [S2] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        try {
                            if (step == 0) {
                                int status = extractStatus(message);
                                String nonce = extractAuthNonce(message);
                                boolean ok = (status == 200 && nonce != null && !nonce.isBlank());
                                printTestResult(
                                        "S2/AuthChallenge",
                                        ok,
                                        "status=" + status + ", authNonce=" + nonce
                                );
                                authNonceLocal = nonce;

                                step = 1;
                                SESSION2_STORAGE_PWD = generateFakeStoragePwd();
                                String json = buildAuthStep2Json(authNonceLocal, SESSION2_STORAGE_PWD);
                                System.out.println("\n📤 [S2 / Шаг 2] Отправляем CreateAuthSession (вторая сессия):");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 1) {
                                int status = extractStatus(message);
                                String sessionId = extractSessionId(message);
                                String sessionPwd = extractSessionPwd(message);

                                boolean ok = (status == 200
                                        && sessionId != null && !sessionId.isBlank()
                                        && sessionPwd != null && !sessionPwd.isBlank());

                                SESSION2_ID = sessionId;
                                SESSION2_PWD = sessionPwd;

                                printTestResult(
                                        "S2/CreateAuthSession (вторая сессия)",
                                        ok,
                                        "status=" + status +
                                                ", sessionId=" + sessionId +
                                                ", sessionPwd=" + (sessionPwd != null ? "[получен]" : "null")
                                );

                                System.out.println("🆔 [S2] SESSION2_ID=" + SESSION2_ID);
                                System.out.println("🔐 [S2] SESSION2_PWD=" + SESSION2_PWD);

                                // Теперь вызываем ListSessions внутри второй сессии (AUTH_STATUS_USER)
                                step = 2;
                                String json = buildListSessionsJson(0L, "", "test-list-in-session2");
                                System.out.println("\n📤 [S2 / Шаг 3] Отправляем ListSessions (внутри второй сессии):");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 2) {
                                int status = extractStatus(message);
                                List<String> sessionIds = extractSessionIds(message);

                                boolean has1 = sessionIds.contains(SESSION1_ID);
                                boolean has2 = sessionIds.contains(SESSION2_ID);

                                boolean ok = (status == 200 && has1 && has2);

                                printTestResult(
                                        "S2/ListSessions (ожидаем 1 и 2 сессии)",
                                        ok,
                                        "status=" + status +
                                                ", sessions=" + sessionIds +
                                                ", contains SESSION1=" + has1 +
                                                ", contains SESSION2=" + has2
                                );

                                step = 3;
                                System.out.println("✅ [S2] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario2 done");
                            }

                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }

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
    }

    // ==========================================================
    //                SCENARIO 3 / 5 / 7: ListSessions
    // ==========================================================

    /**
     * Общий сценарий: AuthChallenge → ListSessions в статусе AUTH_IN_PROGRESS.
     *
     * @param title                  заголовок для вывода
     * @param expectSession1Present  ожидать ли первую сессию в списке
     * @param expectSession2Present  ожидать ли вторую сессию в списке
     */
    private static void scenario3_ListSessions_AuthInProgress(
            String title,
            boolean expectSession1Present,
            boolean expectSession2Present
    ) throws Exception {

        printSection(title);

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AuthChallenge, 1 - ListSessions (AUTH_IN_PROGRESS)
                    private String authNonceLocal;

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S-List] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildAuthStep1Json();
                        System.out.println("\n📤 [S-List / Шаг 1] Отправляем AuthChallenge:");
                        System.out.println(json);
                        webSocket.sendText(json, true);

                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("\n📥 [S-List] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        try {
                            if (step == 0) {
                                int status = extractStatus(message);
                                String nonce = extractAuthNonce(message);
                                boolean ok = (status == 200 && nonce != null && !nonce.isBlank());
                                printTestResult(
                                        "S-List/AuthChallenge",
                                        ok,
                                        "status=" + status + ", authNonce=" + nonce
                                );
                                authNonceLocal = nonce;

                                // Теперь в статусе AUTH_IN_PROGRESS вызываем ListSessions
                                long timeMs = System.currentTimeMillis();
                                String sig = signAuthorificated(authNonceLocal, timeMs);

                                step = 1;
                                String json = buildListSessionsJson(timeMs, sig, "test-list-auth-in-progress");
                                System.out.println("\n📤 [S-List / Шаг 2] Отправляем ListSessions (AUTH_IN_PROGRESS):");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 1) {
                                int status = extractStatus(message);
                                List<String> sessionIds = extractSessionIds(message);

                                boolean has1 = (SESSION1_ID != null && sessionIds.contains(SESSION1_ID));
                                boolean has2 = (SESSION2_ID != null && sessionIds.contains(SESSION2_ID));

                                boolean ok =
                                        status == 200
                                        && (expectSession1Present == has1)
                                        && (expectSession2Present == has2);

                                printTestResult(
                                        "S-List/ListSessions (ожидаемые сессии)",
                                        ok,
                                        "status=" + status +
                                                ", sessions=" + sessionIds +
                                                ", expect1=" + expectSession1Present + ", has1=" + has1 +
                                                ", expect2=" + expectSession2Present + ", has2=" + has2
                                );

                                step = 2;
                                System.out.println("✅ [S-List] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario-list done");
                            }

                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }

                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S-List] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S-List] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
    }

    private static void scenario5_ListSessions_AuthInProgress_AfterClosingSecond() throws Exception {
        scenario3_ListSessions_AuthInProgress(
                "СЦЕНАРИЙ 5: ListSessions (AUTH_IN_PROGRESS) после закрытия второй сессии — должна остаться только первая",
                true,
                false
        );
    }

    private static void scenario7_ListSessions_AuthInProgress_NoSessions() throws Exception {
        scenario3_ListSessions_AuthInProgress(
                "СЦЕНАРИЙ 7: ListSessions (AUTH_IN_PROGRESS) после закрытия обеих сессий — ожидаем пустой список",
                false,
                false
        );
    }

    // ==========================================================
    //                        SCENARIO 4
    // ==========================================================

    private static void scenario4_RefreshFirstSession_And_CloseSecond() throws Exception {
        printSection("СЦЕНАРИЙ 4: Refresh первой сессии и Close второй сессии (из первой)");

        if (SESSION1_ID == null || SESSION1_PWD == null || SESSION2_ID == null) {
            System.out.println("⚠️ [S4] Нет нужных сессий (SESSION1/SESSION2), пропускаем сценарий 4.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - Refresh(1), 1 - Close(2)

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S4] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(SESSION1_ID, SESSION1_PWD, "test-refresh-session1");
                        System.out.println("\n📤 [S4 / Шаг 1] Отправляем RefreshSession для SESSION1:");
                        System.out.println(json);
                        webSocket.sendText(json, true);

                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("\n📥 [S4] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        try {
                            if (step == 0) {
                                int status = extractStatus(message);
                                String storagePwd = extractStoragePwd(message);
                                boolean ok = (status == 200 && storagePwd != null);
                                printTestResult(
                                        "S4/RefreshSession (SESSION1)",
                                        ok,
                                        "status=" + status + ", storagePwd=" + (storagePwd != null ? "[получен]" : "null")
                                );

                                // Теперь, находясь внутри первой сессии (AUTH_STATUS_USER),
                                // закрываем вторую сессию
                                step = 1;
                                String json = buildCloseSessionJson(
                                        SESSION2_ID,
                                        0L,
                                        "",
                                        "test-close-session2-from-session1"
                                );
                                System.out.println("\n📤 [S4 / Шаг 2] Отправляем CloseActiveSession для SESSION2:");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 1) {
                                int status = extractStatus(message);
                                boolean ok = (status == 200);
                                printTestResult(
                                        "S4/CloseActiveSession (SESSION2)",
                                        ok,
                                        "status=" + status + " (ожидали 200)"
                                );

                                step = 2;
                                System.out.println("✅ [S4] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario4 done");
                            }
                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }

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
    }

    // ==========================================================
    //                        SCENARIO 6
    // ==========================================================

    private static void scenario6_CloseFirstSession_AuthInProgress() throws Exception {
        printSection("СЦЕНАРИЙ 6: Close первой сессии (SESSION1) в статусе AUTH_IN_PROGRESS без Refresh");

        if (SESSION1_ID == null) {
            System.out.println("⚠️ [S6] Первая сессия не создана, пропускаем сценарий 6.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AuthChallenge, 1 - CloseActiveSession(SESSION1)
                    private String authNonceLocal;

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S6] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildAuthStep1Json();
                        System.out.println("\n📤 [S6 / Шаг 1] Отправляем AuthChallenge:");
                        System.out.println(json);
                        webSocket.sendText(json, true);

                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("\n📥 [S6] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        try {
                            if (step == 0) {
                                int status = extractStatus(message);
                                String nonce = extractAuthNonce(message);
                                boolean ok = (status == 200 && nonce != null && !nonce.isBlank());
                                printTestResult(
                                        "S6/AuthChallenge",
                                        ok,
                                        "status=" + status + ", authNonce=" + nonce
                                );
                                authNonceLocal = nonce;

                                // Теперь в AUTH_IN_PROGRESS закрываем первую сессию
                                long timeMs = System.currentTimeMillis();
                                String sig = signAuthorificated(authNonceLocal, timeMs);

                                step = 1;
                                String json = buildCloseSessionJson(
                                        SESSION1_ID,
                                        timeMs,
                                        sig,
                                        "test-close-session1-auth-in-progress"
                                );
                                System.out.println("\n📤 [S6 / Шаг 2] Отправляем CloseActiveSession для SESSION1 (AUTH_IN_PROGRESS):");
                                System.out.println(json);
                                webSocket.sendText(json, true);

                            } else if (step == 1) {
                                int status = extractStatus(message);
                                boolean ok = (status == 200);
                                printTestResult(
                                        "S6/CloseActiveSession (SESSION1, AUTH_IN_PROGRESS)",
                                        ok,
                                        "status=" + status + " (ожидали 200)"
                                );

                                step = 2;
                                System.out.println("✅ [S6] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario6 done");
                            }
                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }

                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S6] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S6] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
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
        String sigB64 = signAuthorificated(authNonce, timeMs);

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

    // 5) ListSessions
    private static String buildListSessionsJson(long timeMs, String signatureB64, String requestId) {
        if (signatureB64 == null) {
            signatureB64 = "";
        }
        return """
            {
              "op": "ListSessions",
              "requestId": "%s",
              "payload": {
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(
                requestId,
                timeMs,
                signatureB64
        );
    }

    // 6) CloseActiveSession
    private static String buildCloseSessionJson(String sessionId,
                                                long timeMs,
                                                String signatureB64,
                                                String requestId) {
        if (signatureB64 == null) {
            signatureB64 = "";
        }
        return """
            {
              "op": "CloseActiveSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(
                requestId,
                sessionId,
                timeMs,
                signatureB64
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

    /**
     * Подписывает строку "AUTHORIFICATED:" + timeMs + authNonce приватным ключом устройства.
     */
    private static String signAuthorificated(String authNonce, long timeMs) {
        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        byte[] sig = Ed25519Util.sign(preimage, DEVICE_PRIV_KEY);
        return Base64.getEncoder().encodeToString(sig);
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

    private static List<String> extractSessionIds(String json) {
        List<String> result = new ArrayList<>();
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                return result;
            }
            JsonNode sessionsNode = payload.get("sessions");
            if (sessionsNode == null || !sessionsNode.isArray()) {
                return result;
            }
            for (JsonNode s : sessionsNode) {
                JsonNode idNode = s.get("sessionId");
                if (idNode != null && !idNode.isNull()) {
                    result.add(idNode.asText());
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить список sessions из ответа: " + e.getMessage());
        }
        return result;
    }

    // ==========================================================
    //                     OUTPUT HELPERS
    // ==========================================================

    private static void printSection(String title) {
        System.out.println("\n\n==================================================");
        System.out.println(title);
        System.out.println("==================================================\n");
    }

    private static void printTestResult(String name, boolean ok, String details) {
        if (ok) {
            System.out.println("✅ " + name + " — " + details);
        } else {
            System.out.println("❌ " + name + " — " + details);
        }
    }
}