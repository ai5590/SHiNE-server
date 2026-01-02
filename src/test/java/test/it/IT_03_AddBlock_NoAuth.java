package test.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.it.utils.ItRunContext;
import test.it.utils.TestConfig;
import test.it.utils.TestLog;
import test.it.addBlockUtils.AddBlockFlow;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * Интеграционный тест добавления блоков в персональный блокчейн без отдельной авторизации.
 *
 * Сценарий (как ты попросил):
 *  1) AddBlock: HEADER   (global=0, line=0, lineNum=0, prevGlobalHash=ZERO64) -> 200
 *  2) AddBlock: TEXT#1   (global=1, line=1, lineNum=1, prevGlobalHash=hash(0)) -> 200
 *  3) AddBlock: TEXT#2   (global=2, line=1, lineNum=2, prevGlobalHash=hash(1)) -> 200
 *  4) AddBlock: TEXT#3   (global=3, line=1, lineNum=3, prevGlobalHash=hash(2)) -> 200
 *  5) AddBlock: REACT#1  (global=4, line=2, lineNum=1, prevGlobalHash=hash(3)) -> 200
 *     - реакция на TEXT#1 (toBchName, toGlobal=1, toHash=hash(TEXT#1))
 *
 * Важно по линиям (твоя договорённость):
 *  - line 0: нулевой блок (HEADER) один на весь блокчейн (глобальный 0)
 *  - line 1 и line 2: первый блок каждой линии ссылается prevLineHash на hash(нулевого блока)
 *
 * В этом тесте состояние ведёт AddBlockFlow:
 *  - lineLastNumber[line] — сколько блоков в линии (то есть последний lineNum)
 *  - lineLastHashHex[line] — hash последнего блока линии (HEX64)
 */
public class IT_03_AddBlock_NoAuth {

    public static void main(String[] args) {
        int failed = run();
//        System.exit(failed);
    }

    /** Запуск одного теста (standalone). Возвращает 0 если ок, 1 если упал. */
    public static int run() {
        return TestLog.runOne("IT_03_AddBlock_NoAuth", IT_03_AddBlock_NoAuth::testBody);
    }

    @BeforeAll
    static void ensureUserExists() {
        ItRunContext.initIfNeeded();

        // ВАЖНО:
        //  - requestId тут не важен, но пусть будет.
        //  - отдельная авторизация не нужна, но пользователь должен существовать.
        //
        // Если хочешь реально включить предусловие здесь — просто раскомментируй блок,
        // но сейчас у тебя он закомментирован.
//        String reqJson = JsonBuilders.addUser("it03-adduser-beforeall");
            // ничего не делаем — предусловие временно отключено
    }

//    @Test
    void addBlock_shouldAppendHeaderThenTextThenReaction() {
        // JUnit-режим: как обычно
        testBody();
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();
        ensureUserExists();

        // таймаут на каждый one-shot запрос
        Duration t = Duration.ofSeconds(1);

        if (TestConfig.DEBUG()) {
            TestLog.titleBlock("""
                    IT_03_AddBlock_NoAuth: сценарий AddBlock без отдельной авторизации
                    Используем:
                      login          = %s
                      blockchainName  = %s
                    debug=true: покажем отправку/ответ (JSON) и проверки hash
                    """.formatted(TestConfig.LOGIN(), TestConfig.BCH_NAME()));
        }

        // 1) состояние + сборка + отправка
        AddBlockFlow flow = new AddBlockFlow();

        // =========================================================
        // ШАГ 0: ВАЖНО — первым всегда HEADER global=0
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 0: AddBlock HEADER (global=0, line=0, lineNum=0)");
        flow.sendHeader0(t);

        // =========================================================
        // ШАГ 1..3: TEXT (line=1)
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 1: AddBlock TEXT#1 (line=1)");
        AddBlockFlow.BuiltBlock text1 = flow.sendNextText("Hello #1 from IT_03 test", t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 2: AddBlock TEXT#2 (line=1)");
        flow.sendNextText("Hello #2 from IT_03 test", t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 3: AddBlock TEXT#3 (line=1)");
        flow.sendNextText("Hello #3 from IT_03 test", t);

        // =========================================================
        // ШАГ 4: REACT#1 (line=2) -> на TEXT#1 (global=1, hash=text1)
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 4: AddBlock REACT#1 (line=2) -> на TEXT#1 (global=1)");
        flow.sendNextReaction(
                (short) 1,                // reactionCode (пример: 1 = like)
                TestConfig.BCH_NAME(),    // toBlockchainName
                1,                        // toBlockGlobalNumber = 1 (TEXT#1)
                text1.hash32,             // toBlockHash32 = hash(TEXT#1)
                t
        );

        // Мини-контроль итогов (если захочешь — красиво залогируем через твой TestLog)
        assertEquals(4, flow.globalLastNumber(), "После 1 header + 3 text + 1 react globalLastNumber должен быть 4");
        assertEquals(3, flow.lineLastNumber(AddBlockFlow.LINE_TEXT), "В line=1 должно быть 3 блока");
        assertEquals(1, flow.lineLastNumber(AddBlockFlow.LINE_REACT), "В line=2 должен быть 1 блок");
        assertNotNull(flow.globalLastHashHex());
        assertEquals(64, flow.globalLastHashHex().length());

        // Итог (в обычном режиме это будет единственная строка)
        TestLog.pass("IT_03_AddBlock_NoAuth: OK");
    }
}