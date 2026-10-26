package com.qianwang.privatewealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Small, dependency-free charts for the overview. Values are always supplied in fen. */
public final class WealthCharts {
    private WealthCharts() {}

    public static final class Point {
        public final String day;
        public final long totalFen;
        private final LocalDate date;
        private final long epochDay;

        public Point(String day, long totalFen) {
            this.date = LocalDate.parse(day);
            this.day = this.date.toString();
            this.epochDay = this.date.toEpochDay();
            this.totalFen = totalFen;
        }
    }

    public static final class Slice {
        public final String label;
        public final long valueFen;
        public final int color;

        public Slice(String label, long valueFen, int color) {
            this.label = label == null ? "未命名" : label;
            this.valueFen = valueFen;
            this.color = color;
        }
    }

    public interface SelectionListener {
        void onSelection(int index);
    }

    /** Input must be chronological, with at most one observation per calendar day. */
    public static final class TrendView extends View {
        private final List<Point> points;
        private final Typeface figures;
        private final int accent, muted, line;
        private final boolean hidden;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF plot = new RectF();
        private final float density;
        private final int touchSlop;
        private final DashPathEffect gapDash;
        private final String[] axisLabels = new String[3];
        private String currencyLabel="CNY";
        public void setCurrencyLabel(String value){currencyLabel=value;updateDescription();}
        private final double axisMin, axisStep;
        private int selectedIndex;
        private SelectionListener listener;
        private float downX, downY;
        private boolean dragging, verticalGesture, touchClick;

