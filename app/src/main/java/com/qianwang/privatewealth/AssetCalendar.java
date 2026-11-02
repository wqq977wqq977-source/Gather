package com.qianwang.privatewealth;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Calendar projection of observed assets; absent days are never synthetic zeroes. */
public final class AssetCalendar {
    private AssetCalendar() {}

    public static final class Day {
        public final LocalDate date;
        public final WealthStore.DailySnapshot current, previous;
        Day(LocalDate date, WealthStore.DailySnapshot current, WealthStore.DailySnapshot previous) {
            this.date = date; this.current = current; this.previous = previous;
        }
        public boolean comparable() {
            return current != null && previous != null && LocalDate.parse(previous.day).plusDays(1).equals(date);
        }
        public boolean gap() { return current != null && previous != null && !comparable(); }
        public long delta() { return comparable() ? current.totalFen() - previous.totalFen() : 0; }
    }

    public static final class Month {
        public final YearMonth month;
        public final List<Day> days = new ArrayList<>();
        public int rising, falling, flat, gaps, recorded;
        public WealthStore.DailySnapshot baseline, last;
        Month(YearMonth month) { this.month = month; }
        public boolean comparable() { return baseline != null && last != null && !baseline.day.equals(last.day); }
        public long delta() { return comparable() ? last.totalFen() - baseline.totalFen() : 0; }
        public boolean startsAtBoundary() {
            return baseline != null && baseline.day.equals(month.atDay(1).minusDays(1).toString());
        }
    }

    /** Input snapshots are validated by WealthStore and sorted by date. */
    public static Month build(List<WealthStore.DailySnapshot> snapshots, YearMonth month) {
        Month result = new Month(month);
        WealthStore.DailySnapshot previous = null;
        int index = 0;
        String first = month.atDay(1).toString();
        while (index < snapshots.size() && snapshots.get(index).day.compareTo(first) < 0) previous = snapshots.get(index++);
        if (previous != null && previous.day.equals(month.atDay(1).minusDays(1).toString())) result.baseline = previous;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            WealthStore.DailySnapshot current = index < snapshots.size() && snapshots.get(index).day.equals(date.toString()) ? snapshots.get(index++) : null;
            Day cell = new Day(date, current, previous);
            result.days.add(cell);
            if (current == null) continue;
            result.recorded++;
            if (cell.comparable()) {
                if (cell.delta() > 0) result.rising++;
                else if (cell.delta() < 0) result.falling++;
                else result.flat++;
            } else if (cell.gap()) result.gaps++;
            if (result.baseline == null) result.baseline = current;
            result.last = current;
            previous = current;
        }
        return result;
    }
}
