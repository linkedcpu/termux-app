package com.termux.view.textselection;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.termux.view.R;
import com.termux.view.TerminalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Bergamot 翻译功能的独立封装。
 * 负责：安装/解压 bergamot 资源、创建符号链接、调用 Termux 环境执行翻译、
 * 以及结果展示。从 TextSelectionCursorController 中拆分出来以降低耦合。
 *
 * 说明：面向用户的文案、对话框标题/按钮、日志标签已抽取到 res/values/strings.xml；
 * 逻辑相关的路径、shell 命令等仍保留为代码常量，不做资源化。
 */
public class BergamotTranslator {

    // ===== Bergamot 配置（逻辑常量，不资源化） =====
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

    private final TerminalView terminalView;

    public BergamotTranslator(TerminalView terminalView) {
        this.terminalView = terminalView;
    }

    private boolean isChinese(String text) {
        return CHINESE_PATTERN.matcher(text).find();
    }

    /** 对外入口：翻译给定文本。 */
    public void translate(String text) {
        Context ctx = terminalView.getContext();

        if (!ensureBergamotInstalled()) {
            showDialog(ctx, ctx.getString(R.string.bergamot_err_not_ready));
            return;
        }

        new Thread(() -> {
            Process process = null;
            try {
                boolean toChinese = isChinese(text);
                String configFile = toChinese ? "zhen.yml" : "enzh.yml";
                File config = new File(MODELS_DIR + "/" + (toChinese ? "zhen" : "enzh"), configFile);
                if (!config.exists()) {
                    showDialog(ctx, ctx.getString(
                            R.string.bergamot_err_config_missing,
                            config.getAbsolutePath()));
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
                    Log.e(ctx.getString(R.string.bergamot_log_tag),
                            ctx.getString(R.string.bergamot_log_waitfor_interrupted), ie);
                }

                if (!finished) {
                    process.destroy();
                    showDialog(ctx, ctx.getString(R.string.bergamot_err_timeout));
                    return;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    showDialog(ctx, ctx.getString(
                            R.string.bergamot_err_exit, exitCode, sb.toString()));
                    return;
                }

                final String translation = sb.toString().trim();
                if (translation.isEmpty()) {
                    showDialog(ctx, ctx.getString(R.string.bergamot_err_empty));
                    return;
                }

                showResultDialog(ctx, translation);

            } catch (Exception e) {
                Log.e(ctx.getString(R.string.bergamot_log_tag),
                        ctx.getString(R.string.bergamot_log_translation_failed), e);
                showDialog(ctx, ctx.getString(R.string.bergamot_err_failed, e.getMessage()));
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
            Log.e(ctx.getString(R.string.bergamot_log_tag),
                    ctx.getString(R.string.bergamot_log_ensure_failed), e);
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

    private void extractAssetDir(AssetManager am,
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

    private void showResultDialog(Context ctx, final String translation) {
        new Handler(Looper.getMainLooper()).post(() ->
                new AlertDialog.Builder(ctx)
                        .setTitle(R.string.bergamot_dialog_title_translation)
                        .setMessage(translation)
                        .setPositiveButton(R.string.bergamot_btn_copy, (d, w) -> {
                            ClipboardManager cm = (ClipboardManager)
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setText(translation);
                        })
                        .setNeutralButton(R.string.bergamot_btn_insert, (d, w) -> {
                            if (terminalView.mTermSession != null) {
                                terminalView.mTermSession.write(translation);
                            }
                        })
                        .setNegativeButton(R.string.bergamot_btn_close, null)
                        .show()
        );
    }

    private void showDialog(Context ctx, final String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                new AlertDialog.Builder(ctx)
                        .setTitle(R.string.bergamot_dialog_title_hint)
                        .setMessage(message)
                        .setPositiveButton(R.string.bergamot_btn_close, null)
                        .show()
        );
    }
}
