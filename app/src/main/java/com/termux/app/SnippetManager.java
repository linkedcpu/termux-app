package com.termux.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.app.terminal.ThemeManager;
import com.termux.app.terminal.ThemeSpec;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命令收藏夹 · 内嵌面板版（纯命令版，无 name）
 * 视觉与 DrawerPanel 对齐，配色跟随 ThemeManager 的界面主题。
 */
public final class SnippetManager {

    private static final String TAG = "SnippetManager";
    private static final String FILE = "snippets.json";
    private static final int CMD_DISP_MAX = 30;
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";

    private final TermuxActivity mAct;
    private final List<CmdItem> mList = new ArrayList<>();
    private final Set<String> mDeleted = new HashSet<>();

    private View mPanel;
    private LinearLayout mListBox;

    private EditText mBottomInput;
    private TextView mBtnPlus, mBtnClear;
    private GradientDrawable mBottomBg;

    private boolean mTabMode = false;
    public void setTabMode(boolean tabMode) { mTabMode = tabMode; }

    public static final class CmdItem {
        public final String cmd;
        public CmdItem(String cmd) { this.cmd = cmd == null ? "" : cmd.trim(); }
    }

    /* ================= 调色：跟随界面主题 ================= */

    private ThemeSpec theme() { return ThemeManager.current(mAct); }

    private int bg()      { return solid(theme().bgColor, 0xFF12141A); }
    private int surface() { return shift(bg(), theme().textPrimary, 0.06f); }
    private int stroke()  { return withAlpha(theme().textSecondary, 0x40); }
    private int text()    { return theme().textPrimary; }
    private int sub()     { return theme().textSecondary; }
    private int accent()  { return theme().accent; }
    private int danger()  { return 0xFFE05050; }

    private int solid(int c, int fb) {
        if (c == 0) return fb;
        if (android.graphics.Color.alpha(c) == 0xFF) return c;
        return blend(0xFF12141A, c);
    }

    private int shift(int base, int toward, float ratio) {
        int r = (int) (android.graphics.Color.red(base)   * (1 - ratio) + android.graphics.Color.red(toward)   * ratio);
        int g = (int) (android.graphics.Color.green(base) * (1 - ratio) + android.graphics.Color.green(toward) * ratio);
        int b = (int) (android.graphics.Color.blue(base)  * (1 - ratio) + android.graphics.Color.blue(toward)  * ratio);
        return android.graphics.Color.argb(0xFF, clamp(r), clamp(g), clamp(b));
    }

    private int blend(int back, int front) {
        float a = android.graphics.Color.alpha(front) / 255f;
        return android.graphics.Color.argb(0xFF,
                (int) (android.graphics.Color.red(back)   * (1 - a) + android.graphics.Color.red(front)   * a),
                (int) (android.graphics.Color.green(back) * (1 - a) + android.graphics.Color.green(front) * a),
                (int) (android.graphics.Color.blue(back)  * (1 - a) + android.graphics.Color.blue(front)  * a));
    }

    private int withAlpha(int c, int alpha) {
        return android.graphics.Color.argb(alpha,
                android.graphics.Color.red(c),
                android.graphics.Color.green(c),
                android.graphics.Color.blue(c));
    }

    private int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    public SnippetManager(TermuxActivity act) { mAct = act; migrateLegacy(); load(); }

    /** ★ 供 DrawerPanel / TermuxActivity 切主题后调用：按新主题重绘 */
    public void reloadTheme() {
        if (mPanel == null) return;
        if (mBottomInput != null) mBottomInput.setTextColor(text());
        if (mBottomBg != null) mBottomBg.setStroke(dp(1), stroke());
        render();
    }

    /* ================= 对外 ================= */

