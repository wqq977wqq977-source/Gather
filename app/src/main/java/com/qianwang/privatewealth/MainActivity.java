package com.qianwang.privatewealth;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MainActivity extends Activity {
    private WealthApp app; private WealthStore store;
    private LinearLayout root,body; private ScrollView viewport; private int tab=0; private String detailId=null;
    private boolean hidden=false,editing=false,night=false,preview=false;
    private Typeface figures,hand; private SketchStyle sketch;
    private boolean sankeyByAccount=false;
    private DisplayMoney money;
    private ArrayList<String> sankeyPath=new ArrayList<>();
    private int bg, surface, ink, muted, line, accent, pale, red, negative;
    private ProgressDialog backupProgress;
    private AlertDialog backupConfirmation;
    private boolean choosingBackup;
    private boolean backupDeferredRender;
    private MultiLayerSankeyView currentSankey;
    private HorizontalScrollView sankeyCanvas;
    private String refreshSelection;
    private boolean sankeyExpanded;
    private int refreshSankeyScroll;
    private int statisticsDays=30, dailyOffset=0;
    private boolean dailyPage=false, sankeyPage=false;
    private String sankeyAccountKey; private int sankeyCategory=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable onUpdate=()->{if(!editing&&!preview&&!app.backup.busy()){int y=viewport==null?0:viewport.getScrollY();refreshSelection=currentSankey==null?null:currentSankey.selectedKey();refreshSankeyScroll=sankeyCanvas==null?0:sankeyCanvas.getScrollX();render();if(viewport!=null)viewport.post(()->viewport.scrollTo(0,y));}};
    private final Runnable ticker=new Runnable(){public void run(){if(!preview&&!app.backup.busy())app.refresh(null);handler.postDelayed(this,60000);}};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        sankeyByAccount=getPreferences(0).getBoolean("sankeyByAccount",false);
        app=(WealthApp)getApplication();store=app.store;
        hidden=getPreferences(0).getBoolean("hide",false);
        if(saved!=null){ArrayList<String> path=saved.getStringArrayList("sankeyPath");if(path!=null)sankeyPath=path;tab=saved.getInt("tab",0);detailId=saved.getString("detail");sankeyPage=saved.getBoolean("sankeyPage",false);sankeyAccountKey=saved.getString("sankeyAccountKey");sankeyCategory=saved.getInt("sankeyCategory",-1);statisticsDays=saved.getInt("statisticsDays",30);dailyPage=saved.getBoolean("dailyPage",false);dailyOffset=saved.getInt("dailyOffset",0);choosingBackup=saved.getBoolean("choosingBackup",false);}
        palette();
        hand=SketchStyle.handwriting(this);figures=SketchStyle.numerals(this);
        if(saved!=null&&saved.getBoolean("preview",false)){createPreview();sankeyPage=saved.getBoolean("sankeyPage",false);}
        render();scheduleQuotes();
    }
    @Override protected void onResume(){super.onResume();app.listeners.add(onUpdate);app.backup.listen(this::backupChanged);handler.post(ticker);}
    @Override protected void onPause(){super.onPause();app.listeners.remove(onUpdate);app.backup.listen(null);if(backupConfirmation!=null){backupConfirmation.dismiss();backupConfirmation=null;}if(backupProgress!=null){backupProgress.dismiss();backupProgress=null;}handler.removeCallbacks(ticker);}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("choosingBackup",choosingBackup);out.putStringArrayList("sankeyPath",sankeyPath);out.putBoolean("sankeyPage",sankeyPage);out.putString("sankeyAccountKey",sankeyAccountKey);out.putInt("sankeyCategory",sankeyCategory);out.putInt("statisticsDays",statisticsDays);out.putBoolean("dailyPage",dailyPage);out.putInt("dailyOffset",dailyOffset);out.putBoolean("preview",preview);out.putInt("tab",tab);out.putString("detail",detailId);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(detailId!=null){detailId=null;render();}else if(sankeyPage){if(!sankeyPath.isEmpty())sankeyPath.remove(sankeyPath.size()-1);else sankeyPage=false;sankeyAccountKey=null;render();}else if(dailyPage){dailyPage=false;render();}else if(tab!=0){tab=0;render();}else if(preview)exitPreview();else super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)applySystemBars(getWindow());}
    private void applySystemBars(Window window){
        if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=window.getInsetsController();if(c!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(night?0:mask,mask);}}else window.getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void palette(){
        night=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        sketch=new SketchStyle(this);bg=sketch.paper;surface=sketch.surface;ink=sketch.ink;muted=sketch.muted;line=sketch.line;accent=sketch.accent;pale=sketch.pale;
        red=Color.parseColor(night?"#F3B3A7":"#A23D36");negative=Color.parseColor(night?"#A3D1B3":"#376A4D");
    }
    private void render(){
        if(isFinishing())return;
        // Backup workers may hold the store lock or publish restored lists.
        // Do not read the ledger until their main-thread completion callback.
        if(app.backup.busy()){
            backupDeferredRender=true;
            if(root==null){root=column();root.setBackgroundColor(bg);setContentView(root);}
            return;
        }
        backupDeferredRender=false;
        currentSankey=null;sankeyCanvas=null;
        try{money=new DisplayMoney(getSharedPreferences("appearance",0).getBoolean("usd",false),getSharedPreferences("appearance",0).getString("fxRate",""));}catch(Exception e){money=new DisplayMoney(false,"");}
        root=column();root.setBackground(sketch.paper(getResources().getDisplayMetrics().density));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(root);root.requestApplyInsets();root.post(()->applySystemBars(getWindow()));
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(24),dp(4),dp(12),dp(4));
        if(detailId!=null||dailyPage||sankeyPage)top.addView(iconButton("back","返回",()->{if(detailId!=null)detailId=null;else if(sankeyPage&&!sankeyPath.isEmpty()){sankeyPath.remove(sankeyPath.size()-1);sankeyAccountKey=null;}else {dailyPage=false;sankeyPage=false;}render();}));
        else if(tab==0){Icon mark=new Icon(this,"mark",ink);top.addView(mark,new LinearLayout.LayoutParams(dp(24),dp(24)));Space gap=new Space(this);top.addView(gap,new LinearLayout.LayoutParams(dp(10),1));}
        TextView title=text(detailId!=null?"账户详情":sankeyPage?"资产桑基图":dailyPage?"每日资产":tab==0?"Gather":tab==1?"资产持仓":tab==3?"资产统计":"资金账户",20,ink,true);
        title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        if(tab==1&&detailId==null){View refresh=iconButton("refresh",preview?"示例行情说明":app.refreshing?"行情更新中":"刷新行情",()->{if(preview)message("此页为静态示例行情，不是你的实际持仓或当前价格。");else app.refresh(null);});refresh.setEnabled(!app.refreshing||preview);refresh.setAlpha(refresh.isEnabled()?1f:.4f);top.addView(refresh);}
        top.addView(iconButton("eye",hidden?"显示金额":"隐藏金额",()->{hidden=!hidden;getPreferences(0).edit().putBoolean("hide",hidden).apply();render();}));
        top.addView(iconButton("settings","设置与备份",this::settings));root.addView(top);divider(root);
        if(preview){LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(24),0,dp(12),0);bar.setBackgroundColor(pale);bar.addView(text("示例预览 · 不计入你的资产",11,accent,false),weight());bar.addView(textAction("退出",this::exitPreview));root.addView(bar);}
        viewport=new ScrollView(this);viewport.setFillViewport(true);viewport.setClipToPadding(false);viewport.setVerticalScrollBarEnabled(false);
        body=column();body.setPadding(dp(24),dp(24),dp(24),dp(28));viewport.addView(body);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        if(money.usd){TextView fx=text("USD · 1 美元 = "+money.cnyPerUsd.toPlainString()+" 元 · "+getSharedPreferences("appearance",0).getString("fxDate","手动汇率"),11,muted,false);body.addView(fx);space(body,14);}
        if(app.loadError!=null&&!preview){empty("数据暂时无法读取",app.loadError,"导入备份",this::chooseImport);return;}
        if(detailId!=null)accountDetail();else if(sankeyPage)sankey();else if(dailyPage)dailyHistoryPage();else if(tab==0)overview();else if(tab==1)holdings();else if(tab==3)statistics();else accounts();
        navigation();
    }
    private void overview(){
        if(store.accounts.isEmpty()&&store.dailySnapshots.isEmpty()){welcome();return;}
        LinearLayout caption=row();caption.setGravity(Gravity.CENTER_VERTICAL);caption.addView(text("总资产",14,muted,false),weight());caption.addView(text(dayLabel(WealthStore.today()),12,muted,false));body.addView(caption);
        heroAmount(store.totalAssets());space(body,12);todayChange();
        section("资产增长","查看统计",()->{tab=3;render();});
        List<WealthStore.DailySnapshot> recent=historyInRange(30);trendPanel(body,recent,150);
        space(body,20);LinearLayout split=row();split.addView(metric("资金余额",amount(store.totalCash()),ink),weight());split.addView(metric("持仓市值",amount(store.totalMarketValue()),ink),weight());body.addView(split);
        if(!store.holdings.isEmpty()){space(body,22);marketMetrics();quoteLine();}
        section("我的账户","查看全部",()->{tab=2;render();});for(WealthStore.Account a:store.accounts)accountRow(a);
        if(!store.accounts.isEmpty()){space(body,16);LinearLayout move=row();move.setGravity(Gravity.CENTER);move.addView(textAction("资金划转",this::transferForm));body.addView(move);}
    }
    private void todayChange(){
        List<WealthStore.DailySnapshot> history=store.dailySnapshots;int n=history.size();LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);
        if(n<2){r.addView(text(n==0?"添加账户后开始记录":"已开始记录 · 累积两天即可对比",12,muted,false));}
        else{WealthStore.DailySnapshot last=history.get(n-1),previous=history.get(n-2);boolean adjacent=LocalDate.parse(previous.day).plusDays(1).toString().equals(last.day);String label=last.day.equals(WealthStore.today())?(adjacent?"今日变化":"较上次记录"):"最近记录变化";long delta=last.totalFen()-previous.totalFen();r.addView(text(label+"  ",12,muted,false));r.addView(number(signed(delta),15,hidden?muted:changeColor(delta)));TextView ratio=text("  "+changePercent(previous.totalFen(),last.totalFen()),11,muted,false);r.addView(ratio);}
        body.addView(r);
    }
    private void statistics(){
        if(store.accounts.isEmpty()&&store.dailySnapshots.isEmpty()){empty("看见资产的每一步。","建立账户后，每天保留一份资产记录。", "添加账户",()->accountForm(null));space(body,12);LinearLayout sample=row();sample.setGravity(Gravity.CENTER);sample.addView(textAction("先看看示例",()->{safe(this::createPreview);render();}));body.addView(sample);return;}
        rangeSelector();List<WealthStore.DailySnapshot> history=historyInRange(statisticsDays);space(body,22);
        boolean comparable=history.size()>1;long first=history.isEmpty()?0:history.get(0).totalFen(),last=history.isEmpty()?0:history.get(history.size()-1).totalFen();
        LinearLayout metrics=row();metrics.addView(metric("期间资产变化",comparable?signed(last-first):"—",comparable&&!hidden?changeColor(last-first):ink),weight());metrics.addView(metric("变化幅度",comparable?changePercent(first,last):"—",ink),weight());body.addView(metrics);space(body,18);
        trendPanel(body,history,190);
        LinearLayout scope=row();scope.setGravity(Gravity.CENTER_VERTICAL);String summary=history.size()<2?"从第一条真实记录开始积累":history.size()+" 天记录 · 含余额与持仓调整";scope.addView(text(preview?"示例历史 · 含余额与持仓调整":summary,11,muted,false),weight());scope.addView(iconButton("info","资产统计口径",this::statisticsInfo));body.addView(scope);
        section("资产分布","桑基图",()->{sankeyPage=true;sankeyPath.clear();sankeyAccountKey=null;sankeyCategory=-1;render();});distribution();
        if(!history.isEmpty()){space(body,24);long high=history.get(0).totalFen(),low=high;for(WealthStore.DailySnapshot d:history){high=Math.max(high,d.totalFen());low=Math.min(low,d.totalFen());}LinearLayout range=row();range.addView(metric("期间最高",amount(high),ink),weight());range.addView(metric("期间最低",amount(low),ink),weight());body.addView(range);}
        section("每日变化","全部记录",()->{dailyPage=true;dailyOffset=0;render();});
        if(history.isEmpty())body.addView(text("这段时间还没有资产记录。",13,muted,false));
        for(int i=history.size()-1;i>=Math.max(0,history.size()-5);i--)dailyRow(body,history.get(i));
        space(body,16);body.addView(text(store.accounts.size()+" 个账户 · "+store.holdings.size()+" 笔持仓 · "+money.code(),11,muted,false));
    }
    private void rangeSelector(){
        LinearLayout control=row();control.setPadding(dp(4),dp(4),dp(4),dp(4));control.setBackground(shape(surface,14));String[] labels={"近 7 天","近 30 天","全部"};int[] days={7,30,0};
        for(int i=0;i<days.length;i++){final int range=days[i];boolean chosen=statisticsDays==range;TextView option=text(labels[i],13,chosen?accent:muted,chosen);option.setGravity(Gravity.CENTER);option.setMinimumHeight(dp(48));option.setBackground(chosen?shape(pale,10):selectable());option.setContentDescription(labels[i]);option.setSelected(chosen);option.setFocusable(true);option.setOnClickListener(v->{statisticsDays=range;render();});control.addView(option,new LinearLayout.LayoutParams(0,-2,1));}body.addView(control);
    }
    private List<WealthStore.DailySnapshot> historyInRange(int days){
        List<WealthStore.DailySnapshot> result=new ArrayList<>();String start=days==0?"":LocalDate.parse(WealthStore.today()).minusDays(days-1).toString();for(WealthStore.DailySnapshot value:store.dailySnapshots)if(start.isEmpty()||value.day.compareTo(start)>=0)result.add(value);return result;
    }
    private void trendPanel(LinearLayout parent,List<WealthStore.DailySnapshot> history,int height){
        if(history.isEmpty()){LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(24),dp(12),dp(24));empty.addView(text("等待第一条记录",16,ink,true));space(empty,10);empty.addView(text("记录会随账户和行情更新。",12,muted,false));parent.addView(empty,new LinearLayout.LayoutParams(-1,dp(height)));return;}
        WealthStore.DailySnapshot latest=history.get(history.size()-1);LinearLayout selected=row();selected.setGravity(Gravity.CENTER_VERTICAL);TextView date=text(snapshotLabel(latest),11,muted,false);selected.addView(date,weight());TextView value=number(amount(latest.totalFen()),16,ink);selected.addView(value);selected.setPadding(0,0,0,dp(8));parent.addView(selected);
        List<WealthCharts.Point> points=new ArrayList<>();for(WealthStore.DailySnapshot d:history)points.add(new WealthCharts.Point(d.day,money.fen(d.totalFen())));
        WealthCharts.TrendView chart=new WealthCharts.TrendView(this,points,figures,accent,ink,muted,line,hidden);chart.setCurrencyLabel(money.code());chart.setOnSelectionListener(index->{WealthStore.DailySnapshot d=history.get(index);date.setText(snapshotLabel(d));value.setText(amount(d.totalFen()));});parent.addView(chart,new LinearLayout.LayoutParams(-1,dp(height)));
        if(history.size()==1){space(parent,8);parent.addView(text("仅有一天记录，暂不计算增长。",11,muted,false));}
    }
    private void distribution(){
        List<WealthCharts.Slice> slices=new ArrayList<>();AssetSankey allocation=AssetSankey.build(store.accounts,store.holdings);int[] categoryColors=sketch.colors();for(int k=0;k<AssetSankey.CATEGORY_NAMES.length;k++)if(allocation.categoryValue(k)>0)slices.add(new WealthCharts.Slice(AssetSankey.CATEGORY_NAMES[k],allocation.categoryValue(k),categoryColors[k]));
        LinearLayout panel=row();panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(dp(14),dp(16),dp(14),dp(16));panel.setBackground(shape(surface,14));
        WealthCharts.DonutView chart=new WealthCharts.DonutView(this,slices,figures,ink,muted,line,hidden);panel.addView(chart,new LinearLayout.LayoutParams(dp(142),dp(168)));
        LinearLayout legend=column();legend.setPadding(dp(14),0,0,0);long total=store.totalAssets();
        if(slices.isEmpty())legend.addView(text("账户余额为零时，\n暂不绘制占比。",12,muted,false));
        for(int i=0;i<slices.size();i++){final int index=i;WealthCharts.Slice slice=slices.get(i);LinearLayout item=column();item.setMinimumHeight(dp(48));item.setPadding(0,dp(3),0,dp(3));LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(shape(slice.color,4));title.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView name=text(slice.label,11,ink,false);name.setPadding(dp(6),0,0,0);title.addView(name,weight());TextView pct=number(hidden?"•••":String.format(Locale.CHINA,"%.1f%%",100d*slice.valueFen/total),11,muted);title.addView(pct);item.addView(title);TextView number=number(amount(slice.valueFen),13,ink);number.setPadding(dp(12),dp(5),0,0);item.addView(number);item.setContentDescription(slice.label+"，"+(hidden?"金额已隐藏":amount(slice.valueFen)));item.setFocusable(true);item.setBackground(selectable());item.setOnClickListener(v->chart.setSelectedIndex(index));legend.addView(item);}
        panel.addView(legend,weight());body.addView(panel);
    }
    private void sankey(){
        sankeyPath.clear();
        AssetTree.Node data=AssetTree.build(store.accounts,store.holdings,sankeyByAccount);
        LinearLayout modes=row();for(int i=0;i<2;i++){final boolean accounts=i==1;TextView option=text(accounts?"按账户":"按类别",14,sankeyByAccount==accounts?accent:muted,true);option.setGravity(Gravity.CENTER);option.setMinimumHeight(dp(48));option.setSelected(sankeyByAccount==accounts);option.setBackground(sankeyByAccount==accounts?shape(pale,10):selectable());option.setFocusable(true);option.setOnClickListener(v->{sankeyByAccount=accounts;refreshSelection=null;sankeyExpanded=false;refreshSankeyScroll=0;getPreferences(0).edit().putBoolean("sankeyByAccount",accounts).apply();render();});modes.addView(option,new LinearLayout.LayoutParams(0,-2,1));}body.addView(modes);
        space(body,12);body.addView(text(sankeyByAccount?"总资产 → 账户 → 现金与持仓":"总资产 → 类别 → 账户 → 现金与持仓",13,ink,false));space(body,8);body.addView(text("所有层级同时展开，点节点查看金额与归属。",12,muted,false));
        MultiLayerSankeyView chart=new MultiLayerSankeyView(this,data,money,figures,hidden,sankeyByAccount);
        currentSankey=chart;HorizontalScrollView canvas=new HorizontalScrollView(this);sankeyCanvas=canvas;canvas.setFillViewport(true);canvas.setHorizontalScrollBarEnabled(true);canvas.addView(chart,new android.widget.FrameLayout.LayoutParams(sankeyExpanded?chart.detailWidth():-1,chart.desiredHeight()));
        LinearLayout tools=row();TextView zoom=textAction(sankeyExpanded?"适合屏幕":"放大细看 ↔",()->{});zoom.setOnClickListener(v->{sankeyExpanded=!sankeyExpanded;chart.setLayoutParams(new android.widget.FrameLayout.LayoutParams(sankeyExpanded?chart.detailWidth():-1,chart.desiredHeight()));canvas.scrollTo(0,0);zoom.setText(sankeyExpanded?"适合屏幕":"放大细看 ↔");});tools.addView(zoom);tools.addView(iconButton("info","桑基图说明",()->message("每一列在同一张图中连续连接，所有分支默认展开。放大后可左右滑动查看；上下滑动查看较多持仓。点节点只选择和高亮归属，不会切换层级。\n\n同一账户可能出现在多个类别下，每处只计入该类别的资产，各列总额相同。这是当前资产构成，不是历史现金流。")));body.addView(tools);body.addView(canvas);space(body,12);
        body.addView(number("总资产  "+amount(data.value)+" "+money.code(),20,ink));space(body,12);LinearLayout detail=column();body.addView(detail);detail.addView(text("点任一节点，查看精确金额。",12,muted,false));
        chart.setListener(node->{detail.removeAllViews();if(node==null){detail.addView(text("点任一节点，查看精确金额。",12,muted,false));return;}detail.addView(text(node.name,18,ink,true));detail.addView(number(amount(node.value)+" "+money.code(),24,ink));detail.addView(text(hidden?"比例已隐藏":"占总资产 "+AssetTree.share(node.value,data.value),12,muted,false));detail.addView(textAction("清除选择",chart::clearSelection));if(node.holdingId!=null)primary(detail,"查看持仓",()->holdingDetail(store.holdingById(node.holdingId)),false);else if(node.accountId!=null)primary(detail,"管理账户",()->{detailId=node.accountId;render();},false);});
        chart.selectKey(refreshSelection);refreshSelection=null;int scroll=refreshSankeyScroll;refreshSankeyScroll=0;canvas.post(()->canvas.scrollTo(scroll,0));
        space(body,14);quoteLine();
    }
    private void sankeyAmountRow(LinearLayout parent,String title,long value,Runnable action){
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));TextView label=text(title,13,ink,false);label.setPadding(0,dp(8),dp(10),dp(8));row.addView(label,weight());TextView balance=number(amount(value),14,muted);balance.setGravity(Gravity.END);row.addView(balance,weight());if(action!=null){row.setBackground(selectable());row.setOnClickListener(v->action.run());row.setFocusable(true);row.setContentDescription(title+"，"+amount(value)+"，管理账户");}parent.addView(row);divider(parent);
    }

    private void dailyRow(LinearLayout target,WealthStore.DailySnapshot current){
        WealthStore.DailySnapshot previous=null;for(WealthStore.DailySnapshot d:store.dailySnapshots){if(d.day.compareTo(current.day)>=0)break;previous=d;}
        String state=previous==null?"首条记录":LocalDate.parse(previous.day).plusDays(1).toString().equals(current.day)?"较前日":"较上次记录";
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(14));LinearLayout left=column();left.addView(text((dailyPage?current.day:dayLabel(current.day))+(current.day.equals(WealthStore.today())?" · 今天":""),13,ink,true));space(left,6);left.addView(text(state,11,muted,false));row.addView(left,weight());LinearLayout right=column();TextView balance=number(amount(current.totalFen()),16,ink);balance.setGravity(Gravity.END);right.addView(balance);space(right,6);long delta=previous==null?0:current.totalFen()-previous.totalFen();TextView change=number(previous==null?"—":signed(delta),12,hidden?muted:changeColor(delta));change.setGravity(Gravity.END);right.addView(change);row.addView(right,weight());row.setBackground(selectable());row.setOnClickListener(v->snapshotInfo(current));target.addView(row);divider(target);
    }
    private void dailyHistoryPage(){
        List<WealthStore.DailySnapshot> history=store.dailySnapshots;body.addView(text("每天保留当天最新的资产估值。",13,muted,false));space(body,12);
        if(history.isEmpty()){body.addView(text("还没有资产记录。",14,muted,false));return;}
        dailyOffset=Math.min(Math.max(0,dailyOffset),((history.size()-1)/30)*30);int end=history.size()-dailyOffset,start=Math.max(0,end-30);body.addView(text("共 "+history.size()+" 天记录 · "+(dailyOffset+1)+"–"+(history.size()-start),11,muted,false));space(body,14);
        if(dailyOffset>0)primary(body,"较新记录",()->{dailyOffset=Math.max(0,dailyOffset-30);render();},false);
        for(int i=end-1;i>=start;i--)dailyRow(body,history.get(i));
        if(start>0){space(body,18);primary(body,"更早记录",()->{dailyOffset+=30;render();},false);}
    }
    private String snapshotLabel(WealthStore.DailySnapshot value){return dayLabel(value.day)+(value.day.equals(WealthStore.today())?" · 当天最新":" · 日记录");}
    private String dayLabel(String day){return day.substring(0,4).equals(WealthStore.today().substring(0,4))?day.substring(5,7)+"."+day.substring(8,10):day.replace('-','.');}
    private String changePercent(long first,long last){if(hidden)return "•••";if(first==0)return "—";double value=100d*(last-first)/first;return String.format(Locale.CHINA,(value>0?"+":"")+"%.2f%%",value);}
    private void snapshotInfo(WealthStore.DailySnapshot value){
        String source=value.oldestQuoteTime.isEmpty()?(value.marketFen==0?"不含证券持仓":"尚无有效报价，按录入成本估值"):(preview?"示例行情 ":"最早行情 ")+quoteTime(value.oldestQuoteTime);if(value.unquotedCount>0)source+="\n"+value.unquotedCount+" 笔未报价，按成本估值";
        new HandDialogBuilder().setTitle(value.day+" 资产记录").setMessage("总资产  "+amount(value.totalFen())+"\n资金余额  "+amount(value.cashFen)+"\n持仓市值  "+amount(value.marketFen)+"\n\n记录更新 "+snapshotTime(value.recordedAt)+"\n"+source).setPositiveButton("知道了",null).show();
    }
    private String snapshotTime(long timestamp){SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return format.format(new Date(timestamp))+"（北京时间）";}
    private void statisticsInfo(){new HandDialogBuilder().setTitle("关于资产统计").setMessage("每天保留当天最新记录，时间按北京时间。打开应用、更新账户或行情时记录；后台更新由 Android 择机执行。\n\n资产变化 = 两次记录的总资产差额。包含新增账户、余额调整、持仓数量和市场价格变化，并非投资收益率。账户间划转不改变总资产。\n\n曲线从首次记录开始。没有记录的日期不补造，间隔日期用虚线连接；仅有一条记录时不计算增长。期间变化与最高、最低只统计选定范围内已有记录。\n\n资产分布按当前银行存款、证券持仓、证券现金、其他资金和数字货币计算。行情未更新时沿用上次报价或录入成本，点击每日记录可查看行情时间。\n\n每日记录随加密备份一起保存。").setPositiveButton("知道了",null).show();}
    private void welcome(){
        space(body,26);TextView title=text("让资产，\n清晰可见。",32,ink,false);title.setLineSpacing(dp(10),1);body.addView(title);space(body,20);
        body.addView(text("关注大额资金，也看见每一份持仓。",14,muted,false));space(body,38);
        LinearLayout first=row();first.setGravity(Gravity.CENTER_VERTICAL);Icon mark=new Icon(this,"mark",accent);first.addView(mark,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout words=column();words.setPadding(dp(18),0,0,0);words.addView(text("你的资产，自有章法",16,ink,true));space(words,7);words.addView(text("在本机，建立一份自己的资产清单。",12,muted,false));first.addView(words,weight());body.addView(first);
        space(body,32);divider(body);featureLine("bank","大额资金","余额由你掌握");divider(body);featureLine("hold","股票与 ETF","市值跟随行情");divider(body);space(body,32);
        primary(body,"添加第一个账户",()->accountForm(null),true);space(body,8);LinearLayout sample=row();sample.setGravity(Gravity.CENTER);sample.addView(textAction("先看看示例",()->{safe(this::createPreview);render();}));body.addView(sample);
    }
    private void featureLine(String icon,String title,String note){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setMinimumHeight(dp(66));Icon v=new Icon(this,icon,muted);r.addView(v,new LinearLayout.LayoutParams(dp(21),dp(21)));TextView t=text(title,14,ink,false);t.setPadding(dp(12),0,0,0);r.addView(t,weight());r.addView(text(note,12,muted,false));body.addView(r);}
    private void heroAmount(long value){
        LinearLayout r=row();r.setGravity(Gravity.BOTTOM);r.setPadding(0,dp(9),0,0);TextView money=number(amount(value),46,ink);String v=amount(value);int decimal=v.lastIndexOf('.');if(decimal>=0){SpannableString styled=new SpannableString(v);styled.setSpan(new RelativeSizeSpan(.56f),decimal,v.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);money.setText(styled);}money.setMaxLines(1);money.setAutoSizeTextTypeUniformWithConfiguration(22,46,1,android.util.TypedValue.COMPLEX_UNIT_SP);r.addView(money,weight());TextView currency=text(this.money.code(),11,muted,false);currency.setPadding(dp(10),0,0,dp(9));r.addView(currency);body.addView(r);
    }
    private void marketMetrics(){
        LinearLayout panel=row();panel.setPadding(dp(18),dp(18),dp(10),dp(18));panel.setBackground(shape(surface,14));
        panel.addView(metric("行情变动合计",signed(store.dailyChange()),changeColor(store.dailyChange())),weight());panel.addView(metric("持仓浮盈",signed(store.totalProfit()),changeColor(store.totalProfit())),weight());body.addView(panel);
    }
    private void accounts(){
        if(store.accounts.isEmpty()){empty("资金，各有归处。","建立银行、证券或其他资金账户。", "添加账户",()->accountForm(null));return;}
        body.addView(text("账户总资产",14,muted,false));heroAmount(store.totalAssets());space(body,14);
        LinearLayout info=row();info.setGravity(Gravity.CENTER_VERTICAL);info.addView(text(store.accounts.size()+" 个账户 · "+money.code(),12,muted,false),weight());info.addView(textAction("添加账户",()->accountForm(null)));body.addView(info);
        accountGroups();space(body,16);LinearLayout move=row();move.setGravity(Gravity.CENTER);move.addView(textAction("账户间划转",this::transferForm));body.addView(move);
    }
    private void accountGroups(){
        for(String kind:new String[]{"securities","crypto","bank","cash"}){
            List<WealthStore.Account> group=new ArrayList<>();long sum=0;for(WealthStore.Account a:store.accounts)if(kind.equals(a.kind)){group.add(a);sum+=store.accountTotal(a);}if(group.isEmpty())continue;
            section(kindName(kind),amount(sum),null);for(WealthStore.Account a:group)accountRow(a);
        }
    }
    private void accountRow(WealthStore.Account a){
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(16),0,dp(16));
        Icon icon=new Icon(this,"auto".equals(a.icon)?("securities".equals(a.kind)?"hold":"crypto".equals(a.kind)?"coin":"bank"):a.icon,"securities".equals(a.kind)?accent:ink);icon.setPadding(dp(8),dp(8),dp(8),dp(8));icon.setBackground(shape(surface,10));r.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout texts=column();texts.setPadding(dp(12),0,dp(6),0);TextView name=text(a.name,15,ink,true);name.setMaxLines(2);texts.addView(name);space(texts,6);texts.addView(text(("securities".equals(a.kind)||"crypto".equals(a.kind))?"现金 + 持仓":kindName(a.kind),11,muted,false));r.addView(texts,weight());
        TextView balance=number(amount(store.accountTotal(a)),18,ink);balance.setGravity(Gravity.END);balance.setMaxWidth(dp(165));balance.setMaxLines(2);r.addView(balance);r.setMinimumHeight(dp(78));r.setContentDescription(a.name+"，"+(hidden?"金额已隐藏":amount(store.accountTotal(a)))+"，查看账户");r.setOnClickListener(v->{detailId=a.id;render();});r.setBackground(selectable());body.addView(r);divider(body);
    }
    private void holdings(){
        body.addView(text("持仓市值",14,muted,false));heroAmount(store.totalMarketValue());space(body,24);marketMetrics();quoteLine();
        section("我的持仓","添加持仓",()->holdingForm(null,null));
        if(store.holdings.isEmpty()){space(body,24);empty("从一笔持仓开始","录入股票、ETF 或数字货币，行情变化更新估值。",null,null);}
        for(WealthStore.Account a:store.accounts){List<WealthStore.Holding> list=holdingList(a.id);if(list.isEmpty())continue;space(body,12);body.addView(text(a.name+" · "+list.size()+" 笔",12,muted,false));for(WealthStore.Holding h:list)holdingRow(h);}
    }
    private void holdingRow(WealthStore.Holding h){
        LinearLayout card=column();card.setPadding(0,dp(18),0,dp(18));
        LinearLayout head=row();LinearLayout name=column();TextView nameText=text(h.name==null||h.name.isEmpty()?h.code:h.name,15,ink,true);nameText.setMaxLines(2);name.addView(nameText);space(name,7);name.addView(text((WealthStore.isCrypto(h)?WealthStore.coinSymbol(h.code):h.code.toUpperCase(Locale.ROOT))+" · "+(hidden?"••••":WealthStore.quantityText(h)+(WealthStore.isCrypto(h)?" 枚":" 股 / 份")),11,muted,false));name.setPadding(0,0,dp(8),0);head.addView(name,weight());
        LinearLayout val=column();TextView market=number(amount(WealthStore.holdingValue(h)),19,ink);market.setGravity(Gravity.END);val.addView(market);space(val,6);TextView profit=number(signed(WealthStore.holdingProfit(h)),12,changeColor(WealthStore.holdingProfit(h)));profit.setGravity(Gravity.END);val.addView(profit);head.addView(val,weight());card.addView(head);space(card,13);
        space(card,4);TextView move=text((WealthStore.isCrypto(h)?"24h ":"较昨收 ")+(hidden?"•••":WealthStore.movementPercent(h)),14,hidden?muted:changeColor(h.lastPriceMicros-h.previousCloseMicros),true);if(!quoted(h)||h.previousCloseMicros<=0)move.setTextColor(muted);card.addView(move);space(card,8);
        boolean quoted=quoted(h);card.addView(text(quoted?"现价 "+price(h.lastPriceMicros)+"    成本 "+price(h.costPriceMicros)+" "+money.code():"尚无报价 · 暂按成本估值",11,quoted?muted:red,false));
        if(quoted){space(card,5);card.addView(text((preview?"示例行情 ":WealthStore.isCrypto(h)?"CoinGecko ":"腾讯行情 ")+quoteTime(h.quoteTime),10,muted,false));}
        card.setOnClickListener(v->holdingDetail(h));card.setMinimumHeight(dp(94));card.setBackground(selectable());body.addView(card);divider(body);
    }
    private void accountDetail(){
        WealthStore.Account a;try{a=store.accountById(detailId);}catch(Exception e){detailId=null;render();return;}
        body.addView(text(a.name,26,ink,true));space(body,9);body.addView(text(kindName(a.kind),12,muted,false));space(body,30);
        body.addView(text("账户总资产",14,muted,false));heroAmount(store.accountTotal(a));space(body,26);
        LinearLayout m=row();m.addView(metric("可用现金",amount(a.cashFen),ink),weight());if("securities".equals(a.kind)||"crypto".equals(a.kind))m.addView(metric("持仓市值",amount(store.accountTotal(a)-a.cashFen),ink),weight());body.addView(m);space(body,28);
        primary(body,"更新现金余额",()->accountForm(a),true);space(body,8);LinearLayout actions=row();actions.setGravity(Gravity.CENTER);actions.addView(textAction("转入 / 转出",this::transferForm));body.addView(actions);
        if("securities".equals(a.kind)||"crypto".equals(a.kind)){section("账户持仓","添加",()->holdingForm(null,a));List<WealthStore.Holding> list=holdingList(a.id);if(list.isEmpty())body.addView(text("添加持仓，市值将计入这个账户。",14,muted,false));for(WealthStore.Holding h:list)holdingRow(h);}
        section("账户管理",null,null);primary(body,"编辑名称与余额",()->accountForm(a),false);space(body,10);
        Button delete=button("删除账户",()->{if(previewGuard())return;new HandDialogBuilder().setTitle("删除“"+a.name+"”？").setMessage("删除后该账户不再计入总资产。有持仓的账户需先移除持仓。已有变更记录保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->safe(()->{store.deleteAccount(a.id);detailId=null;render();})).show();},false);delete.setTextColor(red);body.addView(delete,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private void createPreview(){
        File directory=new File(getCacheDir(),"design-preview");if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("暂时无法打开示例");
        WealthStore sample=new WealthStore(new ContextWrapper(this){@Override public File getFilesDir(){return directory;}});
        sample.addAccount("招商银行","bank",68000000);
        WealthStore.Account securities=sample.addAccount("华泰证券","securities",8000000);
        sample.addAccount("备用资金","cash",12000000);
        WealthStore.Holding catl=sample.addHolding(securities.id,"sz300750","宁德时代",1000,330000000);catl.lastPriceMicros=364640000;catl.previousCloseMicros=361800000;catl.quoteTime="20261022150000";
        WealthStore.Holding fund=sample.addHolding(securities.id,"sh510300","沪深300 ETF",10000,4100000);fund.lastPriceMicros=4178000;fund.previousCloseMicros=4152000;fund.quoteTime="20261022150000";
        WealthStore.Account wallet=sample.addAccount("数字资产钱包","crypto",0,"wallet");
        WealthStore.Holding btc=sample.addCryptoHolding(wallet.id,"BTC","0.025",450000000000L);btc.lastPriceMicros=530000000000L;btc.previousCloseMicros=535000000000L;btc.quoteTime="20261022150000";
        WealthStore.Holding eth=sample.addCryptoHolding(wallet.id,"ETH","1.5",14000000000L);eth.lastPriceMicros=15300000000L;eth.previousCloseMicros=15000000000L;eth.quoteTime="20261022150000";
        sample.applyQuotes(new ArrayList<>(sample.holdings));
        try{JSONObject data=new JSONObject(sample.exportJson());JSONArray days=new JSONArray();LocalDate end=LocalDate.parse(WealthStore.today());long cash=sample.totalCash(),market=sample.totalMarketValue(),cost=0;for(WealthStore.Holding h:sample.holdings)cost+=WealthStore.holdingCost(h);
            for(int i=0;i<90;i++){LocalDate day=end.minusDays(89-i);double progress=i/89d;long value=market-Math.round((1-progress)*7900000)+Math.round((Math.sin(i*.51)*420000+Math.sin(i*.17)*700000)*Math.sin(progress*Math.PI));JSONObject point=new JSONObject();point.put("day",day.toString());point.put("recordedAt",i==89?System.currentTimeMillis():day.atTime(18,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());point.put("cashFen",cash);point.put("marketFen",value);point.put("costFen",cost);point.put("unquotedCount",0);point.put("oldestQuoteTime",day.format(DateTimeFormatter.BASIC_ISO_DATE)+"150000");days.put(point);}data.put("dailySnapshots",days);sample.importJson(data.toString());
        }catch(JSONException e){throw new IllegalStateException("示例历史暂时无法显示",e);}
        store=sample;preview=true;detailId=null;dailyPage=false;sankeyPage=false;
    }
    private void exitPreview(){preview=false;store=app.store;detailId=null;dailyPage=false;sankeyPage=false;sankeyAccountKey=null;sankeyCategory=-1;tab=0;render();}
    private boolean previewGuard(){if(!preview)return false;new HandDialogBuilder().setTitle("正在浏览示例").setMessage("示例只用于体验界面。回到自己的账本后即可添加和管理资产。").setNegativeButton("继续看看",null).setPositiveButton("回到我的账本",(d,w)->exitPreview()).show();return true;}

    private void holdingDetail(WealthStore.Holding h){
        LinearLayout panel=form();addInfo(panel,"当前市值",amount(WealthStore.holdingValue(h)));addInfo(panel,"持仓浮盈",signed(WealthStore.holdingProfit(h)));addInfo(panel,"持仓数量",hidden?"••••":WealthStore.quantityText(h));addInfo(panel,"单位成本 · "+money.code(),price(h.costPriceMicros));addInfo(panel,WealthStore.isCrypto(h)?"24 小时涨跌幅":"较昨收涨跌幅",hidden?"•••":WealthStore.movementPercent(h));
        addInfo(panel,"最新报价",quoted(h)?price(h.lastPriceMicros):"尚未取得");addInfo(panel,"行情时间",quoted(h)?quoteTime(h.quoteTime):"暂按成本估值");
        TextView hint=text("添加或修改已有持仓，不会自动增减账户现金。买卖后请同步更新可用现金。",13,muted,false);hint.setPadding(0,dp(18),0,0);panel.addView(hint);
        new HandDialogBuilder().setTitle(h.name+" · "+(WealthStore.isCrypto(h)?WealthStore.coinSymbol(h.code):h.code.toUpperCase(Locale.ROOT))).setView(panel).setPositiveButton("修改持仓",(d,w)->holdingForm(h,null)).setNegativeButton("返回",null).setNeutralButton("移除",(d,w)->{if(previewGuard())return;new HandDialogBuilder().setTitle("移除这笔持仓？").setMessage("市值将从账户中移除，现金余额保持不变。卖出款请更新到账户现金。").setNegativeButton("取消",null).setPositiveButton("移除",(x,y)->safe(()->{store.deleteHolding(h.id);render();})).show();}).show();
    }
    private void accountForm(WealthStore.Account account){accountForm(account,"bank");}
    private void accountForm(WealthStore.Account account,String preferredKind){
        if(previewGuard())return;
        LinearLayout f=form();EditText name=input(f,"账户名称",account==null?"":account.name,"例如：招商银行 / 股票账户",false);
        label(f,"账户类型");Spinner kind=spinner(new String[]{"银行存款","证券账户","其他资金","数字资产账户"});f.addView(kind);String selectedKind=account==null?preferredKind:account.kind;kind.setSelection("securities".equals(selectedKind)?1:"cash".equals(selectedKind)?2:"crypto".equals(selectedKind)?3:0);if(account!=null)kind.setEnabled(false);
        EditText cash=input(f,"现金余额（元）",account==null?"":decimal(account.cashFen),"0.00",true);
        note(f,"金额录入使用人民币 CNY。只填可用现金，不含股票或币的市值；USDT 等作为持仓添加。");
        label(f,"账户图标");final String[] selectedIcon={account==null?"auto":account.icon};ArrayList<LinearLayout> iconCells=new ArrayList<>();
        for(int rowIndex=0;rowIndex<3;rowIndex++){LinearLayout icons=row();for(int col=0;col<4;col++){final int index=rowIndex*4+col;String id=WealthStore.ICON_IDS[index];LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(dp(68));cell.setBackground(id.equals(selectedIcon[0])?shape(pale,10):selectable());Icon icon=new Icon(this,"auto".equals(id)?"bank":id,ink);cell.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView caption=text(WealthStore.ICON_NAMES[index],11,muted,false);caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(6),0,0);cell.addView(caption);cell.setContentDescription("图标："+WealthStore.ICON_NAMES[index]);cell.setFocusable(true);cell.setSelected(id.equals(selectedIcon[0]));cell.setOnClickListener(v->{selectedIcon[0]=id;for(int j=0;j<iconCells.size();j++){boolean active=WealthStore.ICON_IDS[j].equals(id);iconCells.get(j).setSelected(active);iconCells.get(j).setBackground(active?shape(pale,10):selectable());}});iconCells.add(cell);icons.addView(cell,new LinearLayout.LayoutParams(0,dp(68),1));}f.addView(icons);}

        showForm(account==null?"添加账户":"更新账户",f,"保存",()->{
            String type=new String[]{"bank","securities","cash","crypto"}[kind.getSelectedItemPosition()];long value=WealthStore.parseMoney(cash.getText().toString().trim());
            if(account==null)store.addAccount(name.getText().toString().trim(),type,value,selectedIcon[0]);else store.updateAccount(account.id,name.getText().toString().trim(),value,selectedIcon[0]);
        });
    }
    private void holdingForm(WealthStore.Holding holding,WealthStore.Account fixed){
        if(previewGuard())return;
        if(holding==null&&fixed==null){new HandDialogBuilder().setTitle("添加哪类持仓").setItems(new String[]{"股票 / ETF","数字货币"},(d,index)->positionForm(null,null,index==1)).setNegativeButton("取消",null).show();return;}
        positionForm(holding,fixed,holding!=null?WealthStore.isCrypto(holding):"crypto".equals(fixed.kind));
    }
    private void positionForm(WealthStore.Holding holding,WealthStore.Account fixed,boolean crypto){
        List<WealthStore.Account> accounts=new ArrayList<>();for(WealthStore.Account a:store.accounts)if((crypto?"crypto":"securities").equals(a.kind))accounts.add(a);
        if(accounts.isEmpty()){new HandDialogBuilder().setTitle(crypto?"先添加数字资产账户":"先添加证券账户").setMessage("持仓需要归属到账户，才能与可用现金一起计算资产。").setNegativeButton("稍后",null).setPositiveButton("添加账户",(d,w)->accountForm(null,crypto?"crypto":"securities")).show();return;}
        LinearLayout f=form();label(f,"所属账户");String[] names=new String[accounts.size()];int selected=0;for(int i=0;i<names.length;i++){names[i]=accounts.get(i).name;if((holding!=null&&accounts.get(i).id.equals(holding.accountId))||(fixed!=null&&accounts.get(i).id.equals(fixed.id)))selected=i;}
        Spinner account=spinner(names);account.setSelection(selected);if(holding!=null)account.setEnabled(false);f.addView(account);
        EditText code;Spinner coin;
        if(crypto){code=null;label(f,"币种");coin=spinner(WealthStore.COIN_SYMBOLS);if(holding!=null){for(int i=0;i<WealthStore.COIN_IDS.length;i++)if(holding.code.equals("crypto:"+WealthStore.COIN_IDS[i]))coin.setSelection(i);coin.setEnabled(false);}f.addView(coin);}else{coin=null;code=input(f,"A 股 / ETF 代码",holding==null?"":holding.code,"例如 600519、510300",false);if(holding!=null)code.setEnabled(false);}
        EditText quantity=input(f,crypto?"持仓数量（币）":"持仓数量（股 / 份）",holding==null?"":WealthStore.quantityText(holding),crypto?"例如 0.025，最多 8 位小数":"请输入整数",true);if(!crypto)quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText cost=input(f,crypto?"每枚成本（人民币元）":"每股 / 每份成本（元）",holding==null?"":new java.math.BigDecimal(holding.costPriceMicros).movePointLeft(6).stripTrailingZeros().toPlainString(),"单位成本，最多 6 位小数",true);
        note(f,"录入统一使用人民币 CNY，不会扣减现金。"+(crypto?"CoinGecko 行情显示 24 小时涨跌；数量和成本由你维护。":"买卖后需更新数量、成本和账户现金。"));
        showForm(holding==null?(crypto?"添加数字货币":"添加持仓"):"修改持仓",f,"保存并更新行情",()->{
            long costValue=WealthStore.parsePrice(cost.getText().toString());String accountId=accounts.get(account.getSelectedItemPosition()).id;
            if(crypto){String qty=WealthStore.parseCoinQuantity(quantity.getText().toString());if(holding==null)store.addCryptoHolding(accountId,WealthStore.COIN_SYMBOLS[coin.getSelectedItemPosition()],qty,costValue);else store.updateCryptoHolding(holding.id,qty,costValue);}
            else{long qty;try{qty=Long.parseLong(quantity.getText().toString());}catch(Exception e){throw new IllegalArgumentException("持仓数量请输入有效整数");}if(holding==null){String symbol=QuoteClient.normalizeCode(code.getText().toString());store.addHolding(accountId,symbol,symbol.toUpperCase(Locale.ROOT),qty,costValue);}else store.updateHolding(holding.id,qty,costValue);}
            handler.post(()->app.refresh(null));
        });
    }
    private void transferForm(){
        if(previewGuard())return;
        if(store.accounts.size()<2){message("至少需要两个账户，才能进行资金划转。");return;}
        LinearLayout f=form();String[] names=new String[store.accounts.size()];for(int i=0;i<names.length;i++)names[i]=store.accounts.get(i).name;
        label(f,"从哪个账户转出");Spinner from=spinner(names);f.addView(from);label(f,"转入哪个账户");Spinner to=spinner(names);to.setSelection(1);f.addView(to);
        EditText amount=input(f,"划转金额（人民币元）","","0.00",true);EditText memo=input(f,"备注（可选）","","例如：转入证券账户",false);
        note(f,"仅在两个账户的现金间划转，总资产不变。手续费请另行调整余额。");
        showForm("资金划转",f,"确认划转",()->store.transfer(store.accounts.get(from.getSelectedItemPosition()).id,store.accounts.get(to.getSelectedItemPosition()).id,WealthStore.parseMoney(amount.getText().toString()),memo.getText().toString()));
    }
    private void settings(){
        String[] options={"变更记录","导出加密备份","从备份恢复","行情与计算说明","关于Gather","预览示例","界面风格","显示币种"};
        new HandDialogBuilder().setTitle("设置与数据").setItems(options,(d,w)->{if(w==7)currencySettings();else if(w==6)appearance();else if(w==0)history();else if(w==1)exportBackup();else if(w==2)chooseImport();else if(w==3)quoteInfo();else if(w==5){safe(this::createPreview);tab=0;render();}else new HandDialogBuilder().setTitle("Gather  0.7.1").setMessage("只关心值得关注的资产。\n\nAndroid 原生 · 数据保存在本机\n股票 / ETF / 数字货币 · CNY / USD\n\n不会连接券商账户或执行交易。卸载会清除本地账本，请定期导出加密备份。").setPositiveButton("知道了",null).show();}).setNegativeButton("返回",null).show();
    }
    private void currencySettings(){
        LinearLayout f=form();label(f,"显示币种");Spinner currency=spinner(new String[]{"人民币 CNY","美元 USD"});currency.setSelection(money.usd?1:0);f.addView(currency);
        String oldRate=getSharedPreferences("appearance",0).getString("fxRate","");EditText rate=input(f,"1 美元 = 多少人民币",oldRate,"更新参考汇率或手动填写",true);
        final String[] fetched={oldRate,getSharedPreferences("appearance",0).getString("fxDate","手动汇率")};TextView state=text(fetched[1],12,muted,false);space(f,12);f.addView(state);
        Button refresh=button("更新参考汇率",()->{},false);refresh.setOnClickListener(v->{refresh.setEnabled(false);state.setText("正在获取参考汇率…");new Thread(()->{try{String[] r=QuoteClient.fetchUsdRate();handler.post(()->{if(isFinishing())return;rate.setText(r[0]);fetched[0]=r[0];fetched[1]="Frankfurter · "+r[1];state.setText(fetched[1]);refresh.setEnabled(true);});}catch(Exception e){handler.post(()->{state.setText("更新失败，可保留原值或手动填写");refresh.setEnabled(true);});}},"guilong-fx").start();});space(f,10);f.addView(refresh);
        note(f,"仅切换显示，不改写人民币账本。录入与历史流水保持人民币；历史曲线按此参考汇率统一换算，不是当时的美元估值。");
        showForm("显示币种",f,"应用",()->{String value=rate.getText().toString().trim();boolean usd=currency.getSelectedItemPosition()==1;if(usd||!value.isEmpty())DisplayMoney.parseRate(value);String source=value.equals(fetched[0])?fetched[1]:"手动 · "+WealthStore.today();getSharedPreferences("appearance",0).edit().putBoolean("usd",usd).putString("fxRate",value).putString("fxDate",source).apply();});
    }
    private void appearance(){
        Dialog dialog=new Dialog(this);LinearLayout panel=form();panel.setPadding(dp(24),dp(24),dp(24),dp(20));panel.setBackground(shape(surface,12));
        panel.addView(text("界面风格",23,ink,true));space(panel,8);panel.addView(text("同一本手帐，两种用笔。",14,muted,false));space(panel,20);
        String[] names={"彩铅手帐","蓝墨手帐"},descriptions={"暖纸 · 彩色铅笔 · 自然手写","冷纸 · 蓝色墨水 · 自然手写"};
        int[][] swatches={{0xFF397EB5,0xFF809A53,0xFFC08B42,0xFF9873B1},{0xFF3679AA,0xFF588DA1,0xFF6B78A4,0xFF428584}};
        for(int i=0;i<2;i++){final int choice=i;boolean selected=sketch.blue==(i==1);LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(selected?pale:surface,12));
            LinearLayout heading=row();heading.addView(text(names[i],19,ink,true),weight());if(selected)heading.addView(text("使用中",12,accent,false));card.addView(heading);space(card,8);card.addView(text(descriptions[i],12,muted,false));space(card,14);
            LinearLayout colors=row();for(int color:swatches[i]){View swatch=new View(this);swatch.setBackground(sketch.box(color,color,getResources().getDisplayMetrics().density));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(10),1);cp.rightMargin=dp(7);colors.addView(swatch,cp);}card.addView(colors);card.setContentDescription(names[i]);card.setFocusable(true);card.setSelected(selected);card.setOnClickListener(v->{getSharedPreferences("appearance",0).edit().putInt("sketch",choice).apply();palette();dialog.dismiss();render();});panel.addView(card);space(panel,14);}
        panel.addView(textAction("返回",dialog::dismiss));dialog.setContentView(panel);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.show();dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
    }
    private class HandDialogBuilder extends AlertDialog.Builder {
        HandDialogBuilder(){super(MainActivity.this);}
        @Override public AlertDialog create(){AlertDialog d=super.create();d.setOnShowListener(ignored->{d.getWindow().setBackgroundDrawable(shape(surface,12));styleDialog(d.getWindow().getDecorView());if(d.getListView()!=null)d.getListView().setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View p,View c){styleDialog(c);}public void onChildViewRemoved(View p,View c){}});});return d;}
    }
    private void styleDialog(View v){if(v instanceof TextView){TextView t=(TextView)v;t.setTypeface(hand);t.setTextColor(v instanceof Button?accent:ink);}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)styleDialog(g.getChildAt(i));}}
    private void history(){
        LinearLayout list=form();if(store.entries.isEmpty())list.addView(text("还没有变更记录。",15,muted,false));
        int count=0;for(WealthStore.Entry e:store.entries){if(count++>=100)break;list.addView(text(e.title,16,ink,true));list.addView(text(dateTime(e.time)+" · "+(hidden?"金额已隐藏":e.detail),12,muted,false));space(list,8);divider(list);space(list,14);}
        ScrollView sc=new ScrollView(this);sc.addView(list);new HandDialogBuilder().setTitle("最近变更").setView(sc).setPositiveButton("关闭",null).show();
    }
    private void quoteInfo(){
        new HandDialogBuilder().setTitle("行情与计算").setMessage("行情来源：A 股 / ETF 使用腾讯行情；数字货币使用 CoinGecko 人民币快照。\n\n打开 App 自动刷新，前台每 60 秒检查一次；后台由 Android 择机更新，不能保证准点。每笔持仓展示报价时间，断网时保留上次报价。公共接口可能延迟或不可用。\n\n总资产 = 所有账户现金 + 持仓市值。\n持仓浮盈 = 当前市值 − 录入成本。\n股票较昨收变动 = 当前数量 ×（现价 − 昨收）；数字货币显示 24 小时涨跌。行情变动合计混合这两种周期，只供观察，不是投资收益。不含日内交易、手续费、分红或已实现收益。\n\n无有效报价时暂按成本估值。股票数量、成本及证券可用现金需要你手动核对；本工具不自动识别交易或拆并股。\n\n联网请求仅发送证券或币种代码，不发送资产金额和账户名称。").setPositiveButton("知道了",null).show();
    }
    private void backupChanged(){
        if(app.backup.busy()){
            if(backupProgress==null){backupProgress=new ProgressDialog(this);backupProgress.setMessage("正在处理备份…");backupProgress.setCancelable(false);backupProgress.show();}
            return;
        }
        if(backupProgress!=null){backupProgress.dismiss();backupProgress=null;}
        if(backupDeferredRender)render();
        BackupWork.Result result=app.backup.peek();if(result==null)return;
        if(!"confirm".equals(result.kind))app.backup.take();
        if("choose".equals(result.kind)){
            choosingBackup=true;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Gather-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date())+".guilong");
            try{startActivityForResult(intent,100);}catch(Exception e){choosingBackup=false;app.backup.pendingFile().delete();message("无法打开保存位置");}
        }else if("saved".equals(result.kind))message("加密备份已保存");
        else if("error".equals(result.kind))message(result.value);
        else if("restored".equals(result.kind)){app.loadError=null;detailId=null;dailyPage=false;sankeyPage=false;dailyOffset=0;render();app.refresh(null);message("备份已恢复");}
        else if("confirm".equals(result.kind)){
            String json=result.value;
            if(backupConfirmation!=null)return;
            backupConfirmation=new HandDialogBuilder().setTitle("恢复这份备份？").setMessage("备份包含 "+result.accounts+" 个账户、"+result.holdings+" 笔持仓。\n\n恢复将替换当前账本。建议先导出当前账本的备份。").setNegativeButton("取消",(d,w)->{backupConfirmation=null;app.backup.take();}).setPositiveButton("替换并恢复",(d,w)->{backupConfirmation=null;app.backup.take();app.invalidateQuotes();app.backup.restore(store,json);}).setCancelable(false).show();
        }
    }
    private void exportBackup(){
        if(previewGuard())return;
        if(store==null||app.loadError!=null){message("当前数据未能读取，无法导出。");return;}
        if(app.backup.busy()||choosingBackup){message("备份操作正在进行");return;}
        LinearLayout f=form();EditText pwd=input(f,"设置备份密码","","至少 8 位，请妥善保管",false);pwd.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        note(f,"文件使用 AES-GCM 加密。忘记密码无法恢复，请保存到你自己的位置。");
        showForm("导出加密备份",f,"选择保存位置",()->{char[] password=pwd.getText().toString().toCharArray();if(password.length<8){Arrays.fill(password,'\0');throw new IllegalArgumentException("备份密码至少 8 位");}pwd.setText("");app.backup.encrypt(store,password);});
    }
    private void chooseImport(){if(previewGuard())return;if(app.backup.busy()||choosingBackup){message("备份操作正在进行");return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),101);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==100){choosingBackup=false;if(result!=RESULT_OK||data==null||data.getData()==null){app.backup.pendingFile().delete();return;}if(!app.backup.pendingFile().isFile()){message("备份已失效，请重新导出");return;}app.backup.save(data.getData());}
        if(request==101&&result==RESULT_OK&&data!=null&&data.getData()!=null)importPassword(data.getData());
    }
    private void importPassword(android.net.Uri uri){
        LinearLayout f=form();EditText pwd=input(f,"备份密码","","输入导出时设置的密码",false);pwd.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        showForm("解密备份",f,"检查备份",()->{char[] password=pwd.getText().toString().toCharArray();pwd.setText("");app.backup.decrypt(uri,password);});
    }
    private void quoteLine(){
        String timestamp="";int missing=0;for(WealthStore.Holding h:store.holdings){if(!quoted(h)){missing++;continue;}if(timestamp.isEmpty()||h.quoteTime.compareTo(timestamp)<0)timestamp=h.quoteTime;}
        String value=preview?"示例行情 · 静态展示":app.refreshing?"行情更新中…":missing>0?missing+" 笔暂无报价 · 按成本估值":timestamp.isEmpty()?"添加持仓后自动更新行情":"腾讯 / CoinGecko · "+quoteTime(timestamp);
        if(!preview&&!app.refreshing){if(app.quoteStatus.contains("失败")||app.quoteStatus.contains("无法更新"))value="更新失败 · 保留原估值";else if(app.quoteStatus.contains("部分持仓"))value="部分持仓未更新 · 保留原估值";}
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(value,10,muted,false);r.addView(label,weight());r.addView(iconButton("info","行情与计算说明",this::quoteInfo));body.addView(r);
    }
    private void navigation(){
        divider(root);LinearLayout nav=row();nav.setPadding(dp(8),dp(5),dp(8),dp(4));nav.setBackgroundColor(surface);
        String[] labels={"总览","统计","持仓","账户"},icons={"overview","stats","hold","bank"};int[] pages={0,3,1,2};
        for(int i=0;i<4;i++){final int index=pages[i];boolean active=tab==index;LinearLayout item=column();item.setGravity(Gravity.CENTER);FrameLayout capsule=new FrameLayout(this);capsule.setBackground(active?shape(pale,16):new ColorDrawable(Color.TRANSPARENT));Icon icon=new Icon(this,icons[i],active?accent:muted);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);capsule.addView(icon,ip);item.addView(capsule,new LinearLayout.LayoutParams(dp(56),dp(32)));TextView label=text(labels[i],11,active?accent:muted,false);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(5),0,0);item.addView(label);item.setBackground(selectable());item.setContentDescription(labels[i]);item.setFocusable(true);item.setSelected(active);item.setOnClickListener(v->{tab=index;detailId=null;dailyPage=false;sankeyPage=false;render();});nav.addView(item,new LinearLayout.LayoutParams(0,dp(64),1));}root.addView(nav);
    }

    private void scheduleQuotes(){
        try{JobScheduler scheduler=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);scheduler.schedule(new JobInfo.Builder(721,new ComponentName(this,QuoteJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(60*60*1000).setPersisted(false).build());}catch(Exception ignored){}
    }
    private void showForm(String title,LinearLayout form,String action,Runnable submit){
        editing=true;Dialog dialog=new Dialog(this);LinearLayout sheet=column();sheet.setBackground(shape(surface,24));
        LinearLayout top=row();top.setPadding(dp(24),dp(12),dp(12),0);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text(title,20,ink,true),weight());top.addView(iconButton("close","取消",dialog::dismiss));sheet.addView(top);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.addView(form);sheet.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=column();footer.setPadding(dp(24),dp(12),dp(24),dp(24));primary(footer,action,()->{try{submit.run();dialog.dismiss();render();}catch(Exception e){message(e.getMessage()==null?"未能保存，请检查输入":e.getMessage());}},true);sheet.addView(footer);
        dialog.setContentView(sheet);dialog.setOnDismissListener(d->{editing=false;});Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.24f;window.setAttributes(attrs);dialog.show();form.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        final int desiredHeight=Math.min(form.getMeasuredHeight()+dp(148),(int)(getResources().getDisplayMetrics().heightPixels*.86));
        window.setLayout(-1,desiredHeight);window.setNavigationBarColor(surface);
        sheet.getViewTreeObserver().addOnGlobalLayoutListener(()->{Rect visible=new Rect();window.getDecorView().getWindowVisibleDisplayFrame(visible);int available=visible.height();if(available>dp(200)){int target=Math.min(desiredHeight,available);if(window.getAttributes().height!=target)window.setLayout(-1,target);}});
        window.getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void safe(Runnable action){try{action.run();}catch(Exception e){message(e.getMessage()==null?"操作未完成，请重试":e.getMessage());}}
    private void message(String message){new HandDialogBuilder().setMessage(message).setPositiveButton("知道了",null).show();}
    private void empty(String title,String description,String action,Runnable run){body.addView(text(title,26,ink,false));space(body,13);body.addView(text(description,15,muted,false));if(action!=null){space(body,23);primary(body,action,run,true);}}
    private void section(String title,String action,Runnable run){space(body,24);LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.addView(text(title,17,ink,true),weight());if(action!=null){TextView a=text(action,13,run==null?muted:accent,false);a.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);a.setMinimumHeight(dp(48));a.setMinWidth(dp(48));if(run!=null){a.setPadding(dp(15),0,0,0);a.setOnClickListener(v->run.run());a.setBackground(selectable());}r.addView(a);}body.addView(r);space(body,4);}
    private void primary(LinearLayout p,String text,Runnable action,boolean filled){p.addView(button(text,action,filled),new LinearLayout.LayoutParams(-1,dp(52)));}
    private Button button(String value,Runnable action,boolean filled){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(ink);b.setTypeface(hand);b.setBackground(new RippleDrawable(ColorStateList.valueOf(night?0x227777FF:0x22315CFF),shape(filled?pale:surface,12),null));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(52));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setOnClickListener(v->action.run());return b;}
    private TextView textAction(String value,Runnable action){TextView t=text(value,13,accent,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),0,dp(12),0);t.setMinimumHeight(dp(48));t.setMinWidth(dp(48));t.setBackground(selectable());t.setOnClickListener(v->action.run());t.setFocusable(true);return t;}
    private TextView number(String value,float size,int color){TextView t=text(value,size,color,false);t.setTypeface(figures);t.setFontFeatureSettings("tnum");return t;}

    private View iconButton(String icon,String description,Runnable action){Icon view=new Icon(this,icon,muted);view.setContentDescription(description);view.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));view.setPadding(dp(13),dp(13),dp(13),dp(13));view.setBackground(selectable());view.setOnClickListener(v->action.run());view.setFocusable(true);return view;}
    private LinearLayout metric(String label,String value,int color){LinearLayout col=column();col.addView(text(label,12,muted,false));TextView v=number(value,20,color);v.setMaxLines(2);v.setPadding(0,dp(8),dp(6),0);col.addView(v);return col;}
    private void addInfo(LinearLayout f,String label,String value){LinearLayout r=row();r.setPadding(0,dp(9),0,dp(9));r.addView(text(label,14,muted,false),weight());r.addView(text(value,15,ink,true));f.addView(r);}
    private EditText input(LinearLayout f,String title,String value,String hint,boolean number){label(f,title);EditText e=new EditText(this);e.setTextSize(16);e.setTextColor(ink);e.setHintTextColor(muted);e.setText(value);e.setHint(hint);e.setSingleLine(true);e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(bg,10));e.setMinimumHeight(dp(50));e.setTypeface(hand);e.setSelectAllOnFocus(true);e.setContentDescription(title);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Spinner spinner(String[] items){Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTypeface(hand);v.setTextColor(ink);v.setTextSize(16);v.setPadding(dp(12),dp(12),dp(10),dp(12));return v;}@Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convert,parent);v.setTypeface(hand);v.setTextColor(ink);v.setBackgroundColor(surface);return v;}};adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setMinimumHeight(dp(50));spinner.setBackground(shape(bg,10));return spinner;}
    private void label(LinearLayout f,String value){TextView t=text(value,13,muted,true);t.setPadding(0,dp(18),0,dp(8));f.addView(t);}
    private void note(LinearLayout f,String value){TextView t=text(value,12,muted,false);t.setPadding(0,dp(18),0,dp(6));f.addView(t);}
    private LinearLayout form(){LinearLayout f=column();f.setPadding(dp(24),dp(4),dp(24),dp(20));return f;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1f);t.setTypeface(hand);return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void space(LinearLayout parent,int height){Space space=new Space(this);parent.addView(space,new LinearLayout.LayoutParams(1,dp(height)));}
    private void divider(LinearLayout p){View v=new View(this);v.setBackground(sketch.rule(getResources().getDisplayMetrics().density));p.addView(v,new LinearLayout.LayoutParams(-1,dp(3)));}
    private android.graphics.drawable.Drawable shape(int color,int radius){return sketch.box(color,muted,getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.Drawable selectable(){android.util.TypedValue value=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);return getDrawable(value.resourceId);}
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    private String amount(long fen){return hidden?"••••••":money.amount(fen);}
    private String signed(long fen){return hidden?"••••":money.signed(fen);}
    private String price(long micros){return hidden?"••••":money.price(micros);}
    private String decimal(long fen){return new java.math.BigDecimal(fen).movePointLeft(2).toPlainString();}
    private int changeColor(long value){return value>0?red:value<0?negative:muted;}
    private String kindName(String kind){return "securities".equals(kind)?"证券账户":"crypto".equals(kind)?"数字资产账户":"bank".equals(kind)?"银行存款":"其他资金";}
    private String date(long timestamp){return new SimpleDateFormat("MM.dd",Locale.CHINA).format(new Date(timestamp));}
    private String dateTime(long timestamp){return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(timestamp));}
    private boolean quoted(WealthStore.Holding h){return h.lastPriceMicros>0&&h.quoteTime!=null&&!h.quoteTime.isEmpty();}
    private String quoteTime(String time){if(time==null||time.length()!=14)return "未取得";return time.substring(0,4)+"-"+time.substring(4,6)+"-"+time.substring(6,8)+" "+time.substring(8,10)+":"+time.substring(10,12);}
    private List<WealthStore.Holding> holdingList(String account){List<WealthStore.Holding> list=new ArrayList<>();for(WealthStore.Holding h:store.holdings)if(h.accountId.equals(account))list.add(h);return list;}

    private class Allocation extends View{
        final long cash,market;Allocation(Context c,long cash,long market){super(c);this.cash=cash;this.market=market;setContentDescription("资产配置比例");}
        protected void onDraw(Canvas c){Paint p=new Paint(3);p.setColor(line);c.drawRoundRect(0,0,getWidth(),getHeight(),dp(4),dp(4),p);if(cash+market>0){float w=getWidth()*(float)((double)cash/(cash+market));p.setColor(ink);c.drawRoundRect(0,0,w,getHeight(),dp(4),dp(4),p);p.setColor(accent);if(w<getWidth())c.drawRoundRect(w+dp(3),0,getWidth(),getHeight(),dp(4),dp(4),p);}}
    }
    private class Icon extends View{
        final String kind;final int color;Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getPaddingLeft(),getPaddingTop());float w=getWidth()-getPaddingLeft()-getPaddingRight(),h=getHeight()-getPaddingTop()-getPaddingBottom();canvas.scale(w/24f,h/24f);Paint p=new Paint(3);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setPathEffect(new DiscretePathEffect(2,.13f));
            if("mark".equals(kind)){Path path=new Path();path.moveTo(10,3);path.lineTo(3,3);path.lineTo(3,21);path.lineTo(21,21);path.lineTo(21,14);canvas.drawPath(path,p);path=new Path();path.moveTo(14,3);path.lineTo(21,3);path.lineTo(21,10);canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);p.setColor(accent);canvas.drawRect(9,9,15,15,p);}
            else if("close".equals(kind)){canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);}
            else if("info".equals(kind)){canvas.drawCircle(12,12,9,p);canvas.drawLine(12,11,12,17,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,7,1,p);}
            else if("refresh".equals(kind)){canvas.drawArc(4,4,20,20,35,290,false,p);canvas.drawLine(20,4,20,10,p);canvas.drawLine(14,10,20,10,p);}
            else if("overview".equals(kind)){canvas.drawRoundRect(3,3,10,10,1.5f,1.5f,p);canvas.drawRoundRect(14,3,21,10,1.5f,1.5f,p);canvas.drawRoundRect(3,14,10,21,1.5f,1.5f,p);canvas.drawRoundRect(14,14,21,21,1.5f,1.5f,p);}
            else if("stats".equals(kind)){canvas.drawLine(4,20,21,20,p);canvas.drawLine(6,16,6,11,p);canvas.drawLine(12,16,12,4,p);canvas.drawLine(18,16,18,8,p);}
            else if("hold".equals(kind)){canvas.drawLine(4,20,4,4,p);canvas.drawLine(4,20,21,20,p);Path path=new Path();path.moveTo(7,15);path.lineTo(12,10);path.lineTo(16,12);path.lineTo(21,5);canvas.drawPath(path,p);}
            else if("bank".equals(kind)){Path path=new Path();path.moveTo(3,8);path.lineTo(12,3);path.lineTo(21,8);path.close();canvas.drawPath(path,p);canvas.drawLine(3,21,21,21,p);for(int x=6;x<=18;x+=6)canvas.drawLine(x,11,x,18,p);}
            else if("wallet".equals(kind)){canvas.drawRoundRect(3,5,21,20,2,2,p);canvas.drawLine(4,5,17,2,p);canvas.drawRoundRect(14,10,22,16,1,1,p);canvas.drawPoint(17,13,p);}
            else if("card".equals(kind)){canvas.drawRoundRect(2,5,22,19,2,2,p);canvas.drawLine(2,10,22,10,p);canvas.drawLine(5,15,10,15,p);}
            else if("coin".equals(kind)){canvas.drawCircle(12,12,9,p);canvas.drawCircle(12,12,6,p);canvas.drawLine(10,7,10,17,p);canvas.drawArc(8,7,16,12,-90,180,false,p);canvas.drawArc(8,12,16,17,-90,180,false,p);}
            else if("safe".equals(kind)){canvas.drawRoundRect(3,3,21,21,2,2,p);canvas.drawCircle(12,12,5,p);canvas.drawLine(12,8,12,16,p);canvas.drawLine(8,12,16,12,p);}
            else if("home".equals(kind)){Path q=new Path();q.moveTo(2,11);q.lineTo(12,3);q.lineTo(22,11);q.moveTo(5,9);q.lineTo(5,21);q.lineTo(19,21);q.lineTo(19,9);canvas.drawPath(q,p);canvas.drawRect(10,14,14,21,p);}
            else if("globe".equals(kind)){canvas.drawCircle(12,12,9,p);canvas.drawOval(8,3,16,21,p);canvas.drawLine(3,12,21,12,p);}
            else if("briefcase".equals(kind)){canvas.drawRoundRect(3,7,21,20,2,2,p);canvas.drawRect(8,3,16,7,p);canvas.drawLine(3,12,21,12,p);canvas.drawLine(12,11,12,15,p);}
            else if("star".equals(kind)){Path q=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?10:4.5f,x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}q.close();canvas.drawPath(q,p);}
            else if("gift".equals(kind)){canvas.drawRect(4,10,20,21,p);canvas.drawRect(2,7,22,11,p);canvas.drawLine(12,7,12,21,p);canvas.drawOval(5,2,12,7,p);canvas.drawOval(12,2,19,7,p);}
            else if("eye".equals(kind)){Path path=new Path();path.moveTo(2,12);path.cubicTo(7,4,17,4,22,12);path.cubicTo(17,20,7,20,2,12);canvas.drawPath(path,p);canvas.drawCircle(12,12,3,p);if(hidden)canvas.drawLine(3,3,21,21,p);}
            else if("back".equals(kind)){canvas.drawLine(19,12,5,12,p);canvas.drawLine(5,12,11,6,p);canvas.drawLine(5,12,11,18,p);}
            else{for(int y=5;y<=19;y+=7){canvas.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(y==12?15:8,y,2.6f,p);p.setStyle(Paint.Style.STROKE);}}
            canvas.restore();}
    }
}
