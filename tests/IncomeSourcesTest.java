package com.qianwang.privatewealth;

import android.content.ContextWrapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;
import org.json.JSONObject;

/** Actual ledger logic with JVM filesystem doubles; not Android instrumentation. */
public final class IncomeSourcesTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("gather-ledger-check-");
        try {
            WealthDomainTests.main(new String[]{"--storage", work.resolve("existing-regressions").toString()});
            incomeAndSources(work.resolve("income"));
            failuresAreAtomic(work.resolve("failures"));
            migrations(work.resolve("migrations"));
            retainedSources(work.resolve("retention"));
            largeCumulativeSources(work.resolve("large"));
            legacyQuoteAttribution(work.resolve("legacy-quotes"));
            displayText();
            System.out.println("PASS " + checks + " income/source assertions (JVM filesystem doubles)");
        } finally {
            try (Stream<Path> paths = Files.walk(work)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static void incomeAndSources(Path folder) throws Exception {
        Fixture f = new Fixture(folder, "2026-11-01T15:59:00Z");
        WealthStore.Account bank = f.store.addAccount("工资卡", "bank", 1_000_000);
        WealthStore.Account broker = f.store.addAccount("证券账户", "securities", 0);
        WealthStore.Holding holding = f.store.addHolding(broker.id, "510300", "测试 ETF", 1000, 4_000_000);
        WealthStore.DailySnapshot baseline = f.latest();
        eq(1_400_000, baseline.totalFen(), "opening assets");
        eq(1, f.store.dailySnapshots.size(), "first day is one observation");
        f.clock.at("2026-11-01T16:00:00Z");
        f.store.recordIncome(bank.id, 800_000, " 工资 ", " 11月工资 ");
        eq(1_800_000, f.store.accountById(bank.id).cashFen, "income increases selected cash once");
        eq(2_200_000, f.store.totalAssets(), "income is not added twice to assets");
        eq(2, f.store.dailySnapshots.size(), "income after Shanghai midnight creates a new day");
        eq("2026-11-02", f.latest().day, "income uses Shanghai day");
        eq(f.clock.millis(), f.latest().recordedAt, "snapshot shares transaction time");
        eq(f.clock.millis(), f.store.accountById(bank.id).updatedAt, "account shares transaction time");
        eq("income", f.store.entries.get(0).type, "income gets a separate history type");
        eq(f.clock.millis(), f.store.entries.get(0).time, "entry shares injected transaction time");
        contains(f.store.entries.get(0).title + f.store.entries.get(0).detail, "工资", "income source retained");
        contains(f.store.entries.get(0).detail, "工资卡", "recipient name retained");
        contains(f.store.entries.get(0).detail, "11月工资", "memo retained");
        sources(f.latest(), 800_000, 0, 0, true, "income source");
        eq(0, WealthStore.otherChangeFen(baseline, f.latest()), "income accounts for entire change");
        f.store.recordToday();
        f.store.recordToday();
        sources(f.latest(), 800_000, 0, 0, true, "observing does not duplicate income");
        WealthStore.Holding quote = quote(holding.id, 3_800_000, "20261102150000");
        f.store.applyQuotes(Collections.singletonList(quote));
        f.store.applyQuotes(Collections.singletonList(quote));
        eq(780_000, f.latest().totalFen() - baseline.totalFen(), "salary plus price decline");
        eq(-20_000, WealthStore.otherChangeFen(baseline, f.latest()), "only quote movement remains");
        sources(f.latest(), 800_000, 0, 0, true, "quote refresh does not duplicate income");
        f.store.updateAccount(bank.id, "新工资卡", 1_799_000);
        f.store.updateHolding(holding.id, 2000, 4_000_000);
        sources(f.latest(), 800_000, -1000, 380_000, true, "cash and quantity edits are separate sources");
        eq(-20_000, WealthStore.otherChangeFen(baseline, f.latest()), "quantity edit is not investment movement");
        f.store.updateHolding(holding.id, 2000, 9_000_000);
        eq(380_000, f.latest().holdingAdjustmentFen, "quoted cost edit does not change asset value");
        long beforeTransfer = f.store.totalAssets();
        f.store.transfer(bank.id, broker.id, 10_000, "内部转账");
        eq(beforeTransfer, f.store.totalAssets(), "internal transfer conserves assets");
        sources(f.latest(), 800_000, -1000, 380_000, true, "internal transfer is not a change source");
        f.store.deleteHolding(holding.id);
        sources(f.latest(), 800_000, -1000, -380_000, true, "position removal offsets manual value");
        WealthStore.Account extra = f.store.addAccount("临时资金", "cash", 5000);
        eq(4000, f.latest().cashAdjustmentFen, "opening account cash is an adjustment");
        f.store.deleteAccount(extra.id);
        eq(-1000, f.latest().cashAdjustmentFen, "removing account offsets adjustment");
        WealthStore.Holding unquoted = f.store.addHolding(broker.id, "510500", "未报价 ETF", 100, 2_000_000);
        long beforeCostEdit = f.latest().holdingAdjustmentFen;
        f.store.updateHolding(unquoted.id, 100, 3_000_000);
        eq(beforeCostEdit + 10_000, f.latest().holdingAdjustmentFen, "unquoted cost edit changes manual valuation");
        eq(-20_000, WealthStore.otherChangeFen(baseline, f.latest()), "all manual edits leave only observed quote change");
        String backup = f.store.exportJson();
        long savedTotal = f.store.totalAssets();
        long savedHoldingSource = f.latest().holdingAdjustmentFen;
        f.store.recordIncome(bank.id, 1, "奖金", "");
        f.store.importJson(backup);
        f.store.load();
        eq(savedTotal, f.store.totalAssets(), "backup and reload retain total");
        sources(f.latest(), 800_000, -1000, savedHoldingSource, true, "backup retains sources");
        f.clock.at("2026-11-05T04:00:00Z");
        f.store.recordToday();
        eq(3, f.store.dailySnapshots.size(), "missed dates are not fabricated");
        eq("2026-11-05", f.latest().day, "next observed day keeps actual date");
        sources(f.latest(), 0, 0, 0, true, "new day resets source totals");
        f.store.recordIncome(bank.id, 25_000, "奖金", null);
        sources(f.latest(), 25_000, 0, 0, true, "new day's income accumulates separately");
        eq(800_000, f.store.dailySnapshots.get(1).incomeFen, "later day never rewrites past income");
    }

    private static void failuresAreAtomic(Path folder) throws Exception {
        Fixture f = new Fixture(folder, "2026-11-01T04:00:00Z");
        WealthStore.Account bank = f.store.addAccount("银行", "bank", 1000);
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, 0, "工资", ""), "zero income");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, -1, "工资", ""), "negative income");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, WealthStore.MAX_MONEY_FEN + 1, "工资", ""), "oversized income");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, WealthStore.MAX_MONEY_FEN, "工资", ""), "recipient balance overflow");
        rejectsUnchanged(f, () -> f.store.recordIncome("missing", 1, "工资", ""), "missing account");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, 1, " ", ""), "blank source");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, 1, repeat(41), ""), "long source");
        rejectsUnchanged(f, () -> f.store.recordIncome(bank.id, 1, "工资", repeat(201)), "long memo");
        f.clock.at("2026-11-02T04:00:00Z");
        String before = state(f.store);
        byte[] diskBefore = Files.readAllBytes(f.ledger());
        Path blocked = folder.resolve("wealth-v1.json.new");
        Files.createDirectory(blocked);
        try {
            rejectsState(() -> f.store.recordIncome(bank.id, 500, "工资", ""), "write failure surfaces");
            eq(before, state(f.store), "write failure rolls back cash, entries and snapshot");
            truth(Arrays.equals(diskBefore, Files.readAllBytes(f.ledger())), "write failure preserves saved bytes");
        } finally { Files.delete(blocked); }
        f.store.load();
        eq(1000, f.store.totalAssets(), "reload after failure retains original balance");
        eq(1, f.store.dailySnapshots.size(), "failed write does not add a day");
        f.store.recordIncome(bank.id, 500, "工资", "");
        eq(1500, f.store.totalAssets(), "retry after failure records income once");
        sources(f.latest(), 500, 0, 0, true, "retry sources are not doubled");
        String backup = f.store.exportJson();
        for (String field : new String[]{"incomeFen", "cashAdjustmentFen", "holdingAdjustmentFen", "sourcesKnown"}) {
            JSONObject invalid = new JSONObject(backup);
            invalid.getJSONArray("dailySnapshots").getJSONObject(1).remove(field);
            rejectsUnchanged(f, () -> f.store.importJson(invalid.toString()), "v4 missing " + field);
        }
        for (Object value : new Object[]{-1, 1.5, "500"}) {
            JSONObject invalid = new JSONObject(backup);
            invalid.getJSONArray("dailySnapshots").getJSONObject(1).put("incomeFen", value);
            rejectsUnchanged(f, () -> f.store.importJson(invalid.toString()), "invalid income source type or range");
        }
        JSONObject invalidBoolean = new JSONObject(backup);
        invalidBoolean.getJSONArray("dailySnapshots").getJSONObject(1).put("sourcesKnown", "true");
        rejectsUnchanged(f, () -> f.store.importJson(invalidBoolean.toString()), "string is not boolean source flag");
        JSONObject mismatched = new JSONObject(backup);
        mismatched.getJSONArray("accounts").getJSONObject(0).put("cashFen", 2000);
        f.store.importJson(mismatched.toString());
        f.store.recordIncome(bank.id, 100, "工资", "");
        truth(!f.latest().sourcesKnown, "imported current-balance gap is not called market movement");
    }

    private static void migrations(Path folder) throws Exception {
        for (int version : new int[]{1, 2, 3, 4}) {
            Fixture f = new Fixture(folder.resolve("v" + version), "2026-11-01T04:00:00Z");
            WealthStore.Account bank = f.store.addAccount("旧账户", "bank", 1000);
            JSONObject legacy = new JSONObject(f.store.exportJson());
            legacy.put("version", version);
            if (version < 3) legacy.getJSONArray("accounts").getJSONObject(0).remove("icon");
            if (version == 1) legacy.remove("dailySnapshots");
            else {
                JSONObject day = legacy.getJSONArray("dailySnapshots").getJSONObject(0);
                for (String key : new String[]{"incomeFen", "cashAdjustmentFen", "holdingAdjustmentFen", "sourcesKnown"}) day.remove(key);
            }
            if (version == 4) {
                JSONObject day = legacy.getJSONArray("dailySnapshots").getJSONObject(0);
                day.put("incomeFen", 0); day.put("cashAdjustmentFen", 0); day.put("holdingAdjustmentFen", 0); day.put("sourcesKnown", false);
                day.remove("observedMarketChangeFen");
            }
            f.store.importJson(legacy.toString());
            eq(1000, f.store.totalAssets(), "v" + version + " assets retained");
            eq(version == 1 ? 0 : 1, f.store.dailySnapshots.size(), "legacy history retained without fabrication");
            if (version > 1) sources(f.latest(), 0, 0, 0, false, "legacy source is unknown");
            f.store.recordIncome(bank.id, 500, "工资", "");
            eq(1500, f.store.totalAssets(), "income works after migration");
            eq(500, f.latest().incomeFen, "new income retained on migration day");
            if (version > 1) truth(!f.latest().sourcesKnown, "upgrade day remains partially unknown");
            eq(1, f.store.dailySnapshots.size(), "first migrated day has no artificial baseline");
            f.store.load();
            eq(500, f.latest().incomeFen, "new source survives migration reload");
            f.clock.at("2026-11-02T04:00:00Z");
            f.store.recordIncome(bank.id, 100, "奖金", "");
            sources(f.latest(), 100, 0, 0, true, "next complete day tracks sources");
            eq(0, WealthStore.otherChangeFen(f.store.dailySnapshots.get(0), f.latest()), "next day has no invented price gain");
        }
    }

    private static void retainedSources(Path folder) throws Exception {
        Fixture f = new Fixture(folder, "2026-11-01T04:00:00Z");
        WealthStore.Account bank = f.store.addAccount("原工资卡", "bank", 1000);
        f.clock.at("2026-11-02T04:00:00Z");
        f.store.recordIncome(bank.id, 500, "工资", "十一月");
        f.store.updateAccount(bank.id, "新名称", 1500);
        WealthStore.Entry income = f.store.entries.get(1);
        eq("income", income.type, "rename leaves income record intact");
        contains(income.detail, "原工资卡", "income retains original recipient name");
        for (int i = 0; i < 505; i++) f.store.updateAccount(bank.id, "新名称", 1501 + i);
        eq(500, f.store.entries.size(), "recent history remains capped");
        truth(f.store.entries.stream().noneMatch(entry -> "income".equals(entry.type)), "income entry has rolled out of recent history");
        sources(f.latest(), 500, 505, 0, true, "daily source survives recent-history pruning");
        f.store.deleteAccount(bank.id);
        eq(500, f.latest().incomeFen, "deleting recipient does not erase income source");
        f.store.load();
        eq(500, f.latest().incomeFen, "retained source survives reload without account");
        eq(0, f.store.totalAssets(), "deletion does not turn historic income into current cash");
    }

    private static void largeCumulativeSources(Path folder) throws Exception {
        Fixture f = new Fixture(folder, "2026-11-01T04:00:00Z");
        WealthStore.Account bank = f.store.addAccount("大额资金", "bank", 0);
        f.clock.at("2026-11-02T04:00:00Z");
        long limit = WealthStore.MAX_MONEY_FEN;
        f.store.recordIncome(bank.id, limit, "收入", "");
        f.store.updateAccount(bank.id, "大额资金", 0);
        f.store.recordIncome(bank.id, limit, "收入", "");
        eq(limit, f.store.totalAssets(), "balance limit applies to current assets");
        sources(f.latest(), 2 * limit, -limit, 0, true, "source throughput can exceed current balance limit");
        eq(0, WealthStore.otherChangeFen(f.store.dailySnapshots.get(0), f.latest()), "large offsetting sources calculate exactly");
        f.store.load();
        eq(2 * limit, f.latest().incomeFen, "large income sum survives persistence");
    }

    private static void legacyQuoteAttribution(Path folder) throws Exception {
        Fixture f = new Fixture(folder, "2026-11-01T04:00:00Z");
        WealthStore.Account broker = f.store.addAccount("证券", "securities", 0);
        WealthStore.Holding holding = f.store.addHolding(broker.id, "510300", "ETF", 1000, 4_000_000);
        f.clock.at("2026-11-02T04:00:00Z");
        f.store.recordToday();
        JSONObject old = new JSONObject(f.store.exportJson());
        old.put("version", 3);
        f.store.importJson(old.toString());
        f.store.applyQuotes(Collections.singletonList(quote(holding.id, 3_800_000, "20261102150000")));
        String caption = DailyChangeText.describe(f.store.dailySnapshots.get(0), f.latest(), new DisplayMoney(false, ""), false);
        contains(caption, "行情 / 估值 -200.00", "known stock refresh on legacy day must be identified");
        truth(!caption.contains("未分类变化 -200.00"), "known stock refresh must not be swallowed by legacy flag");
        eq(-20_000, f.latest().observedMarketChangeFen, "quote delta stored separately");
        f.store.applyQuotes(Collections.singletonList(quote(holding.id, 3_800_000, "20261102150000")));
        f.store.recordToday();
        f.store.load();
        eq(-20_000, f.latest().observedMarketChangeFen, "duplicate quotes and reload do not double count");
        f.store.applyQuotes(Collections.singletonList(quote(holding.id, 4_100_000, "20261102151000")));
        eq(10_000, f.latest().observedMarketChangeFen, "later rise offsets earlier decline");
        f.store.updateHolding(holding.id, 2000, 4_000_000);
        eq(10_000, f.latest().observedMarketChangeFen, "manual quantity edit is not a quote change");
        eq(0, WealthStore.unclassifiedChangeFen(f.store.dailySnapshots.get(0), f.latest()), "manual adjustment and quotes reconcile");
        JSONObject gap = new JSONObject(f.store.exportJson());
        gap.getJSONArray("accounts").getJSONObject(0).put("cashFen", 5000);
        f.store.importJson(gap.toString());
        f.store.applyQuotes(Collections.singletonList(quote(holding.id, 4_200_000, "20261102152000")));
        eq(30_000, f.latest().observedMarketChangeFen, "known quote still isolated from imported gap");
        eq(5000, WealthStore.unclassifiedChangeFen(f.store.dailySnapshots.get(0), f.latest()), "unexplained cash gap remains unclassified");
        String saved = f.store.exportJson();
        f.store.importJson(saved);
        f.store.load();
        eq(30_000, f.latest().observedMarketChangeFen, "backup round trips quote source");
        JSONObject invalid = new JSONObject(saved);
        invalid.getJSONArray("dailySnapshots").getJSONObject(1).remove("observedMarketChangeFen");
        rejectsUnchanged(f, () -> f.store.importJson(invalid.toString()), "v5 requires observed quote source");
        invalid.getJSONArray("dailySnapshots").getJSONObject(1).put("observedMarketChangeFen", "30000");
        rejectsUnchanged(f, () -> f.store.importJson(invalid.toString()), "quote source must be an integer");
        Path blocked = folder.resolve("wealth-v1.json.new");
        Files.createDirectory(blocked);
        try {
            rejectsState(() -> f.store.applyQuotes(Collections.singletonList(quote(holding.id, 4_300_000, "20261102153000"))), "failed quote save surfaces");
            eq(30_000, f.latest().observedMarketChangeFen, "failed quote save rolls back attribution");
        } finally { Files.delete(blocked); }
        f.clock.at("2026-11-03T04:00:00Z");
        f.store.applyQuotes(Collections.singletonList(quote(holding.id, 4_300_000, "20261103150000")));
        eq(20_000, f.latest().observedMarketChangeFen, "next day starts a fresh quote accumulator");
        truth(f.latest().sourcesKnown, "legacy flag does not propagate into a continuous new day");

    }

    private static void displayText() {
        DisplayMoney cny = new DisplayMoney(false, "");
        WealthStore.DailySnapshot previous = new WealthStore.DailySnapshot(); previous.cashFen = 1_000_000; previous.marketFen = 100_000;
        WealthStore.DailySnapshot current = new WealthStore.DailySnapshot(); current.cashFen = 1_800_000; current.marketFen = 80_000;
        current.incomeFen = 800_000; current.sourcesKnown = true;
        eq("收入 +8,000.00 · 行情 / 估值 -200.00", DailyChangeText.describe(previous, current, cny, false), "caption explains exact observed change");
        eq("变化来源与金额已隐藏", DailyChangeText.describe(previous, current, cny, true), "hidden caption discloses no values or source labels");
        eq("首条记录 · 已记收入 +8,000.00", DailyChangeText.describe(null, current, cny, false), "first day does not pretend prior assets were zero");
        current.sourcesKnown = false;
        eq("收入 +8,000.00 · 未分类变化 -200.00 · 部分来源未记录", DailyChangeText.describe(previous, current, cny, false), "unknown remainder is not market gain");
        current.sourcesKnown = true; current.cashFen = previous.cashFen; current.marketFen = previous.marketFen; current.cashAdjustmentFen = -800_000;
        eq("收入 +8,000.00 · 余额调整 -8,000.00", DailyChangeText.describe(previous, current, cny, false), "net-zero day preserves offsetting sources");
        current.cashFen = 1_072_500; current.incomeFen = 72_500; current.cashAdjustmentFen = 0;
        eq("收入 +100.00", DailyChangeText.describe(previous, current, new DisplayMoney(true, "7.25"), false), "USD caption uses display conversion only");
        eq(72_500, current.incomeFen, "display conversion does not mutate ledger");
        current.cashFen = previous.cashFen; current.incomeFen = 0;
        eq("无资产变化", DailyChangeText.describe(previous, current, cny, false), "known unchanged day has clear label");
        eq("首条记录，暂无前日对比", DailyChangeText.describe(null, current, cny, false), "first day without income has no invented change");
        DisplayMoney tinyRate = new DisplayMoney(true, "0.01");
        eq("9,223,372,036,854,775,807.00", tinyRate.amount(Long.MAX_VALUE), "large source conversion does not overflow intermediate fen");
        current.incomeFen = Long.MAX_VALUE;
        eq("首条记录 · 已记收入 +9,223,372,036,854,775,807.00", DailyChangeText.describe(null, current, tinyRate, false), "large source caption remains displayable");
    }

    private static final class Fixture {
        final Path folder;
        final TestClock clock;
        final WealthStore store;
        Fixture(Path folder, String instant) throws Exception {
            this.folder = folder; Files.createDirectories(folder); clock = new TestClock(instant);
            store = new WealthStore(new ContextWrapper(null) { @Override public File getFilesDir() { return folder.toFile(); } }, clock);
            store.load();
        }
        WealthStore.DailySnapshot latest() { return store.dailySnapshots.get(store.dailySnapshots.size() - 1); }
        Path ledger() { return folder.resolve("wealth-v1.json"); }
    }
    private static final class TestClock extends Clock {
        private Instant value;
        TestClock(String value) { at(value); }
        void at(String value) { this.value = Instant.parse(value); }
        @Override public Instant instant() { return value; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
    private static WealthStore.Holding quote(String id, long micros, String time) {
        WealthStore.Holding quote = new WealthStore.Holding(); quote.id = id; quote.name = "测试 ETF";
        quote.lastPriceMicros = micros; quote.previousCloseMicros = 4_000_000; quote.quoteTime = time; return quote;
    }
    private static void sources(WealthStore.DailySnapshot day, long income, long cash, long holding, boolean known, String label) {
        eq(income, day.incomeFen, label + " income"); eq(cash, day.cashAdjustmentFen, label + " cash");
        eq(holding, day.holdingAdjustmentFen, label + " holding"); truth(known == day.sourcesKnown, label + " completeness");
    }
    private static String repeat(int count) { return String.join("", Collections.nCopies(count, "长")); }
    private static String state(WealthStore store) { JSONObject json = new JSONObject(store.exportJson()); json.remove("exportedAt"); return json.toString(); }
    private static void rejectsUnchanged(Fixture f, Runnable action, String label) throws Exception {
        String before = state(f.store); byte[] bytes = Files.readAllBytes(f.ledger());
        try { action.run(); throw new AssertionError(label + " must reject"); } catch (IllegalArgumentException expected) { checks++; }
        eq(before, state(f.store), label + " preserves in-memory state");
        truth(Arrays.equals(bytes, Files.readAllBytes(f.ledger())), label + " preserves disk");
    }
    private static void rejectsState(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label + " must reject"); } catch (IllegalStateException expected) { checks++; }
    }
    private static void truth(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void contains(String value, String fragment, String label) { truth(value.contains(fragment), label); }
    private static void eq(long expected, long actual, String label) { if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual); checks++; }
    private static void eq(String expected, String actual, String label) { if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual); checks++; }
}
