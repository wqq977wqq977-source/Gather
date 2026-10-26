package com.qianwang.privatewealth;
import java.util.*;
public final class AssetSankeyTest {
    private static int checks;
    private static void equal(long expected,long actual,String message){checks++;if(expected!=actual)throw new AssertionError(message+": "+expected+" != "+actual);}
    private static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static WealthStore.Account account(String id,String kind,long cash){WealthStore.Account a=new WealthStore.Account();a.id=id;a.name=id;a.kind=kind;a.cashFen=cash;return a;}
    private static WealthStore.Holding holding(String account,long quantity,long cost,long price){WealthStore.Holding h=new WealthStore.Holding();h.accountId=account;h.quantity=quantity;h.costPriceMicros=cost;h.lastPriceMicros=price;h.quoteTime=price>0?"20261023150000":"";return h;}
    private static void conserved(AssetSankey data){long left=0,right=0;for(AssetSankey.AccountNode n:data.accounts){long sum=0;for(int k=0;k<4;k++)sum+=n.value(k);equal(n.totalFen,sum,"account ribbon conservation");left+=sum;}for(int k=0;k<4;k++){long sum=0;for(AssetSankey.AccountNode n:data.accounts)sum+=n.value(k);equal(data.categoryValue(k),sum,"category ribbon conservation");right+=sum;}equal(data.totalFen,left,"left equals total");equal(left,right,"both columns agree");}
    public static void main(String[] args){
        List<WealthStore.Account> accounts=Arrays.asList(account("bank","bank",68000000),account("broker","securities",8000000),account("reserve","cash",12000000),account("zero","bank",0));
        List<WealthStore.Holding> holdings=Arrays.asList(holding("broker",1000,330000000,364640000),holding("broker",10000,4100000,4178000));
        AssetSankey data=AssetSankey.build(accounts,holdings);equal(128642000,data.totalFen,"cash plus market exactly once");equal(40642000,data.categoryValue(1),"market");equal(8000000,data.categoryValue(2),"broker cash separate");equal(1,data.zeroAccounts,"zero account counted");equal(3,data.accounts.size(),"zero omitted");conserved(data);
        AssetSankey fallback=AssetSankey.build(Collections.singletonList(account("cash-stock","securities",100)),Collections.singletonList(holding("cash-stock",3,4123456,0)));equal(1337,fallback.totalFen,"unquoted holding cost with fen rounding");conserved(fallback);
        List<WealthStore.Account> many=new ArrayList<>();for(int i=0;i<9;i++)many.add(account("a"+i,i%2==0?"bank":"cash",100+i));
        AssetSankey grouped=AssetSankey.build(many,Collections.emptyList());equal(6,grouped.accounts.size(),"bounded nodes");yes(grouped.accounts.get(5).isGroup(),"remainder group");equal(4,grouped.accounts.get(5).accountIds.size(),"all remainder ids retained");equal(406,grouped.accounts.get(5).totalFen,"remainder exact amount");equal(108,grouped.accounts.get(0).totalFen,"largest first");conserved(grouped);
        AssetSankey zeros=AssetSankey.build(Collections.singletonList(account("empty","bank",0)),Collections.emptyList());equal(0,zeros.totalFen,"zero total");equal(0,zeros.accounts.size(),"no false ribbons");yes("—".equals(zeros.share(0)),"zero denominator");conserved(zeros);
        AssetSankey large=AssetSankey.build(Arrays.asList(account("big","bank",8000000000000000L),account("tiny","cash",1)),Collections.emptyList());equal(8000000000000001L,large.totalFen,"integer precision");yes("<0.1%".equals(large.share(1)),"tiny positive is not zero percent");conserved(large);
        try{AssetSankey.build(Collections.emptyList(),holdings);throw new AssertionError("orphan accepted");}catch(IllegalArgumentException expected){checks++;}
        yes("1.00亿".equals(AssetSankey.compact(10000000000L)),"compact yi");yes("1.00万".equals(AssetSankey.compact(1000000L)),"compact wan");yes("0.01".equals(AssetSankey.compact(1)),"one fen");
        System.out.println("PASS "+checks+" Sankey assertions: conservation, grouping, zero, cost fallback, precision, labels");
    }
}
