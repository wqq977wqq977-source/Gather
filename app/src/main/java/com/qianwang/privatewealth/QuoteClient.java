package com.qianwang.privatewealth;

import java.io.*;
import java.net.*;
import java.math.*;
import java.util.*;
import java.util.regex.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Read-only quotes. The endpoint receives security codes, never account names or balances. */
public final class QuoteClient {
    public static final String SOURCE = "腾讯行情";
    public static final class Quote {
        public String code, name, time;
        public long priceMicros, previousMicros;
    }
    public static String normalizeCode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String digits = value.replaceFirst("^(sh|sz|bj)", "");
        if (!digits.matches("[0-9]{6}")) throw new IllegalArgumentException("请输入 6 位 A 股或 ETF 代码");
        String prefix;
        if (digits.startsWith("6") || digits.startsWith("5")) prefix = "sh";
        else if (digits.startsWith("0") || digits.startsWith("3") || digits.startsWith("15") || digits.startsWith("16")) prefix = "sz";
        else if (digits.startsWith("4") || digits.startsWith("8") || digits.startsWith("92")) prefix = "bj";
        else throw new IllegalArgumentException("目前支持人民币 A 股和场内 ETF");
        if (value.length() > 6 && !value.startsWith(prefix)) throw new IllegalArgumentException("代码与市场前缀不匹配");
        return prefix + digits;
    }
    public static Map<String, Quote> fetchStocks(Collection<String> codes) throws IOException {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String code : codes) unique.add(normalizeCode(code));
        if (unique.isEmpty()) return Collections.emptyMap();
        Map<String,Quote> all = new LinkedHashMap<>();
        List<String> items = new ArrayList<>(unique);
        for (int offset=0; offset<items.size(); offset+=50) {
            String query = String.join(",", items.subList(offset, Math.min(offset+50, items.size())));
            HttpURLConnection con = (HttpURLConnection)new URL("https://qt.gtimg.cn/q=" + query).openConnection();
            con.setConnectTimeout(12000); con.setReadTimeout(12000); con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "Gather/0.7.0 (Android; personal portfolio)");
            try {
                if(con.getResponseCode()!=200) throw new IOException("行情服务返回 " + con.getResponseCode());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try(InputStream in=con.getInputStream()) {
                    byte[] b=new byte[4096]; int n;
                    while((n=in.read(b))!=-1) { out.write(b,0,n); if(out.size()>1024*1024) throw new IOException("行情响应过大"); }
                }
                Map<String,Quote> parsed=parse(new String(out.toByteArray(),"GB18030"));
                for(String code:unique) if(parsed.containsKey(code)) all.put(code,parsed.get(code));
            } finally { con.disconnect(); }
        }
        if(all.isEmpty()) throw new IOException("未取得有效行情，请检查代码或稍后重试");
        return all;
    }
    public static Map<String,Quote> fetch(Collection<String> codes) throws IOException {
        List<String> stocks=new ArrayList<>(),coins=new ArrayList<>();for(String code:codes)if(code.startsWith("crypto:"))coins.add(WealthStore.normalizeCryptoCode(code));else stocks.add(code);
        Map<String,Quote> result=new LinkedHashMap<>();IOException failure=null;
        try{result.putAll(fetchStocks(stocks));}catch(IOException e){failure=e;}
        if(!coins.isEmpty())try{List<String> ids=new ArrayList<>();for(String code:new LinkedHashSet<>(coins))ids.add(code.substring(7));String body=read("https://api.coingecko.com/api/v3/simple/price?ids="+String.join(",",ids)+"&vs_currencies=cny&include_24hr_change=true&include_last_updated_at=true");result.putAll(parseCrypto(body,coins));}catch(Exception e){failure=new IOException("数字货币行情暂时不可用",e);}
        if(result.isEmpty()&&!codes.isEmpty())throw failure==null?new IOException("未取得有效行情"):failure;return result;
    }
    public static Map<String,Quote> parseCrypto(String body,Collection<String> codes) {
        Map<String,Quote> result=new LinkedHashMap<>();
        try{org.json.JSONObject root=new org.json.JSONObject(body);for(String raw:codes)try{
            String code=WealthStore.normalizeCryptoCode(raw);org.json.JSONObject item=root.getJSONObject(code.substring(7));
            BigDecimal value=new BigDecimal(item.get("cny").toString());long micros=value.movePointRight(6).setScale(0,RoundingMode.HALF_UP).longValueExact();if(micros<=0||micros>WealthStore.MAX_PRICE_MICROS)continue;
            long stamp=item.getLong("last_updated_at");if(stamp<946684800L||stamp>System.currentTimeMillis()/1000+300)continue;
            Quote q=new Quote();q.code=code;q.name=WealthStore.coinSymbol(code);q.priceMicros=micros;q.time=java.time.Instant.ofEpochSecond(stamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            if(!item.isNull("cny_24h_change")){BigDecimal change=new BigDecimal(item.get("cny_24h_change").toString());BigDecimal ratio=BigDecimal.ONE.add(change.movePointLeft(2));if(ratio.signum()>0){long previous=BigDecimal.valueOf(micros).divide(ratio,0,RoundingMode.HALF_UP).longValueExact();if(previous>0&&previous<=WealthStore.MAX_PRICE_MICROS)q.previousMicros=previous;}}
            result.put(code,q);
        }catch(Exception ignored){/* One malformed coin does not invalidate other quotes. */}}catch(org.json.JSONException ignored){}return result;
    }
    public static String read(String url) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Gather/0.7.0 Android");
        try{if(c.getResponseCode()!=200)throw new IOException("数据服务返回 "+c.getResponseCode());ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("响应过大");}}return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}finally{c.disconnect();}
    }
    public static String[] fetchUsdRate() throws Exception {
        org.json.JSONObject j=new org.json.JSONObject(read("https://api.frankfurter.dev/v1/latest?base=USD&symbols=CNY"));if(!"USD".equals(j.getString("base")))throw new IOException("汇率基准不匹配");String rate=j.getJSONObject("rates").get("CNY").toString();DisplayMoney.parseRate(rate);String date=j.getString("date");java.time.LocalDate.parse(date);return new String[]{rate,date};
    }
    public static Map<String, Quote> parse(String body) {
        Map<String,Quote> quotes=new LinkedHashMap<>();
        Matcher matcher=Pattern.compile("v_((?:sh|sz|bj)[0-9]{6})=\"([^\"]*)\"").matcher(body);
        while(matcher.find()) {
            String[] fields=matcher.group(2).split("~",-1);
            if(fields.length<31 || !fields[2].equals(matcher.group(1).substring(2)) || !fields[30].matches("[0-9]{14}")) continue;
            try {
                LocalDateTime.parse(fields[30],DateTimeFormatter.ofPattern("uuuuMMddHHmmss",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));
                Quote q=new Quote(); q.code=normalizeCode(matcher.group(1)); q.name=fields[1]; q.time=fields[30];
                q.priceMicros=price(fields[3]); q.previousMicros=price(fields[4]);
                if(q.priceMicros<=0 || q.previousMicros<=0 || q.name.isEmpty() || q.name.length()>80) continue;
                quotes.put(q.code,q);
            } catch (RuntimeException ignored) { /* An invalid item never overwrites a valid cached quote. */ }
        }
        return quotes;
    }
    private static long price(String value) {
        long result=new BigDecimal(value).movePointRight(6).setScale(0,RoundingMode.UNNECESSARY).longValueExact();
        if(result<0 || result>1_000_000_000_000L) throw new IllegalArgumentException("报价超出范围");
        return result;
    }
}
