package com.qianwang.privatewealth;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Local, single-currency wealth ledger. Cash is fen; per-share prices are millionths of yuan. */
public final class WealthStore {
    public static final int SCHEMA_VERSION = 3;
    public static final long MAX_MONEY_FEN = 9_000_000_000_000_000L;
    public static final long MAX_PRICE_MICROS = 1_000_000_000_000_000L;
    public static final long MAX_QUANTITY = 1_000_000_000_000L;
    public static final int MAX_HISTORY = 500;
    public static final int MAX_DAILY_SNAPSHOTS = 10000;
    private static final int MAX_ACCOUNTS = 500;
    private static final int MAX_HOLDINGS = 5000;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final DateTimeFormatter QUOTE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId RECORD_ZONE = ZoneId.of("Asia/Shanghai");
    private final AtomicFile file;
    private final Clock clock;
    private boolean loadFailed;
    public final List<Account> accounts = new ArrayList<>();
    public final List<Holding> holdings = new ArrayList<>();
    public final List<Entry> entries = new ArrayList<>();
    /** Observed valuations only, ascending by Shanghai calendar day. Missing days are not fabricated. */
    public final List<DailySnapshot> dailySnapshots = new ArrayList<>();

    public static final class Account {
        public String id = "";
        public String name = "";
        public String kind = "bank";
        public String icon = "auto";
        public long cashFen;
        public long updatedAt;
        public Account() {}
    }

    public static final class Holding {
        public String id = "";
        public String accountId = "";
        public String code = "";
        public String name = "";
        public long quantity;
        public String assetType = "stock";
        public String quantityDecimal = "";
        public long costPriceMicros;
        public long lastPriceMicros;
        public long previousCloseMicros;
        public String quoteTime = "";
        public Holding() {}
    }

    public static final class Entry {
        public String id = "";
        public String type = "";
        public String title = "";
        public String detail = "";
        public long amountFen;
        public long time;
        public Entry() {}
    }

    public static final class DailySnapshot {
        public String day = "";
        public long recordedAt;
        public long cashFen;
        public long marketFen;
        public long costFen;
        public int unquotedCount;
        public String oldestQuoteTime = "";
        public DailySnapshot() {}
        public long totalFen() { return add(cashFen, marketFen); }
    }

    private static final class State {
        final List<Account> accounts = new ArrayList<>();
        final List<Holding> holdings = new ArrayList<>();
        final List<Entry> entries = new ArrayList<>();
        final List<DailySnapshot> dailySnapshots = new ArrayList<>();
    }

    public WealthStore(Context context) {
        this(context, Clock.system(RECORD_ZONE));
    }

    /** Package-private clock injection keeps calendar-boundary tests deterministic. */
    WealthStore(Context context, Clock clock) {
        if (context == null) throw new IllegalArgumentException("缺少应用上下文");
        if (clock == null) throw new IllegalArgumentException("缺少记录时钟");
        file = new AtomicFile(new File(context.getFilesDir(), "wealth-v1.json"));
        this.clock = clock;
    }

    public static String today() { return LocalDate.now(RECORD_ZONE).format(DAY_FORMAT); }

    /** Record the latest known valuation, including cached quotes; never fill unobserved past days. */
    public synchronized boolean recordToday() {
        requireWritable();
        State next = snapshot();
        if (!upsertToday(next)) return false;
        persist(next);
        apply(next);
        return true;
    }

