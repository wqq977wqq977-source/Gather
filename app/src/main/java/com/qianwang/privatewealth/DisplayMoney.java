package com.qianwang.privatewealth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Display-only FX: the ledger and historical snapshots always stay in CNY. */
public final class DisplayMoney {
    public final boolean usd;
    public final BigDecimal cnyPerUsd;
    public DisplayMoney(boolean usd,String rate){this.usd=usd;this.cnyPerUsd=rate==null||rate.isEmpty()?BigDecimal.ZERO:parseRate(rate);if(usd&&cnyPerUsd.signum()==0)throw new IllegalArgumentException("美元显示需要有效汇率");}
    public static BigDecimal parseRate(String text){if(text==null||!text.matches("[0-9]+(?:\\.[0-9]{1,6})?"))throw new IllegalArgumentException("汇率须为正数，最多 6 位小数");BigDecimal r=new BigDecimal(text);if(r.compareTo(new BigDecimal("0.01"))<0||r.compareTo(new BigDecimal("1000"))>0)throw new IllegalArgumentException("请核对 1 美元对应的人民币金额");return r;}
    public String code(){return usd?"USD":"CNY";}
    public long fen(long cnyFen){return usd?BigDecimal.valueOf(cnyFen).divide(cnyPerUsd,0,RoundingMode.HALF_UP).longValueExact():cnyFen;}
    public String amount(long value){return String.format(Locale.CHINA,"%,.2f",BigDecimal.valueOf(fen(value),2));}
    public String compact(long value){return AssetSankey.compact(fen(value));}
    public String signed(long value){return (value>0?"+":"")+amount(value);}
    public String price(long micros){BigDecimal v=BigDecimal.valueOf(micros,6);if(usd)v=v.divide(cnyPerUsd,6,RoundingMode.HALF_UP);v=v.stripTrailingZeros();return v.setScale(Math.max(2,v.scale())).toPlainString();}
}
