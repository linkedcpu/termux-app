package com.termux.view.textselection;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.WcWidth;
import com.termux.view.R;
import com.termux.view.TerminalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class TextSelectionCursorController implements CursorController {

    private final TerminalView terminalView;
    private final TextSelectionHandleView mStartHandle, mEndHandle;
    private String mStoredSelectedText;
    private boolean mIsSelectingText = false;
    private long mShowStartTime = System.currentTimeMillis();

    private final int mHandleHeight;
    private int mSelX1 = -1, mSelX2 = -1, mSelY1 = -1, mSelY2 = -1;

    private ActionMode mActionMode;

    public final int ACTION_COPY = 1;
    public final int ACTION_PASTE = 2;
    public final int ACTION_MORE = 3;
    public static final int ACTION_TRANSLATE = 4;
    public static final int ACTION_INSERT = 5;

    // ===== Bergamot 配置 =====
    private static final String HOME =
            "/data/data/com.termux/files/home";
    private static final String BERGAMOT_DIR =
            HOME + "/.local/share/bergamot";
    private static final String BERGAMOT_BIN =
            BERGAMOT_DIR + "/bin/bergamot";
    private static final String BERGAMOT_SYMLINK =
            "/data/data/com.termux/files/usr/bin/bergamot";
    private static final String MODELS_DIR =
            BERGAMOT_DIR + "/models";

    private static final Pattern CHINESE_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");

    public TextSelectionCursorController(TerminalView terminalView) {
        this.terminalView = terminalView;
        mStartHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT);
        mEndHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT);
        mHandleHeight = Math.max(mStartHandle.getHandleHeight(), mEndHandle.getHandleHeight());
    }

    @Override
    public void show(MotionEvent event) {
        setInitialTextSelectionPosition(event);
        mStartHandle.positionAtCursor(mSelX1, mSelY1, true);
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, true);
        setActionModeCallBacks();
        mShowStartTime = System.currentTimeMillis();
        mIsSelectingText = true;
    }

    @Override
    public boolean hide() {
        if (!isActive()) return false;
        if (System.currentTimeMillis() - mShowStartTime < 300) return false;

        mStartHandle.hide();
        mEndHandle.hide();
        if (mActionMode != null) mActionMode.finish();

        mSelX1 = mSelY1 = mSelX2 = mSelY2 = -1;
        mIsSelectingText = false;
        return true;
    }

    @Override
    public void render() {
        if (!isActive()) return;
        mStartHandle.positionAtCursor(mSelX1, mSelY1, false);
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, false);
        if (mActionMode != null) mActionMode.invalidate();
    }

    public void setInitialTextSelectionPosition(MotionEvent event) {
        int[] columnAndRow = terminalView.getColumnAndRow(event, true);
        mSelX1 = mSelX2 = columnAndRow[0];
        mSelY1 = mSelY2 = columnAndRow[1];

        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        if (!" ".equals(screen.getSelectedText(mSelX1, mSelY1, mSelX1, mSelY1))) {
            while (mSelX1 > 0 && !"".equals(screen.getSelectedText(mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1)))
                mSelX1--;
            while (mSelX2 < terminalView.mEmulator.mColumns - 1 && !"".equals(screen.getSelectedText(mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1)))
                mSelX2++;
        }
    }

    public void setActionModeCallBacks() {
        final ActionMode.Callback callback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                int show = MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
                ClipboardManager clipboard = (ClipboardManager)
                        terminalView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);

                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, R.string.copy_text).setShowAsAction(show);
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, R.string.paste_text)
                        .setEnabled(clipboard != null && clipboard.hasPrimaryClip()).setShowAsAction(show);
                menu.add(Menu.NONE, ACTION_TRANSLATE, Menu.NONE, "翻译").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                menu.add(Menu.NONE, ACTION_INSERT, Menu.NONE, "插入").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                menu.add(Menu.NONE, ACTION_MORE, Menu.NONE, R.string.text_selection_more);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                if (menu.findItem(ACTION_INSERT) != null) {
                    boolean hasSelection = !TextUtils.isEmpty(getSelectedText());
                    menu.findItem(ACTION_INSERT).setEnabled(hasSelection);
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (!isActive()) return true;

                switch (item.getItemId()) {
                    case ACTION_COPY:
                        terminalView.mTermSession.onCopyTextToClipboard(getSelectedText());
                        terminalView.stopTextSelectionMode();
                        break;

                    case ACTION_PASTE:
                        terminalView.stopTextSelectionMode();
                        terminalView.mTermSession.onPasteTextFromClipboard();
                        break;

                    case ACTION_MORE:
                        mStoredSelectedText = getSelectedText();
                        terminalView.stopTextSelectionMode();
                        terminalView.showContextMenu();
                        break;

                    case ACTION_TRANSLATE:
                        String text = getSelectedText();
                        if (text != null && !text.trim().isEmpty()) {
                            File bin = new File(BERGAMOT_BIN);
                            if (!bin.exists() || !bin.canExecute()) {
                                new Handler(Looper.getMainLooper()).post(() ->
                                        Toast.makeText(terminalView.getContext(),
                                                "首次翻译需初始化，请稍候...", Toast.LENGTH_SHORT).show()
                                );
                            }
                            translateWithBergamot(text.trim());
                        }
                        terminalView.stopTextSelectionMode();
                        break;

                    case ACTION_INSERT:
                        String insertText = getSelectedText();
                        if (!TextUtils.isEmpty(insertText)
                                && terminalView.mTermSession != null) {
                            terminalView.mTermSession.write(insertText);
                        }
                        terminalView.stopTextSelectionMode();
                        break;
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        };

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mActionMode = terminalView.startActionMode(callback);
            return;
        }

        mActionMode = terminalView.startActionMode(new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return callback.onPrepareActionMode(mode, menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return callback.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                callback.onDestroyActionMode(mode);
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                int x1 = Math.round(mSelX1 * terminalView.mRenderer.getFontWidth());
                int x2 = Math.round(mSelX2 * terminalView.mRenderer.getFontWidth());
                int y1 = Math.round((mSelY1 - 1 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing());
                int y2 = Math.round((mSelY2 + 1 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing());
                if (x1 > x2) {
                    int tmp = x1;
                    x1 = x2;
                    x2 = tmp;
                }
                int terminalBottom = terminalView.getBottom();
                int top = y1 + mHandleHeight;
                int bottom = y2 + mHandleHeight;
                if (top > terminalBottom) top = terminalBottom;
                if (bottom > terminalBottom) bottom = terminalBottom;
                outRect.set(x1, top, x2, bottom);
            }
        }, ActionMode.TYPE_FLOATING);
    }

    // ===== Bergamot 相关 =====

    private boolean ensureBergamotInstalled() {
        Context ctx = terminalView.getContext();
        File bin = new File(BERGAMOT_BIN);

        try {
            if (bin.exists() && bin.canExecute()) {
                ensureSymlink();
                return true;
            }

            File destDir = new File(BERGAMOT_DIR);
            extractAssetDir(ctx.getAssets(), "bergamot", destDir);
            bin.setExecutable(true, false);
            ensureSymlink();
            return bin.exists() && bin.canExecute();

        } catch (Exception e) {
            Log.e("Bergamot", "ensureBergamotInstalled failed", e);
            return false;
        }
    }

    private void ensureSymlink() {
        File symlink = new File(BERGAMOT_SYMLINK);
        if (!symlink.exists()) {
            try {
                new ProcessBuilder("ln", "-sf", BERGAMOT_BIN, BERGAMOT_SYMLINK)
                        .start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    private void extractAssetDir(android.content.res.AssetManager am,
                                String assetPath, File destDir) throws Exception {
        String[] list = am.list(assetPath);
        if (list == null || list.length == 0) {
            destDir.getParentFile().mkdirs();
            try (InputStream is = am.open(assetPath);
                 FileOutputStream fos = new FileOutputStream(destDir)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            }
            return;
        }
        destDir.mkdirs();
        for (String f : list) {
            extractAssetDir(am, assetPath + "/" + f, new File(destDir, f));
        }
    }

    private boolean isChinese(String text) {
        return CHINESE_PATTERN.matcher(text).find();
    }

    private void translateWithBergamot(String text) {
        Context ctx = terminalView.getContext();

        if (!ensureBergamotInstalled()) {
            showDialog(ctx, "翻译模块未就绪，请确认 APK 包含 bergamot");
            return;
        }

        new Thread(() -> {
            Process process = null;
            try {
                boolean toChinese = isChinese(text);
                String configFile = toChinese ? "zhen.yml" : "enzh.yml";
                File config = new File(MODELS_DIR + "/" + (toChinese ? "zhen" : "enzh"), configFile);
                if (!config.exists()) {
                    showDialog(ctx, "模型配置不存在:\n" + config.getAbsolutePath());
                    return;
                }

                // ✅ 核心改动：login -c 走 Termux 执行环境
                String innerCmd = "exec bergamot --model-config-paths '"
                        + config.getAbsolutePath().replace("'", "'\\''") + "'";

                List<String> cmd = new ArrayList<>();
                cmd.add("/data/data/com.termux/files/usr/bin/login");
                cmd.add("-c");
                cmd.add(innerCmd);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                Map<String, String> env = pb.environment();
                env.clear();
                env.put("HOME", "/data/data/com.termux/files/home");
                env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin");
                pb.directory(new File("/data/data/com.termux/files/home"));
                pb.redirectErrorStream(true);

                process = pb.start();

                // 往 stdin 写待翻译文本
                try (PrintWriter pw = new PrintWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    pw.println(text);
                }

                // 读输出
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }

                boolean finished = false;
                try {
                    finished = process.waitFor(30, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Log.e("Bergamot", "waitFor interrupted", ie);
                }

                if (!finished) {
                    process.destroy();
                    showDialog(ctx, "翻译超时（30s）");
                    return;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    showDialog(ctx, "翻译失败 (exit=" + exitCode + "):\n" + sb.toString());
                    return;
                }

                final String translation = sb.toString().trim();
                if (translation.isEmpty()) {
                    showDialog(ctx, "(无返回结果，可能模型未加载)");
                    return;
                }

                showResultDialog(ctx, translation, text);

            } catch (Exception e) {
                Log.e("Bergamot", "translation failed", e);
                showDialog(ctx, "翻译失败：" + e.getMessage());
            } finally {
                if (process != null) {
                    try {
                        process.destroy();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    private void showResultDialog(Context ctx, final String translation, final String original) {
        new Handler(Looper.getMainLooper()).post(() ->
                new android.app.AlertDialog.Builder(ctx)
                        .setTitle("翻译结果")
                        .setMessage(translation)
                        .setPositiveButton("复制", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager)
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setText(translation);
                        })
                        .setNeutralButton("插入终端", (d, w) -> {
                            if (terminalView.mTermSession != null) {
                                terminalView.mTermSession.write(translation);
                            }
                        })
                        .setNegativeButton("关闭", null)
                        .show()
        );
    }

    private void showDialog(Context ctx, final String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                new android.app.AlertDialog.Builder(ctx)
                        .setTitle("翻译")
                        .setMessage(message)
                        .setPositiveButton("关闭", null)
                        .show()
        );
    }

    @Override
    public void updatePosition(TextSelectionHandleView handle, int x, int y) {
        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        final int scrollRows = screen.getActiveRows() - terminalView.mEmulator.mRows;

        if (handle == mStartHandle) {
            mSelX1 = terminalView.getCursorX(x);
            mSelY1 = terminalView.getCursorY(y);
            if (mSelX1 < 0) mSelX1 = 0;
            if (mSelY1 < -scrollRows) mSelY1 = -scrollRows;
            if (mSelY1 > terminalView.mEmulator.mRows - 1) mSelY1 = terminalView.mEmulator.mRows - 1;
            if (mSelY1 > mSelY2) mSelY1 = mSelY2;
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) mSelX1 = mSelX2;
            mSelX1 = getValidCurX(screen, mSelY1, mSelX1);
        } else {
            mSelX2 = terminalView.getCursorX(x);
            mSelY2 = terminalView.getCursorY(y);
            if (mSelX2 < 0) mSelX2 = 0;
            if (mSelY2 < -scrollRows) mSelY2 = -scrollRows;
            if (mSelY2 > terminalView.mEmulator.mRows - 1) mSelY2 = terminalView.mEmulator.mRows - 1;
            if (mSelY1 > mSelY2) mSelY2 = mSelY1;
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) mSelX2 = mSelX1;
            mSelX2 = getValidCurX(screen, mSelY2, mSelX2);
        }
        terminalView.invalidate();
    }

    private int getValidCurX(TerminalBuffer screen, int cy, int cx) {
        String line = screen.getSelectedText(0, cy, cx, cy);
        if (!TextUtils.isEmpty(line)) {
            int col = 0;
            for (int i = 0; i < line.length(); ) {
                char ch = line.charAt(i);
                int wc;
                if (Character.isHighSurrogate(ch) && i + 1 < line.length()) {
                    wc = WcWidth.width(Character.toCodePoint(ch, line.charAt(++i)));
                } else {
                    wc = WcWidth.width(ch);
                }
                int next = col + wc;
                if (cx >= col && cx <= next) return next;
                col = next;
                i++;
            }
        }
        return cx;
    }

    public void decrementYTextSelectionCursors(int decrement) {
        mSelY1 -= decrement;
        mSelY2 -= decrement;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public void onTouchModeChanged(boolean isInTouchMode) {
        if (!isInTouchMode) terminalView.stopTextSelectionMode();
    }

    @Override
    public void onDetached() {
    }

    @Override
    public boolean isActive() {
        return mIsSelectingText;
    }

    public void getSelectors(int[] sel) {
        if (sel == null || sel.length != 4) return;
        sel[0] = mSelY1;
        sel[1] = mSelY2;
        sel[2] = mSelX1;
        sel[3] = mSelX2;
    }

    public String getSelectedText() {
        return terminalView.mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2);
    }

    @Nullable
    public String getStoredSelectedText() {
        return mStoredSelectedText;
    }

    public void unsetStoredSelectedText() {
        mStoredSelectedText = null;
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    public boolean isSelectionStartDragged() {
        return mStartHandle.isDragging();
    }

    public boolean isSelectionEndDragged() {
        return mEndHandle.isDragging();
    }
}
