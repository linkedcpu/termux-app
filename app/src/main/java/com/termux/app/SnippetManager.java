package com.termux.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.termux.R;
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
 *   ~/.termux/snippets.json  形态：["cmd1","cmd2"]
 *
 *  ★ 卡片一行：左边命令（超长 … 截断）+ 右边 [▶][⚑][🗑]，按钮钉死右边缘
 *      · 单击卡片 = 插入命令（不执行、不关面板）
 *      · 长按卡片 = 面板内展开完整命令（140dp 可滚 / 复制），再长按收起
 *      · 底部 ＋ / 回车 = 收藏输入框内容；有字 ✕ 清空，空 ▼ 关面板
 *      · 搜索 = 实时按命令原文过滤，空输入还原全量
 *  ★ 置顶 / 删除：全部按 cmd 字符串定位，搜素、乱序、过滤场景下都精准
 *  ★ 删除落盘：mDeleted 黑名单，删过的绝不从磁盘复活
 *  ★ 只调用 TerminalSession 公开 API：write(String) / isRunning()
 *  ★ 文件：纯字符串数组，无 name、无对象；外部改完重开面板即生效
 *  ★ 判重只看 cmd 全文 → ls / ls -a / ls -l 互不冲突
 */
public final class SnippetManager {

    private static final String TAG = "SnippetManager";
    private static final String FILE = "snippets.json";
    /** 卡片上命令显示上限（字符，非字节），超长才加 … */
    private static final int CMD_DISP_MAX = 30;
    /** 唯一真身：Termux 标准 home，和终端 pwd 一致，不碰 $HOME 环境变量 */
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";

    private final TermuxActivity mAct;
    /** 内存列表：每条只装 cmd */
    private final List<CmdItem> mList = new ArrayList<>();

    /** ★ 本次会话里被删掉的 cmd：落盘时坚决不补回来 */
    private final Set<String> mDeleted = new HashSet<>();

    private View mPanel;
    private LinearLayout mListBox;

    /** 底部输入栏 */
    private EditText         mBottomInput;
    private TextView         mBtnPlus;   // ＋ 收藏
    private TextView         mBtnClear;  // 有字=✕，空=▼
    private GradientDrawable mBottomBg;

    private boolean mExpanded = false;
    /** 当前长按展开的那条在【全列表 mList】里的真实下标；-1=没展开 */
    private int     mViewingIdx = -1;

    /** 内存里的收藏项：只有命令，没有名字 */
    public static final class CmdItem {
        public final String cmd;
        public CmdItem(String cmd) { this.cmd = cmd == null ? "" : cmd.trim(); }
    }

    /* ---------- 调色 ---------- */
    private int bg()      { return color(R.color.snippet_bg,      0xFF12141a); }
    private int surface() { return color(R.color.snippet_surface, 0xFF1c1f27); }
    private int stroke()  { return color(R.color.snippet_stroke,  0xFF2b303b); }
    private int text()    { return 0xFFe7eaef; }
    private int sub()     { return 0xFF8e97a5; }
    private int accent()  { return color(R.color.snippet_accent,  0xFF5ee0a0); }
    private int danger()  { return 0xFFe05e5e; }

    public SnippetManager(TermuxActivity act) { mAct = act; migrateLegacy(); load(); }

    /* ================= 对外 ================= */

