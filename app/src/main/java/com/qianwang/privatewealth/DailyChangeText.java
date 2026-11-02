package com.qianwang.privatewealth;

import java.util.ArrayList;
import java.util.List;

/** Shared, display-only explanation of the difference between observed asset snapshots. */
public final class DailyChangeText {
    private DailyChangeText() {}

    public static String describe(WealthStore.DailySnapshot previous,
            WealthStore.DailySnapshot current, DisplayMoney money, boolean hidden) {
        if (hidden) return "变化来源与金额已隐藏";
        if (previous == null) {
            return current.incomeFen > 0
                    ? "首条记录 · 已记收入 " + money.signed(current.incomeFen)
                    : "首条记录，暂无前日对比";
        }
        List<String> parts = new ArrayList<>();
        append(parts, "收入", current.incomeFen, money);
        append(parts, "余额调整", current.cashAdjustmentFen, money);
        append(parts, "持仓调整", current.holdingAdjustmentFen, money);
        long other = WealthStore.otherChangeFen(previous, current);
        append(parts, current.sourcesKnown ? "行情 / 估值" : "未分类变化", other, money);
        if (!current.sourcesKnown) parts.add("部分来源未记录");
        if (parts.isEmpty()) return "无资产变化";
        return String.join(" · ", parts);
    }

    private static void append(List<String> parts, String label, long fen, DisplayMoney money) {
        if (fen != 0) parts.add(label + " " + money.signed(fen));
    }
}
