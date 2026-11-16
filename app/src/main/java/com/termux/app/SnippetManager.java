package com.termux.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.termux.R;
import com.termux.app.terminal.theme.Theme;
import com.termux.app.terminal.theme.ThemeStore;
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
 *
 * ★ 外观口径与 DrawerPanel 完全一致：只调 Theme 语义入口，
 *   不判亮度、不混色、不判壁纸、不写死色值与圆角。
 *     颜色 → theme().color(角色)
 *     形状 → theme().surface() / button() / radius() / stroke() / pillButtons()
 *   以后换皮肤只动 Theme，本文件冻结。
 */
public final class SnippetManager {

    private static final String TAG = "SnippetManager";
    private static final String FILE = "snippets.json";
    private static final int CMD_DISP_MAX = 38;
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";

    private static final int CARD_GAP_DP   = 6;   // 卡片间距（替代分割线）
    private static final float RADIUS_CARD_DP = 10f;
    private static final float RADIUS_FIELD_DP = 8f;

    private static final int ICON_SIZE_DP  = 15;
    private static final int ICON_PAD_H_DP = 5;
    private static final int ICON_PAD_V_DP = 5;

    private final TermuxActivity mAct;
    private final List<CmdItem> mList = new ArrayList<>();
    private final Set<String> mDeleted = new HashSet<>();

    private View mPanel;
    private LinearLayout mListBox;

    private TextView mBtnPlus, mBtnClear;
    private EditText mBottomInput;
    private GradientDrawable mBottomBg;

    private boolean mTabMode = false;
    public void setTabMode(boolean tabMode) { mTabMode = tabMode; }

    /* ================= 状态回调 ================= */

    public interface OnStateChanged { void onStateChanged(boolean expanded); }

    private OnStateChanged mOnStateChanged;

    public void setOnStateChanged(OnStateChanged cb) { mOnStateChanged = cb; }

    public void setOnStateChanged(final Runnable r) {
        mOnStateChanged = r == null ? null : expanded -> r.run();
    }

    private void notifyStateChanged() {
        if (mOnStateChanged != null) mOnStateChanged.onStateChanged(mExpanded);
    }

    public static final class CmdItem {
        public final String cmd;
        public CmdItem(String cmd) { this.cmd = cmd == null ? "" : cmd.trim(); }
    }

    /* ================= 调色（全部委托 Theme，零推导） ================= */

    private Theme theme() { return ThemeStore.get(mAct); }

    private int accent() { return theme().color(Theme.ACCENT); }
    private int text()   { return theme().color(Theme.TEXT); }
    private int sub()    { return theme().color(Theme.TEXT_SEC); }
    private int danger() { return theme().color(Theme.DANGER); }
    private int stroke() { return theme().color(Theme.OUTLINE); }

    public SnippetManager(TermuxActivity act) { mAct = act; migrateLegacy(); load(); }

    public void reloadTheme() {
        if (mPanel == null) return;
        if (mBottomInput != null) {
            mBottomInput.setTextColor(text());
            mBottomInput.setHintTextColor(sub());
        }
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

        int bottomBtnW = dp(ICON_SIZE_DP + ICON_PAD_H_DP * 2);
        LinearLayout.LayoutParams bottomBtnLp = new LinearLayout.LayoutParams(bottomBtnW, -2);
        mBtnPlus  = iconBtn(R.drawable.ic_add, accent(), v -> commitBottomInput());
        mBtnClear = iconBtn(R.drawable.ic_expand, sub(), v -> {
            if (mBottomInput == null) return;
            if (!mBottomInput.getText().toString().isEmpty()) mBottomInput.setText("");
            else collapse();
        });
        bottom.addView(mBtnPlus,  bottomBtnLp);
        bottom.addView(mBtnClear, bottomBtnLp);
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
        notifyStateChanged();
    }

    public void collapse() {
        boolean was = mExpanded;
        mExpanded = false; mViewingIdx = -1;
        if (mPanel == null) {
            if (was) notifyStateChanged();
            return;
        }
        if (mBottomInput != null) mBottomInput.setText("");
        mPanel.clearFocus();
        mPanel.setVisibility(View.GONE);
        if (was) notifyStateChanged();
    }

    public boolean isExpanded() { return mExpanded; }
    public void save() { saveSnippets(); }
    public View getPanelView() { return mPanel; }

    private boolean mExpanded = false;
    private int mViewingIdx = -1;

    /* ================= 底部栏 ================= */