        public TrendView(Context context, List<Point> points, Typeface figures,
                         int accent, int ink, int muted, int line, boolean hidden) {
            super(context);
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            for (int i = 1; i < this.points.size(); i++) {
                if (this.points.get(i).epochDay <= this.points.get(i - 1).epochDay) {
                    throw new IllegalArgumentException("Chart points must have unique chronological dates");
                }
            }
            this.figures = figures == null ? Typeface.DEFAULT : figures;
            this.accent = accent;
            this.muted = muted;
            this.line = line;
            this.hidden = hidden;
            density = getResources().getDisplayMetrics().density;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            gapDash = new DashPathEffect(new float[]{dp(4), dp(4)}, 0);
            selectedIndex = points.isEmpty() ? -1 : points.size() - 1;

            double min = 0, max = 0;
            if (!points.isEmpty()) {
                min = max = points.get(0).totalFen / 100.0;
                for (Point point : points) {
                    double value = point.totalFen / 100.0;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
            if (max == min) {
                double padding = Math.max(1, Math.abs(max) * .025);
                if (min == 0) max = padding * 2;
                else { min -= padding; max += padding; }
            }
            double step = niceCeiling((max - min) / 2);
            double lower = Math.floor(min / step) * step;
            while (lower + 2 * step < max) {
                step = niceCeiling(step * 1.01);
                lower = Math.floor(min / step) * step;
            }
            axisMin = lower;
            axisStep = step;
            double magnitude = Math.max(Math.abs(axisMin), Math.abs(axisMin + 2 * axisStep));
            double unit = magnitude >= 100000000 ? 100000000 : magnitude >= 10000 ? 10000 : 1;
            // Retain distinct ticks even when a large balance changes by only a few fen.
            if (step / unit < .0001) unit = unit == 100000000 ? 10000 : 1;
            if (step / unit < .0001) unit = 1;
            String suffix = unit == 100000000 ? "亿" : unit == 10000 ? "万" : "";
            int precision = Math.max(0, Math.min(8, (int) Math.ceil(-Math.log10(step / unit)) + 1));
            DecimalFormat format = decimalFormat(precision);
            for (int i = 0; i < axisLabels.length; i++) {
                double value = (axisMin + i * axisStep) / unit;
                axisLabels[i] = format.format(Math.abs(value) < 1e-10 ? 0 : value) + suffix;
            }
            setMinimumHeight((int) dp(150));
            setMinimumWidth((int) dp(180));
            setFocusable(true);
            setClickable(!hidden && !points.isEmpty());
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            updateDescription();
        }

        public void setOnSelectionListener(SelectionListener listener) { this.listener = listener; }
        public int getSelectedIndex() { return selectedIndex; }

        /** Programmatic selection does not call the listener. */
        public void setSelectedIndex(int index) {
            if (index < 0 || index >= points.size() || index == selectedIndex) return;
            selectedIndex = index;
            updateDescription();
            invalidate();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize((int) dp(280), widthSpec),
                    resolveSize((int) dp(190), heightSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (hidden || points.isEmpty()) {
                centeredText(canvas, hidden ? "金额已隐藏" : "暂无资产记录");
                return;
            }
            configureText(10, figures, muted);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;
            float widest = 0;
            for (String label : axisLabels) widest = Math.max(widest, paint.measureText(label));
            plot.set(getPaddingLeft() + widest + dp(12),
                    getPaddingTop() + textHeight / 2 + dp(5),
                    getWidth() - getPaddingRight() - dp(6),
                    getHeight() - getPaddingBottom() - textHeight - dp(14));
            if (plot.width() < dp(32) || plot.height() < dp(32)) return;

            for (int i = 0; i < 3; i++) {
                float y = plot.bottom - i * plot.height() / 2;
                configureStroke(line, dp(1));
                canvas.drawLine(plot.left, y, plot.right, y, paint);
                configureText(10, figures, muted);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(axisLabels[i], plot.left - dp(10),
                        y - (metrics.ascent + metrics.descent) / 2, paint);
            }
            drawDateLabels(canvas, metrics);

            if (selectedIndex >= 0 && points.size() > 1) {
                configureStroke(line, dp(1));
                canvas.drawLine(xAt(selectedIndex), plot.top, xAt(selectedIndex), plot.bottom, paint);
            }
            for (int i = 1; i < points.size(); i++) {
                configureStroke(accent, dp(2));
                if (points.get(i).epochDay - points.get(i - 1).epochDay > 1) paint.setPathEffect(gapDash);
                canvas.drawLine(xAt(i - 1), yAt(i - 1), xAt(i), yAt(i), paint);
            }
            // Dots identify actual observations, including either end of an unobserved interval.
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            for (int i = 0; i < points.size(); i++) {
                boolean gapEnd = (i > 0 && points.get(i).epochDay - points.get(i - 1).epochDay > 1)
                        || (i + 1 < points.size() && points.get(i + 1).epochDay - points.get(i).epochDay > 1);
                if (points.size() <= 12 || gapEnd) canvas.drawCircle(xAt(i), yAt(i), dp(2.3f), paint);
            }
            if (selectedIndex >= 0) {
                paint.setColor(accent);
                canvas.drawCircle(xAt(selectedIndex), yAt(selectedIndex), dp(4), paint);
            }
            drawFocus(canvas);
        }

        private void drawDateLabels(Canvas canvas, Paint.FontMetrics metrics) {
            configureText(10, figures, muted);
            float baseline = getHeight() - getPaddingBottom() - dp(3) - metrics.descent;
            if (points.size() == 1) {
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(dateLabel(points.get(0), true), plot.centerX(), baseline, paint);
                return;
            }
            boolean withYear = points.get(0).date.getYear() != points.get(points.size() - 1).date.getYear();
            String first = dateLabel(points.get(0), withYear);
            String last = dateLabel(points.get(points.size() - 1), withYear);
            float firstRight = plot.left + paint.measureText(first);
            float lastLeft = plot.right - paint.measureText(last);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(first, plot.left, baseline, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (lastLeft > firstRight + dp(8)) canvas.drawText(last, plot.right, baseline, paint);
            if (points.size() > 2) {
                int middle = nearestIndex(plot.centerX());
                String label = dateLabel(points.get(middle), withYear);
                float halfWidth = paint.measureText(label) / 2;
                float x = xAt(middle);
                if (middle > 0 && middle < points.size() - 1
                        && x - halfWidth > firstRight + dp(10) && x + halfWidth < lastLeft - dp(10)) {
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, x, baseline, paint);
                }
            }
        }

        private float xAt(int index) {
            if (points.size() == 1) return plot.centerX();
            long first = points.get(0).epochDay;
            long span = points.get(points.size() - 1).epochDay - first;
            return plot.left + (float) ((points.get(index).epochDay - first) / (double) span) * plot.width();
        }

        private float yAt(int index) {
            double fraction = (points.get(index).totalFen / 100.0 - axisMin) / (2 * axisStep);
            return plot.bottom - (float) Math.max(0, Math.min(1, fraction)) * plot.height();
        }

        private int nearestIndex(float x) {
            int nearest = 0;
            float distance = Float.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) {
                float next = Math.abs(xAt(i) - x);
                if (next < distance) { nearest = i; distance = next; }
            }
            return nearest;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isClickable()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY();
                    dragging = false; verticalGesture = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
                    if (!dragging && dy > touchSlop && dy > dx) {
                        verticalGesture = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    } else if (!verticalGesture && (dragging || (dx > touchSlop && dx > dy))) {
                        dragging = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        choose(nearestIndex(event.getX()));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    if (!verticalGesture) {
                        choose(nearestIndex(event.getX()));
                        touchClick = true;
                        performClick();
                        touchClick = false;
                    }
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    dragging = false; verticalGesture = true;
                    return true;
                default: return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            if (hidden || points.isEmpty()) return false;
            if (!touchClick) choose((selectedIndex + 1) % points.size());
            return true;
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (!hidden && !points.isEmpty()) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { choose(Math.max(0, selectedIndex - 1)); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { choose(Math.min(points.size() - 1, selectedIndex + 1)); return true; }
                if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) { choose(0); return true; }
                if (keyCode == KeyEvent.KEYCODE_MOVE_END) { choose(points.size() - 1); return true; }
            }
            return super.onKeyDown(keyCode, event);
        }

        @SuppressWarnings("deprecation") // RangeInfo.obtain remains compatible with API 26.
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (!hidden && !points.isEmpty()) {
                info.setClassName("android.widget.SeekBar");
                info.setScrollable(points.size() > 1);
                info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, points.size() - 1, selectedIndex));
                if (selectedIndex < points.size() - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                if (selectedIndex > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            if (!hidden && !points.isEmpty()) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && selectedIndex < points.size() - 1) {
                    choose(selectedIndex + 1); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && selectedIndex > 0) {
                    choose(selectedIndex - 1); return true;
                }
            }
            return super.performAccessibilityAction(action, arguments);
        }

