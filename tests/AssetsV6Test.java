package com.qianwang.privatewealth;
import java.util.*;
import java.io.File;
import android.content.ContextWrapper;
import org.json.JSONObject;
public final class AssetsV6Test {
 static int checks;
 static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 static void reject(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
 static void conserved(AssetTree.Node n){long sum=0;for(AssetTree.Node c:n.children){sum+=c.value;conserved(c);}if(!n.children.isEmpty())eq(n.value,sum);long visible=0;for(AssetTree.Node c:AssetTree.visible(n))visible+=c.value;if(!n.children.isEmpty())eq(n.value,visible);}
 public static void main(String[] args)throws Exception{
  eq(1,AssetTree.categoryColor("category:1"));eq(-1,AssetTree.categoryColor("category:1/rest"));eq(-1,AssetTree.categoryColor("account:1:0"));
  DisplayMoney usd=new DisplayMoney(true,"7.25");eq(10000L,usd.fen(72500));eq(-10000L,usd.fen(-72500));eq("100.00",usd.amount(72500));eq("1.00",usd.price(7250000));eq(72500L,new DisplayMoney(false,"").fen(72500));reject(()->new DisplayMoney(true,""));reject(()->DisplayMoney.parseRate("0"));reject(()->DisplayMoney.parseRate("NaN"));
  eq("0.025",WealthStore.parseCoinQuantity("0.02500000"));eq("0.00000001",WealthStore.parseCoinQuantity("0.00000001"));reject(()->WealthStore.parseCoinQuantity("0"));reject(()->WealthStore.parseCoinQuantity("1e3"));reject(()->WealthStore.parseCoinQuantity("0.000000001"));reject(()->WealthStore.parseCoinQuantity("-1"));
  WealthStore.Holding btc=new WealthStore.Holding();btc.id="btc";btc.accountId="wallet";btc.assetType="crypto";btc.code="crypto:bitcoin";btc.name="BTC";btc.quantityDecimal="0.025";btc.costPriceMicros=400000000000L;eq(1000000L,WealthStore.holdingValue(btc));eq("—",WealthStore.movementPercent(btc));btc.lastPriceMicros=500000000000L;btc.previousCloseMicros=400000000000L;btc.quoteTime="20261023150000";eq(1250000L,WealthStore.holdingValue(btc));eq("+25.00%",WealthStore.movementPercent(btc));eq(250000L,WealthStore.holdingDailyChange(btc));
  WealthStore.Account wallet=new WealthStore.Account();wallet.id="wallet";wallet.name="钱包";wallet.kind="crypto";wallet.cashFen=20000;
  List<WealthStore.Account> accounts=new ArrayList<>();accounts.add(wallet);for(int i=0;i<14;i++){WealthStore.Account a=new WealthStore.Account();a.id="bank"+i;a.name="银行"+i;a.kind="bank";a.cashFen=10000;accounts.add(a);}
  List<WealthStore.Holding> holdings=Collections.singletonList(btc);AssetTree.Node category=AssetTree.build(accounts,holdings,false),account=AssetTree.build(accounts,holdings,true);eq(1410000L,category.value);eq(category.value,account.value);conserved(category);conserved(account);eq(6,AssetTree.visible(account).size());eq(10,AssetTree.visible(account).get(5).children.size());eq("<0.1%",AssetTree.share(1,100000));
  if(args.length>0)android(new File(args[0]));System.out.println("PASS "+checks+" v6 assertions");
 }
 static void android(File dir)throws Exception{
  dir.mkdirs();WealthStore s=new WealthStore(new ContextWrapper(null){@Override public File getFilesDir(){return dir;}});s.load();WealthStore.Account a=s.addAccount("钱包","crypto",20000,"wallet");WealthStore.Holding h=s.addCryptoHolding(a.id,"BTC","0.025",400000000000L);eq(1020000L,s.totalAssets());reject(()->s.addCryptoHolding(a.id,"BTC","1",1));reject(()->s.updateCryptoHolding(h.id,"0",1));eq("0.025",s.holdingById(h.id).quantityDecimal);
  WealthStore.Holding update=new WealthStore.Holding();update.id=h.id;update.name="BTC";update.lastPriceMicros=500000000000L;update.previousCloseMicros=400000000000L;update.quoteTime="20261023150000";s.applyQuotes(Collections.singletonList(update));eq(1270000L,s.totalAssets());s.load();eq("0.025",s.holdingById(h.id).quantityDecimal);eq("+25.00%",WealthStore.movementPercent(s.holdingById(h.id)));reject(()->s.updateAccount(a.id,"钱包",20000,"unknown-icon"));eq("wallet",s.accountById(a.id).icon);
  s.updateCryptoHolding(h.id,"0.00000001",500000000000L);eq(20001L,s.totalAssets());s.load();eq("wallet",s.accountById(a.id).icon);eq("0.00000001",s.holdingById(h.id).quantityDecimal);String backup=s.exportJson();s.importJson(backup);eq(20001L,s.totalAssets());JSONObject bad=new JSONObject(backup);bad.getJSONArray("holdings").getJSONObject(0).put("quantityDecimal","-1");reject(()->s.importJson(bad.toString()));eq(20001L,s.totalAssets());
  s.deleteHolding(h.id);s.deleteAccount(a.id);WealthStore.Account bank=s.addAccount("旧账户","bank",12300);JSONObject old=new JSONObject(s.exportJson());old.put("version",2);old.getJSONArray("accounts").getJSONObject(0).remove("icon");s.importJson(old.toString());eq("auto",s.accountById(bank.id).icon);eq(12300L,s.totalAssets());
  String body="{\"bitcoin\":{\"cny\":500000,\"cny_24h_change\":25,\"last_updated_at\":1792728000},\"ethereum\":{\"cny\":10000,\"cny_24h_change\":null,\"last_updated_at\":1792728000}}";Map<String,QuoteClient.Quote> q=QuoteClient.parseCrypto(body,Arrays.asList("BTC","ETH"));eq(2,q.size());eq(500000000000L,q.get("crypto:bitcoin").priceMicros);eq(400000000000L,q.get("crypto:bitcoin").previousMicros);eq(0L,q.get("crypto:ethereum").previousMicros);eq(0,QuoteClient.parseCrypto("{}",Collections.singletonList("BTC")).size());eq(0,QuoteClient.parseCrypto(body.replace("500000","-1"),Collections.singletonList("BTC")).size());
 }
}
