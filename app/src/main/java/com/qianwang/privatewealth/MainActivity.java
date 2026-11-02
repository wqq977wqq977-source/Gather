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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MainActivity extends Activity {
    private WealthApp app; private WealthStore store;
    private LinearLayout root,body; private ScrollView viewport; private int tab=4; private String detailId=null;
    private boolean hidden=false,editing=false,night=false,preview=false;
    private Typeface figures,hand; private SketchStyle sketch;
    private boolean sankeyByAccount=false;
    private DisplayMoney money;
    private ArrayList<String> sankeyPath=new ArrayList<>();
    private int bg, surface, ink, muted, line, accent, pale, red, positive;
    private ProgressDialog backupProgress;
    private AlertDialog backupConfirmation;
    private boolean choosingBackup;
    private boolean backupDeferredRender;
    private MultiLayerSankeyView currentSankey;
    private HorizontalScrollView sankeyCanvas;
    private String refreshSelection;
    private boolean sankeyExpanded;
    private int refreshSankeyScroll;
    private int distributionMode=0;
    private int statisticsDays=30;
    private YearMonth calendarMonth=YearMonth.from(LocalDate.parse(WealthStore.today()));
    private String calendarSelected;
    private boolean calendarPercent;
    private boolean sankeyPage=false;
    private String sankeyAccountKey; private int sankeyCategory=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable onUpdate=()->{if(!editing&&!preview&&!app.backup.busy()){int y=viewport==null?0:viewport.getScrollY();refreshSelection=currentSankey==null?null:currentSankey.selectedKey();refreshSankeyScroll=sankeyCanvas==null?0:sankeyCanvas.getScrollX();render();if(viewport!=null)viewport.post(()->viewport.scrollTo(0,y));}};
    private final Runnable ticker=new Runnable(){public void run(){if(!preview&&!app.backup.busy()){app.refreshFx(false,null);app.refresh(null);}handler.postDelayed(this,60000);}};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        sankeyByAccount=getPreferences(0).getBoolean("sankeyByAccount",false);
        app=(WealthApp)getApplication();store=app.store;tab=navOrder()[0];
        hidden=getPreferences(0).getBoolean("hide",false);
        calendarPercent=getPreferences(0).getBoolean("calendarPercent",false);
        if(saved!=null){ArrayList<String> path=saved.getStringArrayList("sankeyPath");if(path!=null)sankeyPath=path;tab=saved.getInt("tab",navOrder()[0]);settingsPage=saved.getBoolean("settingsPage",false);navigationSettingsPage=saved.getBoolean("navigationSettingsPage",false);if(tab==2)tab=0;detailId=saved.getString("detail");sankeyPage=saved.getBoolean("sankeyPage",false);sankeyAccountKey=saved.getString("sankeyAccountKey");sankeyCategory=saved.getInt("sankeyCategory",-1);statisticsDays=saved.getInt("statisticsDays",30);calendarMonth=YearMonth.parse(saved.getString("calendarMonth",calendarMonth.toString()));calendarSelected=saved.getString("calendarSelected");calendarPercent=saved.getBoolean("calendarPercent",false);choosingBackup=saved.getBoolean("choosingBackup",false);}
        palette();
        hand=SketchStyle.handwriting(this);figures=SketchStyle.numerals(this);
        if(saved!=null&&saved.getBoolean("preview",false)){createPreview();sankeyPage=saved.getBoolean("sankeyPage",false);}
        render();scheduleQuotes();
    }
    @Override protected void onResume(){super.onResume();app.listeners.add(onUpdate);app.backup.listen(this::backupChanged);app.refreshFx(false,null);handler.post(ticker);}
    @Override protected void onPause(){super.onPause();app.listeners.remove(onUpdate);app.backup.listen(null);if(backupConfirmation!=null){backupConfirmation.dismiss();backupConfirmation=null;}if(backupProgress!=null){backupProgress.dismiss();backupProgress=null;}handler.removeCallbacks(ticker);}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("settingsPage",settingsPage);out.putBoolean("navigationSettingsPage",navigationSettingsPage);out.putBoolean("choosingBackup",choosingBackup);out.putStringArrayList("sankeyPath",sankeyPath);out.putBoolean("sankeyPage",sankeyPage);out.putString("sankeyAccountKey",sankeyAccountKey);out.putInt("sankeyCategory",sankeyCategory);out.putInt("statisticsDays",statisticsDays);out.putString("calendarMonth",calendarMonth.toString());out.putString("calendarSelected",calendarSelected);out.putBoolean("calendarPercent",calendarPercent);out.putBoolean("preview",preview);out.putInt("tab",tab);out.putString("detail",detailId);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(settingsPage){if(navigationSettingsPage)navigationSettingsPage=false;else settingsPage=false;render();}else if(detailId!=null){detailId=null;render();}else if(sankeyPage){if(!sankeyPath.isEmpty())sankeyPath.remove(sankeyPath.size()-1);else sankeyPage=false;sankeyAccountKey=null;render();}else if(tab!=navOrder()[0]){tab=navOrder()[0];render();}else if(preview)exitPreview();else super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)applySystemBars(getWindow());}
    private void applySystemBars(Window window){
        if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=window.getInsetsController();if(c!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(night?0:mask,mask);}}else window.getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void palette(){
        night=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        sketch=new SketchStyle(this);bg=sketch.paper;surface=sketch.surface;ink=sketch.ink;muted=sketch.muted;line=sketch.line;accent=sketch.accent;pale=sketch.pale;
        red=Color.parseColor(night?"#F3B3A7":"#A23D36");positive=Color.parseColor(night?"#A3D1B3":"#376A4D");
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
        if(settingsPage||detailId!=null||sankeyPage)top.addView(iconButton("back","返回",()->{if(settingsPage){if(navigationSettingsPage)navigationSettingsPage=false;else settingsPage=false;}else if(detailId!=null)detailId=null;else if(sankeyPage&&!sankeyPath.isEmpty()){sankeyPath.remove(sankeyPath.size()-1);sankeyAccountKey=null;}else {sankeyPage=false;}render();}));
        else if(tab==0){Icon mark=new Icon(this,"mark",ink);top.addView(mark,new LinearLayout.LayoutParams(dp(24),dp(24)));Space gap=new Space(this);top.addView(gap,new LinearLayout.LayoutParams(dp(10),1));}
        TextView title=text(settingsPage?(navigationSettingsPage?"底部导航":"设置与数据"):detailId!=null?"账户详情":sankeyPage?"资产桑基图":tab==4?"资产日历":tab==0?"Gather":tab==1?"资产持仓":tab==3?"资产统计":"资金账户",20,ink,true);
        title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        if(!settingsPage&&tab==1&&detailId==null){View refresh=iconButton("refresh",preview?"示例行情说明":app.refreshing?"行情更新中":"刷新行情",()->{if(preview)message("此页为静态示例行情，不是你的实际持仓或当前价格。");else app.refresh(null);});refresh.setEnabled(!app.refreshing||preview);refresh.setAlpha(refresh.isEnabled()?1f:.4f);top.addView(refresh);}
        if(!settingsPage)top.addView(iconButton("eye",hidden?"显示金额":"隐藏金额",()->{hidden=!hidden;getPreferences(0).edit().putBoolean("hide",hidden).apply();render();}));
        if(!settingsPage)top.addView(iconButton("settings","设置与备份",this::settings));root.addView(top);divider(root);
        if(preview){LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(24),0,dp(12),0);bar.setBackgroundColor(pale);bar.addView(text("示例预览 · 不计入你的资产",11,accent,false),weight());bar.addView(textAction("退出",this::exitPreview));root.addView(bar);}
        viewport=new ScrollView(this);viewport.setFillViewport(true);viewport.setClipToPadding(false);viewport.setVerticalScrollBarEnabled(false);
        body=column();body.setPadding(dp(24),dp(24),dp(24),dp(28));viewport.addView(body);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        if(!settingsPage&&app.loadError!=null&&!preview){empty("数据暂时无法读取",app.loadError,"导入备份",this::chooseImport);return;}
        if(settingsPage){if(navigationSettingsPage)navigationSettings();else settingsHome();}else if(detailId!=null)accountDetail();else if(sankeyPage)sankey();else if(tab==0)overview();else if(tab==1)holdings();else if(tab==3)statistics();else calendar();
        if(!settingsPage)navigation();
    }
    private void overview(){
        if(store.accounts.isEmpty()&&store.dailySnapshots.isEmpty()){welcome();return;}
        LinearLayout caption=row();caption.setGravity(Gravity.CENTER_VERTICAL);caption.addView(text("总资产",14,muted,false),weight());caption.addView(text(dayLabel(WealthStore.today()),12,muted,false));body.addView(caption);
        heroAmount(store.totalAssets());space(body,12);todayChange();
        section("资产增长","查看统计",()->{tab=3;render();});
        List<WealthStore.DailySnapshot> recent=historyInRange(30);trendPanel(body,recent,150);
        space(body,20);LinearLayout split=row();split.addView(metric("资金余额",amount(store.totalCash()),ink),weight());split.addView(metric("持仓市值",amount(store.totalMarketValue()),ink),weight());body.addView(split);
        if(!store.holdings.isEmpty()){space(body,22);marketMetrics();quoteLine();}
        section("我的账户","添加资产",this::addAssetMenu);for(WealthStore.Account a:store.accounts)accountRow(a);
        if(!store.accounts.isEmpty()){space(body,16);LinearLayout move=row();move.setGravity(Gravity.CENTER);move.addView(textAction("记收入",()->incomeForm(null)));move.addView(textAction("资金划转",this::transferForm));body.addView(move);}
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
        section("资产分布","桑基图",()->{sankeyPage=true;sankeyPath.clear();sankeyAccountKey=null;sankeyCategory=-1;render();});distribution();
        if(!history.isEmpty()){space(body,24);long high=history.get(0).totalFen(),low=high;for(WealthStore.DailySnapshot d:history){high=Math.max(high,d.totalFen());low=Math.min(low,d.totalFen());}LinearLayout range=row();range.addView(metric("期间最高",amount(high),ink),weight());range.addView(metric("期间最低",amount(low),ink),weight());body.addView(range);}
        section("每日变化","查看日历",()->{tab=4;render();});
    }
    private void calendar(){
        AssetCalendar.Month month=AssetCalendar.build(store.dailySnapshots,calendarMonth);
        YearMonth now=YearMonth.from(LocalDate.parse(WealthStore.today()));
        YearMonth earliest=store.dailySnapshots.isEmpty()?now:YearMonth.from(LocalDate.parse(store.dailySnapshots.get(0).day));
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);
        View before=iconButton("chevronLeft","上个月",()->{calendarMonth=calendarMonth.minusMonths(1);calendarSelected=null;render();});
        before.setEnabled(calendarMonth.isAfter(earliest));before.setAlpha(before.isEnabled()?1f:.3f);heading.addView(before);
        TextView title=text(calendarMonth.getYear()+" 年 "+calendarMonth.getMonthValue()+" 月",22,ink,true);title.setGravity(Gravity.CENTER);heading.addView(title,weight());
        View after=iconButton("chevronRight","下个月",()->{calendarMonth=calendarMonth.plusMonths(1);calendarSelected=null;render();});
        after.setEnabled(calendarMonth.isBefore(now));after.setAlpha(after.isEnabled()?1f:.3f);heading.addView(after);body.addView(heading);
        LinearLayout tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);
        if(store.accounts.isEmpty()&&store.dailySnapshots.isEmpty())tools.addView(textAction("添加账户",()->accountForm(null)),weight());
        else tools.addView(textAction("回到本月",()->{calendarMonth=now;calendarSelected=null;render();}),weight());
        for(boolean percent:new boolean[]{false,true}){
            TextView option=textAction(percent?"涨跌幅":"金额",()->{calendarPercent=percent;getPreferences(0).edit().putBoolean("calendarPercent",percent).apply();render();});
            option.setSelected(calendarPercent==percent);option.setBackground(calendarPercent==percent?shape(pale,10):selectable());tools.addView(option);
        }
        body.addView(tools);space(body,12);
        LinearLayout grid=column();
        LinearLayout weekdays=row();
        for(String label:new String[]{"一","二","三","四","五","六","日"}){TextView day=text(label,12,muted,false);day.setGravity(Gravity.CENTER);weekdays.addView(day,new LinearLayout.LayoutParams(0,dp(32),1));}
        grid.addView(weekdays);
        if(calendarSelected==null||!calendarSelected.startsWith(calendarMonth.toString()))calendarSelected=month.last==null?null:month.last.day;
        int offset=calendarMonth.atDay(1).getDayOfWeek().getValue()-1;
        int rows=(offset+month.days.size()+6)/7;
        for(int week=0;week<rows;week++){
            LinearLayout weekRow=row();
            for(int weekday=0;weekday<7;weekday++){
                int index=week*7+weekday-offset;
                if(index<0||index>=month.days.size()){weekRow.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(68),1));continue;}
                AssetCalendar.Day day=month.days.get(index);
                boolean selected=day.date.toString().equals(calendarSelected),today=day.date.toString().equals(WealthStore.today());
                int color=hidden||!day.comparable()?muted:changeColor(day.delta());
                LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(1),dp(7),dp(1),dp(7));
                cell.setMinimumHeight(dp(Math.max(68,58*getResources().getConfiguration().fontScale)));
                if(selected)cell.setBackground(sketch.box(pale,accent,getResources().getDisplayMetrics().density));
                else if(day.comparable()&&!hidden&&day.delta()!=0){int tint=blendColor(surface,color,.09f);cell.setBackground(sketch.box(tint,blendColor(surface,color,.25f),getResources().getDisplayMetrics().density));}
                else cell.setBackground(selectable());
                TextView date=text(String.valueOf(day.date.getDayOfMonth())+(today?"·":""),14,today?accent:day.current==null?muted:ink,selected);date.setGravity(Gravity.CENTER);cell.addView(date);
                space(cell,7);String value=calendarCellValue(day);TextView change=text(value,11,color,false);change.setGravity(Gravity.CENTER);change.setMaxLines(1);change.setAutoSizeTextTypeUniformWithConfiguration(9,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);cell.addView(change,new LinearLayout.LayoutParams(-1,dp(20)));
                cell.setSelected(selected);cell.setFocusable(true);cell.setContentDescription(day.date+"，"+(hidden?"金额已隐藏":day.comparable()?"资产变化 "+money.signed(day.delta())+"，"+changePercent(day.previous.totalFen(),day.current.totalFen()):day.gap()?"记录有间隔，点击查看":day.current==null?"无记录":"首条记录"));
                cell.setOnClickListener(v->{calendarSelected=day.date.toString();int y=viewport.getScrollY();render();viewport.post(()->viewport.scrollTo(0,y));});
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.setMargins(dp(1),dp(2),dp(1),dp(2));weekRow.addView(cell,cp);
            }
            grid.addView(weekRow,new LinearLayout.LayoutParams(-1,-2));
        }
        HorizontalScrollView calendarScroll=new HorizontalScrollView(this);calendarScroll.setFillViewport(true);calendarScroll.setHorizontalScrollBarEnabled(false);
        grid.setMinimumWidth(dp(350));calendarScroll.addView(grid,new android.widget.FrameLayout.LayoutParams(-1,-2));body.addView(calendarScroll);
        space(body,22);divider(body);space(body,18);
        LinearLayout summary=row();String label=month.startsAtBoundary()?"本月资产变化":"有记录期间变化";
        summary.addView(metric(label,month.comparable()?(calendarPercent?changePercent(month.baseline.totalFen(),month.last.totalFen()):signed(month.delta())):"—",hidden||!month.comparable()?ink:changeColor(month.delta())),weight());
        LinearLayout count=column();count.addView(text("上涨 / 下跌天数",12,muted,false));space(count,8);count.addView(number(hidden?"••• / •••":month.rising+" / "+month.falling,20,ink));summary.addView(count,weight());body.addView(summary);space(body,10);
        String period=month.comparable()?month.baseline.day+" → "+month.last.day:month.recorded==0?"本月暂无记录":"尚无可比较的两条记录";
        body.addView(text(period,11,muted,false));
        section("当日记录",null,null);
        AssetCalendar.Day selected=null;for(AssetCalendar.Day day:month.days)if(day.date.toString().equals(calendarSelected))selected=day;
        if(selected!=null&&selected.current!=null)dailyRow(body,selected.current);
        else body.addView(text(selected==null?"选择日期，查看资产变化与来源。":selected.date+" · 没有资产记录",13,muted,false));
        if(store.accounts.isEmpty()&&store.dailySnapshots.isEmpty()){
            space(body,18);primary(body,"添加第一个账户",()->accountForm(null),true);
            body.addView(textAction("先看看示例",()->{safe(this::createPreview);render();}));
        }
    }
    private String calendarCellValue(AssetCalendar.Day day){
        if(hidden)return day.current==null?"—":"•••";
        if(day.current==null)return "—";
        if(day.gap())return "间隔";
        if(!day.comparable())return "首记";
        if(calendarPercent)return changePercent(day.previous.totalFen(),day.current.totalFen());
        double value=Double.parseDouble(money.amount(day.delta()).replace(",",""));
        double absolute=Math.abs(value);String sign=value>0?"+":value<0?"−":"";
        if(absolute>=100000000)return sign+String.format(Locale.CHINA,"%.1f亿",absolute/100000000);
        if(absolute>=10000)return sign+String.format(Locale.CHINA,"%.1f万",absolute/10000);
        if(absolute>=1000)return sign+String.format(Locale.CHINA,"%.0f",absolute);
        return sign+String.format(Locale.CHINA,"%.2f",absolute);
    }
    private int blendColor(int base,int tint,float amount){return Color.rgb(Math.round(Color.red(base)*(1-amount)+Color.red(tint)*amount),Math.round(Color.green(base)*(1-amount)+Color.green(tint)*amount),Math.round(Color.blue(base)*(1-amount)+Color.blue(tint)*amount));}
    private void calendarInfo(){new HandDialogBuilder().setTitle("日历统计口径").setMessage("日历记录的是总资产变化，包含资金进出、余额与持仓调整、行情变化，不是投资收益率。\n\n单日金额 = 当日资产 − 前日资产；涨跌幅以前日资产为基准，前日为零时显示 —。只有相邻两天均有记录，才计入上涨、下跌或持平天数。缺记录的日期不补值；间隔记录可点击查看较上次记录的差额。\n\n月度变化优先以上月最后一天为基准；没有该日记录时，使用本月第一条记录，并标注实际比较日期。百分比由区间首尾资产计算，不累加每日涨跌幅。\n\n格内金额为简写，点击日期可查看精确金额。美元按当前选定汇率换算。日期按北京时间。").setPositiveButton("知道了",null).show();}

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
    }
    private void distribution(){
        LinearLayout modes=row();String[] labels={"类别","账户","持仓"};
        for(int i=0;i<labels.length;i++){final int mode=i;TextView option=text(labels[i],14,distributionMode==i?accent:muted,true);option.setGravity(Gravity.CENTER);option.setMinimumHeight(dp(48));option.setBackground(distributionMode==i?shape(pale,10):selectable());option.setOnClickListener(v->{distributionMode=mode;int y=viewport.getScrollY();render();viewport.post(()->viewport.scrollTo(0,y));});modes.addView(option,weight());}body.addView(modes);space(body,12);
        List<WealthCharts.Slice> slices=new ArrayList<>();int[] colors=sketch.colors();
        if(distributionMode==0){AssetSankey allocation=AssetSankey.build(store.accounts,store.holdings);for(int k=0;k<AssetSankey.CATEGORY_NAMES.length;k++)if(allocation.categoryValue(k)>0)slices.add(new WealthCharts.Slice(AssetSankey.CATEGORY_NAMES[k],allocation.categoryValue(k),colors[k%colors.length]));}
        else if(distributionMode==1){for(WealthStore.Account account:store.accounts){long value=store.accountTotal(account);if(value>0)slices.add(new WealthCharts.Slice(account.name,value,colors[slices.size()%colors.length]));}}
        else {java.util.LinkedHashMap<String,Long> values=new java.util.LinkedHashMap<>();java.util.Map<String,String> names=new java.util.HashMap<>();for(WealthStore.Holding h:store.holdings){values.put(h.code,values.getOrDefault(h.code,0L)+WealthStore.holdingValue(h));names.put(h.code,h.name==null||h.name.isEmpty()?h.code:h.name);}for(String code:values.keySet())if(values.get(code)>0)slices.add(new WealthCharts.Slice(names.get(code),values.get(code),colors[slices.size()%colors.length]));if(store.totalCash()>0)slices.add(new WealthCharts.Slice("现金余额",store.totalCash(),colors[slices.size()%colors.length]));}
        java.util.Collections.sort(slices,(a,b)->Long.compare(b.valueFen,a.valueFen));
        LinearLayout panel=row();panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(dp(14),dp(16),dp(14),dp(16));panel.setBackground(shape(surface,14));
        WealthCharts.DonutView chart=new WealthCharts.DonutView(this,slices,figures,ink,muted,line,hidden);panel.addView(chart,new LinearLayout.LayoutParams(dp(142),dp(168)));
        LinearLayout legend=column();legend.setPadding(dp(14),0,0,0);long total=store.totalAssets();
        if(slices.isEmpty())legend.addView(text("账户余额为零时，\n暂不绘制占比。",12,muted,false));
        for(int i=0;i<slices.size();i++){final int index=i;WealthCharts.Slice slice=slices.get(i);LinearLayout item=column();item.setMinimumHeight(dp(48));item.setPadding(0,dp(3),0,dp(3));LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(shape(slice.color,4));title.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView name=text(slice.label,11,ink,false);name.setPadding(dp(6),0,0,0);title.addView(name,weight());TextView pct=number(hidden?"•••":String.format(Locale.CHINA,"%.1f%%",100d*slice.valueFen/total),11,muted);title.addView(pct);item.addView(title);TextView number=number(amount(slice.valueFen),13,ink);number.setPadding(dp(12),dp(5),0,0);item.addView(number);item.setContentDescription(slice.label+"，"+(hidden?"金额已隐藏":amount(slice.valueFen)));item.setFocusable(true);item.setBackground(selectable());item.setOnClickListener(v->chart.setSelectedIndex(index));legend.addView(item);}
        panel.addView(legend,weight());body.addView(panel);
        if(!slices.isEmpty()&&total>0){space(body,16);LinearLayout insight=row();insight.addView(metric("最大占比 · "+slices.get(0).label,hidden?"•••":String.format(Locale.CHINA,"%.1f%%",100d*slices.get(0).valueFen/total),ink),weight());insight.addView(metric("现金占比",hidden?"•••":String.format(Locale.CHINA,"%.1f%%",100d*store.totalCash()/total),ink),weight());body.addView(insight);}
    }
    private void sankey(){
        sankeyPath.clear();
        AssetTree.Node data=AssetTree.build(store.accounts,store.holdings,sankeyByAccount);
        LinearLayout modes=row();for(int i=0;i<2;i++){final boolean accounts=i==1;TextView option=text(accounts?"按账户":"按类别",14,sankeyByAccount==accounts?accent:muted,true);option.setGravity(Gravity.CENTER);option.setMinimumHeight(dp(48));option.setSelected(sankeyByAccount==accounts);option.setBackground(sankeyByAccount==accounts?shape(pale,10):selectable());option.setFocusable(true);option.setOnClickListener(v->{sankeyByAccount=accounts;refreshSelection=null;sankeyExpanded=false;refreshSankeyScroll=0;getPreferences(0).edit().putBoolean("sankeyByAccount",accounts).apply();render();});modes.addView(option,new LinearLayout.LayoutParams(0,-2,1));}body.addView(modes);
        space(body,12);
        MultiLayerSankeyView chart=new MultiLayerSankeyView(this,data,money,figures,hidden,sankeyByAccount);
        currentSankey=chart;HorizontalScrollView canvas=new HorizontalScrollView(this);sankeyCanvas=canvas;canvas.setFillViewport(true);canvas.setHorizontalScrollBarEnabled(true);canvas.addView(chart,new android.widget.FrameLayout.LayoutParams(sankeyExpanded?chart.detailWidth():-1,chart.desiredHeight()));
        LinearLayout tools=row();TextView zoom=textAction(sankeyExpanded?"适合屏幕":"放大细看 ↔",()->{});zoom.setOnClickListener(v->{sankeyExpanded=!sankeyExpanded;chart.setLayoutParams(new android.widget.FrameLayout.LayoutParams(sankeyExpanded?chart.detailWidth():-1,chart.desiredHeight()));canvas.scrollTo(0,0);zoom.setText(sankeyExpanded?"适合屏幕":"放大细看 ↔");});tools.addView(zoom);body.addView(tools);body.addView(canvas);space(body,12);
        body.addView(number("总资产  "+amount(data.value)+" "+money.code(),20,ink));space(body,12);LinearLayout detail=column();body.addView(detail);
        chart.setListener(node->{detail.removeAllViews();if(node==null){return;}detail.addView(text(node.name,18,ink,true));detail.addView(number(amount(node.value)+" "+money.code(),24,ink));detail.addView(text(hidden?"比例已隐藏":"占总资产 "+AssetTree.share(node.value,data.value),12,muted,false));detail.addView(textAction("清除选择",chart::clearSelection));});
        chart.selectKey(refreshSelection);refreshSelection=null;int scroll=refreshSankeyScroll;refreshSankeyScroll=0;canvas.post(()->canvas.scrollTo(scroll,0));

    }
    private void sankeyAmountRow(LinearLayout parent,String title,long value,Runnable action){
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));TextView label=text(title,13,ink,false);label.setPadding(0,dp(8),dp(10),dp(8));row.addView(label,weight());TextView balance=number(amount(value),14,muted);balance.setGravity(Gravity.END);row.addView(balance,weight());if(action!=null){row.setBackground(selectable());row.setOnClickListener(v->action.run());row.setFocusable(true);row.setContentDescription(title+"，"+amount(value)+"，管理账户");}parent.addView(row);divider(parent);
    }

    private WealthStore.DailySnapshot previousSnapshot(WealthStore.DailySnapshot current){
        WealthStore.DailySnapshot previous=null;
        for(WealthStore.DailySnapshot d:store.dailySnapshots){if(d.day.compareTo(current.day)>=0)break;previous=d;}
        return previous;
    }
    private void dailyRow(LinearLayout target,WealthStore.DailySnapshot current){
        WealthStore.DailySnapshot previous=previousSnapshot(current);
        String state=previous==null?"首条记录":LocalDate.parse(previous.day).plusDays(1).toString().equals(current.day)?"较前日":"较上次记录";
        LinearLayout card=column();card.setPadding(0,dp(14),0,dp(14));
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=column();left.addView(text(current.day+(current.day.equals(WealthStore.today())?" · 今天":""),13,ink,true));space(left,6);left.addView(text(state,11,muted,false));row.addView(left,weight());
        LinearLayout right=column();TextView balance=number(amount(current.totalFen()),16,ink);balance.setGravity(Gravity.END);right.addView(balance);space(right,6);
        long delta=previous==null?0:current.totalFen()-previous.totalFen();String changeText=previous==null?"—":signed(delta);
        TextView change=number(changeText,12,hidden?muted:changeColor(delta));change.setGravity(Gravity.END);right.addView(change);row.addView(right,weight());card.addView(row);
        String sources=DailyChangeText.describe(previous,current,money,hidden);
        space(card,8);card.addView(text(sources,11,muted,false));
        card.setBackground(selectable());card.setFocusable(true);card.setContentDescription(current.day+"，总资产 "+amount(current.totalFen())+"，"+state+" "+changeText+"，"+sources);card.setOnClickListener(v->snapshotInfo(current));target.addView(card);divider(target);
    }
    private String snapshotLabel(WealthStore.DailySnapshot value){return dayLabel(value.day)+(value.day.equals(WealthStore.today())?" · 当天最新":" · 日记录");}
    private String dayLabel(String day){return day.substring(0,4).equals(WealthStore.today().substring(0,4))?day.substring(5,7)+"."+day.substring(8,10):day.replace('-','.');}
    private String changePercent(long first,long last){if(hidden)return "•••";if(first==0)return "—";double value=100d*(last-first)/first;return String.format(Locale.CHINA,(value>0?"+":"")+"%.2f%%",value);}
    private void snapshotInfo(WealthStore.DailySnapshot value){
        String source=value.oldestQuoteTime.isEmpty()?(value.marketFen==0?"不含证券持仓":"尚无有效报价，按录入成本估值"):(preview?"示例行情 ":"最早行情 ")+quoteTime(value.oldestQuoteTime);if(value.unquotedCount>0)source+="\n"+value.unquotedCount+" 笔未报价，按成本估值";
        String changes=DailyChangeText.describe(previousSnapshot(value),value,money,hidden);
        new HandDialogBuilder().setTitle(value.day+" 资产记录").setMessage("总资产  "+amount(value.totalFen())+"\n资金余额  "+amount(value.cashFen)+"\n持仓市值  "+amount(value.marketFen)+"\n\n变化来源\n"+changes+"\n\n记录更新 "+snapshotTime(value.recordedAt)+"\n"+source).setPositiveButton("知道了",null).show();
    }
    private String snapshotTime(long timestamp){SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return format.format(new Date(timestamp))+"（北京时间）";}
    private void statisticsInfo(){new HandDialogBuilder().setTitle("关于资产统计").setMessage("每天保留当天最新记录，时间按北京时间。打开应用、更新账户或行情时记录；后台更新由 Android 择机执行。\n\n资产变化 = 两次记录的总资产差额。包含收入、新增账户、余额调整、持仓数量和市场价格变化，并非投资收益率。账户间划转不改变总资产。每日变化下方的小字按收入、余额调整、持仓调整和行情 / 估值列出来源；来源不完整时，已记录的行情更新仍单独显示，仅无法追溯的差额保留为未分类变化。\n\n曲线从首次记录开始。没有记录的日期不补造，间隔日期用虚线连接；仅有一条记录时不计算增长。期间变化与最高、最低只统计选定范围内已有记录。\n\n资产分布按当前银行存款、证券持仓、证券现金、其他资金和数字货币计算。行情未更新时沿用上次报价或录入成本，点击每日记录可查看行情时间。\n\n每日记录随加密备份一起保存。").setPositiveButton("知道了",null).show();}
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
        card.setOnClickListener(v->holdingDetail(h));card.setMinimumHeight(dp(94));card.setBackground(selectable());body.addView(card);divider(body);
    }
    private void accountDetail(){
        WealthStore.Account a;try{a=store.accountById(detailId);}catch(Exception e){detailId=null;render();return;}
        body.addView(text(a.name,26,ink,true));space(body,9);body.addView(text(kindName(a.kind),12,muted,false));space(body,30);
        body.addView(text("账户总资产",14,muted,false));heroAmount(store.accountTotal(a));space(body,26);
        LinearLayout m=row();m.addView(metric("可用现金",amount(a.cashFen),ink),weight());if("securities".equals(a.kind)||"crypto".equals(a.kind))m.addView(metric("持仓市值",amount(store.accountTotal(a)-a.cashFen),ink),weight());body.addView(m);space(body,28);
        primary(body,"更新现金余额",()->accountForm(a),true);space(body,8);LinearLayout actions=row();actions.setGravity(Gravity.CENTER);actions.addView(textAction("记收入",()->incomeForm(a)));actions.addView(textAction("转入 / 转出",this::transferForm));body.addView(actions);
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
            for(int i=0;i<90;i++){LocalDate day=end.minusDays(89-i);double progress=i/89d;long value=market-Math.round((1-progress)*7900000)+Math.round((Math.sin(i*.51)*420000+Math.sin(i*.17)*700000)*Math.sin(progress*Math.PI));JSONObject point=new JSONObject();point.put("day",day.toString());point.put("recordedAt",i==89?System.currentTimeMillis():day.atTime(18,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());point.put("cashFen",cash);point.put("marketFen",value);point.put("costFen",cost);point.put("unquotedCount",0);point.put("oldestQuoteTime",day.format(DateTimeFormatter.BASIC_ISO_DATE)+"150000");point.put("incomeFen",0);point.put("cashAdjustmentFen",0);point.put("holdingAdjustmentFen",0);point.put("observedMarketChangeFen",0);point.put("sourcesKnown",true);days.put(point);}data.put("dailySnapshots",days);sample.importJson(data.toString());
        }catch(JSONException e){throw new IllegalStateException("示例历史暂时无法显示",e);}
        store=sample;preview=true;detailId=null;sankeyPage=false;
    }
    private void exitPreview(){preview=false;store=app.store;detailId=null;sankeyPage=false;sankeyAccountKey=null;sankeyCategory=-1;tab=navOrder()[0];calendarSelected=null;render();}
    private boolean previewGuard(){if(!preview)return false;new HandDialogBuilder().setTitle("正在浏览示例").setMessage("示例只用于体验界面。回到自己的账本后即可添加和管理资产。").setNegativeButton("继续看看",null).setPositiveButton("回到我的账本",(d,w)->exitPreview()).show();return true;}

    private void holdingDetail(WealthStore.Holding h){
        LinearLayout panel=form();addInfo(panel,"当前市值",amount(WealthStore.holdingValue(h)));addInfo(panel,"持仓浮盈",signed(WealthStore.holdingProfit(h)));addInfo(panel,"持仓数量",hidden?"••••":WealthStore.quantityText(h));addInfo(panel,"单位成本 · "+money.code(),price(h.costPriceMicros));addInfo(panel,WealthStore.isCrypto(h)?"24 小时涨跌幅":"较昨收涨跌幅",hidden?"•••":WealthStore.movementPercent(h));
        addInfo(panel,"最新报价",quoted(h)?price(h.lastPriceMicros):"尚未取得");
        TextView hint=text("添加或修改已有持仓，不会自动增减账户现金。买卖后请同步更新可用现金。",13,muted,false);hint.setPadding(0,dp(18),0,0);panel.addView(hint);
        new HandDialogBuilder().setTitle(h.name+" · "+(WealthStore.isCrypto(h)?WealthStore.coinSymbol(h.code):h.code.toUpperCase(Locale.ROOT))).setView(panel).setPositiveButton("修改持仓",(d,w)->holdingForm(h,null)).setNegativeButton("返回",null).setNeutralButton("移除",(d,w)->{if(previewGuard())return;new HandDialogBuilder().setTitle("移除这笔持仓？").setMessage("市值将从账户中移除，现金余额保持不变。卖出款请更新到账户现金。").setNegativeButton("取消",null).setPositiveButton("移除",(x,y)->safe(()->{store.deleteHolding(h.id);render();})).show();}).show();
    }
    private void addAssetMenu(){
        if(previewGuard())return;
        new HandDialogBuilder().setTitle("添加资产")
                .setItems(new String[]{"添加账户","添加持仓","记一笔收入"},(dialog,index)->{
                    if(index==0)accountForm(null);
                    else if(index==1)holdingForm(null,null);
                    else incomeForm(null);
                }).setNegativeButton("取消",null).show();
    }
    private void incomeForm(WealthStore.Account preferred){
        if(previewGuard())return;
        if(store.accounts.isEmpty()){
            new HandDialogBuilder().setTitle("先添加收款账户")
                    .setMessage("收入需要记入一个账户。先填写本次收入到账前的现金余额，再记收入。")
                    .setNegativeButton("稍后",null)
                    .setPositiveButton("添加账户",(dialog,which)->accountForm(null)).show();
            return;
        }
        List<WealthStore.Account> choices=new ArrayList<>(store.accounts);
        String[] names=new String[choices.size()];int selected=0;
        for(int i=0;i<names.length;i++){
            WealthStore.Account a=choices.get(i);names[i]=a.name+" · "+kindName(a.kind);
            if(preferred!=null&&preferred.id.equals(a.id))selected=i;
        }
        LinearLayout f=form();label(f,"收入到账账户");Spinner account=spinner(names);account.setSelection(selected);f.addView(account);
        String[] sources={"工资","奖金","其他收入"};label(f,"收入来源");Spinner source=spinner(sources);f.addView(source);
        EditText value=input(f,"收入金额（人民币元）","","例如 8000.00",true);
        EditText memo=input(f,"备注（可选）","","例如：10 月工资",false);
        note(f,"保存后增加所选账户的现金余额，按保存时的北京时间记入当天。已计入余额的收入无需重复录入。");
        showForm("记一笔收入",f,"确认入账",()->store.recordIncome(
                choices.get(account.getSelectedItemPosition()).id,
                WealthStore.parseMoney(value.getText().toString()),
                sources[source.getSelectedItemPosition()],memo.getText().toString()));
    }
    private void accountForm(WealthStore.Account account){accountForm(account,"bank");}
    private void accountForm(WealthStore.Account account,String preferredKind){
        if(previewGuard())return;
        LinearLayout f=form();EditText name=input(f,"账户名称",account==null?"":account.name,"例如：招商银行 / 股票账户",false);
        label(f,"账户类型");Spinner kind=spinner(new String[]{"银行存款","证券账户","其他资金","数字资产账户"});f.addView(kind);String selectedKind=account==null?preferredKind:account.kind;kind.setSelection("securities".equals(selectedKind)?1:"cash".equals(selectedKind)?2:"crypto".equals(selectedKind)?3:0);if(account!=null)kind.setEnabled(false);
        EditText cash=input(f,"现金余额（元）",account==null?"":decimal(account.cashFen),"0.00",true);
        note(f,"金额录入使用人民币 CNY。只填可用现金，不含股票或币的市值；USDT 等作为持仓添加。");
        label(f,"账户图标");final String[] selectedIcon={account==null?"auto":account.icon};ArrayList<LinearLayout> iconCells=new ArrayList<>();
        for(int rowIndex=0;rowIndex<(WealthStore.ICON_IDS.length+3)/4;rowIndex++){LinearLayout icons=row();for(int col=0;col<4;col++){final int index=rowIndex*4+col;if(index>=WealthStore.ICON_IDS.length){icons.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(68),1));continue;}String id=WealthStore.ICON_IDS[index];LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(dp(68));cell.setBackground(id.equals(selectedIcon[0])?shape(pale,10):selectable());Icon icon=new Icon(this,"auto".equals(id)?"bank":id,ink);cell.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView caption=text(WealthStore.ICON_NAMES[index],11,muted,false);caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(6),0,0);cell.addView(caption);cell.setContentDescription("图标："+WealthStore.ICON_NAMES[index]);cell.setFocusable(true);cell.setSelected(id.equals(selectedIcon[0]));cell.setOnClickListener(v->{selectedIcon[0]=id;for(int j=0;j<iconCells.size();j++){boolean active=WealthStore.ICON_IDS[j].equals(id);iconCells.get(j).setSelected(active);iconCells.get(j).setBackground(active?shape(pale,10):selectable());}});iconCells.add(cell);icons.addView(cell,new LinearLayout.LayoutParams(0,dp(68),1));}f.addView(icons);}

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
    private boolean settingsPage, navigationSettingsPage;
    private void settings(){settingsPage=true;navigationSettingsPage=false;render();}
    private void settingsHome(){
        settingsGroup("显示与外观");
        settingsRow("star","界面风格",sketch.blue?"蓝墨手帐":"彩铅手帐",this::appearance);
        settingsRow("globe","显示币种",money.usd?"美元 USD · 汇率 "+getSharedPreferences("appearance",0).getString("fxDate",""):"人民币 CNY",this::currencySettings);
        settingsRow("overview","底部导航","调整顺序与图标",()->{navigationSettingsPage=true;render();});
        settingsGroup("数据与备份");
        settingsRow("book","变更记录","查看账本操作",this::history);
        settingsRow("safe","导出加密备份","保存一份安全副本",this::exportBackup);
        settingsRow("refresh","从备份恢复","恢复前核对备份内容",this::chooseImport);
        settingsGroup("帮助与说明");
        settingsRow("hold","行情与计算","数据来源与估值口径",this::quoteInfo);
        settingsRow("calendar","日历与统计","涨跌、间隔与月度计算",this::calendarInfo);
        settingsRow("eye","预览示例","独立体验，不写入账本",()->{safe(this::createPreview);settingsPage=false;tab=navOrder()[0];render();});
        settingsRow("info","关于 Gather",versionName()+" · 开源与更新",this::about);
    }
    private void settingsGroup(String title){if(body.getChildCount()>0)space(body,24);TextView heading=text(title,13,muted,true);heading.setPadding(0,0,0,dp(6));body.addView(heading);}
    private void settingsRow(String icon,String title,String detail,Runnable action){
        LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);item.setMinimumHeight(dp(72));item.setPadding(0,dp(12),0,dp(12));
        item.addView(new Icon(this,icon,accent),new LinearLayout.LayoutParams(dp(22),dp(22)));
        LinearLayout words=column();words.setPadding(dp(14),0,dp(8),0);words.addView(text(title,16,ink,false));space(words,5);words.addView(text(detail,12,muted,false));item.addView(words,weight());
        item.addView(new Icon(this,"chevronRight",muted),new LinearLayout.LayoutParams(dp(18),dp(18)));item.setBackground(selectable());item.setFocusable(true);item.setContentDescription(title+"，"+detail);item.setOnClickListener(v->action.run());body.addView(item);divider(body);
    }
    private int[] navOrder(){return NavigationLayout.parse(getSharedPreferences("appearance",0).getString("navOrder",""));}
    private String navIcon(int page){return NavigationLayout.icon(page,getSharedPreferences("appearance",0).getString("navIcon_"+page,""));}
    private void navigationSettings(){
        body.addView(text("按你的习惯，安排四个入口。",16,ink,false));space(body,8);body.addView(text("上下移动调整顺序，点击图标即可更换。",12,muted,false));space(body,20);
        LinearLayout previewBar=row();previewBar.setPadding(dp(4),dp(10),dp(4),dp(10));previewBar.setBackground(shape(surface,12));
        for(int page:navOrder()){
            LinearLayout item=column();item.setGravity(Gravity.CENTER);item.addView(new Icon(this,navIcon(page),accent),new LinearLayout.LayoutParams(dp(24),dp(24)));space(item,7);TextView title=text(NavigationLayout.label(page),12,ink,false);title.setGravity(Gravity.CENTER);item.addView(title);previewBar.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        body.addView(previewBar);space(body,20);
        int[] order=navOrder();
        for(int i=0;i<order.length;i++){
            final int position=i,page=order[i];String name=NavigationLayout.label(page);
            LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(0,dp(10),0,dp(10));
            View icon=iconButton(navIcon(page),"更换"+name+"图标",()->navIconForm(page));icon.setBackground(shape(pale,10));item.addView(icon);
            LinearLayout title=column();title.setPadding(dp(12),0,0,0);title.addView(text(name,16,ink,true));space(title,5);title.addView(text(i==0?"启动时显示":"第 "+(i+1)+" 个入口",11,muted,false));item.addView(title,weight());
            View up=iconButton("chevronUp","将"+name+"上移",()->moveNavigation(position,-1));up.setEnabled(i>0);up.setAlpha(i>0?1f:.25f);item.addView(up);
            View down=iconButton("chevronDown","将"+name+"下移",()->moveNavigation(position,1));down.setEnabled(i<order.length-1);down.setAlpha(i<order.length-1?1f:.25f);item.addView(down);body.addView(item);divider(body);
        }
        space(body,18);body.addView(text("修改自动保存。图标跟随页面，不会随排序错位。",12,muted,false));space(body,16);
        body.addView(textAction("恢复默认导航",()->{android.content.SharedPreferences.Editor prefs=getSharedPreferences("appearance",0).edit().remove("navOrder");for(int page:NavigationLayout.defaults())prefs.remove("navIcon_"+page);prefs.apply();render();}));
    }
    private void moveNavigation(int position,int direction){getSharedPreferences("appearance",0).edit().putString("navOrder",NavigationLayout.encode(NavigationLayout.move(navOrder(),position,direction))).apply();render();}
    private void navIconForm(int page){
        LinearLayout form=form();note(form,"只改变图标，页面名称和功能保持不变。");final String[] selected={navIcon(page)};ArrayList<LinearLayout> cells=new ArrayList<>();
        for(int r=0;r<(NavigationLayout.ICON_IDS.length+3)/4;r++){
            LinearLayout gridRow=row();
            for(int c=0;c<4;c++){
                int index=r*4+c;
                if(index>=NavigationLayout.ICON_IDS.length){gridRow.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(76),1));continue;}
                String id=NavigationLayout.ICON_IDS[index];LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(dp(76));cell.setSelected(selected[0].equals(id));cell.setBackground(selected[0].equals(id)?shape(pale,10):selectable());
                cell.addView(new Icon(this,id,ink),new LinearLayout.LayoutParams(dp(24),dp(24)));space(cell,6);TextView caption=text(NavigationLayout.ICON_NAMES[index],11,muted,false);caption.setGravity(Gravity.CENTER);cell.addView(caption);cell.setContentDescription("导航图标："+NavigationLayout.ICON_NAMES[index]);cell.setFocusable(true);
                cell.setOnClickListener(v->{selected[0]=id;for(int j=0;j<cells.size();j++){boolean active=NavigationLayout.ICON_IDS[j].equals(id);cells.get(j).setSelected(active);cells.get(j).setBackground(active?shape(pale,10):selectable());}});cells.add(cell);gridRow.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
            }
            form.addView(gridRow);
        }
        showForm("更换"+NavigationLayout.label(page)+"图标",form,"保存图标",()->getSharedPreferences("appearance",0).edit().putString("navIcon_"+page,selected[0]).apply());
    }
    private void currencySettings(){
        new HandDialogBuilder().setTitle("显示币种").setSingleChoiceItems(new String[]{"人民币 CNY","美元 USD"},money.usd?1:0,(dialog,index)->{
            dialog.dismiss();
            android.content.SharedPreferences prefs=getSharedPreferences("appearance",0);
            prefs.edit().putBoolean("usd",index==1).apply();
            if(index==0){render();return;}
            try{DisplayMoney.parseRate(prefs.getString("fxRate",""));render();}catch(Exception e){Toast.makeText(this,"正在获取汇率…",Toast.LENGTH_SHORT).show();}
            app.refreshFx(true,()->{if(isFinishing())return;render();if(app.fxError!=null)message(app.fxError);});
        }).setNegativeButton("取消",null).show();
    }
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "未知版本";}}
    private void openProject(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){message("无法打开浏览器："+url);}}
    private void about(){
        new HandDialogBuilder().setTitle("Gather  "+versionName()).setMessage("只关心值得关注的资产。\n\n开源地址\nhttps://github.com/wqq977wqq977-source/Gather")
            .setPositiveButton("检查更新",(d,w)->checkUpdate()).setNeutralButton("GitHub",(d,w)->openProject(UpdateClient.REPOSITORY)).setNegativeButton("关闭",null).show();
    }
    private boolean checkingUpdate;
    private void checkUpdate(){
        if(checkingUpdate)return;checkingUpdate=true;Toast.makeText(this,"正在检查更新…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{String latest=UpdateClient.latest();boolean newer=UpdateClient.isNewer(latest,versionName());handler.post(()->{checkingUpdate=false;if(isFinishing())return;if(newer)new HandDialogBuilder().setTitle("发现新版本 "+latest).setMessage("当前版本 "+versionName()).setPositiveButton("前往下载",(d,w)->openProject(UpdateClient.REPOSITORY+"/releases/latest")).setNegativeButton("稍后",null).show();else message("当前版本 "+versionName()+"，暂无更新。");});}catch(Exception e){handler.post(()->{checkingUpdate=false;if(!isFinishing())message("暂时无法检查更新，请稍后重试。");});}},"gather-update").start();
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
        new HandDialogBuilder().setTitle("行情与计算").setMessage("行情来源：A 股 / ETF 使用腾讯行情；数字货币使用 CoinGecko 人民币快照。\n\n打开 App 自动刷新，前台每 60 秒检查一次；后台由 Android 择机更新，不能保证准点。持仓页顶部统一展示最早有效报价时间，断网时保留上次报价。公共接口可能延迟或不可用。\n\n总资产 = 所有账户现金 + 持仓市值。\n持仓浮盈 = 当前市值 − 录入成本。\n股票较昨收变动 = 当前数量 ×（现价 − 昨收）；数字货币显示 24 小时涨跌。行情变动合计混合这两种周期，只供观察，不是投资收益。不含日内交易、手续费、分红或已实现收益。\n\n无有效报价时暂按成本估值。股票数量、成本及证券可用现金需要你手动核对；本工具不自动识别交易或拆并股。\n\n联网请求仅发送证券或币种代码，不发送资产金额和账户名称。").setPositiveButton("知道了",null).show();
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
        else if("restored".equals(result.kind)){app.loadError=null;detailId=null;sankeyPage=false;calendarSelected=null;render();app.refresh(null);message("备份已恢复");}
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
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(value,10,muted,false);r.addView(label,weight());body.addView(r);
    }
    private void navigation(){
        divider(root);LinearLayout nav=row();nav.setPadding(dp(8),dp(5),dp(8),dp(4));nav.setBackgroundColor(surface);
        int[] pages=navOrder();
        for(int i=0;i<4;i++){final int index=pages[i];boolean active=tab==index;LinearLayout item=column();item.setGravity(Gravity.CENTER);FrameLayout capsule=new FrameLayout(this);capsule.setBackground(active?shape(pale,16):new ColorDrawable(Color.TRANSPARENT));Icon icon=new Icon(this,navIcon(index),active?accent:muted);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);capsule.addView(icon,ip);item.addView(capsule,new LinearLayout.LayoutParams(dp(56),dp(32)));TextView label=text(NavigationLayout.label(index),11,active?accent:muted,false);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(5),0,0);item.addView(label);item.setBackground(selectable());item.setContentDescription(NavigationLayout.label(index));item.setFocusable(true);item.setSelected(active);item.setOnClickListener(v->{tab=index;detailId=null;sankeyPage=false;render();});nav.addView(item,new LinearLayout.LayoutParams(0,dp(64),1));}root.addView(nav);
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
    private int changeColor(long value){return value>0?positive:value<0?red:muted;}
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
            else if("calendar".equals(kind)){canvas.drawRoundRect(3,5,21,22,2,2,p);canvas.drawLine(3,10,21,10,p);canvas.drawLine(8,2,8,7,p);canvas.drawLine(16,2,16,7,p);for(int y=14;y<=18;y+=4)for(int x=7;x<=17;x+=5)canvas.drawPoint(x,y,p);}
            else if("chevronUp".equals(kind)){canvas.drawLine(5,15,12,8,p);canvas.drawLine(12,8,19,15,p);}
            else if("chevronDown".equals(kind)){canvas.drawLine(5,9,12,16,p);canvas.drawLine(12,16,19,9,p);}
            else if("chevronLeft".equals(kind)){canvas.drawLine(15,5,8,12,p);canvas.drawLine(8,12,15,19,p);}
            else if("chevronRight".equals(kind)){canvas.drawLine(9,5,16,12,p);canvas.drawLine(16,12,9,19,p);}
            else if("shield".equals(kind)){Path q=new Path();q.moveTo(12,2);q.lineTo(21,6);q.cubicTo(21,14,18,19,12,22);q.cubicTo(6,19,3,14,3,6);q.close();canvas.drawPath(q,p);canvas.drawLine(8,12,11,15,p);canvas.drawLine(11,15,16,9,p);}
            else if("piggy".equals(kind)){canvas.drawOval(3,6,20,19,p);canvas.drawRect(19,10,23,15,p);canvas.drawLine(7,18,7,22,p);canvas.drawLine(16,18,16,22,p);canvas.drawLine(8,9,13,9,p);canvas.drawLine(15,6,18,3,p);canvas.drawLine(18,3,18,8,p);canvas.drawPoint(17,10,p);canvas.drawArc(0,9,5,14,60,260,false,p);}
            else if("building".equals(kind)){canvas.drawRect(5,2,19,22,p);for(int x=9;x<18;x+=6)for(int y=6;y<16;y+=4)canvas.drawLine(x,y,x+1,y,p);canvas.drawRect(10,18,14,22,p);}
            else if("gold".equals(kind)){Path q=new Path();q.moveTo(6,7);q.lineTo(18,7);q.lineTo(22,18);q.lineTo(2,18);q.close();q.moveTo(6,7);q.lineTo(9,13);q.lineTo(22,18);q.moveTo(9,13);q.lineTo(2,18);canvas.drawPath(q,p);canvas.drawLine(11,2,11,4,p);canvas.drawLine(17,3,16,5,p);}
            else if("car".equals(kind)){canvas.drawRoundRect(2,10,22,18,2,2,p);Path q=new Path();q.moveTo(4,10);q.lineTo(7,4);q.lineTo(17,4);q.lineTo(20,10);canvas.drawPath(q,p);canvas.drawCircle(6,18,2,p);canvas.drawCircle(18,18,2,p);canvas.drawLine(5,13,8,13,p);canvas.drawLine(16,13,19,13,p);}
            else if("plane".equals(kind)){Path q=new Path();q.moveTo(12,2);q.lineTo(14,10);q.lineTo(22,15);q.lineTo(22,17);q.lineTo(14,14);q.lineTo(14,19);q.lineTo(17,21);q.lineTo(7,21);q.lineTo(10,19);q.lineTo(10,14);q.lineTo(2,17);q.lineTo(2,15);q.lineTo(10,10);q.close();canvas.drawPath(q,p);}
            else if("book".equals(kind)){Path q=new Path();q.moveTo(12,6);q.quadTo(6,2,2,5);q.lineTo(2,20);q.quadTo(6,17,12,21);q.quadTo(18,17,22,20);q.lineTo(22,5);q.quadTo(18,2,12,6);q.lineTo(12,21);canvas.drawPath(q,p);}
            else if("leaf".equals(kind)){Path q=new Path();q.moveTo(4,18);q.cubicTo(0,5,14,4,21,2);q.cubicTo(21,15,16,23,4,18);q.moveTo(2,22);q.lineTo(16,8);q.moveTo(9,15);q.lineTo(9,10);canvas.drawPath(q,p);}
            else if("target".equals(kind)){canvas.drawCircle(11,13,9,p);canvas.drawCircle(11,13,5,p);canvas.drawLine(11,13,21,3,p);canvas.drawLine(16,3,21,3,p);canvas.drawLine(21,3,21,8,p);}
            else if("diamond".equals(kind)){Path q=new Path();q.moveTo(6,3);q.lineTo(18,3);q.lineTo(23,9);q.lineTo(12,22);q.lineTo(1,9);q.close();q.moveTo(1,9);q.lineTo(23,9);q.moveTo(8,9);q.lineTo(12,22);q.lineTo(16,9);q.moveTo(8,9);q.lineTo(12,3);q.lineTo(16,9);canvas.drawPath(q,p);}
            else if("heart".equals(kind)){Path q=new Path();q.moveTo(12,21);q.cubicTo(5,16,1,12,2,7);q.cubicTo(3,1,10,2,12,7);q.cubicTo(14,2,21,1,22,7);q.cubicTo(23,12,19,16,12,21);canvas.drawPath(q,p);}
            else if("cloud".equals(kind)){Path q=new Path();q.moveTo(6,20);q.cubicTo(-1,20,0,10,6,10);q.cubicTo(5,0,20,0,20,11);q.cubicTo(25,12,24,20,18,20);q.close();canvas.drawPath(q,p);}
            else if("eye".equals(kind)){Path path=new Path();path.moveTo(2,12);path.cubicTo(7,4,17,4,22,12);path.cubicTo(17,20,7,20,2,12);canvas.drawPath(path,p);canvas.drawCircle(12,12,3,p);if(hidden)canvas.drawLine(3,3,21,21,p);}
            else if("back".equals(kind)){canvas.drawLine(19,12,5,12,p);canvas.drawLine(5,12,11,6,p);canvas.drawLine(5,12,11,18,p);}
            else{for(int y=5;y<=19;y+=7){canvas.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(y==12?15:8,y,2.6f,p);p.setStyle(Paint.Style.STROKE);}}
            canvas.restore();}
    }
}
