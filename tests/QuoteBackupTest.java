import com.qianwang.privatewealth.BackupCodec;
import com.qianwang.privatewealth.QuoteClient;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** Dependency-free checks with synthetic financial data; no personal files are read. */
public final class QuoteBackupTest {
    private static int checks;
    private static int failures;
    private interface Check { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--live".equals(args[0])) {
            liveQuote();
            return;
        }
        test("normalize supported markets", () -> {
            equal("sh600519", QuoteClient.normalizeCode(" 600519 "));
            equal("sh510300", QuoteClient.normalizeCode("SH510300"));
            equal("sz000001", QuoteClient.normalizeCode("000001"));
            equal("sz300750", QuoteClient.normalizeCode("300750"));
            equal("sz159915", QuoteClient.normalizeCode("159915"));
            equal("bj920001", QuoteClient.normalizeCode("920001"));
            equal("bj430047", QuoteClient.normalizeCode("430047"));
        });
        test("reject mismatched market, B shares and malformed code", () -> {
            for (String code : new String[]{"sz600519", "sh000001", "bj510300", "900901", "sh900901", "200002", "sz200002", "00700", "AAPL", "600519,000001", "", null}) {
                rejected(() -> QuoteClient.normalizeCode(code));
            }
        });
        test("parse realistic three-decimal ETF without rounding cents", () -> {
            QuoteClient.Quote q = QuoteClient.parse(fixture("sh510300", "沪深300ETF", "510300", "4.178", "4.151", "20261023150000")).get("sh510300");
            truth(q != null, "ETF quote missing");
            equal(4_178_000L, q.priceMicros);
            equal(4_151_000L, q.previousMicros);
            equal("沪深300ETF", q.name);
            equal("20261023150000", q.time);
        });
        test("parse multiple market quotes and sub-cent values", () -> {
            String body = fixture("sh600519", "贵州茅台", "600519", "1456.67", "1460.01", "20261023145958")
                + fixture("sz159915", "创业板ETF", "159915", "0.001001", "0.002", "20261023150000")
                + fixture("bj920001", "合成测试", "920001", "15.01", "14.88", "20261023150000");
            Map<String, QuoteClient.Quote> quotes = QuoteClient.parse(body);
            equal(3, quotes.size());
            equal(1_456_670_000L, quotes.get("sh600519").priceMicros);
            equal(1_001L, quotes.get("sz159915").priceMicros);
        });
        test("reject missing, malformed or impossible quote timestamp", () -> {
            for (String time : new String[]{"", "20261023", "2026102315000Z", "20260230150000", "20261023250000"}) {
                equal(0, QuoteClient.parse(fixture("sh510300", "沪深300ETF", "510300", "4.178", "4.151", time)).size());
            }
        });
        test("reject negative, zero, nonnumeric, overflow and excessive precision prices", () -> {
            for (String price : new String[]{"-4.178", "0", "NaN", "", "1000000.000001", "4.1780001", "999999999999999999999999"}) {
                equal(0, QuoteClient.parse(fixture("sh510300", "沪深300ETF", "510300", price, "4.151", "20261023150000")).size());
                equal(0, QuoteClient.parse(fixture("sh510300", "沪深300ETF", "510300", "4.178", price, "20261023150000")).size());
            }
        });
        test("reject malformed records, wrong symbol and B-share response", () -> {
            equal(0, QuoteClient.parse("v_sh510300=\"1~short\";\nv_pv_none_match=\"1\";").size());
            equal(0, QuoteClient.parse(fixture("sh510300", "ETF", "510500", "4.178", "4.151", "20261023150000")).size());
            equal(0, QuoteClient.parse(fixture("sz510300", "ETF", "510300", "4.178", "4.151", "20261023150000")).size());
            equal(0, QuoteClient.parse(fixture("sh900901", "测试B股", "900901", "0.75", "0.74", "20261023150000")).size());
            equal(0, QuoteClient.parse(fixture("sz200002", "测试B股", "200002", "3.75", "3.74", "20261023150000")).size());
            equal(0, QuoteClient.parse(fixture("sh510300", "", "510300", "4.178", "4.151", "20261023150000")).size());
        });
        test("invalid duplicate cannot replace valid quote", () -> {
            String body = fixture("sh510300", "沪深300ETF", "510300", "4.178", "4.151", "20261023150000")
                + fixture("sh510300", "坏数据", "510300", "-1", "4.151", "20261023150001");
            Map<String, QuoteClient.Quote> quotes = QuoteClient.parse(body);
            equal(1, quotes.size());
            equal(4_178_000L, quotes.get("sh510300").priceMicros);
        });
        test("empty fetch does not use network", () -> equal(0, QuoteClient.fetch(Arrays.<String>asList()).size()));
        test("encrypted backup roundtrip preserves UTF-8 and newlines", () -> {
            String data = "{\"accounts\":[{\"name\":\"合成测试账户🌱\",\"balance\":123456}]}\n第二行\n";
            char[] password = "synthetic-password-123".toCharArray();
            String encrypted = BackupCodec.encrypt(data, password);
            equal(data, BackupCodec.decrypt(encrypted, password));
            truth(!encrypted.contains("合成测试账户"), "plaintext exposed in backup");
            truth(encrypted.startsWith("GUILONG-BACKUP-1\n"), "version header missing");
            truth(!encrypted.equals(BackupCodec.encrypt(data, password)), "encryption must use fresh salt/nonce");
            equal("", BackupCodec.decrypt(BackupCodec.encrypt("", password), password));
        });
        test("wrong backup password fails authentication", () -> {
            String encrypted = BackupCodec.encrypt("synthetic-only", "correct-password".toCharArray());
            rejected(() -> BackupCodec.decrypt(encrypted, "wrong-password".toCharArray()));
        });
        test("tampered ciphertext, salt and IV fail authentication", () -> {
            char[] password = "synthetic-password".toCharArray();
            String encrypted = BackupCodec.encrypt("synthetic-only", password);
            for (int part : new int[]{1, 2, 3}) {
                String[] pieces = encrypted.split("\n");
                byte[] bytes = Base64.getDecoder().decode(pieces[part]);
                bytes[bytes.length - 1] ^= 1;
                pieces[part] = Base64.getEncoder().encodeToString(bytes);
                rejected(() -> BackupCodec.decrypt(String.join("\n", pieces), password));
            }
        });
        test("malformed backup and short encryption password rejected", () -> {
            rejected(() -> BackupCodec.encrypt("synthetic-only", "short".toCharArray()));
            char[] password = "synthetic-password".toCharArray();
            for (String data : new String[]{"", "{}", "GUILONG-BACKUP-2\na\nb\nc", "GUILONG-BACKUP-1\nAA==\nAA==\nAA==", "GUILONG-BACKUP-1\n@@\n@@\n@@"}) {
                rejected(() -> BackupCodec.decrypt(data, password));
            }
        });
        System.out.println("Quote/backup checks: " + checks + " passed, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static String fixture(String symbol, String name, String code, String price, String previous, String timestamp) {
        // The first 31 Tencent-style tilde fields include the identity, price, previous close and timestamp.
        String[] f = {"1", name, code, price, previous, "4.160", "3865920", "1994231", "1871689",
            "4.177", "1586", "4.176", "1101", "4.175", "3012", "4.174", "1679", "4.173", "890",
            "4.178", "1236", "4.179", "2110", "4.180", "3189", "4.181", "823", "4.182", "1440", "", timestamp};
        return "v_" + symbol + "=\"" + String.join("~", f) + "~0.027~0.65~4.191~4.142\";\n";
    }
    private static void liveQuote() throws Exception {
        Map<String, QuoteClient.Quote> quotes = QuoteClient.fetch(Arrays.asList("510300", "600519"));
        for (QuoteClient.Quote q : quotes.values()) {
            System.out.println("LIVE source=" + QuoteClient.SOURCE + " symbol=" + q.code + " time=" + q.time
                + " priceMicros=" + q.priceMicros + " previousMicros=" + q.previousMicros);
        }
        System.out.println("Live smoke only: observed one response, not an availability or freshness guarantee.");
    }
    private static void test(String name, Check check) {
        try { check.run(); checks++; System.out.println("PASS " + name); }
        catch (Throwable error) { failures++; System.out.println("FAIL " + name + ": " + error); }
    }
    private static void rejected(Check check) throws Exception {
        try { check.run(); } catch (Exception expected) { return; }
        throw new AssertionError("expected rejection");
    }
    private static void truth(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
