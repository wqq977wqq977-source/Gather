package com.qianwang.privatewealth;

import android.content.ContextWrapper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import org.json.JSONObject;

/** Standalone assertions; --storage PATH runs against real Android AtomicFile and org.json. */
public final class WealthDomainTests {
    private static int checks;
    public static void main(String[] args) throws Exception {
        equal(10L, WealthStore.parseMoney("0.10"), "decimal money");
        equal(123456789012345L, WealthStore.parseMoney("1234567890123.45"), "large exact money");
        equal(4_178_000L, WealthStore.parsePrice("4.178"), "ETF price precision");
        equal(417_800L, WealthStore.positionValue(4_178_000L, 1000), "ETF market value");
        equal(1L, WealthStore.positionValue(5_000L, 1), "half-fen rounds up");
        equal(0L, WealthStore.positionValue(4_999L, 1), "less than half-fen rounds down");
        equal("4.178", WealthStore.formatPrice(4_178_000L), "price formatting");
        equal("10.00", WealthStore.formatMoney(1000), "money formatting");
        rejects(() -> WealthStore.parseMoney("0.001"), "fractional fen rejected");
        rejects(() -> WealthStore.parseMoney("-1"), "negative balance rejected");
        rejects(() -> WealthStore.parseMoney("1e6"), "exponent input rejected");
        rejects(() -> WealthStore.parseMoney("NaN"), "NaN rejected");
        rejects(() -> WealthStore.parseMoney("90000000000000.01"), "money bound enforced");
        rejects(() -> WealthStore.parsePrice("1.0000001"), "price precision bound");
        rejects(() -> WealthStore.positionValue(WealthStore.MAX_PRICE_MICROS, WealthStore.MAX_QUANTITY), "multiplication overflow rejected");
        equal("false", String.valueOf(WealthStore.isValidQuoteTime("20260230150000")), "invalid calendar date rejected");
        equal("false", String.valueOf(WealthStore.isValidQuoteTime("20261023250000")), "invalid clock time rejected");
        equal("sh510300", WealthStore.normalizeSecurityCode("510300"), "bare ETF code becomes canonical");
        equal("sh510300", WealthStore.normalizeSecurityCode("SH510300"), "prefixed ETF code preserved");
        rejects(() -> WealthStore.normalizeSecurityCode("sz510300"), "incorrect market prefix rejected");
        rejects(() -> WealthStore.normalizeSecurityCode("900901"), "B shares excluded from CNY ledger");

        WealthStore.Holding holding = new WealthStore.Holding();
        holding.quantity = 1000; holding.costPriceMicros = 4_000_000;
        equal(400_000L, WealthStore.holdingValue(holding), "unquoted position uses cost");
        equal(0L, WealthStore.holdingProfit(holding), "unquoted gain is zero");
        equal(0L, WealthStore.holdingDailyChange(holding), "unquoted daily change is zero");
        holding.lastPriceMicros = 4_178_000; holding.previousCloseMicros = 4_100_000; holding.quoteTime = "20261023150000";
        equal(417_800L, WealthStore.holdingValue(holding), "quoted valuation");
        equal(17_800L, WealthStore.holdingProfit(holding), "quoted profit");
        equal(7_800L, WealthStore.holdingDailyChange(holding), "change versus previous close");
        WealthStore.DailySnapshot daily = new WealthStore.DailySnapshot(); daily.cashFen = 1200; daily.marketFen = 3400;
        equal(4600L, daily.totalFen(), "daily total uses cash plus market value");
        daily.cashFen = WealthStore.MAX_MONEY_FEN;
        rejects(daily::totalFen, "daily total overflow rejected");

        if (args.length == 2 && "--storage".equals(args[0])) storage(new File(args[1]));
        System.out.println("PASS " + checks + " wealth-domain assertions");
    }

