package com.qianwang.privatewealth;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;

/** Calendar arithmetic: boundaries, absent observations, and zero baselines. */
public final class AssetCalendarTest {
    private static int checks;
    public static void main(String[] args) {
        AssetCalendar.Month empty=AssetCalendar.build(Collections.emptyList(),YearMonth.of(2024,2));
        eq(29,empty.days.size(),"leap February");eq(0,empty.recorded,"empty records");truth(!empty.comparable(),"empty has no return");
        eq(28,AssetCalendar.build(Collections.emptyList(),YearMonth.of(2025,2)).days.size(),"normal February");
        AssetCalendar.Month full=AssetCalendar.build(Arrays.asList(point("2026-10-31",10000),point("2026-11-01",11000),point("2026-11-02",9900),point("2026-11-03",9900)),YearMonth.of(2026,11));
        eq(1,full.rising,"up count");eq(1,full.falling,"down count");eq(1,full.flat,"flat count");eq(-100,full.delta(),"monthly change uses previous month end");truth(full.startsAtBoundary(),"exact monthly baseline");eq(1000,full.days.get(0).delta(),"first of month daily change");
        truth(full.days.get(3).current==null,"missing day stays absent");truth(!full.days.get(3).comparable(),"missing is not flat");
        AssetCalendar.Month gaps=AssetCalendar.build(Arrays.asList(point("2026-10-20",10000),point("2026-11-02",13000),point("2026-11-04",15000),point("2026-11-05",14000)),YearMonth.of(2026,11));
        eq(2,gaps.gaps,"gaps detected");eq(0,gaps.rising,"multi-day increase not counted as daily rise");eq(1,gaps.falling,"adjacent decline counted");eq(1000,gaps.delta(),"partial period excludes unknown October baseline");truth(!gaps.startsAtBoundary(),"partial month labelled");truth(gaps.days.get(1).gap(),"gap is tappable observation");
        AssetCalendar.Month one=AssetCalendar.build(Collections.singletonList(point("2026-11-01",5000)),YearMonth.of(2026,11));truth(!one.comparable(),"one observation no invented return");truth(!one.days.get(0).comparable(),"first observation no invented prior zero");
        AssetCalendar.Month zero=AssetCalendar.build(Arrays.asList(point("2026-10-31",0),point("2026-11-01",100)),YearMonth.of(2026,11));eq(100,zero.delta(),"zero baseline amount is valid");eq(1,zero.rising,"zero baseline still rises");eq(0,zero.baseline.totalFen(),"zero denominator retained for dash UI");
        AssetCalendar.Month year=AssetCalendar.build(Arrays.asList(point("2025-12-31",100),point("2026-01-01",200)),YearMonth.of(2026,1));eq(100,year.delta(),"year boundary");
        eq(24,WealthStore.ICON_IDS.length,"24 icons");eq(WealthStore.ICON_IDS.length,WealthStore.ICON_NAMES.length,"icon labels match");eq(24,new java.util.HashSet<>(Arrays.asList(WealthStore.ICON_IDS)).size(),"stable unique icon IDs");
        System.out.println("PASS "+checks+" calendar assertions");
    }
    private static WealthStore.DailySnapshot point(String day,long fen){WealthStore.DailySnapshot value=new WealthStore.DailySnapshot();value.day=day;value.cashFen=fen;return value;}
    private static void eq(long expected,long actual,String label){checks++;if(expected!=actual)throw new AssertionError(label+": "+actual+" != "+expected);}
    private static void truth(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
}
