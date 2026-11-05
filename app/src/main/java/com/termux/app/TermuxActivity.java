package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.DrawerPanel;
import com.termux.app.terminal.SessionTabBar;
import com.termux.app.terminal.TerminalContextMenu;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Termux 主界面
 *
 * <p>★ 左抽屉 = 独立 DrawerPanel（菜单 + 测试按钮）
 * <p>★ 底部   = SessionTabBar（标签 / ＋ / ☆ / ≡）
 * <p>★ 长按   = TerminalContextMenu
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    TermuxService mTermuxService;
    TerminalView mTerminalView;
    TermuxTerminalViewClient mTermuxTerminalViewClient;
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxAppSharedProperties mProperties;
    TermuxActivityRootView mTermuxActivityRootView;
    View mTermuxActivityBottomSpaceView;
    ExtraKeysView mExtraKeysView;
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    private final BroadcastReceiver mTermuxActivityBroadcastReceiver =
            new TermuxActivityBroadcastReceiver();
    Toast mLastToast;
    private boolean mIsVisible;
    private boolean mIsOnResumeAfterOnCreate = false;
    private boolean mIsActivityRecreated = false;
    private boolean mIsInvalidState;
    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    private SessionTabBar mSessionTabBar;
    private SnippetManager mSnippetMgr;
    private View mSnippetPanel;
    private DrawerPanel mDrawerPanel;                       // ★
    private TerminalContextMenu mContextMenu;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String LOG_TAG = "TermuxActivity";

    static final class Snippet {
        final String name;
        final String cmd;

        Snippet(String n, String c) {
            name = n == null ? "" : n.trim();
            cmd = c == null ? "" : c.trim();
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("cmd", cmd);
            return o;
        }

        static Snippet fromJson(JSONObject o) throws JSONException {
            return new Snippet(o.optString("name", ""), o.optString("cmd", ""));
        }
    }

    /* ========================================================= */
    /* 生命周期                                                   */
    /* ========================================================= */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;
        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();
        setActivityTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux);

        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(
                new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    mNavBarHeight = insets.getSystemWindowInsetBottom();
                    return insets;
                });

        if (mProperties.isUsingFullScreen())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setTermuxTerminalViewAndClients();

        attachInlineSnippetPanel();
        setupBottomSnippetButton();

        setTerminalToolbarView(savedInstanceState);
        installDrawerPanel();                                 // ★

        mSessionTabBar =
                new SessionTabBar(
                        this,
                        mTerminalView,
                        findViewById(R.id.tab_bar_root),
                        findViewById(R.id.tab_container),
                        findViewById(R.id.tab_add_button));
        mSessionTabBar.init();

        mContextMenu =
                new TerminalContextMenu(
                        mTerminalView,
                        mTermuxTerminalViewClient,
                        new TerminalContextMenu.ExtraKeysHandle() {
                            @Override
                            public boolean isVisible() {
                                return isTerminalToolbarShowing();
                            }

                            @Override
                            public void toggle() {
                                toggleTerminalToolbar();
                            }
                        },
                        new TerminalContextMenu.TabBarHandle() {
                            @Override
                            public boolean isVisible() {
                                return mSessionTabBar != null && mSessionTabBar.isVisibleNow();
                            }

                            @Override
                            public void toggle() {
                                if (mSessionTabBar != null) mSessionTabBar.toggle();
                            }
                        },
                        new TerminalContextMenu.SessionHandle() {
                            @Override
                            public void onReset() {
                                if (mTermuxTerminalSessionActivityClient != null)
                                    mTermuxTerminalSessionActivityClient.onResetTerminalSession();
                            }

                            @Override
                            public void saveKeepScreenOn(boolean on) {
                                mPreferences.setKeepScreenOn(on);
                            }
                        },
                        this::showStylingDialog,
                        new TerminalContextMenu.NavigationHandle() {
                            @Override
                            public void openHelp() {
                                ActivityUtils.startActivity(
                                        TermuxActivity.this,
                                        new Intent(TermuxActivity.this, HelpActivity.class));
                            }

                            @Override
                            public void openSettings() {
                                ActivityUtils.startActivity(
                                        TermuxActivity.this,
                                        new Intent(TermuxActivity.this, SettingsActivity.class));
                            }

                            @Override
                            public void showKillConfirm(@NonNull TerminalSession session) {
                                TermuxActivity.this.confirmKill(session);
                            }

                            @Override
                            public void toast(int resId, boolean longDur) {
                                TermuxActivity.this.showToast(getString(resId), longDur);
                            }
                        },
                        this::getCurrentSession);

        registerForContextMenu(mTerminalView);
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(
                    LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(
                    this,
                    getString(
                            e.getMessage() != null
                                            && e.getMessage().contains("app is in background")
                                    ? R.string.error_termux_service_start_failed_bg
                                    : R.string.error_termux_service_start_failed_general),
                    true);
            mIsInvalidState = true;
            return;
        }

        TermuxUtils.sendTermuxOpenedBroadcast(this);
        mSessionTabBar.render();
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");
        if (mIsInvalidState) return;
        mIsVisible = true;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStart();
        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();
        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");
        if (mIsInvalidState) return;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onResume();
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
        if (mSessionTabBar != null) {
            mSessionTabBar.ensureVisible();
            mSessionTabBar.render();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");
        if (mIsInvalidState) return;
        mIsVisible = false;
        if (mSessionTabBar != null) mSessionTabBar.detach();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStop();
        removeTermuxActivityRootViewGlobalLayoutListener();
        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
        if (mSnippetMgr != null) mSnippetMgr.save();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        if (mIsInvalidState) return;
        if (mSessionTabBar != null) mSessionTabBar.detach();
        if (mTermuxService != null) {
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }
        if (mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.detachFromActivity();
        }
        mContextMenu = null;
        mDrawerPanel = null;
        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(out);
        saveTerminalToolbarTextInput(out);
        out.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");
        mTermuxService = ((TermuxService.LocalBinder) service).service;

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(
                        TermuxActivity.this,
                        () -> {
                            if (mTermuxService == null) return;
                            try {
                                boolean fs =
                                        intent != null
                                                && intent.getExtras() != null
                                                && intent.getExtras()
                                                        .getBoolean(
                                                                TERMUX_ACTIVITY
                                                                        .EXTRA_FAILSAFE_SESSION,
                                                                false);
                                mTermuxTerminalSessionActivityClient.addNewSession(fs, null);
                            } catch (WindowManager.BadTokenException e) {
                                // ignore
                            }
                        });
            } else finishActivityIfNotFinishing();
        } else {
            if (!mIsActivityRecreated
                    && intent != null
                    && Intent.ACTION_RUN.equals(intent.getAction())) {
                mTermuxTerminalSessionActivityClient.addNewSession(
                        intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false),
                        null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(
                        mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
        if (mSessionTabBar != null) {
            mSessionTabBar.ensureVisible();
            mSessionTabBar.render();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        finishActivityIfNotFinishing();
    }

    /* ========================================================= */
    /* ★ 左抽屉面板                                              */
    /* ========================================================= */

    private void installDrawerPanel() {
        ViewGroup leftDrawer = findViewById(R.id.left_drawer);
        if (leftDrawer == null) return;
        leftDrawer.removeAllViews();

        mDrawerPanel = new DrawerPanel(this);
        // ★ 想换测试按钮行为就取消下一行注释：
        // mDrawerPanel.setTestAction(() -> { showToast("测试按钮 ✔", false); return true; });

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        leftDrawer.addView(mDrawerPanel.getView(), lp);
    }

    /* ========================================================= */
    /* ★ 内嵌收藏夹                                               */
    /* ========================================================= */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void attachInlineSnippetPanel() {
        TerminalView tv = findViewById(R.id.terminal_view);
        if (tv == null) return;

        mSnippetMgr = new SnippetManager(this);

        ViewGroup parent = (ViewGroup) tv.getParent();
        if (parent == null) return;

        int idx = parent.indexOfChild(tv);
        parent.removeView(tv);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        mSnippetPanel = mSnippetMgr.buildPanel();
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(340));
        plp.setMargins(dp(8), dp(6), dp(8), 0);
        wrapper.addView(mSnippetPanel, plp);

        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        tvlp.setMargins(0, dp(6), 0, 0);
        wrapper.addView(tv, tvlp);

        parent.addView(wrapper, idx, tv.getLayoutParams());
        mSnippetMgr.collapse();
    }

    private void setupBottomSnippetButton() {
        View star = findViewById(R.id.snippet_button);
        if (star == null) return;
        star.setOnClickListener(
                v -> {
                    if (mSnippetMgr == null) return;
                    if (mSnippetMgr.isExpanded()) mSnippetMgr.collapse();
                    else mSnippetMgr.expand();
                });
    }

    /* ========================================================= */
    /* 属性 / 视图初始化                                          */
    /* ========================================================= */

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityTheme() {
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout rl = findViewById(R.id.activity_termux_root_relative_layout);
        ViewUtils.setLayoutMarginsInDp(
                rl,
                mProperties.getTerminalMarginHorizontal(),
                mProperties.getTerminalMarginVertical(),
                mProperties.getTerminalMarginHorizontal(),
                mProperties.getTerminalMarginVertical());
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView()
                .getViewTreeObserver()
                .addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView()
                    .getViewTreeObserver()
                    .removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    private void setTermuxTerminalViewAndClients() {
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient =
                new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys =
                new TermuxTerminalExtraKeys(
                        this,
                        mTerminalView,
                        mTermuxTerminalViewClient,
                        mTermuxTerminalSessionActivityClient);
        final ViewPager vp = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) vp.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = vp.getLayoutParams();
        mTerminalToolbarDefaultHeight = lp.height;
        setTerminalToolbarHeight();
        String saved =
                savedInstanceState == null
                        ? null
                        : savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        vp.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, saved));
        vp.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, vp));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager vp = getTerminalToolbarViewPager();
        if (vp == null) return;
        ViewGroup.LayoutParams lp = vp.getLayoutParams();
        int rows =
                mTermuxTerminalExtraKeys.getExtraKeysInfo() == null
                        ? 0
                        : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        lp.height =
                Math.round(
                        mTerminalToolbarDefaultHeight
                                * rows
                                * mProperties.getTerminalToolbarHeightScaleFactor());
        vp.setLayoutParams(lp);
    }

    public boolean toggleTerminalToolbar() {
        final ViewPager vp = getTerminalToolbarViewPager();
        if (vp == null) return false;
        final boolean show = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(
                this,
                getString(
                        show
                                ? R.string.msg_enabling_terminal_toolbar
                                : R.string.msg_disabling_terminal_toolbar),
                true);
        vp.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && isTerminalToolbarTextInputViewSelected())
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        return show;
    }

    private boolean isTerminalToolbarShowing() {
        final ViewPager vp = getTerminalToolbarViewPager();
        return vp != null && vp.getVisibility() == View.VISIBLE;
    }

    private void saveTerminalToolbarTextInput(Bundle out) {
        if (out == null) return;
        android.widget.EditText et = findViewById(R.id.terminal_toolbar_text_input);
        if (et != null && !et.getText().toString().isEmpty())
            out.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, et.getText().toString());
    }

    private void setupDrawerButtons() {
        View mb = findViewById(R.id.drawer_menu_button);
        if (mb != null)
            mb.setOnClickListener(
                    v -> {
                        getDrawer().closeDrawers();
                        if (getCurrentSession() == null) {
                            Toast.makeText(this, "还没有会话", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mTerminalView.post(() -> openContextMenu(mTerminalView));
                    });

        View sb = findViewById(R.id.snippet_button);
        if (sb != null)
            sb.setOnClickListener(
                    v -> {
                        getDrawer().closeDrawers();
                        if (mSnippetMgr != null) {
                            if (mSnippetMgr.isExpanded()) mSnippetMgr.collapse();
                            else mSnippetMgr.expand();
                        }
                    });
    }

    /* ========================================================= */
    /* ★ 桥接方法                                                */
    /* ========================================================= */

    public void setCurrentSession(TerminalSession s) {
        mTermuxTerminalSessionActivityClient.setCurrentSession(s);
    }

    public void addNewSession(boolean failsafe, String name) {
        mTermuxTerminalSessionActivityClient.addNewSession(failsafe, name);
    }

    /**
     * ★ 严格对齐 TextInputDialogUtils.textInput 的 10 参签名：
     *    (Activity, titleRes, initialText, okRes, onOk,
     *     neuRes, onNeu, negRes, onNeg, onDismiss)
     */
    public void showNamedSessionDialog(boolean failsafe) {
        TextInputDialogUtils.textInput(
                /* activity      */ TermuxActivity.this,
                /* titleRes      */ R.string.title_create_named_session,
                /* initialText   */ null,
                /* okRes         */ R.string.action_create_named_session_confirm,
                /* onOk          */ t ->
                        mTermuxTerminalSessionActivityClient.addNewSession(failsafe, t),
                /* neuRes        */ R.string.action_new_session_failsafe,
                /* onNeu         */ t ->
                        mTermuxTerminalSessionActivityClient.addNewSession(true, t),
                /* negRes        */ -1,
                /* onNeg         */ null,
                /* onDismiss     */ null);
    }

    public void resetSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    public void confirmKill(TerminalSession session) {
        if (session == null) return;
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.title_confirm_kill_process)
                .setPositiveButton(
                        android.R.string.yes,
                        (d, w) -> {
                            d.dismiss();
                            session.finishIfRunning();
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @SuppressWarnings("rawtypes")
    public void closeSession(TerminalSession session) {
        if (session == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        int idx = service.getIndexOfSession(session);
        session.finishIfRunning();
        final int fidx = idx;
        mTerminalView.postDelayed(
                () -> {
                    session.write("\n");
                    service.removeTermuxSession(session);
                    int n = service.getTermuxSessionsSize();
                    if (n == 0) finishActivityIfNotFinishing();
                    else {
                        int newIdx = Math.max(0, Math.min(fidx, n - 1));
                        Object o = service.getTermuxSession(newIdx);
                        if (o
                                instanceof
                                com.termux.shared.termux.shell.command.runner.terminal
                                        .TermuxSession) {
                            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
                                    next =
                                            (com.termux.shared.termux.shell.command.runner.terminal
                                                            .TermuxSession)
                                                    o;
                            if (next.getTerminalSession() != null) {
                                mTerminalView.attachSession(next.getTerminalSession());
                                mTermuxTerminalSessionActivityClient.setCurrentSession(
                                        next.getTerminalSession());
                            }
                        }
                    }
                    mTerminalView.post(
                            () -> {
                                if (mSessionTabBar != null) {
                                    mSessionTabBar.ensureVisible();
                                    mSessionTabBar.render();
                                }
                            });
                },
                10);
    }

    /* ========================================================= */
    /* ★ 长按菜单                                                */
    /* ========================================================= */

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
        if (mContextMenu != null) mContextMenu.build(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mTerminalView != null) mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mContextMenu != null && mContextMenu.dispatch(item)) return true;
        return super.onContextItemSelected(item);
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        if (mTerminalView != null) mTerminalView.onContextMenuClosed(menu);
    }

    /* ========================================================= */
    /* 样式 / 权限 / 杂项                                        */
    /* ========================================================= */

    private void showStylingDialog() {
        Intent i = new Intent();
        i.setClassName(
                TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
                TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.error_styling_not_installed))
                    .setPositiveButton(
                            R.string.action_styling_install,
                            (d, w) ->
                                    ActivityUtils.startActivity(
                                            this,
                                            new Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(
                                                            TermuxConstants
                                                                    .TERMUX_STYLING_FDROID_PACKAGE_URL))))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    public void requestStoragePermission(boolean callback) {
        final int rc = callback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
        new Thread(
                        () -> {
                            if (isDestroyed() || mIsInvalidState) return;
                            if (PermissionUtils
                                    .checkAndRequestLegacyOrManageExternalStoragePermission(
                                            TermuxActivity.this, rc, !callback)) {
                                if (callback)
                                    Logger.logInfoAndShowToast(
                                            TermuxActivity.this,
                                            LOG_TAG,
                                            getString(
                                                    com.termux.shared.R.string
                                                            .msg_storage_permission_granted_on_request));
                                TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                            } else if (callback) {
                                Logger.logInfoAndShowToast(
                                        TermuxActivity.this,
                                        LOG_TAG,
                                        getString(
                                                com.termux.shared.R.string
                                                        .msg_storage_permission_not_granted_on_request));
                            }
                        })
                .start();
    }

    @Override
    protected void onActivityResult(int rc, int res, @Nullable Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) requestStoragePermission(true);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) requestStoragePermission(true);
    }

    /* ================= 访问器 ================= */

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView v) {
        mExtraKeysView = v;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }

    public void termuxSessionListNotifyUpdated() {
        if (mSessionTabBar != null) mSessionTabBar.render();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        return mTerminalView != null ? mTerminalView.getCurrentSession() : null;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    public SessionTabBar getSessionTabBar() {
        return mSessionTabBar;
    }

    public void renderTabBar() {
        if (mSessionTabBar != null) mSessionTabBar.render();
    }

    /** ★ 供 DrawerPanel 操作收藏夹 */
    public SnippetManager getSnippetManager() {
        return mSnippetMgr;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public static void updateTermuxActivityStyling(Context ctx, boolean recreate) {
        Intent i = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        i.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreate);
        ctx.sendBroadcast(i);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        f.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        f.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        registerReceiver(mTermuxActivityBroadcastReceiver, f);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixBroadcastIntent(Intent i) {
        if (i == null) return;
        if ("storage".equals(i.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE))) {
            i.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            i.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            if (i == null || !mIsVisible) return;
            fixBroadcastIntent(i);
            switch (i.getAction()) {
                case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                    TermuxCrashUtils.notifyAppCrashFromCrashLogFile(ctx, LOG_TAG);
                    break;
                case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                    reloadActivityStyling(
                            i.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                    break;
                case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                    requestStoragePermission(false);
                    break;
            }
        }
    }

    private void reloadActivityStyling(boolean recreate) {
        if (mProperties != null) {
            reloadProperties();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(
                        mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setMargins();
        setTerminalToolbarHeight();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadActivityStyling();
        if (mSessionTabBar != null) mSessionTabBar.render();
        if (recreate) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            recreate();
        }
    }

    public static void startTermuxActivity(@NonNull Context ctx) {
        ActivityUtils.startActivity(ctx, newInstance(ctx));
    }

    public static Intent newInstance(@NonNull Context ctx) {
        Intent i = new Intent(ctx, TermuxActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (mSessionTabBar != null && mSessionTabBar.onBackPressed()) return;
        if (mSnippetMgr != null && mSnippetMgr.isExpanded()) {
            mSnippetMgr.collapse();
            return;
        }
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) getDrawer().closeDrawers();
        else finishActivityIfNotFinishing();
    }

    public void finishActivityIfNotFinishing() {
        if (!isFinishing()) finish();
    }

    public void showToast(String text, boolean longDur) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(this, text, longDur ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(48));
        mLastToast.show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