    public View buildPanel() {
        int dp = dp(1);
        LinearLayout root = new LinearLayout(mAct);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.setVisibility(View.GONE);
        root.setFocusableInTouchMode(true);
        mPanel = root;

        mListBox = new LinearLayout(mAct);
        mListBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(mAct);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(mListBox, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(mAct);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(10), dp(6), dp(6), dp(6));
        mBottomBg = searchField(false);
        bottom.setBackground(mBottomBg);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        mBottomInput = new EditText(mAct);
        mBottomInput.setHint("搜索或输入命令，点 ＋ 收藏");
        mBottomInput.setTextSize(13);
        mBottomInput.setTextColor(text());
        mBottomInput.setHintTextColor(sub());
        mBottomInput.setSingleLine(true);
        mBottomInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mBottomInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mBottomInput.setBackground(null);
        mBottomInput.setPadding(dp(8), dp(6), dp(6), dp(6));

        mBottomInput.setOnFocusChangeListener((v, has) -> {
            if (mBottomBg != null)
                mBottomBg.setStroke(dp(has ? 2 : 1), has ? accent() : stroke());
        });
        mBottomInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { syncBottomButtons(); render(); }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
        });
        mBottomInput.setOnEditorActionListener((v, id, e) -> { commitBottomInput(); return true; });

        bottom.addView(mBottomInput, new LinearLayout.LayoutParams(0, -2, 1f));

        mBtnPlus  = flatBtn("＋", accent(), v -> commitBottomInput());
        mBtnClear = flatBtn("▼", sub(), v -> {
            if (mBottomInput == null) return;
            if (!mBottomInput.getText().toString().isEmpty()) mBottomInput.setText("");
            else collapse();
        });
        bottom.addView(mBtnPlus);
        bottom.addView(mBtnClear);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        syncBottomButtons();
        render();
        return root;
    }

    public void expand() {
        if (mPanel == null) buildPanel();
        mExpanded = true;
        load(); mViewingIdx = -1; syncBottomButtons(); render();
        mPanel.setVisibility(View.VISIBLE);
    }

    public void collapse() {
        mExpanded = false; mViewingIdx = -1;
        if (mPanel == null) return;
        if (mBottomInput != null) mBottomInput.setText("");
        mPanel.clearFocus();
        mPanel.setVisibility(View.GONE);
    }

    public boolean isExpanded() { return mExpanded; }
    public void save() { saveSnippets(); }
    public View getPanelView() { return mPanel; }

    private boolean mExpanded = false;
    private int mViewingIdx = -1;

    /* ================= 底部栏 ================= */

    private void syncBottomButtons() {
        boolean has = mBottomInput != null && !mBottomInput.getText().toString().isEmpty();
        if (mBtnPlus  != null) mBtnPlus.setVisibility(View.VISIBLE);
        if (mBtnClear != null) {
            mBtnClear.setText(has ? "✕" : "▼");
            mBtnClear.setContentDescription(has ? "清空" : "关闭");
        }
    }

    private void commitBottomInput() {
        if (mBottomInput == null) return;
        String t = mBottomInput.getText().toString().trim();
        if (t.isEmpty()) { mAct.showToast("输入点命令再点 ＋ 收藏～", false); return; }
        addSnippet(t);
        mBottomInput.setText("");
        render();
    }

    /* ================= 渲染 ================= */

    private void render() {
        if (mListBox == null) return;
        int dp = dp(1);
        mListBox.removeAllViews();

        String q = (mBottomInput == null ? "" : mBottomInput.getText().toString()).trim().toLowerCase();
        List<CmdItem> show = new ArrayList<>();
        for (CmdItem c : mList)
            if (q.isEmpty() || c.cmd.toLowerCase().contains(q)) show.add(c);

        if (show.isEmpty()) {
            mListBox.addView(empty(
                    q.isEmpty() ? "📭  还没有收藏" : "🔎  没有匹配 「" + q + "」",
                    q.isEmpty() ? "输入命令，点 ＋ 收藏（太长会截断，长按看全）" : "换个关键词试试"));
            return;
        }

        for (CmdItem item : show) {
            final boolean viewing = (mViewingIdx == indexOfCmd(item.cmd));

            LinearLayout card = new LinearLayout(mAct);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cardBg(surface(), stroke()));
            card.setPadding(dp(12), dp(9), dp(10), dp(9));
            card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = new LinearLayout(mAct);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            TextView cmd = new TextView(mAct);
            cmd.setText(dispCmd(item.cmd));
            cmd.setTextColor(text());
            cmd.setTextSize(13);
            cmd.setTypeface(Typeface.DEFAULT);
            cmd.setSingleLine(true);
            cmd.setEllipsize(TextUtils.TruncateAt.END);
            cmd.setHorizontallyScrolling(true);
            cmd.setMaxWidth(Integer.MAX_VALUE);
            cmd.setPadding(0, 0, dp(8), 0);
            row.addView(cmd, new LinearLayout.LayoutParams(0, -2, 1f));

            row.addView(flatBtn("▶", accent(), v -> actionRun(item)));
            row.addView(flatBtn("⚑", accent(), v -> actionPin(item)));
            row.addView(flatBtn("🗑", danger(),  v -> actionDelete(item)));
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> actionInsert(item));
            card.setOnLongClickListener(v -> { actionView(item); return true; });

            if (viewing) card.addView(buildExpandBox(item), new LinearLayout.LayoutParams(-1, -2));

            mListBox.addView(card, new LinearLayout.LayoutParams(-1, -2));
            mListBox.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private String dispCmd(String c) {
        if (c == null) return "";
        if (c.length() <= CMD_DISP_MAX) return c;
        String head = c.substring(0, CMD_DISP_MAX).trim();
        int sp = Math.max(head.lastIndexOf(' '), head.lastIndexOf('\t'));
        if (sp > CMD_DISP_MAX * 2 / 3) head = head.substring(0, sp).trim();
        return head + " …";
    }

    private View buildExpandBox(CmdItem item) {
        int dp = dp(1);
        LinearLayout box = new LinearLayout(mAct);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(pill(bg(), dp(8)));
        box.setPadding(dp(10), dp(6), dp(10), dp(6));

        LinearLayout sub = new LinearLayout(mAct);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        TextView lb = new TextView(mAct);
        lb.setText("长按卡片收起 · 完整命令");
        lb.setTextColor(sub()); lb.setTextSize(10.5f);
        sub.addView(lb, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cp = new TextView(mAct);
        cp.setText("复制"); cp.setTextColor(accent()); cp.setTextSize(11.5f);
        cp.setPadding(dp(8), dp(2), 0, dp(2));
        cp.setClickable(true); cp.setFocusable(true);
        cp.setOnClickListener(x -> {
            ClipboardManager cm = (ClipboardManager) mAct.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("cmd", item.cmd));
            mAct.showToast("已复制完整命令", false);
        });
        sub.addView(cp);
        box.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        TextView body = new TextView(mAct);
        body.setText(item.cmd);
        body.setTextColor(text());
        body.setTextSize(12);
        body.setTypeface(Typeface.DEFAULT);
        body.setPadding(0, dp(6), 0, 0);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        ScrollView scr = new ScrollView(mAct);
        scr.setVerticalScrollBarEnabled(true);
        scr.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(140)));
        scr.addView(body, new LinearLayout.LayoutParams(-1, -2));
        box.addView(scr, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private TextView flatBtn(String glyph, int color, View.OnClickListener onClick) {
        int dp = dp(1);
        TextView b = new TextView(mAct);
        b.setText(glyph); b.setTextColor(color); b.setTextSize(14f);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true); b.setFocusable(true);
        b.setBackgroundResource(selectableItemBg());
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setOnClickListener(onClick);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) b.setAlpha(0.5f);
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) b.setAlpha(1f);
            return false;
        });
        return b;
    }

    /* ================= 动作 ================= */

    private boolean sessionReady(TerminalSession s) { return s != null && s.isRunning(); }

    private void actionInsert(CmdItem item) {
        TerminalSession cur = mAct.getCurrentSession();
        if (!sessionReady(cur)) { mAct.showToast("会话未就绪，先敲几下回车～", false); return; }
        cur.write(item.cmd);
        mAct.showToast("已插入", false);
    }

    private void actionRun(CmdItem item) {
        TerminalSession cur = mAct.getCurrentSession();
        if (!sessionReady(cur)) { mAct.showToast("会话未就绪，先敲几下回车～", false); return; }
        cur.write(item.cmd + "\n");
        if (!mTabMode) collapse();
        mAct.showToast("已运行", false);
    }

    private void actionPin(CmdItem target) {
        if (target == null) return;
        int idx = indexOfCmd(target.cmd);
        if (idx < 0) { render(); return; }
        CmdItem sn = mList.remove(idx);
        mList.add(0, sn);
        save(); render();
        mAct.showToast("已置顶：" + dispCmd(sn.cmd), false);
    }

    private void actionDelete(final CmdItem target) {
        if (target == null) return;
        final String cmdKey = target.cmd;
        new android.app.AlertDialog.Builder(mAct)
                .setTitle((CharSequence) dispCmd(cmdKey))
                .setMessage("删除这条收藏？")
                .setCancelable(true)
                .setPositiveButton("删除", (dialog, which) -> {
                    int idx = indexOfCmd(cmdKey);
                    if (idx >= 0) {
                        mList.remove(idx);
                        mDeleted.add(cmdKey);
                        if (mViewingIdx == idx) mViewingIdx = -1;
                    }
                    save(); render();
                    mAct.showToast("已删除：" + dispCmd(cmdKey), false);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void actionView(CmdItem target) {
        if (target == null) return;
        int idx = indexOfCmd(target.cmd);
        if (idx < 0) { mViewingIdx = -1; render(); return; }
        mViewingIdx = (mViewingIdx == idx) ? -1 : idx;
        render();
        mAct.showToast(mViewingIdx < 0 ? "已收起" : "长按卡片可收起 · 拖滚动看全", false);
    }

    private int indexOfCmd(String cmd) {
        if (cmd == null) return -1;
        for (int i = 0; i < mList.size(); i++)
            if (mList.get(i).cmd.equals(cmd)) return i;
        return -1;
    }

    /* ================= 数据 ================= */

    private void addSnippet(String cmd) {
        String c = cmd.trim();
        if (c.isEmpty()) return;
        mDeleted.remove(c);
        for (CmdItem s : mList)
            if (s.cmd.equals(c)) { mAct.showToast("已经收藏过了：" + dispCmd(c), false); return; }
        mList.add(0, new CmdItem(c));
        save();
        mAct.showToast("已收藏：" + dispCmd(c), false);
    }

    private List<CmdItem> readAll(File f) {
        List<CmdItem> out = new ArrayList<>();
        if (f == null || !f.exists()) return out;
        try {
            String s = new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (s == null || s.trim().isEmpty()) return out;
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                String c = arr.optString(i, null);
                if (c != null && !c.trim().isEmpty() && !containsCmd(out, c.trim()))
                    out.add(new CmdItem(c.trim()));
            }
        } catch (Exception e) {
            Logger.logVerbose(TAG, "read skipped(" + f.getPath() + "): " + e.getMessage());
        }
        return out;
    }

    private void load() { mList.clear(); mList.addAll(readAll(file())); }

    private void saveSnippets() {
        File f = file();
        if (f == null) return;
        try {
            Map<String, CmdItem> disk = new LinkedHashMap<>();
            for (CmdItem c : readAll(f))
                if (c != null && !mDeleted.contains(c.cmd)) disk.put(c.cmd, c);

            List<CmdItem> ordered = new ArrayList<>();
            for (CmdItem c : mList)
                if (c != null) { ordered.add(c); disk.remove(c.cmd); }
            ordered.addAll(disk.values());

            mList.clear();
            mList.addAll(ordered);

            JSONArray arr = new JSONArray();
            for (CmdItem c : mList) arr.put(c.cmd);

            String payload = arr.toString(2);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) { w.write(payload); w.flush(); }
            if (!tmp.renameTo(f))
                Files.write(f.toPath(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            mDeleted.clear();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(TAG, "save failed", e);
        }
    }

    private boolean containsCmd(List<CmdItem> list, String cmd) {
        for (CmdItem c : list) if (c.cmd.equals(cmd)) return true;
        return false;
    }

    private File file() {
        File dir = new File(TERMUX_HOME, ".termux");
        dir.mkdirs();
        return new File(dir, FILE);
    }

    private void migrateLegacy() {
        List<File> legacy = new ArrayList<>();
        String envHome = System.getenv("HOME");
        if (envHome != null && !envHome.equals(TERMUX_HOME))
            legacy.add(new File(envHome, ".termux/" + FILE));
        String appHome = mAct.getApplicationContext().getFilesDir().getAbsolutePath();
        if (!appHome.equals(TERMUX_HOME))
            legacy.add(new File(appHome, ".termux/" + FILE));
        if (legacy.isEmpty()) return;

        File target = file();
        boolean changed = false;
        try {
            Map<String, String> cmds = new LinkedHashMap<>();
            for (CmdItem c : readAll(target)) cmds.put(c.cmd, c.cmd);

            for (File lf : legacy) {
                if (!lf.exists()) continue;
                try {
                    String s = new String(Files.readAllBytes(lf.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    if (s == null || s.trim().isEmpty()) continue;
                    JSONArray arr = new JSONArray(s);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            String c = arr.optString(i, "").trim();
                            if (!c.isEmpty()) cmds.putIfAbsent(c, c);
                        } else {
                            String c = o.optString("cmd", "").trim();
                            if (c.isEmpty()) c = o.optString("name", "").trim();
                            if (!c.isEmpty()) cmds.putIfAbsent(c, c);
                        }
                    }
                } catch (JSONException ignore) { }
                changed = true;
            }

            if (changed && !cmds.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String c : cmds.values()) arr.put(c);
                File tmp = new File(target.getPath() + ".tmp");
                try (FileWriter w = new FileWriter(tmp)) { w.write(arr.toString(2)); w.flush(); }
                if (tmp.renameTo(target)) {
                    for (File lf : legacy) { try { lf.delete(); } catch (Exception ignore) {} }
                    Logger.logVerbose(TAG, "legacy migrated → " + target.getPath());
                }
            }
        } catch (Exception e) {
            Logger.logVerbose(TAG, "migrateLegacy skipped: " + e.getMessage());
        }
    }

    /* ================= 样式 ================= */

    private GradientDrawable cardBg(int fill, int st) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(10));
        d.setColor(fill);
        d.setStroke(dp(1), st);
        return d;
    }

    private GradientDrawable searchField(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(8));
        d.setColor(bg());
        d.setStroke(dp(focused ? 2 : 1), focused ? accent() : stroke());
        return d;
    }

    private GradientDrawable pill(int fill, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(r);
        d.setColor(fill);
        return d;
    }

    private View divider() {
        View v = new View(mAct);
        v.setBackgroundColor(stroke());
        return v;
    }

    private int selectableItemBg() {
        TypedValue tv = new TypedValue();
        if (mAct.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
            return tv.resourceId;
        return 0;
    }

    private LinearLayout empty(String big, String sub) {
        int dp = dp(1);
        LinearLayout box = new LinearLayout(mAct);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(20), 0, dp(16));
        TextView a = new TextView(mAct);
        a.setText(big); a.setTextColor(text()); a.setTextSize(13.5f);
        a.setTypeface(null, Typeface.BOLD); a.setPadding(0, 0, 0, dp(4));
        box.addView(a);
        TextView b = new TextView(mAct);
        b.setText(sub); b.setTextColor(sub()); b.setTextSize(11.5f);
        box.addView(b);
        return box;
    }

    private int dp(int dp) { return Math.round(dp * mAct.getResources().getDisplayMetrics().density); }
}
