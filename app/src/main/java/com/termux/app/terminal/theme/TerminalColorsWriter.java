package com.termux.app.terminal.theme;

import android.content.Context;
import android.content.Intent;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 终端配色联动：界面主题 → ~/.termux/colors.properties。
 * 单向：不反向读取终端配色，避免 16 色映射丢失。
 *
 * ★ 写入路径必须是 $HOME/.termux/colors.properties
 *   （= /data/data/com.termux/files/home/.termux/colors.properties）
 * ★ 写完后必须广播 ACTION_RELOAD_STYLE，否则已开会话不刷新
 */
public final class TerminalColorsWriter {

    private static final String TAG = "TerminalColorsWriter";
    private static final String DIR = ".termux";
    private static final String FILE = "colors.properties";

    private TerminalColorsWriter() {}

    /**
     * 内置方案：dark / light / warm / ink-blue / ubuntu /
     *          apple-light / apple-dark / windows-light / windows-dark / windows-xp。
     * 也可把 name 换成你自己的 .termux/colors/ 文件名。
     *
     * @return 是否实际写入并触发了重载
     */
    public static boolean apply(Context ctx, ThemePreset preset) {
        if (ctx == null || preset == null) return false;

        String name = preset.terminalScheme;
        if (name == null || name.trim().isEmpty()) {
            Logger.logVerbose(TAG, "terminalScheme 未设置，跳过终端配色联动");
            return false;
        }

        String body = scheme(name.trim());
        if (body == null) {
            Logger.logWarn(TAG, "未知终端配色方案: " + name);
            return false;
        }

        File dir = homeDir();
        if (dir == null) return false;

        try {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, FILE);
            File tmp = new File(dir, FILE + ".tmp");

            FileOutputStream fos = new FileOutputStream(tmp, false);
            try {
                fos.write(body.getBytes("UTF-8"));
                fos.flush();
            } finally {
                fos.close();
            }

            if (!tmp.renameTo(out)) {
                // 跨文件系统时 rename 会失败，退回直接写
                FileOutputStream fos2 = new FileOutputStream(out, false);
                try {
                    fos2.write(body.getBytes("UTF-8"));
                    fos2.flush();
                } finally {
                    fos2.close();
                }
            }

            Logger.logInfoAndShowToast(ctx, TAG, "终端配色已同步：" + name);
            reloadTerminalStyle(ctx);
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(TAG, "写入 colors.properties 失败", e);
            return false;
        }
    }

    /** ★ $HOME/.termux —— Termux 真正读取的目录 */
    private static File homeDir() {
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty()) {
            home = "/data/data/com.termux/files/home";
        }
        return new File(home, DIR);
    }

    /** ★ 让已开会话重读配色，否则只有新会话才变 */
    private static void reloadTerminalStyle(Context ctx) {
        try {
            Intent i = new Intent(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
                    .ACTION_RELOAD_STYLE);
            i.putExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
                    .EXTRA_RECREATE_ACTIVITY, false);
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            Logger.logVerbose(TAG, "reload broadcast skipped: " + t.getMessage());
        }
    }

    private static String scheme(String name) {
        switch (name) {
            case "dark":
                return ""
                        + "background=#1e1f22\nforeground=#e6e6e6\ncursor=#e6e6e6\n"
                        + "color0=#1e1f22\ncolor1=#ff6b6b\ncolor2=#5cc97a\ncolor3=#e0c060\n"
                        + "color4=#6cb6ff\ncolor5=#c084fc\ncolor6=#67d6d6\ncolor7=#e6e6e6\n"
                        + "color8=#6b6c70\ncolor9=#ff8a8a\ncolor10=#7fd89a\ncolor11=#ecd07a\n"
                        + "color12=#8cc6ff\ncolor13=#d3a0fb\ncolor14=#8ae0e0\ncolor15=#ffffff\n";
            case "light":
                return ""
                        + "background=#f7f7f5\nforeground=#1f2328\ncursor=#1f2328\n"
                        + "color0=#3a3d42\ncolor1=#c0392b\ncolor2=#2f7d46\ncolor3=#8a6d1f\n"
                        + "color4=#24567f\ncolor5=#7d3f8f\ncolor6=#1f6f6f\ncolor7=#5c6169\n"
                        + "color8=#8a8f95\ncolor9=#d9534f\ncolor10=#3a8b52\ncolor11=#b8860b\n"
                        + "color12=#34618f\ncolor13=#9b59b6\ncolor14=#2e8b8b\ncolor15=#1f2328\n";
            case "warm":
                return ""
                        + "background=#241f1c\nforeground=#f0e8e0\ncursor=#f0e8e0\n"
                        + "color0=#241f1c\ncolor1=#e07a5f\ncolor2=#8fae7e\ncolor3=#e9c46a\n"
                        + "color4=#6f9bb5\ncolor5=#c08ca0\ncolor6=#7fb0a6\ncolor7=#f0e8e0\n"
                        + "color8=#8a7d72\ncolor9=#ec9378\ncolor10=#a3bf93\ncolor11=#f0d385\n"
                        + "color12=#86b0c9\ncolor13=#d1a0b2\ncolor14=#9ac3b9\ncolor15=#ffffff\n";
            case "ink-blue":
                return ""
                        + "background=#17202a\nforeground=#e3ecf3\ncursor=#6cb6ff\n"
                        + "color0=#17202a\ncolor1=#ff7a7a\ncolor2=#6fd08a\ncolor3=#e6c46a\n"
                        + "color4=#6cb6ff\ncolor5=#b79cff\ncolor6=#66cfd6\ncolor7=#e3ecf3\n"
                        + "color8=#6c7a88\ncolor9=#ff9c9c\ncolor10=#8adaa0\ncolor11=#f0d385\n"
                        + "color12=#8cc6ff\ncolor13=#c9b0ff\ncolor14=#8ae0e0\ncolor15=#ffffff\n";

            /* ★ Ubuntu 经典终端配色（Tango / Ubuntu default） */
            case "ubuntu":
                return ""
                        + "background=#300a24\nforeground=#eeeeec\ncursor=#eeeeec\n"
                        + "color0=#2e3436\ncolor1=#cc0000\ncolor2=#4e9a06\ncolor3=#c4a000\n"
                        + "color4=#3465a4\ncolor5=#75507b\ncolor6=#06989a\ncolor7=#d3d7cf\n"
                        + "color8=#555753\ncolor9=#ef2929\ncolor10=#8ae234\ncolor11=#fce94f\n"
                        + "color12=#729fcf\ncolor13=#ad7fa8\ncolor14=#34e2e2\ncolor15=#eeeeec\n";

            /* ★ Apple 终端配色：浅色 = 系统白底 + 系统蓝/语义色 */
            case "apple-light":
                return ""
                        + "background=#ffffff\nforeground=#1d1d1f\ncursor=#007aff\n"
                        + "color0=#1d1d1f\ncolor1=#ff3b30\ncolor2=#34c759\ncolor3=#ff9500\n"
                        + "color4=#007aff\ncolor5=#af52de\ncolor6=#5ac8fa\ncolor7=#6e6e73\n"
                        + "color8=#86868b\ncolor9=#ff453a\ncolor10=#30d158\ncolor11=#ff9f0a\n"
                        + "color12=#0a84ff\ncolor13=#bf5af2\ncolor14=#64d2ff\ncolor15=#000000\n";

            /* ★ Apple 终端配色：深色 = OLED 黑底 + 深模态蓝/语义色 */
            case "apple-dark":
                return ""
                        + "background=#000000\nforeground=#f5f5f7\ncursor=#0a84ff\n"
                        + "color0=#1c1c1e\ncolor1=#ff453a\ncolor2=#30d158\ncolor3=#ff9f0a\n"
                        + "color4=#0a84ff\ncolor5=#bf5af2\ncolor6=#64d2ff\ncolor7=#a1a1a6\n"
                        + "color8=#48484a\ncolor9=#ff6961\ncolor10=#4cd964\ncolor11=#ffcc00\n"
                        + "color12=#64d2ff\ncolor13=#d668f0\ncolor14=#7ee0ea\ncolor15=#ffffff\n";

            /* ★ Windows 终端配色：浅色 = CMD/VS Code 浅色风 */
            case "windows-light":
                return ""
                        + "background=#ffffff\nforeground=#0a0a0a\ncursor=#0067c0\n"
                        + "color0=#0a0a0a\ncolor1=#c50f1f\ncolor2=#13a10e\ncolor3=#c19c00\n"
                        + "color4=#0037da\ncolor5=#881798\ncolor6=#3a96dd\ncolor7=#767676\n"
                        + "color8=#595959\ncolor9=#e74856\ncolor10=#16c60c\ncolor11=#f9f1a5\n"
                        + "color12=#3b78ff\ncolor13=#b4009e\ncolor14=#61d6d6\ncolor15=#000000\n";

            /* ★ Windows 终端配色：深色 = Windows Terminal 默认深色（Campbell 变体） */
            case "windows-dark":
                return ""
                        + "background=#0c0c0c\nforeground=#cccccc\ncursor=#4cc2ff\n"
                        + "color0=#0c0c0c\ncolor1=#c50f1f\ncolor2=#13a10e\ncolor3=#c19c00\n"
                        + "color4=#0037da\ncolor5=#881798\ncolor6=#3a96dd\ncolor7=#cccccc\n"
                        + "color8=#767676\ncolor9=#e74856\ncolor10=#16c60c\ncolor11=#f9f1a5\n"
                        + "color12=#3b78ff\ncolor13=#b4009e\ncolor14=#61d6d6\ncolor15=#f2f2f2\n";

            /* ★ Windows XP 终端配色：经典 CMD 黑底白字 + XP 控制台默认 16 色 */
            case "windows-xp":
                return ""
                        + "background=#000000\nforeground=#ffffff\ncursor=#ffffff\n"
                        + "color0=#000000\ncolor1=#800000\ncolor2=#008000\ncolor3=#808000\n"
                        + "color4=#000080\ncolor5=#800080\ncolor6=#008080\ncolor7=#c0c0c0\n"
                        + "color8=#808080\ncolor9=#ff0000\ncolor10=#00ff00\ncolor11=#ffff00\n"
                        + "color12=#0000ff\ncolor13=#ff00ff\ncolor14=#00ffff\ncolor15=#ffffff\n";

            default:
                return null;
        }
    }
}
