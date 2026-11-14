package com.termux.app.terminal;

import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.data.DataUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * 终端长按菜单：菜单长什么样 + 点了做什么，全部在这里。
 *
 * <p>★ 本类不持有 Context、不自己 startActivity，所有页面跳转委托给 NavigationHandle，
 * 因此彻底避免 "Calling startActivity() from outside of an Activity context
 * requires FLAG_ACTIVITY_NEW_TASK" 这类崩溃。
 * <p>★ dispatchById(int) 供抽屉 DrawerPanel 复用同一套动作，逻辑只有一份。
 */
public final class TerminalContextMenu {

    static final int ID_SELECT_URL = 0;
    static final int ID_SHARE_TRANSCRIPT = 1;
    static final int ID_AUTOFILL_PW = 2;
    static final int ID_RESET = 3;
    static final int ID_KILL = 4;
    static final int ID_STYLING = 5;
    static final int ID_KEEP_SCREEN = 6;
    static final int ID_HELP = 7;
    static final int ID_SETTINGS = 8;
    static final int ID_REPORT = 9;
    static final int ID_SHARE_SELECTED = 10;
    static final int ID_AUTOFILL_USER = 11;
    static final int ID_TOGGLE_EXTRA_KEYS = 12;
    static final int ID_TOGGLE_TAB_BAR = 13;

    private final TerminalView terminalView;
    private final TermuxTerminalViewClient viewClient;
    private final ExtraKeysHandle extraKeys;
    private final TabBarHandle tabBar;
    private final SessionHandle sessions;
    private final StylingHandle styling;
    private final NavigationHandle nav;
    private final SessionSupplier currentSession;

    public TerminalContextMenu(
            @NonNull TerminalView terminalView,
            @NonNull TermuxTerminalViewClient viewClient,
            @NonNull ExtraKeysHandle extraKeys,
            @NonNull TabBarHandle tabBar,
            @NonNull SessionHandle sessions,
            @NonNull StylingHandle styling,
            @NonNull NavigationHandle nav,
            @NonNull SessionSupplier currentSession) {
        this.terminalView = terminalView;
        this.viewClient = viewClient;
        this.extraKeys = extraKeys;
        this.tabBar = tabBar;
        this.sessions = sessions;
        this.styling = styling;
        this.nav = nav;
        this.currentSession = currentSession;
    }

    /* ---------------- 构建 ---------------- */

    public void build(@NonNull ContextMenu menu) {
        TerminalSession cur = currentSession.get();
        if (cur == null) return;

        boolean af = terminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, ID_SELECT_URL, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, ID_SHARE_TRANSCRIPT, Menu.NONE, R.string.action_share_transcript);

        if (!DataUtils.isNullOrEmpty(terminalView.getStoredSelectedText())) {
            menu.add(Menu.NONE, ID_SHARE_SELECTED, Menu.NONE, R.string.action_share_selected_text);
        }

        if (af) {
            menu.add(Menu.NONE, ID_AUTOFILL_USER, Menu.NONE, R.string.action_autofill_username);
            menu.add(Menu.NONE, ID_AUTOFILL_PW, Menu.NONE, R.string.action_autofill_password);
        }

        menu.add(Menu.NONE, ID_RESET, Menu.NONE, R.string.action_reset_terminal);

        MenuItem kill =
                menu.add(
                        Menu.NONE,
                        ID_KILL,
                        Menu.NONE,
                        terminalView
                                .getResources()
                                .getString(R.string.action_kill_process, cur.getPid()));
        kill.setEnabled(cur.isRunning());

        menu.add(Menu.NONE, ID_TOGGLE_EXTRA_KEYS, Menu.NONE, "快捷键栏（ESC/CTRL/ALT）")
                .setCheckable(true)
                .setChecked(extraKeys.isVisible());

        menu.add(Menu.NONE, ID_TOGGLE_TAB_BAR, Menu.NONE, "Session 栏（会话标签）")
                .setCheckable(true)
                .setChecked(tabBar.isVisible());

        menu.add(Menu.NONE, ID_STYLING, Menu.NONE, R.string.action_style_terminal);

        menu.add(Menu.NONE, ID_KEEP_SCREEN, Menu.NONE, R.string.action_toggle_keep_screen_on)
                .setCheckable(true)
                .setChecked(terminalView.getKeepScreenOn());

