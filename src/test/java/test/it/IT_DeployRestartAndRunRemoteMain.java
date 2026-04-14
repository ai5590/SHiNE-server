package test.it;

import test.it.runner.IT_RunAllMain;

import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Objects;

public class IT_DeployRestartAndRunRemoteMain {

    // ====== НАСТРОЙКИ (можно переопределять systemProperty) ======
    private static final String REMOTE_HOST = System.getProperty("it.remoteHost", "194.87.0.247");
    private static final String REMOTE_USER = System.getProperty("it.remoteUser", "user");

    private static final String REMOTE_DIR  = System.getProperty("it.remoteDir", "/home/user/docker/shine-server");
    private static final String REMOTE_JAR  = REMOTE_DIR + "/shine-server.jar";
    private static final String REMOTE_DATA = System.getProperty("it.remoteDataDir", REMOTE_DIR + "/data");

    private static final String SERVICE_NAME = System.getProperty("it.service", "shine-server");

    private static final String LOCAL_JAR = System.getProperty("it.localJar", "build/libs/shine-server.jar");

    // URI для IT-тестов (переключаем на сервер)
    private static final String WS_URI_SERVER = System.getProperty("it.wsUri", "wss://shineup.me/ws");

    public static void main(String[] args) {

        // 0) Build shadowJar локально
//        shStrict("./gradlew -q shadowJar");

        // 1) stop service на сервере
        sshStrict("sudo systemctl stop " + SERVICE_NAME + " || true");

        // 2) upload jar -> .new
        validateLocalFatJarOrThrow(LOCAL_JAR);
        scpStrict(LOCAL_JAR, REMOTE_JAR + ".new");
        verifyRemoteNewJarOrThrow(REMOTE_JAR + ".new");

        // 3) заменить jar атомарно
        sshStrict("mv -f " + q(REMOTE_JAR + ".new") + " " + q(REMOTE_JAR));

        // 4) удалить data/*
        // (на всякий случай: если папки нет — создать)
        sshStrict("mkdir -p " + q(REMOTE_DATA) + " && rm -rf " + q(REMOTE_DATA) + "/*");

        // 5) start service
        sshStrict("sudo systemctl start " + SERVICE_NAME);

        // 6) дождаться поднятия (простая проверка: порт слушается)
        waitRemotePort7070();

        // 7) переключаем IT на серверный WS URI (без правок исходников)
        System.setProperty("it.wsUri", WS_URI_SERVER);

        // 8) прогон тестов
        int failed = IT_RunAllMain.runAll();
        System.exit(failed);
    }

    private static void waitRemotePort7070() {
        for (int i = 0; i < 50; i++) {
            int code = ssh("ss -ltnp | grep -q ':7070'"); // 0 если найдено
            if (code == 0) return;
            sleepMs(200);
        }
        throw new RuntimeException("Remote port 7070 did not start in time on " + REMOTE_HOST);
    }

    // ---------- helpers ----------
    private static void shStrict(String cmd) {
        int code = sh(cmd);
        if (code != 0) throw new RuntimeException("Command failed (" + code + "): " + cmd);
    }

    private static void sshStrict(String remoteCmd) {
        int code = ssh(remoteCmd);
        if (code != 0) throw new RuntimeException("SSH command failed (" + code + "): " + remoteCmd);
    }

    private static int ssh(String remoteCmd) {
        String cmd = "ssh " + REMOTE_USER + "@" + REMOTE_HOST + " " + q("bash -lc " + q(remoteCmd));
        return sh(cmd);
    }

    private static void scpStrict(String local, String remote) {
        Objects.requireNonNull(local);
        Objects.requireNonNull(remote);
        int code = sh("scp -p " + q(local) + " " + REMOTE_USER + "@" + REMOTE_HOST + ":" + q(remote));
        if (code != 0) throw new RuntimeException("SCP failed (" + code + ")");
    }

    private static int sh(String cmd) {
        try {
            Process p = new ProcessBuilder("bash", "-lc", cmd).inheritIO().start();
            return p.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Command error: " + cmd, e);
        }
    }

    private static String q(String s) {
        // простая одинарная кавычка для bash
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void validateLocalFatJarOrThrow(String localJarPath) {
        File jar = new File(localJarPath);
        if (!jar.isFile()) {
            throw new RuntimeException("Local jar not found: " + localJarPath);
        }
        long size = jar.length();
        // В нашем проекте fat-jar обычно ~30+ MB. Маленький (<10 MB) — почти точно не fat-jar.
        if (size < 10L * 1024L * 1024L) {
            throw new RuntimeException("Local jar is too small for fat-jar: " + size + " bytes (" + localJarPath + ")");
        }
        try (JarFile jf = new JarFile(jar)) {
            boolean hasJetty = false;
            boolean hasBc = false;
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!hasJetty && "org/eclipse/jetty/server/Handler.class".equals(name)) hasJetty = true;
                if (!hasBc && "org/bouncycastle/jce/provider/BouncyCastleProvider.class".equals(name)) hasBc = true;
                if (hasJetty && hasBc) break;
            }
            if (!hasJetty || !hasBc) {
                throw new RuntimeException(
                        "Local jar doesn't look like fat-jar (missing deps). hasJetty=" + hasJetty + ", hasBC=" + hasBc
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to inspect local jar: " + localJarPath, e);
        }
    }

    private static void verifyRemoteNewJarOrThrow(String remoteJarNewPath) {
        // Проверка на сервере до mv: файл существует и не подозрительно маленький.
        String cmd = "test -f " + q(remoteJarNewPath) + " && " +
                "sz=$(stat -c %s " + q(remoteJarNewPath) + ") && " +
                "echo remote_new_size=$sz && test \"$sz\" -ge 10485760";
        int code = ssh(cmd);
        if (code != 0) {
            throw new RuntimeException("Remote uploaded jar is missing or too small: " + remoteJarNewPath);
        }
    }
}
