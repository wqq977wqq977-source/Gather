package com.qianwang.privatewealth;

import android.app.Application;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

public final class WealthApp extends Application {
    public WealthStore store;
    BackupWork backup;
    public String loadError;
    public boolean refreshing=false;
    public String quoteStatus="行情随打开自动更新";
    public final List<Runnable> listeners=new ArrayList<>();
    private int quoteGeneration;
    void invalidateQuotes(){quoteGeneration++;}
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate() {
        super.onCreate();
        backup=new BackupWork(this);
        try {store=new WealthStore(this); store.load();}
        catch(Exception e) {loadError="本地数据未能读取，已停止写入。请保留应用数据并从备份恢复。";}
        if(loadError==null) {
            try {store.recordToday();}
            catch(Exception e) {quoteStatus="资产记录保存失败，请重试";}
        }
    }
    public void refresh(Runnable finished) {
        if(store==null || loadError!=null || refreshing || backup.busy()) {if(finished!=null)finished.run();return;}
        // A daily observation is useful even without stocks or a working quote connection.
        boolean observed;
        try {observed=store.recordToday();}
        catch(Exception e) {quoteStatus="资产记录保存失败，请重试";notifyListeners();if(finished!=null)finished.run();return;}
        if(store.holdings.isEmpty()) {if(observed)notifyListeners();if(finished!=null)finished.run();return;}
        refreshing=true; quoteStatus="正在获取行情…"; notifyListeners();
        List<String> codes=new ArrayList<>(); for(WealthStore.Holding h:store.holdings)codes.add(h.code);
        final int generation=quoteGeneration;
        executor.execute(()->{
            try {
                Map<String,QuoteClient.Quote> result=QuoteClient.fetch(codes);
                main.post(()->{
                    if(backup.busy()||generation!=quoteGeneration){refreshing=false;notifyListeners();if(finished!=null)finished.run();return;}
                    try {
                        List<WealthStore.Holding> updates=new ArrayList<>();
                        for(WealthStore.Holding h:store.holdings) {
                            QuoteClient.Quote q=result.get(h.code);
                            if(q==null) continue;
                            WealthStore.Holding update=new WealthStore.Holding();
                            update.id=h.id;update.name=q.name;update.lastPriceMicros=q.priceMicros;
                            update.previousCloseMicros=q.previousMicros;update.quoteTime=q.time;
                            updates.add(update);
                        }
                        int updated=store.applyQuotes(updates);
                        quoteStatus=updated==store.holdings.size()?"行情已更新 · 腾讯 / CoinGecko":"部分持仓未更新 · 保留原估值";
                    } catch(Exception e) {quoteStatus="行情保存失败，请重试";}
                    refreshing=false;notifyListeners();if(finished!=null)finished.run();
                });
            } catch(Exception e) {main.post(()->{refreshing=false;quoteStatus="暂时无法更新 · 显示上次估值";notifyListeners();if(finished!=null)finished.run();});}
        });
    }
    private void notifyListeners(){for(Runnable r:new ArrayList<>(listeners))r.run();}
}