    private void syncBottomButtons() {
        boolean has = mBottomInput != null && !mBottomInput.getText().toString().isEmpty();
        if (mBtnPlus != null) mBtnPlus.setVisibility(View.VISIBLE);
        if (mBtnClear != null) {
            String state = has ? "clear" : "collapse";
            if (!state.equals(mBtnClear.getTag())) {
                mBtnClear.setTag(state);
                setBtnIcon(mBtnClear, has ? R.drawable.ic_clear : R.drawable.ic_expand, sub());
            }
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

        int btnW = dp(ICON_SIZE_DP + ICON_PAD_H_DP * 2);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(btnW, -2);

        boolean first = true;
        for (CmdItem item : show) {
            final boolean viewing = (mViewingIdx == indexOfCmd(item.cmd));

            if (!first) {
                View gap = new View(mAct);
                gap.setBackgroundColor(0x00000000);
                mListBox.addView(gap, new LinearLayout.LayoutParams(-1, dp(CARD_GAP_DP)));
            }
            first = false;

            LinearLayout card = new LinearLayout(mAct);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(theme().surface(mAct, RADIUS_CARD_DP));   // ★ Theme 决定卡底
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
            cmd.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cmd.setHorizontallyScrolling(true);
            cmd.setMaxWidth(Integer.MAX_VALUE);
            cmd.setPadding(0, 0, dp(4), 0);
            row.addView(cmd, new LinearLayout.LayoutParams(0, -2, 1f));

            row.addView(iconBtn(R.drawable.ic_play,   accent(), v -> actionRun(item)),   btnLp);
            row.addView(iconBtn(R.drawable.ic_pin,    accent(), v -> actionPin(item)),   btnLp);
            row.addView(iconBtn(R.drawable.ic_delete, danger(), v -> actionDelete(item)), btnLp);
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> actionInsert(item));
            card.setOnLongClickListener(v -> { actionView(item); return true; });

            if (viewing) card.addView(buildExpandBox(item), new LinearLayout.LayoutParams(-1, -2));

            mListBox.addView(card, new LinearLayout.LayoutParams(-1, -2));
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
        box.setBackground(theme().surface(mAct, RADIUS_FIELD_DP));      // ★ Theme 决定底
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

    /* ================= 按钮 ================= */

    /** 图标按钮：紧凑尺寸，宽度固定不抢命令文字空间；外观由 Theme 决定。 */
    private TextView iconBtn(int iconRes, int color, View.OnClickListener onClick) {
        int dp = dp(1);
        TextView b = new TextView(mAct);
        b.setTextColor(color);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true); b.setFocusable(true);
        b.setBackground(theme().button(mAct, RADIUS_FIELD_DP));        // ★ Theme 决定形态
        b.setPadding(dp(ICON_PAD_H_DP), dp(ICON_PAD_V_DP), dp(ICON_PAD_H_DP), dp(ICON_PAD_V_DP));
        b.setText("");
        setBtnIcon(b, iconRes, color);
        b.setOnClickListener(onClick);
        final int pressMs = theme().motion().pressDurationMs;
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().setDuration(pressMs).alpha(0.5f).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().setDuration(pressMs).alpha(1f).start();
            }
            return false;
        });
        return b;
    }

    private void setBtnIcon(TextView btn, int iconRes, int color) {
        Drawable d = ContextCompat.getDrawable(mAct, iconRes);
        if (d == null) return;
        d = DrawableCompat.wrap(d).mutate();
        DrawableCompat.setTint(d, color);
        int size = dp(ICON_SIZE_DP);
        d.setBounds(0, 0, size, size);
        btn.setCompoundDrawables(null, null, d, null);
        btn.setCompoundDrawablePadding(0);
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
                if (lf.exists()) {
                    try {
                        String s = new String(Files.readAllBytes(lf.toPath()),
                                java.nio.charset.StandardCharsets.UTF_8);
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
            }

            if (changed && !cmds.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String c : cmds.values()) arr.put(c);
                File tmp = new File(target.getPath() + ".tmp");
                try (FileWriter w = new FileWriter(tmp)) {
                    w.write(arr.toString(2));
                    w.flush();
                }
                if (tmp.renameTo(target)) {
                    for (File lf : legacy) {
                        try { lf.delete(); } catch (Exception ignore) { }
                    }
                    Logger.logVerbose(TAG, "legacy migrated → " + target.getPath());
                }
            }
        } catch (Exception e) {
            Logger.logVerbose(TAG, "migrateLegacySkipped: " + e.getMessage());
        }
    }

    /* ================= 样式（只包一层 Drawable，不推导） ================= */

    private GradientDrawable searchField(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(RADIUS_FIELD_DP));
        d.setColor(theme().color(Theme.SURFACE));
        d.setStroke(dp(focused ? 2 : 1), focused ? accent() : stroke());
        return d;
    }

    /** 空状态占位：与卡片同套主题色，不引入新色值。 */
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

    private int dp(float value) {
        return Math.round(value * mAct.getResources().getDisplayMetrics().density);
    }
}