    private static void storage(File directory) throws Exception {
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IllegalStateException("Cannot create isolated test directory");
        if (new File(directory, "wealth-v1.json").exists()) throw new IllegalStateException("Test path must be a fresh directory");
        ContextWrapper context = new ContextWrapper(null) {
            @Override public File getFilesDir() { return directory; }
        };
        TestClock clock = new TestClock("2026-10-23T02:00:00Z");
        WealthStore store = new WealthStore(context, clock);
        store.load();
        equal(0L, store.totalAssets(), "first run is empty");
        equal(0L, store.dailySnapshots.size(), "empty first run has no invented history");
        equal("false", String.valueOf(store.recordToday()), "empty observation is a no-op");
        WealthStore.Account bank = store.addAccount("大额余额", "bank", 1_000_000);
        equal(1L, store.dailySnapshots.size(), "first account creates today's observation");
        equal("2026-10-23", store.dailySnapshots.get(0).day, "snapshot uses Shanghai calendar day");
        WealthStore.Account broker = store.addAccount("证券账户", "securities", 100_000);
        WealthStore.Holding position = store.addHolding(broker.id, "510300", "沪深300ETF", 1000, 4_000_000);
        equal(1L, store.dailySnapshots.size(), "same-day edits replace instead of appending");
        equal(1_500_000L, store.dailySnapshots.get(0).totalFen(), "snapshot values match current accounts");
        equal(400_000L, store.dailySnapshots.get(0).costFen, "snapshot preserves recorded cost basis");
        equal(1L, store.dailySnapshots.get(0).unquotedCount, "cost-valued positions are identified");
        equal("", store.dailySnapshots.get(0).oldestQuoteTime, "unquoted snapshot has no fabricated quote time");
        equal("sh510300", position.code, "stored code matches quote map key");
        equal(1_500_000L, store.totalAssets(), "holdings counted once");
        equal(100_000L, store.accountById(broker.id).cashFen, "existing position leaves available cash unchanged");
        int historyBeforeQuote = store.entries.size();
        WealthStore.Holding incoming = quote(position.id, "沪深300ETF", 4_178_000, "20261023150000");
        incoming.quantity = 1; incoming.costPriceMicros = 1;
        equal(1L, store.applyQuotes(Collections.singletonList(incoming)), "quote applies by holding ID");
        equal(1000L, store.holdingById(position.id).quantity, "quote cannot change position quantity");
        equal(4_000_000L, store.holdingById(position.id).costPriceMicros, "quote cannot change cost basis");
        equal(historyBeforeQuote, store.entries.size(), "quote updates do not create ledger history");
        equal(1_517_800L, store.totalAssets(), "cash plus marked holdings");
        equal(17_800L, store.totalProfit(), "aggregate unrealized profit");
        equal(7_800L, store.dailyChange(), "aggregate previous-close movement");
        equal(1_517_800L, store.dailySnapshots.get(0).totalFen(), "quote atomically updates daily value");
        equal(0L, store.dailySnapshots.get(0).unquotedCount, "quoted observation clears fallback count");
        equal("20261023150000", store.dailySnapshots.get(0).oldestQuoteTime, "snapshot records quote provenance");
        equal("false", String.valueOf(store.recordToday()), "identical observation avoids needless write");
        WealthStore.Holding stale = quote(position.id, "过期报价", 9_000_000, "20261022150000");
        equal(0L, store.applyQuotes(Collections.singletonList(stale)), "older quotes skipped");
        WealthStore.Holding invalidQuote = quote(position.id, String.join("", Collections.nCopies(81, "长")), 9_000_000, "20261023150100");
        rejects(() -> store.applyQuotes(Collections.singletonList(invalidQuote)), "invalid quote batch rejected");
        equal(4_178_000L, store.holdingById(position.id).lastPriceMicros, "invalid quote batch leaves live prices unchanged");
        File unwritableNewFile = new File(directory, "wealth-v1.json.new");
        if (!unwritableNewFile.mkdir()) throw new AssertionError("Could not arrange isolated write failure");
        try {
            WealthStore.Holding unsaved = quote(position.id, "未保存报价", 9_000_000, "20261023150100");
            rejectsState(() -> store.applyQuotes(Collections.singletonList(unsaved)), "quote write failure surfaced");
            equal(4_178_000L, store.holdingById(position.id).lastPriceMicros, "failed quote write preserves live prices");
            equal(1_517_800L, store.dailySnapshots.get(0).totalFen(), "failed quote write preserves live snapshot");
            clock.at("2026-10-24T02:00:00Z");
            rejectsState(store::recordToday, "new-day snapshot write failure surfaced");
            equal(1L, store.dailySnapshots.size(), "failed observation does not append to memory");
            clock.at("2026-10-23T02:00:00Z");
        } finally {
            if (!unwritableNewFile.delete()) throw new AssertionError("Could not remove write failure fixture");
        }
        long beforeTransfer = store.totalAssets();
        store.transfer(bank.id, broker.id, 20_000, "追加可用资金");
        equal(beforeTransfer, store.totalAssets(), "internal transfer conserves wealth");
        equal(beforeTransfer, store.dailySnapshots.get(0).totalFen(), "internal transfer does not fabricate asset growth");
        equal(980_000L, store.accountById(bank.id).cashFen, "sender balance");
        equal(120_000L, store.accountById(broker.id).cashFen, "receiver balance");
        int beforeReject = store.entries.size();
        rejects(() -> store.transfer(bank.id, broker.id, 2_000_000, ""), "insufficient transfer rejected");
        rejects(() -> store.transfer(bank.id, bank.id, 1, ""), "same-account transfer rejected");
        equal(beforeTransfer, store.totalAssets(), "failed transfer preserves wealth");
        equal(beforeReject, store.entries.size(), "failed transfer leaves no history");
        rejects(() -> store.addHolding(broker.id, "510300", "重复", 100, 4_000_000), "duplicate position rejected");
        rejects(() -> store.addHolding(bank.id, "510500", "错误账户", 100, 4_000_000), "bank positions rejected");
        rejects(() -> store.deleteAccount(broker.id), "account with positions cannot be deleted");
        clock.at("2026-10-23T16:00:00Z");
        equal("true", String.valueOf(store.recordToday()), "Shanghai midnight creates the next day");
        equal(2L, store.dailySnapshots.size(), "new day appends one observed record");
        equal("2026-10-24", store.dailySnapshots.get(1).day, "record day is not UTC day");
        equal("20261023150000", store.dailySnapshots.get(1).oldestQuoteTime, "next-day cached valuation retains actual old quote time");
        equal(beforeTransfer, store.dailySnapshots.get(1).totalFen(), "cached quote observation never invents a new market price");
        store.updateAccount(bank.id, "大额余额", 900_000);
        equal(-80_000L, store.entries.get(0).amountFen, "manual balance adjustment history");
        equal(beforeTransfer, store.dailySnapshots.get(0).totalFen(), "past observation is never recalculated");
        equal(1_437_800L, store.dailySnapshots.get(1).totalFen(), "manual cash change contributes to observed growth");
        String backup = store.exportJson();
        equal(3L, new JSONObject(backup).getLong("version"), "new backups use schema v3");
        long validTotal = store.totalAssets();
        JSONObject malformed = new JSONObject(backup);
        malformed.getJSONArray("holdings").getJSONObject(0).put("accountId", "missing-account");
        final String orphaned = malformed.toString();
        rejects(() -> store.importJson(orphaned), "orphaned holding import rejected");
        equal(validTotal, store.totalAssets(), "invalid import preserves memory");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("accounts").getJSONObject(0).put("cashFen", 12.5);
        final String fractional = malformed.toString();
        rejects(() -> store.importJson(fractional), "fractional fen import rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").put(malformed.getJSONArray("dailySnapshots").getJSONObject(0));
        rejectImport(store, malformed, "duplicate daily snapshot rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").getJSONObject(0).put("day", "2026-02-30");
        rejectImport(store, malformed, "nonexistent calendar date rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").getJSONObject(0).put("recordedAt", Instant.parse("2026-10-24T02:00:00Z").toEpochMilli());
        rejectImport(store, malformed, "date and timestamp mismatch rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").getJSONObject(0).put("cashFen", WealthStore.MAX_MONEY_FEN);
        rejectImport(store, malformed, "overflowing historical total rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").getJSONObject(0).put("unquotedCount", -1);
        rejectImport(store, malformed, "negative fallback count rejected");
        malformed = new JSONObject(backup);
        malformed.getJSONArray("dailySnapshots").getJSONObject(0).put("oldestQuoteTime", "20260230150000");
        rejectImport(store, malformed, "malformed historical quote timestamp rejected");
        malformed = new JSONObject(backup);
        malformed.remove("dailySnapshots");
        rejectImport(store, malformed, "v2 missing snapshot collection rejected");
        equal(2L, store.dailySnapshots.size(), "malformed imports leave snapshot memory intact");
        WealthStore reload = new WealthStore(context, clock);
        reload.load();
        equal(validTotal, reload.totalAssets(), "invalid import preserves saved ledger");
        equal(4_178_000L, reload.holdingById(position.id).lastPriceMicros, "quote precision survives persistence");
        equal(beforeTransfer, reload.dailySnapshots.get(0).totalFen(), "invalid import preserves historical file");
        store.updateHolding(position.id, 2000, 4_010_000);
        store.importJson(backup);
        equal(validTotal, store.totalAssets(), "valid backup replaces state atomically");
        equal(1000L, store.holdingById(position.id).quantity, "backup restores quantities");
        equal(2L, store.dailySnapshots.size(), "backup restores observed day count");
        equal(beforeTransfer, store.dailySnapshots.get(0).totalFen(), "backup round-trips historic total");
        equal(1_437_800L, store.dailySnapshots.get(1).totalFen(), "backup round-trips latest total");
        if (!unwritableNewFile.mkdir()) throw new AssertionError("Could not arrange import write failure");
        try {
            JSONObject changed = new JSONObject(backup);
            changed.getJSONArray("dailySnapshots").getJSONObject(0).put("cashFen", 100);
            String failedImport = changed.toString();
            rejectsState(() -> store.importJson(failedImport), "import write failure surfaced");
            equal(beforeTransfer, store.dailySnapshots.get(0).totalFen(), "failed import preserves snapshot memory");
        } finally { if (!unwritableNewFile.delete()) throw new AssertionError("Could not remove import write fixture"); }
        JSONObject v2 = new JSONObject(backup); v2.put("version", 2);
        for(int i=0;i<v2.getJSONArray("accounts").length();i++)v2.getJSONArray("accounts").getJSONObject(i).remove("icon");
        for(int i=0;i<v2.getJSONArray("holdings").length();i++){JSONObject item=v2.getJSONArray("holdings").getJSONObject(i);item.remove("assetType");item.remove("quantityDecimal");}
        store.importJson(v2.toString());equal(validTotal,store.totalAssets(),"v2 migration preserves assets");equal("stock",store.holdings.get(0).assetType,"v2 positions default to stock");equal("auto",store.accounts.get(0).icon,"v2 accounts default icon");equal(2L,store.dailySnapshots.size(),"v2 migration preserves history");
        JSONObject legacy = new JSONObject(backup); legacy.put("version", 1); legacy.remove("dailySnapshots");
        store.importJson(legacy.toString());
        equal(0L, store.dailySnapshots.size(), "v1 migration does not invent past daily values");
        equal(validTotal, store.totalAssets(), "v1 migration preserves current wealth");
        store.recordToday();
        equal(1L, store.dailySnapshots.size(), "legacy ledger starts with one observed day");
        equal("2026-10-24", store.dailySnapshots.get(0).day, "legacy first observation is current date");
        store.importJson(backup);
        clock.at("2026-10-27T02:00:00Z");
        store.recordToday();
        equal(3L, store.dailySnapshots.size(), "missed days remain absent instead of backfilled");
        equal("2026-10-27", store.dailySnapshots.get(2).day, "next observed day keeps its actual date");
        store.deleteHolding(position.id);
        store.deleteAccount(bank.id);
        store.deleteAccount(broker.id);
        equal(0L, store.dailySnapshots.get(2).totalFen(), "removing last account records zero on existing history");
        equal(beforeTransfer, store.dailySnapshots.get(0).totalFen(), "removals preserve past account values");
        store.importJson(backup);
        for (int i = 0; i < 505; i++) store.updateAccount(bank.id, "大额余额", 900_001 + i);
        equal(500L, store.entries.size(), "history retains latest 500 entries");
        String latestBackup = store.exportJson();
        try (FileOutputStream output = new FileOutputStream(new File(directory, "wealth-v1.json"))) {
            output.write("corrupt-data".getBytes(StandardCharsets.UTF_8));
        }
        rejectsState(store::load, "corrupt local file surfaced");
        rejectsState(() -> store.addAccount("不得覆盖", "cash", 0), "write after failed load blocked");
        store.importJson(latestBackup);
        store.load();
        equal(500L, store.entries.size(), "valid import recovers from corruption");
        cashHistory(new File(directory, "cash-only"));
    }