        private void choose(int index) {
            if (index == selectedIndex || index < 0 || index >= points.size()) return;
            setSelectedIndex(index);
            if (listener != null) listener.onSelection(index);
        }

        private void updateDescription() {
            if (hidden) setContentDescription("资产趋势，金额已隐藏");
            else if (points.isEmpty()) setContentDescription("资产趋势，暂无资产记录");
            else {
                Point point = points.get(selectedIndex);
                setContentDescription("资产趋势，共 " + points.size() + " 条记录。已选 "
                        + point.date.getYear() + " 年 " + point.date.getMonthValue() + " 月 "
                        + point.date.getDayOfMonth() + " 日，总资产 " + exactYuan(point.totalFen)
                        + " "+currencyLabel+"。左右调整可切换记录。虚线表示日期之间没有记录。");
            }
        }

        private void centeredText(Canvas canvas, String text) {
            configureText(12, SketchStyle.handwriting(getContext()), muted);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, getWidth() / 2f,
                    getHeight() / 2f - (metrics.ascent + metrics.descent) / 2, paint);
            drawFocus(canvas);
        }

        private void drawFocus(Canvas canvas) {
            if (!isFocused()) return;
            configureStroke(accent, dp(1.5f));
            canvas.drawRoundRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), dp(4), dp(4), paint);
        }

        private void configureStroke(int color, float width) {
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width);
            paint.setStrokeCap(Paint.Cap.BUTT); paint.setPathEffect(null);
        }

        private void configureText(float sp, Typeface typeface, int color) {
            paint.setStyle(Paint.Style.FILL); paint.setPathEffect(null); paint.setColor(color);
            paint.setTypeface(typeface);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private float dp(float value) { return value * density; }
    }

    public static final class DonutView extends View {
        private final List<Slice> slices;
        private final Typeface figures;
        private final int ink, muted, line;
        private final boolean hidden;
        private final double total;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ring = new RectF();
        private final float density;
        private final int touchSlop;
        private int selectedIndex = -1;
        private SelectionListener listener;
        private float downX, downY, radius, stroke;
        private boolean moved, touchClick;

        public DonutView(Context context, List<Slice> slices, Typeface figures,
                         int ink, int muted, int line, boolean hidden) {
            super(context);
            this.slices = Collections.unmodifiableList(new ArrayList<>(slices));
            this.figures = figures == null ? Typeface.DEFAULT : figures;
            this.ink = ink; this.muted = muted; this.line = line; this.hidden = hidden;
            density = getResources().getDisplayMetrics().density;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            double sum = 0;
            for (int i = 0; i < slices.size(); i++) {
                if (slices.get(i).valueFen > 0) {
                    sum += slices.get(i).valueFen;
                    if (selectedIndex < 0) selectedIndex = i;
                }
            }
            total = sum;
            setMinimumHeight((int) dp(48));
            setMinimumWidth((int) dp(48));
            setFocusable(true);
            setClickable(!hidden && total > 0);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            updateDescription();
        }

        public void setOnSelectionListener(SelectionListener listener) { this.listener = listener; }
        public int getSelectedIndex() { return selectedIndex; }

        /** Zero-value categories have no arc and are not selectable. Does not call the listener. */
        public void setSelectedIndex(int index) {
            if (index < 0 || index >= slices.size() || slices.get(index).valueFen <= 0 || index == selectedIndex) return;
            selectedIndex = index;
            updateDescription();
            invalidate();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize((int) dp(156), widthSpec),
                    resolveSize((int) dp(156), heightSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = (getPaddingLeft() + getWidth() - getPaddingRight()) / 2f;
            float cy = (getPaddingTop() + getHeight() - getPaddingBottom()) / 2f;
            float diameter = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                    getHeight() - getPaddingTop() - getPaddingBottom());
            stroke = Math.min(dp(17), diameter * .12f);
            radius = Math.max(0, diameter / 2 - stroke / 2 - dp(7));
            ring.set(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.BUTT); paint.setColor(line);
            canvas.drawOval(ring, paint);
            if (hidden || total <= 0) {
                drawCenter(canvas, cx, cy, hidden ? "金额已隐藏" : "暂无分布", null);
            } else {
                double start = -90;
                double selectedMiddle = -90;
                for (int i = 0; i < slices.size(); i++) {
                    Slice slice = slices.get(i);
                    if (slice.valueFen <= 0) continue;
                    double sweep = slice.valueFen / total * 360;
                    paint.setColor(slice.color);
                    RectF outerRing=new RectF(ring);outerRing.inset(-stroke/2,-stroke/2);RectF innerRing=new RectF(ring);innerRing.inset(stroke/2,stroke/2);
                    Path segment=new Path();segment.arcTo(outerRing,(float)start,(float)sweep);segment.arcTo(innerRing,(float)(start+sweep),(float)-sweep);segment.close();
                    SketchStyle.pencilFill(canvas,segment,slice.color,density,90);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.1f));paint.setColor(slice.color);canvas.drawPath(segment,paint);paint.setStrokeWidth(stroke);
                    if (i == selectedIndex) selectedMiddle = start + sweep / 2;
                    start += sweep;
                }
                // A short marker identifies the selected category without changing its angle or width.
                double angle = Math.toRadians(selectedMiddle);
                float outer = radius + stroke / 2;
                paint.setColor(slices.get(selectedIndex).color);
                paint.setStrokeWidth(dp(2)); paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx + (float) Math.cos(angle) * (outer + dp(2)),
                        cy + (float) Math.sin(angle) * (outer + dp(2)),
                        cx + (float) Math.cos(angle) * (outer + dp(5)),
                        cy + (float) Math.sin(angle) * (outer + dp(5)), paint);
                Slice selected = slices.get(selectedIndex);
                drawCenter(canvas, cx, cy, selected.label, percentage(selected.valueFen / total));
            }
            if (isFocused()) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(ink);
                canvas.drawRoundRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), dp(4), dp(4), paint);
            }
        }

        private void drawCenter(Canvas canvas, float cx, float cy, String label, String percent) {
            labelPaint.setColor(muted); labelPaint.setTextSize(sp(12));
            labelPaint.setTypeface(SketchStyle.handwriting(getContext())); labelPaint.setTextAlign(Paint.Align.CENTER);
            float inner = Math.max(0, radius - stroke / 2);
            float maxWidth = inner * 1.75f;
            String visible = TextUtils.ellipsize(label, labelPaint, maxWidth, TextUtils.TruncateAt.END).toString();
            Paint.FontMetrics labelMetrics = labelPaint.getFontMetrics();
            if (percent == null) {
                canvas.drawText(visible, cx, cy - (labelMetrics.ascent + labelMetrics.descent) / 2, labelPaint);
                return;
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(ink); paint.setTypeface(figures);
            paint.setTextSize(sp(percent.length() > 5 ? 18 : getWidth() < dp(150) ? 20 : 22)); paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics numberMetrics = paint.getFontMetrics();
            float labelHeight = labelMetrics.descent - labelMetrics.ascent;
            float numberHeight = numberMetrics.descent - numberMetrics.ascent;
            float top = cy - (labelHeight + dp(4) + numberHeight) / 2;
            canvas.drawText(visible, cx, top - labelMetrics.ascent, labelPaint);
            canvas.drawText(percent, cx, top + labelHeight + dp(4) - numberMetrics.ascent, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isClickable()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY(); moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) moved = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        float dx = event.getX() - ring.centerX(), dy = event.getY() - ring.centerY();
                        if (Math.hypot(dx, dy) < radius - stroke / 2) chooseNext(1);
                        else choose(sliceAt(dx, dy));
                        touchClick = true; performClick(); touchClick = false;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    moved = true; return true;
                default: return true;
            }
        }

        private int sliceAt(float dx, float dy) {
            double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 450) % 360;
            double end = 0;
            int last = selectedIndex;
            for (int i = 0; i < slices.size(); i++) {
                if (slices.get(i).valueFen <= 0) continue;
                end += slices.get(i).valueFen / total * 360;
                last = i;
                if (angle < end) return i;
            }
            return last;
        }

        @Override public boolean performClick() {
            super.performClick();
            if (hidden || total <= 0) return false;
            if (!touchClick) chooseNext(1);
            return true;
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (!hidden && total > 0) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { chooseNext(-1); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { chooseNext(1); return true; }
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (!hidden && total > 0) {
                info.setClassName("android.widget.Button");
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "切换资产类别"));
                if (positiveCount() > 1) {
                    info.setScrollable(true);
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                }
            }
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            if (!hidden && total > 0) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { chooseNext(1); return true; }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { chooseNext(-1); return true; }
            }
            return super.performAccessibilityAction(action, arguments);
        }

        private void chooseNext(int direction) {
            for (int count = 1; count <= slices.size(); count++) {
                int index = (selectedIndex + direction * count + slices.size()) % slices.size();
                if (slices.get(index).valueFen > 0) { choose(index); return; }
            }
        }

        private void choose(int index) {
            if (index == selectedIndex || index < 0 || index >= slices.size() || slices.get(index).valueFen <= 0) return;
            setSelectedIndex(index);
            if (listener != null) listener.onSelection(index);
        }

        private int positiveCount() {
            int count = 0;
            for (Slice slice : slices) if (slice.valueFen > 0) count++;
            return count;
        }

        private void updateDescription() {
            if (hidden) setContentDescription("资产分布，金额已隐藏");
            else if (total <= 0) setContentDescription("资产分布，暂无分布");
            else {
                Slice selected = slices.get(selectedIndex);
                setContentDescription("资产分布，共 " + positiveCount() + " 个类别。已选 " + selected.label
                        + "，占 " + percentage(selected.valueFen / total) + "。点按可切换类别。");
            }
        }

        private float dp(float value) { return value * density; }
        private float sp(float value) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
        }
    }

    private static String dateLabel(Point point, boolean withYear) {
        return (withYear ? point.date.getYear() + "/" : "")
                + point.date.getMonthValue() + "/" + point.date.getDayOfMonth();
    }

    private static double niceCeiling(double value) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double fraction = value / magnitude;
        double rounded = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
        return rounded * magnitude;
    }

    private static DecimalFormat decimalFormat(int precision) {
        DecimalFormat format = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.CHINA));
        format.setMaximumFractionDigits(precision);
        format.setGroupingUsed(false);
        return format;
    }

    private static String percentage(double fraction) {
        if (fraction > 0 && fraction < .001) return "<0.1%";
        if (fraction < 1 && fraction > .999) return ">99.9%";
        return decimalFormat(1).format(fraction * 100) + "%";
    }

    private static String exactYuan(long fen) {
        return java.math.BigDecimal.valueOf(fen, 2).toPlainString();
    }
}
