package test.it;

import server.ws.WsServer;
import test.it.runner.IT_CleanAllDate;
import test.it.runner.IT_RunAllMain;

public class IT_RunAllCleanStartWsMain {

    public static void main(String[] args) {
        runBash("kill -9 $(lsof -t -i:7070) 2>/dev/null || true");

        IT_CleanAllDate.main(new String[0]);

        Thread wsThread = new Thread(() -> {
            try {
                WsServer.main(new String[0]);
            } catch (Throwable t) {
                t.printStackTrace(System.out);
            }
        }, "wsServer-thread");
        wsThread.setDaemon(true);
        wsThread.start();

        sleepMs(1000);

        int failed = IT_RunAllMain.runAll();
        System.exit(failed);
    }

    private static void runBash(String cmd) {
        try {
            Process p = new ProcessBuilder("bash", "-lc", cmd).inheritIO().start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println("WARN: bash command failed: " + e);
        }
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}