    private static void cashHistory(File directory) {
        if (!directory.mkdirs()) throw new AssertionError("Could not create cash-only test directory");
        ContextWrapper context = new ContextWrapper(null) { @Override public File getFilesDir() { return directory; } };
        TestClock clock = new TestClock("2026-10-23T15:59:59Z");
        WealthStore store = new WealthStore(context, clock); store.load();
        store.addAccount("大额存款", "bank", 8_000_000);
        clock.at("2026-10-23T16:00:00Z");
        equal("true", String.valueOf(store.recordToday()), "cash-only ledger records a new calendar day without quote fetch");
        equal(2L, store.dailySnapshots.size(), "cash-only unchanged balances have daily observations");
        equal(8_000_000L, store.dailySnapshots.get(1).totalFen(), "cash-only daily total remains exact");
        equal("", store.dailySnapshots.get(1).oldestQuoteTime, "cash-only record does not invent quote provenance");
    }

    private interface Attempt { void run(); }
    private static final class TestClock extends Clock {
        private Instant value;
        TestClock(String value) { at(value); }
        void at(String value) { this.value = Instant.parse(value); }
        @Override public Instant instant() { return value; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
    private static void rejectImport(WealthStore store, JSONObject json, String label) {
        final String input = json.toString();
        rejects(() -> store.importJson(input), label);
    }
    private static WealthStore.Holding quote(String id, String name, long micros, String time) {
        WealthStore.Holding quote = new WealthStore.Holding(); quote.id = id; quote.name = name;
        quote.lastPriceMicros = micros; quote.previousCloseMicros = 4_100_000; quote.quoteTime = time;
        return quote;
    }
    private static void rejects(Attempt work, String label) {
        try { work.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void rejectsState(Attempt work, String label) {
        try { work.run(); } catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void equal(long expected, long actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        checks++;
    }
    private static void equal(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        checks++;
    }
}
