package com.qianwang.privatewealth;

import java.util.Arrays;

/** Stable page identities keep icons attached to their page when the order changes. */
public final class NavigationLayout {
    private NavigationLayout() {}
    public static int[] defaults() { return new int[]{4,0,3,1}; }
    public static String label(int page) {
        switch(page) { case 4:return "日历";case 0:return "总览";case 3:return "统计";case 1:return "持仓";default:throw new IllegalArgumentException("未知页面"); }
    }
    public static String defaultIcon(int page) {
        switch(page) { case 4:return "calendar";case 0:return "overview";case 3:return "stats";case 1:return "hold";default:throw new IllegalArgumentException("未知页面"); }
    }
    public static final String[] ICON_IDS={"calendar","overview","stats","hold","bank","wallet","card","coin","safe","home","globe","briefcase","star","gift","shield","piggy","building","gold","car","plane","book","leaf","target","diamond","heart","cloud"};
    public static final String[] ICON_NAMES={"日历","总览","统计","投资","银行","钱包","银行卡","数字货币","保险箱","房屋","全球","公文包","星标","礼物","保障","储蓄罐","办公楼","黄金","汽车","旅行","教育","成长","目标","钻石","心愿","云端"};
    public static String icon(int page,String stored) { return Arrays.asList(ICON_IDS).contains(stored)?stored:defaultIcon(page); }
    public static int[] parse(String stored) {
        if(stored==null)return defaults();
        String[] parts=stored.split(",");if(parts.length!=4)return defaults();
        int[] order=new int[4];
        try{for(int i=0;i<4;i++)order[i]=Integer.parseInt(parts[i]);}catch(NumberFormatException ignored){return defaults();}
        int[] sorted=order.clone();Arrays.sort(sorted);
        return Arrays.equals(sorted,new int[]{0,1,3,4})?order:defaults();
    }
    public static String encode(int[] order) { return order[0]+","+order[1]+","+order[2]+","+order[3]; }
    public static int[] move(int[] order,int from,int step) {
        int[] next=order.clone();int to=from+step;
        if(from>=0&&from<next.length&&to>=0&&to<next.length){int page=next[from];next[from]=next[to];next[to]=page;}
        return next;
    }
}