    public View buildPanel() {
        int dp = dp(1);
        Context ctx = mAct;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(panel(bg(), stroke(), dp(14)));
        root.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.setVisibility(View.GONE);
        root.setFocusableInTouchMode(true);
        mPanel = root;

        /* 列表：吃满剩余空间 */
        mListBox = new LinearLayout(ctx);
        mListBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(mListBox, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        /* 底部一行：输入框 + ＋ + ✕/▼ */
        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(4), dp(3), dp(4), dp(3));
        mBottomBg = searchField(false);
        bottom.setBackground(mBottomBg);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        mBottomInput = new EditText(ctx);
        mBottomInput.setHint("搜索或输入命令，点 ＋ 收藏");
        mBottomInput.setTextSize(13);
        mBottomInput.setTextColor(text());
        mBottomInput.setHintTextColor(sub());
        mBottomInput.setSingleLine(true);
        mBottomInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mBottomInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mBottomInput.setBackground(null);
        mBottomInput.setPadding(dp(10), dp(8), dp(8), dp(8));

        mBottomInput.setOnFocusChangeListener((v, has) -> {
            if (mBottomBg != null)
                mBottomBg.setStroke(dp(has ? 2 : 1), has ? accent() : stroke());
            syncBottomButtons();
        });
        mBottomInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { syncBottomButtons(); render(); }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
        });
        mBottomInput.setOnEditorActionListener((v, id, e) -> { commitBottomInput(); return true; });

        bottom.addView(mBottomInput, new LinearLayout.LayoutParams(0, -2, 1f));

        mBtnPlus  = tinyBtn("＋", accent(), v -> commitBottomInput());
        mBtnClear = tinyBtn("▼", sub(),   v -> {
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

    /** 每次展开都重读盘：外部改完重开即生效 */
    public void expand() {
        mExpanded = true;
        if (mPanel != null) { load(); mViewingIdx = -1; syncBottomButtons(); render(); mPanel.setVisibility(View.VISIBLE); }
    }
    public void collapse() {
        mExpanded = false; mViewingIdx = -1;
        if (mPanel == null) return;
        if (mBottomInput != null) mBottomInput.setText("");
        mPanel.clearFocus(); mPanel.setVisibility(View.GONE);
    }
    public boolean isExpanded() { return mExpanded; }
    public void save() { saveSnippets(); }

    /* ================= 底部栏 ================= */

    private void syncBottomButtons() {
        boolean has = mBottomInput != null && !mBottomInput.getText().toString().isEmpty();
        if (mBtnPlus  != null) mBtnPlus.setVisibility(View.VISIBLE);
        if (mBtnClear != null) {
            mBtnClear.setText(has ? "✕" : "▼");
            mBtnClear.setContentDescription(has ? "清空" : "关闭");
        }
    }

    /** ＋ / 回车：只收藏，不插入不执行 */
    private void commitBottomInput() {
        if (mBottomInput == null) return;
        String t = mBottomInput.getText().toString().trim();
        if (t.isEmpty()) { mAct.showToast("输入点命令再点 ＋ 收藏～", false); return; }
        addSnippet(t);
        mBottomInput.setText("");
        render();
    }

    /* ================= 渲染：命令与按钮同行、等高 ================= */

    private void render() {
        if (mListBox == null) return;
        int dp = dp(1);
        mListBox.removeAllViews();

        // ★ 搜索：实时按命令原文过滤；空输入 = 全量
        String q = (mBottomInput == null ? "" : mBottomInput.getText().toString())
            .trim().toLowerCase();
        List<CmdItem> show = new ArrayList<>();
        for (CmdItem c : mList)
            if (q.isEmpty() || c.cmd.toLowerCase().contains(q)) show.add(c);

        if (show.isEmpty()) {
            mListBox.addView(empty(
                q.isEmpty() ? "📭  还没有收藏" : "🔎  没有匹配 「" + q + "」",
                q.isEmpty()
                    ? "输入命令，点右边 ＋ 收藏（太长会截断，长按看全）"
                    : "换个关键词试试"));
            return;
        }

        for (CmdItem item : show) {
            final boolean viewing = (mViewingIdx == indexOfCmd(item.cmd));

            LinearLayout card = new LinearLayout(mAct);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cardNormal(surface(), stroke()));
            card.setPadding(dp(12), dp(9), dp(10), dp(9));
            card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            /* ★ 第一行：命令（左，锁宽截断）+ 按钮（右，固定尺寸不漂移） */
            LinearLayout row = new LinearLayout(mAct);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            TextView cmd = new TextView(mAct);
            cmd.setText(dispCmd(item.cmd));
            cmd.setTextColor(text());
            cmd.setTextSize(12.5f);
            cmd.setTypeface(Typeface.MONOSPACE);
            cmd.setSingleLine(true);
            cmd.setEllipsize(TextUtils.TruncateAt.END);
            cmd.setHorizontallyScrolling(true);   // 禁止折行重测
            cmd.setMaxWidth(Integer.MAX_VALUE);    // 自然宽度锁死，weight 才稳
            cmd.setPadding(0, 0, dp(8), 0);
            row.addView(cmd, new LinearLayout.LayoutParams(0, -2, 1f)); // ★ 吃满命令区

            row.addView(fixedBtn("▶", accent(), v -> actionRun(item)));
            row.addView(fixedBtn("⚑", accent(), v -> actionPin(item)));
            row.addView(fixedBtn("🗑", danger(),  v -> actionDelete(item)));
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));

            /* 单击=插入；长按=展开完整命令 */
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> actionInsert(item));
            card.setOnLongClickListener(v -> { actionView(item); return true; });

            /* 展开区（限高 140dp，可滚） */
            if (viewing) card.addView(buildExpandBox(item),
                    new LinearLayout.LayoutParams(-1, -2));

            mListBox.addView(card, new LinearLayout.LayoutParams(-1, -2));
            mListBox.addView(spacer(mAct, dp(5)));
        }
    }

    /** 超长截断：尽量在单词边界断，末尾 … 即“长按看全”的提示 */
    private String dispCmd(String c) {
        if (c == null) return "";
        if (c.length() <= CMD_DISP_MAX) return c;
        String head = c.substring(0, CMD_DISP_MAX).trim();
        int sp = Math.max(head.lastIndexOf(' '), head.lastIndexOf('\t'));
        if (sp > CMD_DISP_MAX * 2 / 3) head = head.substring(0, sp).trim();
        return head + " …";
    }

    /** 长按：面板内展开完整命令 + 复制，最高 140dp */
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
        body.setText(item.cmd);          // ★ 完整命令，不截断
        body.setTextColor(text());
        body.setTextSize(12);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(0, dp(6), 0, 0);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        ScrollView scr = new ScrollView(mAct);
        scr.setVerticalScrollBarEnabled(true);
        scr.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(140)));
        scr.addView(body, new LinearLayout.LayoutParams(-1, -2));
        box.addView(scr, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    /* ================= 固定尺寸按钮：右边缘永不漂移 ================= */

    private TextView fixedBtn(String glyph, int color, View.OnClickListener onClick) {
        int dp = dp(1);
        int size = dp(26);                 // 触摸区 26dp 见方，三按钮等宽
        TextView b = new TextView(mAct);
        b.setText(glyph); b.setTextColor(color); b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true); b.setFocusable(true);
        b.setBackground(pill(surface(), dp(7)));
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(onClick);
        b.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: b.setBackground(pill(color, dp(7))); b.setTextColor(text()); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: b.setBackground(pill(surface(), dp(7))); b.setTextColor(color); break;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(4), 0, 0, 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        b.setLayoutParams(lp);
        return b;
    }

    private TextView tinyBtn(String glyph, int color, View.OnClickListener onClick) {
        int dp = dp(1);
        TextView b = new TextView(mAct);
        b.setText(glyph); b.setTextColor(color); b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true); b.setFocusable(true);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setOnClickListener(onClick);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) b.setAlpha(0.5f);
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) b.setAlpha(1f);
            return false;
        });
        return b;
    }

    /* ================= 动作（★ 下标安全 + 原版 API） ================= */

    /**
     * 会话是否可写：只走公开 API TerminalSession.isRunning()
     *   public synchronized boolean isRunning() { return mShellPid != -1; }
     * 不碰任何包私有字段，跨编译单元也不会报可见性错误。
     */
    private boolean sessionReady(TerminalSession s) {
        return s != null && s.isRunning();
    }

    /** 单击：只插入，不执行不关面板。write(String) 是原版 toolbar 同款 */
    private void actionInsert(CmdItem item) {
        TerminalSession cur = mAct.getCurrentSession();
        if (!sessionReady(cur)) { mAct.showToast("会话未就绪，先敲几下回车～", false); return; }
        cur.write(item.cmd);                 // ★ 原版 String 重载，UTF-8 与进程守卫都在 session 内
        mAct.showToast("已插入", false);
    }

    /** 运行：命令 + 换行。原版工具栏回车走 write("\r")，PTY 下 \n 等价 */
    private void actionRun(CmdItem item) {
        TerminalSession cur = mAct.getCurrentSession();
        if (!sessionReady(cur)) { mAct.showToast("会话未就绪，先敲几下回车～", false); return; }
        cur.write(item.cmd + "\n");          // ★ 同款 write(String)
        collapse();
        mAct.showToast("已运行", false);
    }

    /**
     * ★ 置顶：按 cmd 字符串在全列表里挪，搜素/过滤/乱序都精准，
     *   彻底避开“展示下标当全列表下标”的老坑。
     */
    private void actionPin(CmdItem target) {
        if (target == null) return;
        int idx = indexOfCmd(target.cmd);
        if (idx < 0) { render(); return; }
        CmdItem sn = mList.remove(idx);
        mList.add(0, sn);
        mViewingIdx = (mViewingIdx == idx) ? 0 : -1;
        save();
        render();
        mAct.showToast("已置顶：" + dispCmd(sn.cmd), false);
    }

    /**
     * ★ 删除：弹窗确认后才真删，按 cmd 找、不碰下标。
     *   同时进 mDeleted，save() 绝不会把它从磁盘救回来。
     */
    private void actionDelete(final CmdItem target) {
        if (target == null) return;
        final String cmdKey = target.cmd;   // ★ 抓字符串，弹窗回来时下标早变了

        new android.app.AlertDialog.Builder(mAct)
            .setTitle((CharSequence) dispCmd(cmdKey))
            .setMessage("删除这条收藏？")
            .setCancelable(true)
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int idx = indexOfCmd(cmdKey);
                    if (idx >= 0) {
                        mList.remove(idx);
                        mDeleted.add(cmdKey);          // ★ 记住：磁盘里有也不能复活
                        if (mViewingIdx == idx) mViewingIdx = -1;
                    }
                    save();
                    render();
                    mAct.showToast("已删除：" + dispCmd(cmdKey), false);
                    dialog.dismiss();
                }
            })
            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            })
            .show();
    }

    /** 长按：展开/收起，一次只看一条（按 cmd 定位，重绘也不丢） */
    private void actionView(CmdItem target) {
        if (target == null) return;
        int idx = indexOfCmd(target.cmd);
        if (idx < 0) { mViewingIdx = -1; render(); return; }
        mViewingIdx = (mViewingIdx == idx) ? -1 : idx;
        render();
        mAct.showToast(mViewingIdx < 0 ? "已收起" : "长按卡片可收起 · 拖滚动看全", false);
    }

    /** ★ 按命令全文查找，永远返回全列表里的真实下标 */
    private int indexOfCmd(String cmd) {
        if (cmd == null) return -1;
        for (int i = 0; i < mList.size(); i++)
            if (mList.get(i).cmd.equals(cmd)) return i;
        return -1;
    }

    /* ================= 数据：纯字符串数组，无 name ================= */

    private void addSnippet(String cmd) {
        String c = cmd.trim();
        if (c.isEmpty()) return;
        mDeleted.remove(c);                 // 删了又重新收藏 → 取消黑名单
        for (CmdItem s : mList)
            if (s.cmd.equals(c)) { mAct.showToast("已经收藏过了：" + dispCmd(c), false); return; }
        mList.add(0, new CmdItem(c));
        save();
        mAct.showToast("已收藏：" + dispCmd(c), false);
    }

    /** 读盘：["cmd1","cmd2"]；坏文件返回空，不崩 */
    private List<CmdItem> readAll(File f) {
        List<CmdItem> out = new ArrayList<>();
        if (f == null || !f.exists()) return out;
        try {
            String s = new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (s == null || s.trim().isEmpty()) return out;
            JSONArray arr = new JSONArray(s);                 // ← 可能抛
            for (int i = 0; i < arr.length(); i++) {
                String c = arr.optString(i, null);            // ★ 直接拿字符串，容错
                if (c != null && !c.trim().isEmpty() && !containsCmd(out, c.trim()))
                    out.add(new CmdItem(c.trim()));
            }
        } catch (Exception e) {
            Logger.logVerbose(TAG, "read skipped(" + f.getPath() + "): " + e.getMessage());
        }
        return out;
    }

    private void load() { mList.clear(); mList.addAll(readAll(file())); }

    /**
     * 原子落盘：
     *   1) 读磁盘当前数组，但剔除 mDeleted（删过的绝不复活）
     *   2) 内存里还活着的排最前，磁盘没被动的跟后面
     *   3) tmp → rename，失败兜底覆盖写
     *   写出：["cmd1","cmd2"]
     */
    private void saveSnippets() {
        File f = file();
        if (f == null) return;
        try {
            Map<String, CmdItem> disk = new LinkedHashMap<>();
            for (CmdItem c : readAll(f))
                if (c != null && !mDeleted.contains(c.cmd))      // ★ 删过的别救
                    disk.put(c.cmd, c);

            List<CmdItem> ordered = new ArrayList<>();
            for (CmdItem c : mList)
                if (c != null) { ordered.add(c); disk.remove(c.cmd); }
            ordered.addAll(disk.values());

            mList.clear();
            mList.addAll(ordered);

            JSONArray arr = new JSONArray();
            for (CmdItem c : mList) arr.put(c.cmd);              // ★ 只写字符串

            String payload = arr.toString(2);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) { w.write(payload); w.flush(); }
            if (!tmp.renameTo(f))
                Files.write(f.toPath(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            mDeleted.clear();   // 落盘成功再清黑名单
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(TAG, "save failed", e);
        }
    }

    private boolean containsCmd(List<CmdItem> list, String cmd) {
        for (CmdItem c : list) if (c.cmd.equals(cmd)) return true;
        return false;
    }

    /* ================= 路径（唯一真相） ================= */

    /** /data/data/com.termux/files/home/.termux/snippets.json，与终端 pwd 一致 */
    private File file() {
        File dir = new File(TERMUX_HOME, ".termux");
        dir.mkdirs();
        return new File(dir, FILE);
    }

    /**
     * 一次性搬家：把旧路径里【对象格式】{"name","cmd"} 提取成纯 cmd 数组，
     * 迁移成功后删掉旧文件。只跑一次，之后全程纯字符串。
     */
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
            for (CmdItem c : readAll(target)) cmds.put(c.cmd, c.cmd);   // 新格式优先

            for (File lf : legacy) {
                if (!lf.exists()) continue;
                try {
                    String s = new String(Files.readAllBytes(lf.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    if (s == null || s.trim().isEmpty()) continue;
                    JSONArray arr = new JSONArray(s);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);             // 旧对象格式
                        if (o == null) {
                            String c = arr.optString(i, "").trim();     // 已是纯字符串
                            if (!c.isEmpty()) cmds.putIfAbsent(c, c);
                        } else {
                            String c = o.optString("cmd", "").trim();
                            if (c.isEmpty()) c = o.optString("name", "").trim();
                            if (!c.isEmpty()) cmds.putIfAbsent(c, c);
                        }
                    }
                } catch (JSONException ignore) { /* 读坏文件静默跳过 */ }
                changed = true;
            }

            if (changed && !cmds.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String c : cmds.values()) arr.put(c);              // ★ 落成纯字符串
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

    private GradientDrawable panel(int fill, int st, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(r);
        d.setColor(fill); d.setStroke(dp1(), st); return d;
    }
    private GradientDrawable searchField(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(10));
        d.setColor(bg()); d.setStroke(dp(focused ? 2 : 1), focused ? accent() : stroke()); return d;
    }
    private GradientDrawable cardNormal(int fill, int st) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(10));
        d.setColor(fill); d.setStroke(dp1(), st); return d;
    }
    private GradientDrawable pill(int fill, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(r); d.setColor(fill); return d;
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
    private View spacer(Context c, int h) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(-1, h)); return v; }

    private int dp(int dp) { return Math.round(dp * mAct.getResources().getDisplayMetrics().density); }
    private int dp1() { return Math.max(1, dp(1)); }
    private int color(int res, int fb) { try { return ContextCompat.getColor(mAct, res); } catch (Exception e) { return fb; } }
}