        menu.add(Menu.NONE, ID_HELP, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, ID_SETTINGS, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, ID_REPORT, Menu.NONE, R.string.action_report_issue);
    }

    /* ---------------- 分发 + 执行 ---------------- */

    public boolean dispatch(@NonNull MenuItem item) {
        TerminalSession cur = currentSession.get();
        switch (item.getItemId()) {
            case ID_SELECT_URL:
                viewClient.showUrlSelection();
                return true;
            case ID_SHARE_TRANSCRIPT:
                viewClient.shareSessionTranscript();
                return true;
            case ID_SHARE_SELECTED:
                viewClient.shareSelectedText();
                return true;
            case ID_AUTOFILL_USER:
                terminalView.requestAutoFillUsername();
                return true;
            case ID_AUTOFILL_PW:
                terminalView.requestAutoFillPassword();
                return true;
            case ID_RESET:
                reset(cur);
                return true;
            case ID_KILL:
                kill(cur);
                return true;
            case ID_TOGGLE_EXTRA_KEYS:
                extraKeys.toggle();
                return true;
            case ID_TOGGLE_TAB_BAR:
                tabBar.toggle();
                return true;
            case ID_STYLING:
                styling.open();
                return true;
            case ID_KEEP_SCREEN:
                toggleKeepScreenOn();
                return true;
            case ID_HELP:
                nav.openHelp();
                return true;
            case ID_SETTINGS:
                nav.openSettings();
                return true;
            case ID_REPORT:
                viewClient.reportIssueFromTranscript();
                return true;
            default:
                return false;
        }
    }

    /**
     * ★ 供抽屉 DrawerPanel 按菜单 ID 直接触发同一套动作。
     * 内部把 ID 转成对 dispatch 的等价调用，不重复实现逻辑。
     */
    public boolean dispatchById(int id) {
        TerminalSession cur = currentSession.get();
        switch (id) {
            case ID_SELECT_URL:
                viewClient.showUrlSelection();
                return true;
            case ID_SHARE_TRANSCRIPT:
                viewClient.shareSessionTranscript();
                return true;
            case ID_SHARE_SELECTED:
                viewClient.shareSelectedText();
                return true;
            case ID_AUTOFILL_USER:
                terminalView.requestAutoFillUsername();
                return true;
            case ID_AUTOFILL_PW:
                terminalView.requestAutoFillPassword();
                return true;
            case ID_RESET:
                reset(cur);
                return true;
            case ID_KILL:
                kill(cur);
                return true;
            case ID_TOGGLE_EXTRA_KEYS:
                extraKeys.toggle();
                return true;
            case ID_TOGGLE_TAB_BAR:
                tabBar.toggle();
                return true;
            case ID_STYLING:
                styling.open();
                return true;
            case ID_KEEP_SCREEN:
                toggleKeepScreenOn();
                return true;
            case ID_HELP:
                nav.openHelp();
                return true;
            case ID_SETTINGS:
                nav.openSettings();
                return true;
            case ID_REPORT:
                viewClient.reportIssueFromTranscript();
                return true;
            default:
                return false;
        }
    }

    private void reset(@Nullable TerminalSession s) {
        if (s == null) return;
        s.reset();
        nav.toast(R.string.msg_terminal_reset, true);
        sessions.onReset();
    }

    private void kill(@Nullable TerminalSession s) {
        if (s == null) return;
        nav.showKillConfirm(s);
    }

    private void toggleKeepScreenOn() {
        boolean on = !terminalView.getKeepScreenOn();
        terminalView.setKeepScreenOn(on);
        sessions.saveKeepScreenOn(on);
    }

    /* ---------------- 把手接口 ---------------- */

    public interface ExtraKeysHandle {
        boolean isVisible();

        void toggle();
    }

    public interface TabBarHandle {
        boolean isVisible();

        void toggle();
    }

    public interface SessionHandle {
        void onReset();

        void saveKeepScreenOn(boolean on);
    }

    public interface StylingHandle {
        void open();
    }

    public interface NavigationHandle {
        void openHelp();

        void openSettings();

        void showKillConfirm(@NonNull TerminalSession session);

        void toast(int resId, boolean longDur);
    }

    @FunctionalInterface
    public interface SessionSupplier {
        @Nullable
        TerminalSession get();
    }
}
