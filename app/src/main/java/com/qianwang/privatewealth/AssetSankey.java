package com.qianwang.privatewealth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A current allocation, never a transaction or cash-flow history. All amounts are fen. */
public final class AssetSankey {
    public static final String[] CATEGORY_NAMES = {"银行存款", "证券持仓", "证券现金", "其他资金", "数字货币"};
    public static final int MAX_NODES = 6;

    public static final class AccountNode {
        public final String label;
        public final List<String> accountIds;
        public final long totalFen;
        private final long[] values;

        private AccountNode(String label, List<String> ids, long[] values) {
            this.label = label;
            this.accountIds = Collections.unmodifiableList(new ArrayList<>(ids));
            this.values = values.clone();
            long sum = 0;
            for (long value : values) sum = Math.addExact(sum, value);
            this.totalFen = sum;
        }
        public long value(int category) { return values[category]; }
        public boolean isGroup() { return accountIds.size() > 1; }
    }

    public final List<AccountNode> accounts;
    public final long totalFen;
    public final int zeroAccounts;
    private final long[] categories;

    private AssetSankey(List<AccountNode> accounts, long[] categories, int zeroAccounts) {
        this.accounts = Collections.unmodifiableList(accounts);
        this.categories = categories.clone();
        this.zeroAccounts = zeroAccounts;
        long sum = 0;
        for (long value : categories) sum = Math.addExact(sum, value);
        this.totalFen = sum;
    }
    public long categoryValue(int category) { return categories[category]; }
    public int categoryCount() { int n=0; for(long v:categories) if(v>0)n++; return n; }

    public static AssetSankey build(List<WealthStore.Account> source, List<WealthStore.Holding> holdings) {
        Map<String, long[]> parts = new LinkedHashMap<>();
        for (WealthStore.Account account : source) {
            if (parts.containsKey(account.id) || account.cashFen < 0) throw new IllegalArgumentException("账户数据无效");
            long[] values = new long[CATEGORY_NAMES.length];
            int category = "bank".equals(account.kind) ? 0 : "securities".equals(account.kind) ? 2 : 3;
            values[category] = account.cashFen;
            parts.put(account.id, values);
        }
        for (WealthStore.Holding holding : holdings) {
            long[] values = parts.get(holding.accountId);
            if (values == null) throw new IllegalArgumentException("持仓所属账户不存在");
            long value = WealthStore.holdingValue(holding);
            if (value < 0) throw new IllegalArgumentException("持仓金额无效");
            int k=WealthStore.isCrypto(holding)?4:1;values[k] = Math.addExact(values[k], value);
        }
        List<AccountNode> nodes = new ArrayList<>();
        long[] categories = new long[CATEGORY_NAMES.length];
        int zero = 0;
        for (WealthStore.Account account : source) {
            long[] values = parts.get(account.id);
            AccountNode node = new AccountNode(account.name, Collections.singletonList(account.id), values);
            for (int k=0;k<CATEGORY_NAMES.length;k++) categories[k] = Math.addExact(categories[k], values[k]);
            if (node.totalFen > 0) nodes.add(node); else zero++;
        }
        // Stable sort preserves account order for equal amounts.
        Collections.sort(nodes, (a,b)->Long.compare(b.totalFen,a.totalFen));
        if (nodes.size() > MAX_NODES) {
            long[] remainder = new long[CATEGORY_NAMES.length]; List<String> ids = new ArrayList<>();
            for (int i=MAX_NODES-1;i<nodes.size();i++) {
                AccountNode node=nodes.get(i); ids.addAll(node.accountIds);
                for(int k=0;k<CATEGORY_NAMES.length;k++) remainder[k]=Math.addExact(remainder[k],node.value(k));
            }
            nodes = new ArrayList<>(nodes.subList(0, MAX_NODES-1));
            nodes.add(new AccountNode("其余 "+ids.size()+" 个账户",ids,remainder));
        }
        return new AssetSankey(nodes, categories, zero);
    }

    public static String money(long fen) { return String.format(java.util.Locale.CHINA,"%,.2f",BigDecimal.valueOf(fen,2)); }
    public static String compact(long fen) {
        if(fen>=10_000_000_000L)return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(10_000_000_000L),2,RoundingMode.HALF_UP).toPlainString()+"亿";
        if(fen>=1_000_000L)return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(1_000_000L),2,RoundingMode.HALF_UP).toPlainString()+"万";
        return money(fen);
    }
    public String share(long fen) {
        if(totalFen==0)return "—";
        BigDecimal value=BigDecimal.valueOf(fen).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalFen),1,RoundingMode.HALF_UP);
        return fen>0&&value.signum()==0?"<0.1%":value.toPlainString()+"%";
    }
}