    /** A corrupt file is never silently replaced by an empty ledger. */
    public synchronized void load() {
        File base = file.getBaseFile();
        if (!base.exists() && !new File(base.getPath() + ".bak").exists()) {
            apply(new State());
            loadFailed = false;
            return;
        }
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_FILE_BYTES) throw invalid("本地数据文件过大");
                output.write(buffer, 0, count);
            }
            State next = decode(new String(output.toByteArray(), StandardCharsets.UTF_8));
            apply(next);
            loadFailed = false;
        } catch (IOException | RuntimeException error) {
            loadFailed = true;
            throw new IllegalStateException("本地数据读取失败，原文件已保留。请恢复有效备份。", error);
        }
    }

    /** Use after a batch of quote-field updates; quote updates do not create ledger history. */
    public synchronized void save() {
        commit(snapshot());
    }

    public synchronized Account addAccount(String name, String kind, long cashFen) { return addAccount(name,kind,cashFen,"auto"); }
    public synchronized Account addAccount(String name, String kind, long cashFen, String icon) {
        State next = snapshot();
        Account account = new Account();
        account.id = id();
        account.name = clean(name, 40, "账户名称");
        account.kind = kind;account.icon=icon;
        account.cashFen = cashFen;
        account.updatedAt = now();
        next.accounts.add(account);
        history(next, "account_created", "新增账户", account.name + " · 初始可用余额 ¥" + formatMoney(cashFen), cashFen);
        commit(next);
        return accountById(account.id);
    }

    public synchronized Account updateAccount(String accountId, String name, long cashFen) {return updateAccount(accountId,name,cashFen,accountById(accountId).icon);}
    public synchronized Account updateAccount(String accountId, String name, long cashFen, String icon) {
        State next = snapshot();
        Account account = findAccount(next.accounts, accountId);
        long old = account.cashFen;
        String oldName = account.name;account.icon=icon;
        account.name = clean(name, 40, "账户名称");
        account.cashFen = cashFen;
        account.updatedAt = now();
        if (old != cashFen) {
            history(next, "balance_adjustment", "调整余额", account.name + " · ¥" + formatMoney(old) + " → ¥" + formatMoney(cashFen), subtract(cashFen, old));
        } else if (!oldName.equals(account.name)) {
            history(next, "account_updated", "修改账户名称", oldName + " → " + account.name, 0);
        }
        commit(next);
        return accountById(accountId);
    }

    public synchronized void deleteAccount(String accountId) {
        State next = snapshot();
        Account account = findAccount(next.accounts, accountId);
        for (Holding holding : next.holdings) {
            if (holding.accountId.equals(accountId)) throw invalid("请先移除该账户中的持仓");
        }
        next.accounts.remove(account);
        history(next, "account_deleted", "移除账户", account.name + " · 移除余额 ¥" + formatMoney(account.cashFen), -account.cashFen);
        commit(next);
    }

    /** Records an existing position; it deliberately does not debit available cash. */
    public synchronized Holding addHolding(String accountId, String code, String name, long quantity, long costPriceMicros) {
        State next = snapshot();
        Account account = findAccount(next.accounts, accountId);
        if (!"securities".equals(account.kind)) throw invalid("持仓只能添加到证券账户");
        String normalizedCode = normalizeSecurityCode(code);
        for (Holding existing : next.holdings) {
            if (existing.accountId.equals(accountId) && existing.code.equals(normalizedCode)) throw invalid("该账户已存在此证券，请修改已有持仓");
        }
        Holding holding = new Holding();
        holding.id = id();
        holding.accountId = accountId;
        holding.code = normalizedCode;
        holding.name = clean(name == null || name.trim().isEmpty() ? normalizedCode : name, 80, "证券名称");
        holding.quantity = quantity;
        holding.costPriceMicros = costPriceMicros;
        next.holdings.add(holding);
        account.updatedAt = now();
        history(next, "holding_added", "录入持仓", account.name + " · " + holding.name + " " + holding.code + " · " + quantity + " 份/股", 0);
        commit(next);
        return holdingById(holding.id);
    }

    public synchronized Holding updateHolding(String holdingId, long quantity, long costPriceMicros) {
        State next = snapshot();
        Holding holding = findHolding(next.holdings, holdingId);
        holding.quantity = quantity;
        holding.costPriceMicros = costPriceMicros;
        findAccount(next.accounts, holding.accountId).updatedAt = now();
        history(next, "holding_adjusted", "调整持仓", holding.name + " " + holding.code + " · " + quantity + " 份/股 · 成本 ¥" + formatPrice(costPriceMicros), 0);
        commit(next);
        return holdingById(holdingId);
    }

    public static final String[] COIN_IDS={"bitcoin","ethereum","solana","binancecoin","ripple","dogecoin","tether","usd-coin"};
    public static final String[] COIN_SYMBOLS={"BTC","ETH","SOL","BNB","XRP","DOGE","USDT","USDC"};
    public static final String[] ICON_IDS={"auto","bank","wallet","card","hold","coin","safe","home","globe","briefcase","star","gift"};
    public static final String[] ICON_NAMES={"自动","银行","钱包","银行卡","投资","数字货币","保险箱","房屋","全球","公文包","星标","礼物"};
    public static boolean isCrypto(Holding h){return "crypto".equals(h.assetType);}
    public static String normalizeCryptoCode(String raw){String v=raw==null?"":raw.trim().replaceFirst("(?i)^crypto:","");for(int i=0;i<COIN_IDS.length;i++)if(COIN_IDS[i].equalsIgnoreCase(v)||COIN_SYMBOLS[i].equalsIgnoreCase(v))return "crypto:"+COIN_IDS[i];throw invalid("请选择支持的数字货币");}
    public static String coinSymbol(String code){for(int i=0;i<COIN_IDS.length;i++)if(("crypto:"+COIN_IDS[i]).equals(code))return COIN_SYMBOLS[i];return code;}
    public static String parseCoinQuantity(String raw){String v=raw==null?"":raw.trim();if(v.length()>32||!v.matches("[0-9]+(?:\\.[0-9]{1,8})?"))throw invalid("币数量须为正数，最多 8 位小数");BigDecimal q=new BigDecimal(v);if(q.signum()<=0||q.compareTo(BigDecimal.valueOf(MAX_QUANTITY))>0)throw invalid("币数量超出支持范围");return q.stripTrailingZeros().toPlainString();}
    public static String quantityText(Holding h){return isCrypto(h)?h.quantityDecimal:String.valueOf(h.quantity);}
    public synchronized Holding addCryptoHolding(String accountId,String code,String quantity,long cost){
        State next=snapshot();Account a=findAccount(next.accounts,accountId);if(!"crypto".equals(a.kind))throw invalid("数字货币持仓请放入数字资产账户");String normalized=normalizeCryptoCode(code);
        for(Holding h:next.holdings)if(h.accountId.equals(accountId)&&h.code.equals(normalized))throw invalid("该账户已存在此币种，请修改已有持仓");
        Holding h=new Holding();h.id=id();h.accountId=accountId;h.assetType="crypto";h.code=normalized;h.name=coinSymbol(normalized);h.quantityDecimal=parseCoinQuantity(quantity);h.costPriceMicros=cost;next.holdings.add(h);a.updatedAt=now();history(next,"holding_added","录入数字货币",a.name+" · "+h.name+" · "+h.quantityDecimal,0);commit(next);return holdingById(h.id);
    }
    public synchronized Holding updateCryptoHolding(String holdingId,String quantity,long cost){State next=snapshot();Holding h=findHolding(next.holdings,holdingId);if(!isCrypto(h))throw invalid("不是数字货币持仓");h.quantityDecimal=parseCoinQuantity(quantity);h.costPriceMicros=cost;findAccount(next.accounts,h.accountId).updatedAt=now();history(next,"holding_adjusted","调整数字货币",h.name+" · "+h.quantityDecimal,0);commit(next);return holdingById(h.id);}
    public static long holdingPositionValue(long micros,Holding h){
        if(!isCrypto(h))return positionValue(micros,h.quantity);
        bounded(micros,MAX_PRICE_MICROS,"单价");BigDecimal q=new BigDecimal(parseCoinQuantity(h.quantityDecimal));
        try{long fen=BigDecimal.valueOf(micros).multiply(q).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();bounded(fen,MAX_MONEY_FEN,"持仓市值");return fen;}catch(ArithmeticException e){throw invalid("持仓市值超出支持范围");}
    }
    public static String movementPercent(Holding h){if(!isQuoted(h)||h.previousCloseMicros<=0)return "—";BigDecimal pct=BigDecimal.valueOf(h.lastPriceMicros).subtract(BigDecimal.valueOf(h.previousCloseMicros)).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(h.previousCloseMicros),2,RoundingMode.HALF_UP);return (pct.signum()>0?"+":"")+pct.toPlainString()+"%";}

    /** Stages quote fields only. A failed validation/write never changes the live ledger. */
    public synchronized int applyQuotes(List<Holding> quotes) {
        if (quotes == null) throw invalid("缺少行情数据");
        State next = snapshot();
        int updated = 0;
        for (Holding quote : quotes) {
            if (quote == null) throw invalid("行情数据无效");
            Holding target = null;
            for (Holding holding : next.holdings) if (holding.id.equals(quote.id)) { target = holding; break; }
            if (target == null) continue; // The position may have been deleted while the request was in flight.
            if (!isValidQuoteTime(quote.quoteTime) || quote.lastPriceMicros <= 0) throw invalid("行情数据无效");
            if (!target.quoteTime.isEmpty() && quote.quoteTime.compareTo(target.quoteTime) < 0) continue;
            target.name = clean(quote.name, 80, "证券名称");
            target.lastPriceMicros = quote.lastPriceMicros;
            target.previousCloseMicros = quote.previousCloseMicros;
            target.quoteTime = quote.quoteTime;
            updated++;
        }
        if (updated > 0) commit(next);
        return updated;
    }

    public synchronized void deleteHolding(String holdingId) {
        State next = snapshot();
        Holding holding = findHolding(next.holdings, holdingId);
        next.holdings.remove(holding);
        findAccount(next.accounts, holding.accountId).updatedAt = now();
        history(next, "holding_deleted", "移除持仓", holding.name + " " + holding.code + " · 可用余额未变更", 0);
        commit(next);
    }

    public synchronized void transfer(String fromId, String toId, long amountFen, String note) {
        if (fromId != null && fromId.equals(toId)) throw invalid("转出和转入账户不能相同");
        if (amountFen <= 0 || amountFen > MAX_MONEY_FEN) throw invalid("转账金额必须大于零且在支持范围内");
        State next = snapshot();
        Account from = findAccount(next.accounts, fromId);
        Account to = findAccount(next.accounts, toId);
        if (from.cashFen < amountFen) throw invalid("转出账户可用余额不足");
        from.cashFen = subtract(from.cashFen, amountFen);
        to.cashFen = add(to.cashFen, amountFen);
        from.updatedAt = to.updatedAt = now();
        String memo = note == null ? "" : note.trim();
        if (memo.length() > 200) throw invalid("转账备注不能超过 200 字");
        history(next, "transfer", "账户转账", from.name + " → " + to.name + (memo.isEmpty() ? "" : " · " + memo), amountFen);
        commit(next);
    }

    public synchronized Account accountById(String accountId) { return findAccount(accounts, accountId); }
    public synchronized Holding holdingById(String holdingId) { return findHolding(holdings, holdingId); }

    public synchronized long accountTotal(Account account) {
        if (account == null) throw invalid("账户不存在");
        long total = account.cashFen;
        for (Holding holding : holdings) if (holding.accountId.equals(account.id)) total = add(total, holdingValue(holding));
        return total;
    }

    public synchronized long totalAssets() { return add(totalCash(), totalMarketValue()); }
    public synchronized long totalCash() {
        long total = 0;
        for (Account account : accounts) total = add(total, account.cashFen);
        return total;
    }
    public synchronized long totalMarketValue() {
        long total = 0;
        for (Holding holding : holdings) total = add(total, holdingValue(holding));
        return total;
    }
    public static boolean isQuoted(Holding holding) {
        return holding != null && holding.lastPriceMicros > 0 && holding.quoteTime != null && !holding.quoteTime.isEmpty();
    }
    public static boolean isValidQuoteTime(String value) {
        if (value == null || !value.matches("[0-9]{14}")) return false;
        try { LocalDateTime.parse(value, QUOTE_TIME_FORMAT); return true; }
        catch (DateTimeParseException error) { return false; }
    }
    public static long holdingValue(Holding holding) {
        return holdingPositionValue(isQuoted(holding) ? holding.lastPriceMicros : holding.costPriceMicros, holding);
    }
    public static long holdingCost(Holding holding) { return holdingPositionValue(holding.costPriceMicros, holding); }
    public static long holdingProfit(Holding holding) { return subtract(holdingValue(holding), holdingCost(holding)); }
    public static long holdingDailyChange(Holding holding) {
        if (!isQuoted(holding) || holding.previousCloseMicros <= 0) return 0;
        return subtract(holdingValue(holding), holdingPositionValue(holding.previousCloseMicros, holding));
    }
    public synchronized long dailyChange() {
        long total = 0;
        for (Holding holding : holdings) total = add(total, holdingDailyChange(holding));
        return total;
    }
    public synchronized long totalProfit() {
        long total = 0;
        for (Holding holding : holdings) total = add(total, holdingProfit(holding));
        return total;
    }

    public static long parseMoney(String value) { return parseDecimal(value, 2, MAX_MONEY_FEN, "金额最多保留 2 位小数"); }
    public static long parsePrice(String value) { return parseDecimal(value, 6, MAX_PRICE_MICROS, "单价最多保留 6 位小数"); }
    public static String normalizeSecurityCode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        String digits = value.replaceFirst("^(sh|sz|bj)", "");
        if (!digits.matches("[0-9]{6}")) throw invalid("请输入 6 位 A 股或 ETF 代码");
        String prefix;
        if (digits.startsWith("6") || digits.startsWith("5")) prefix = "sh";
        else if (digits.startsWith("0") || digits.startsWith("3") || digits.startsWith("15") || digits.startsWith("16")) prefix = "sz";
        else if (digits.startsWith("4") || digits.startsWith("8") || digits.startsWith("92")) prefix = "bj";
        else throw invalid("目前支持人民币 A 股和场内 ETF");
        if (value.length() > 6 && !value.startsWith(prefix)) throw invalid("代码与市场前缀不匹配");
        return prefix + digits;
    }
    public static String formatMoney(long fen) { return BigDecimal.valueOf(fen, 2).setScale(2).toPlainString(); }
    public static String formatPrice(long micros) {
        BigDecimal number = BigDecimal.valueOf(micros, 6).stripTrailingZeros();
        return number.setScale(Math.max(2, number.scale())).toPlainString();
    }
    public static long positionValue(long priceMicros, long quantity) {
        bounded(priceMicros, MAX_PRICE_MICROS, "单价");
        if (quantity < 0 || quantity > MAX_QUANTITY) throw invalid("持仓数量超出支持范围");
        try {
            long fen = BigDecimal.valueOf(priceMicros).multiply(BigDecimal.valueOf(quantity))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
            bounded(fen, MAX_MONEY_FEN, "持仓市值");
            return fen;
        } catch (ArithmeticException error) { throw invalid("持仓市值超出支持范围"); }
    }

    public synchronized String exportJson() { return encode(snapshot()); }
    public synchronized String exportData() { return exportJson(); }
    /** Parse and validate the complete backup before replacing either memory or the atomic file. */
    public synchronized void importJson(String json) {
        State next = decode(json);
        persist(next);
        apply(next);
        loadFailed = false;
    }
    public synchronized void importData(String json) { importJson(json); }

    /** Explicit demo action only; never called during construction or initial load. */
    public synchronized void loadDemo() {
        if (!accounts.isEmpty() || !holdings.isEmpty()) throw invalid("仅能在空账本中载入示例");
        State next = new State();
        Account bank = new Account();
        bank.id = id(); bank.name = "示例 · 大额存款"; bank.kind = "bank"; bank.cashFen = 35_000_000; bank.updatedAt = now();
        Account broker = new Account();
        broker.id = id(); broker.name = "示例 · 股票账户"; broker.kind = "securities"; broker.cashFen = 8_000_000; broker.updatedAt = now();
        next.accounts.add(bank); next.accounts.add(broker);
        Holding fund = new Holding();
        fund.id = id(); fund.accountId = broker.id; fund.code = "sh510300"; fund.name = "沪深300ETF（示例）";
        fund.quantity = 10_000; fund.costPriceMicros = 4_000_000;
        next.holdings.add(fund);
        history(next, "demo", "载入示例", "示例金额，持仓以录入成本估值；请按自己的实际情况修改。", 0);
        commit(next);
    }

    private void commit(State next) {
        requireWritable();
        upsertToday(next);
        persist(next);
        apply(next);
    }

    private boolean upsertToday(State state) {
        if (state.accounts.isEmpty() && state.holdings.isEmpty() && state.dailySnapshots.isEmpty()) return false;
        DailySnapshot value = new DailySnapshot();
        value.recordedAt = clock.millis();
        value.day = dayAt(value.recordedAt);
        for (Account account : state.accounts) value.cashFen = add(value.cashFen, account.cashFen);
        for (Holding holding : state.holdings) {
            value.marketFen = add(value.marketFen, holdingValue(holding));
            value.costFen = add(value.costFen, holdingCost(holding));
            if (!isQuoted(holding)) value.unquotedCount++;
            else if (value.oldestQuoteTime.isEmpty() || holding.quoteTime.compareTo(value.oldestQuoteTime) < 0) value.oldestQuoteTime = holding.quoteTime;
        }
        value.totalFen();
        int index = 0;
        while (index < state.dailySnapshots.size() && state.dailySnapshots.get(index).day.compareTo(value.day) < 0) index++;
        if (index < state.dailySnapshots.size() && state.dailySnapshots.get(index).day.equals(value.day)) {
            DailySnapshot previous = state.dailySnapshots.get(index);
            if (previous.cashFen == value.cashFen && previous.marketFen == value.marketFen && previous.costFen == value.costFen
                    && previous.unquotedCount == value.unquotedCount && previous.oldestQuoteTime.equals(value.oldestQuoteTime)) return false;
            state.dailySnapshots.set(index, value);
        } else {
            if (state.dailySnapshots.size() >= MAX_DAILY_SNAPSHOTS) throw invalid("每日资产记录已达上限，请先导出备份");
            state.dailySnapshots.add(index, value);
        }
        return true;
    }
    private void requireWritable() {
        if (loadFailed) throw new IllegalStateException("本地数据读取失败，已停止写入以保护原文件；请先导入有效备份。");
    }
    private void persist(State next) {
        byte[] bytes = encode(next).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw invalid("账本数据过大");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (IOException error) {
            if (output != null) file.failWrite(output);
            throw new IllegalStateException("保存失败，原账本已保留。请检查设备剩余空间。", error);
        }
    }
    private State snapshot() {
        State state = new State();
        for (Account old : accounts) {
            Account copy = new Account(); copy.id = old.id; copy.name = old.name; copy.kind = old.kind; copy.icon=old.icon; copy.cashFen = old.cashFen; copy.updatedAt = old.updatedAt;
            state.accounts.add(copy);
        }
        for (Holding old : holdings) {
            Holding copy = new Holding(); copy.id = old.id; copy.accountId = old.accountId; copy.code = old.code; copy.name = old.name; copy.quantity = old.quantity; copy.assetType=old.assetType;copy.quantityDecimal=old.quantityDecimal;
            copy.costPriceMicros = old.costPriceMicros; copy.lastPriceMicros = old.lastPriceMicros; copy.previousCloseMicros = old.previousCloseMicros; copy.quoteTime = old.quoteTime;
            state.holdings.add(copy);
        }
        for (Entry old : entries) {
            Entry copy = new Entry(); copy.id = old.id; copy.type = old.type; copy.title = old.title; copy.detail = old.detail; copy.amountFen = old.amountFen; copy.time = old.time;
            state.entries.add(copy);
        }
        for (DailySnapshot old : dailySnapshots) {
            DailySnapshot copy = new DailySnapshot(); copy.day = old.day; copy.recordedAt = old.recordedAt;
            copy.cashFen = old.cashFen; copy.marketFen = old.marketFen; copy.costFen = old.costFen;
            copy.unquotedCount = old.unquotedCount; copy.oldestQuoteTime = old.oldestQuoteTime;
            state.dailySnapshots.add(copy);
        }
        return state;
    }
    private void apply(State state) {
        accounts.clear(); accounts.addAll(state.accounts);
        holdings.clear(); holdings.addAll(state.holdings);
        entries.clear(); entries.addAll(state.entries);
        dailySnapshots.clear(); dailySnapshots.addAll(state.dailySnapshots);
    }
    private static String encode(State state) {
        validate(state);
        try {
            JSONObject root = new JSONObject();
            root.put("format", "qianwang-private-wealth"); root.put("version", SCHEMA_VERSION); root.put("currency", "CNY");
            root.put("exportedAt", now());
            JSONArray accountArray = new JSONArray();
            for (Account account : state.accounts) {
                JSONObject item = new JSONObject(); item.put("id", account.id); item.put("name", account.name); item.put("kind", account.kind);item.put("icon",account.icon);
                item.put("cashFen", account.cashFen); item.put("updatedAt", account.updatedAt); accountArray.put(item);
            }
            JSONArray holdingArray = new JSONArray();
            for (Holding holding : state.holdings) {
                JSONObject item = new JSONObject(); item.put("id", holding.id); item.put("accountId", holding.accountId); item.put("code", holding.code); item.put("name", holding.name);
                item.put("quantity", holding.quantity);item.put("assetType",holding.assetType);item.put("quantityDecimal",holding.quantityDecimal); item.put("costPriceMicros", holding.costPriceMicros); item.put("lastPriceMicros", holding.lastPriceMicros);
                item.put("previousCloseMicros", holding.previousCloseMicros); item.put("quoteTime", holding.quoteTime); holdingArray.put(item);
            }
            JSONArray entryArray = new JSONArray();
            for (Entry entry : state.entries) {
                JSONObject item = new JSONObject(); item.put("id", entry.id); item.put("type", entry.type); item.put("title", entry.title); item.put("detail", entry.detail);
                item.put("amountFen", entry.amountFen); item.put("time", entry.time); entryArray.put(item);
            }
            JSONArray dailyArray = new JSONArray();
            for (DailySnapshot daily : state.dailySnapshots) {
                JSONObject item = new JSONObject(); item.put("day", daily.day); item.put("recordedAt", daily.recordedAt);
                item.put("cashFen", daily.cashFen); item.put("marketFen", daily.marketFen); item.put("costFen", daily.costFen);
                item.put("unquotedCount", daily.unquotedCount); item.put("oldestQuoteTime", daily.oldestQuoteTime); dailyArray.put(item);
            }
            root.put("accounts", accountArray); root.put("holdings", holdingArray); root.put("entries", entryArray);
            root.put("dailySnapshots", dailyArray);
            return root.toString(2);
        } catch (JSONException error) { throw new IllegalStateException("无法序列化账本", error); }
    }
    private static State decode(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw invalid("备份文件为空或过大");
        try {
            JSONObject root = new JSONObject(json);
            long version = integer(root, "version");
            if (!"qianwang-private-wealth".equals(string(root, "format")) || (version != 1 && version != 2 && version != SCHEMA_VERSION) || !"CNY".equals(string(root, "currency"))) throw invalid("不支持此备份格式、版本或币种");
            State state = new State();
            JSONArray accountArray = root.getJSONArray("accounts");
            JSONArray holdingArray = root.getJSONArray("holdings");
            JSONArray entryArray = root.getJSONArray("entries");
            if (accountArray.length() > MAX_ACCOUNTS || holdingArray.length() > MAX_HOLDINGS || entryArray.length() > MAX_HISTORY) throw invalid("备份记录数量超出支持范围");
            for (int i = 0; i < accountArray.length(); i++) {
                JSONObject item = accountArray.getJSONObject(i); Account account = new Account();
                account.id = string(item, "id"); account.name = string(item, "name"); account.kind = string(item, "kind");account.icon=version>=3?string(item,"icon"):"auto";
                account.cashFen = integer(item, "cashFen"); account.updatedAt = integer(item, "updatedAt"); state.accounts.add(account);
            }
            for (int i = 0; i < holdingArray.length(); i++) {
                JSONObject item = holdingArray.getJSONObject(i); Holding holding = new Holding();
                holding.id = string(item, "id"); holding.accountId = string(item, "accountId"); holding.assetType=version>=3?string(item,"assetType"):"stock";holding.quantityDecimal=version>=3?string(item,"quantityDecimal"):"";holding.code = isCrypto(holding)?normalizeCryptoCode(string(item,"code")):normalizeSecurityCode(string(item, "code")); holding.name = string(item, "name");
                holding.quantity = integer(item, "quantity"); holding.costPriceMicros = integer(item, "costPriceMicros"); holding.lastPriceMicros = integer(item, "lastPriceMicros");
                holding.previousCloseMicros = integer(item, "previousCloseMicros"); holding.quoteTime = string(item, "quoteTime"); state.holdings.add(holding);
            }
            for (int i = 0; i < entryArray.length(); i++) {
                JSONObject item = entryArray.getJSONObject(i); Entry entry = new Entry();
                entry.id = string(item, "id"); entry.type = string(item, "type"); entry.title = string(item, "title"); entry.detail = string(item, "detail");
                entry.amountFen = integer(item, "amountFen"); entry.time = integer(item, "time"); state.entries.add(entry);
            }
            if (version >= 2) {
                JSONArray dailyArray = root.getJSONArray("dailySnapshots");
                if (dailyArray.length() > MAX_DAILY_SNAPSHOTS) throw invalid("每日资产记录数量超出支持范围");
                for (int i = 0; i < dailyArray.length(); i++) {
                    JSONObject item = dailyArray.getJSONObject(i); DailySnapshot daily = new DailySnapshot();
                    daily.day = string(item, "day"); daily.recordedAt = integer(item, "recordedAt");
                    daily.cashFen = integer(item, "cashFen"); daily.marketFen = integer(item, "marketFen"); daily.costFen = integer(item, "costFen");
                    long unquotedCount = integer(item, "unquotedCount"); bounded(unquotedCount, MAX_HOLDINGS, "未报价持仓数量");
                    daily.unquotedCount = (int) unquotedCount; daily.oldestQuoteTime = string(item, "oldestQuoteTime");
                    state.dailySnapshots.add(daily);
                }
            }
            validate(state);
            return state;
        } catch (JSONException error) { throw new IllegalArgumentException("备份内容无效或字段不完整", error); }
    }
    private static void validate(State state) {
        if (state.accounts.size() > MAX_ACCOUNTS || state.holdings.size() > MAX_HOLDINGS || state.entries.size() > MAX_HISTORY || state.dailySnapshots.size() > MAX_DAILY_SNAPSHOTS) throw invalid("记录数量超出支持范围");
        Set<String> ids = new HashSet<>(); Set<String> positions = new HashSet<>();
        long cashTotal = 0; long marketTotal = 0; long costTotal = 0; long previousTotal = 0;
        for (Account account : state.accounts) {
            unique(ids, account.id); clean(account.name, 40, "账户名称");
            if (!"bank".equals(account.kind) && !"securities".equals(account.kind) && !"cash".equals(account.kind) && !"crypto".equals(account.kind)) throw invalid("账户类型无效");
            if(!java.util.Arrays.asList(ICON_IDS).contains(account.icon))throw invalid("账户图标无效");
            bounded(account.cashFen, MAX_MONEY_FEN, "账户余额"); timestamp(account.updatedAt); cashTotal = add(cashTotal, account.cashFen);
        }
        for (Holding holding : state.holdings) {
            unique(ids, holding.id); clean(holding.name, 80, "证券名称");
            if (!"stock".equals(holding.assetType)&&!isCrypto(holding))throw invalid("持仓类型无效");
            if (!(isCrypto(holding)?normalizeCryptoCode(holding.code):normalizeSecurityCode(holding.code)).equals(holding.code)) throw invalid("证券代码须包含对应市场前缀");
            if (!(isCrypto(holding)?"crypto":"securities").equals(findAccount(state.accounts, holding.accountId).kind)) throw invalid("持仓必须属于证券账户");
            if (!positions.add(holding.accountId + ":" + holding.code)) throw invalid("同一账户包含重复证券");
            if(isCrypto(holding)){parseCoinQuantity(holding.quantityDecimal);if(holding.quantity!=0)throw invalid("数字货币使用小数数量字段");}else if (holding.quantity <= 0 || holding.quantity > MAX_QUANTITY) throw invalid("持仓数量须为支持范围内的正整数");
            bounded(holding.costPriceMicros, MAX_PRICE_MICROS, "成本单价"); bounded(holding.lastPriceMicros, MAX_PRICE_MICROS, "行情单价"); bounded(holding.previousCloseMicros, MAX_PRICE_MICROS, "昨收单价");
            if (holding.quoteTime == null || (!holding.quoteTime.isEmpty() && !isValidQuoteTime(holding.quoteTime))) throw invalid("行情时间格式无效");
            if (!holding.quoteTime.isEmpty() && holding.lastPriceMicros <= 0) throw invalid("有行情时间的证券须包含有效价格");
            marketTotal = add(marketTotal, holdingValue(holding)); costTotal = add(costTotal, holdingCost(holding));
            previousTotal = add(previousTotal, holdingPositionValue(holding.previousCloseMicros, holding));
        }
        add(cashTotal, marketTotal);
        for (Entry entry : state.entries) {
            unique(ids, entry.id); clean(entry.type, 40, "记录类型"); clean(entry.title, 80, "记录标题");
            if (entry.detail == null || entry.detail.length() > 500) throw invalid("记录详情无效");
            if (entry.amountFen < -MAX_MONEY_FEN || entry.amountFen > MAX_MONEY_FEN) throw invalid("记录金额超出支持范围");
            timestamp(entry.time);
        }
        String previousDay = "";
        for (DailySnapshot daily : state.dailySnapshots) {
            if (daily.day == null || !daily.day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("每日资产日期格式无效");
            try { LocalDate.parse(daily.day, DAY_FORMAT); }
            catch (DateTimeParseException error) { throw invalid("每日资产日期无效"); }
            if (daily.day.compareTo(previousDay) <= 0) throw invalid("每日资产记录须按日期递增且不可重复");
            timestamp(daily.recordedAt);
            if (!dayAt(daily.recordedAt).equals(daily.day)) throw invalid("每日资产日期与记录时间不一致");
            bounded(daily.cashFen, MAX_MONEY_FEN, "历史可用余额"); bounded(daily.marketFen, MAX_MONEY_FEN, "历史持仓市值");
            bounded(daily.costFen, MAX_MONEY_FEN, "历史持仓成本"); daily.totalFen();
            bounded(daily.unquotedCount, MAX_HOLDINGS, "未报价持仓数量");
            if (daily.oldestQuoteTime == null || (!daily.oldestQuoteTime.isEmpty() && !isValidQuoteTime(daily.oldestQuoteTime))) throw invalid("历史行情时间格式无效");
            previousDay = daily.day;
        }
    }
    private static long parseDecimal(String text, int scale, long max, String message) {
        String value = text == null ? "" : text.trim();
        if (value.length() > 40 || !value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) throw invalid("请输入非负数字");
        try {
            BigDecimal number = new BigDecimal(value);
            if (number.scale() > scale) throw invalid(message);
            long units = number.movePointRight(scale).longValueExact(); bounded(units, max, "金额"); return units;
        } catch (ArithmeticException error) { throw invalid("金额超出支持范围"); }
    }
    private static long integer(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw invalid("备份中的 " + key + " 必须为整数");
        return ((Number) value).longValue();
    }
    private static String string(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw invalid("备份中的 " + key + " 必须为文本");
        return (String) value;
    }
    private static void history(State state, String type, String title, String detail, long amountFen) {
        Entry entry = new Entry(); entry.id = id(); entry.type = type; entry.title = title; entry.detail = detail; entry.amountFen = amountFen; entry.time = now();
        state.entries.add(0, entry);
        while (state.entries.size() > MAX_HISTORY) state.entries.remove(state.entries.size() - 1);
    }
    private static Account findAccount(List<Account> list, String id) {
        for (Account account : list) if (account.id.equals(id)) return account;
        throw invalid("账户不存在");
    }
    private static Holding findHolding(List<Holding> list, String id) {
        for (Holding holding : list) if (holding.id.equals(id)) return holding;
        throw invalid("持仓不存在");
    }
    private static String clean(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) throw invalid(label + "须为 1–" + max + " 字");
        return text;
    }
    private static void unique(Set<String> ids, String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(id)) throw invalid("记录 ID 无效或重复");
    }
    private static void bounded(long value, long max, String label) {
        if (value < 0 || value > max) throw invalid(label + "超出支持范围");
    }
    private static void timestamp(long value) { if (value < 0 || value > 253402300799999L) throw invalid("记录时间无效"); }
    private static long add(long left, long right) {
        try {
            long result = Math.addExact(left, right);
            if (result < -MAX_MONEY_FEN || result > MAX_MONEY_FEN) throw invalid("资产总额超出支持范围");
            return result;
        } catch (ArithmeticException error) { throw invalid("资产总额超出支持范围"); }
    }
    private static long subtract(long left, long right) {
        try {
            long result = Math.subtractExact(left, right);
            if (result < -MAX_MONEY_FEN || result > MAX_MONEY_FEN) throw invalid("资产变动超出支持范围");
            return result;
        } catch (ArithmeticException error) { throw invalid("资产变动超出支持范围"); }
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static long now() { return System.currentTimeMillis(); }
    private static String dayAt(long time) { return Instant.ofEpochMilli(time).atZone(RECORD_ZONE).toLocalDate().format(DAY_FORMAT); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
