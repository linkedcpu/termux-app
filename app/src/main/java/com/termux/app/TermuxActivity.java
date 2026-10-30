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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Termux 主界面
 *  - 终端 / 会话 / Tab 栏 / 快捷键 / 菜单：原样
 *  - 命令收藏夹：内嵌在 terminal_view 上方，只由底部 ☆ 按钮开关
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
    TermuxSessionsListViewController mTermuxSessionListViewController;
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    Toast mLastToast;
    private boolean mIsVisible;
    private boolean mIsOnResumeAfterOnCreate = false;
    private boolean mIsActivityRecreated = false;
    private boolean mIsInvalidState;
    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    /* ========== 底部 Session 栏 ========== */
    private View         mTabBarRoot;
    private LinearLayout mTabContainer;
    private TextView     mTabAddButton;
    private boolean mTabBarVisible = true;
    private int     mTabBarOriginalHeight;

    /* ========== 内嵌收藏夹（无开关栏） ========== */
    private SnippetManager mSnippetMgr;
    private View           mSnippetPanel;

    /* ========== 菜单 id ========== */
    private static final int CONTEXT_MENU_TOGGLE_EXTRA_KEYS_ID = 12;
    private static final int CONTEXT_MENU_TOGGLE_TAB_BAR_ID    = 13;
    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String LOG_TAG = "TermuxActivity";

    /* ==================== 收藏夹条目 ==================== */
    static final class Snippet {
        final String name;
        final String cmd;
        Snippet(String n, String c) { name = n == null ? "" : n.trim(); cmd = c == null ? "" : c.trim(); }
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("cmd", cmd); return o;
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

        mTabBarRoot   = findViewById(R.id.tab_bar_root);
        mTabContainer = findViewById(R.id.tab_container);
        mTabAddButton = findViewById(R.id.tab_add_button);

        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) { mIsInvalidState = true; return; }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        /* ★ 内嵌收藏夹：插到 terminal_view 上方，初始收起 */
        attachInlineSnippetPanel();
        setupBottomSnippetButton();

        setTermuxTerminalViewAndClients();
        setTerminalToolbarView(savedInstanceState);
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();
        setupTabAddButton();
        setupDrawerButtons();
        mTabBarRoot.post(this::initTabBarHeight);

        registerForContextMenu(mTerminalView);
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background")
                    ? R.string.error_termux_service_start_failed_bg
                    : R.string.error_termux_service_start_failed_general), true);
            mIsInvalidState = true;
            return;
        }

        TermuxUtils.sendTermuxOpenedBroadcast(this);
        renderTabBar();
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");
        if (mIsInvalidState) return;
        mIsVisible = true;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStart();
        if (mPreferences.isTerminalMarginAdjustmentEnabled()) addTermuxActivityRootViewGlobalLayoutListener();
        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");
        if (mIsInvalidState) return;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onResume();
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
        ensureTabBarVisible();
        renderTabBar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");
        if (mIsInvalidState) return;
        mIsVisible = false;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStop();
        removeTermuxActivityRootViewGlobalLayoutListener();
        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
        if (mSnippetMgr != null) mSnippetMgr.save();   // ★ 落盘
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        if (mIsInvalidState) return;
        if (mTermuxService != null) {
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }
        try { unbindService(this); } catch (Exception e) {}
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
        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return;
                    try {
                        boolean fs = intent != null && intent.getExtras() != null
                            && intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        mTermuxTerminalSessionActivityClient.addNewSession(fs, null);
                    } catch (WindowManager.BadTokenException e) {}
                });
            } else finishActivityIfNotFinishing();
        } else {
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                mTermuxTerminalSessionActivityClient.addNewSession(
                    intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false), null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(
                    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
        ensureTabBarVisible();
        renderTabBar();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        finishActivityIfNotFinishing();
    }

    /* ========================================================= */
    /* ★ 内嵌收藏夹：无开关栏，面板直接插在 terminal_view 上方   */
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

        /* 面板：固定 340dp，显隐由底部 ☆ 控制 */
        mSnippetPanel = mSnippetMgr.buildPanel();
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(340));
        plp.setMargins(dp(8), dp(6), dp(8), 0);
        wrapper.addView(mSnippetPanel, plp);

        /* 终端本体吃满剩余空间 */
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        tvlp.setMargins(0, dp(6), 0, 0);
        wrapper.addView(tv, tvlp);

        parent.addView(wrapper, idx, tv.getLayoutParams());

        /* 默认收起 */
        mSnippetMgr.collapse();
    }

    /** 底部 ☆ 按钮：展开 / 收起 */
    private void setupBottomSnippetButton() {
        View star = findViewById(R.id.snippet_button);   // ← 你底部那个 ☆
        if (star == null) return;
        star.setOnClickListener(v -> {
            if (mSnippetMgr == null) return;
            if (mSnippetMgr.isExpanded()) mSnippetMgr.collapse();
            else mSnippetMgr.expand();
        });
    }

    /* ========================================================= */
    /* 属性 / 视图初始化（原样）                                  */
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
        ViewUtils.setLayoutMarginsInDp(rl,
            mProperties.getTerminalMarginHorizontal(), mProperties.getTerminalMarginVertical(),
            mProperties.getTerminalMarginHorizontal(), mProperties.getTerminalMarginVertical());
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }
    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    private void setTermuxTerminalViewAndClients() {
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView lv = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        lv.setAdapter(mTermuxSessionListViewController);
        lv.setOnItemClickListener(mTermuxSessionListViewController);
        lv.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);
        final ViewPager vp = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) vp.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = vp.getLayoutParams();
        mTerminalToolbarDefaultHeight = lp.height;
        setTerminalToolbarHeight();
        String saved = savedInstanceState == null ? null
            : savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        vp.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, saved));
        vp.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, vp));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager vp = getTerminalToolbarViewPager();
        if (vp == null) return;
        ViewGroup.LayoutParams lp = vp.getLayoutParams();
        int rows = mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0
            : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        lp.height = Math.round(mTerminalToolbarDefaultHeight * rows * mProperties.getTerminalToolbarHeightScaleFactor());
        vp.setLayoutParams(lp);
    }

    public boolean toggleTerminalToolbar() {
        final ViewPager vp = getTerminalToolbarViewPager();
        if (vp == null) return false;
        final boolean show = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, getString(show ? R.string.msg_enabling_terminal_toolbar
            : R.string.msg_disabling_terminal_toolbar), true);
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

    private void setSettingsButtonView() {
        ImageButton b = findViewById(R.id.settings_button);
        if (b != null) b.setOnClickListener(v -> ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)));
    }

    private void setNewSessionButtonView() {
        View b = findViewById(R.id.new_session_button);
        if (b == null) return;
        b.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        b.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm,
                t -> mTermuxTerminalSessionActivityClient.addNewSession(false, t),
                R.string.action_new_session_failsafe,
                t -> mTermuxTerminalSessionActivityClient.addNewSession(true, t),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        View b = findViewById(R.id.toggle_keyboard_button);
        if (b == null) return;
        b.setOnClickListener(v -> { mTermuxTerminalViewClient.onToggleSoftKeyboardRequest(); getDrawer().closeDrawers(); });
    }

    /* ================= 抽屉按钮（收藏夹交给底部 ☆） ================= */

    private void setupDrawerButtons() {
        View mb = findViewById(R.id.drawer_menu_button);
        if (mb != null) mb.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (getCurrentSession() == null) { Toast.makeText(this, "还没有会话", Toast.LENGTH_SHORT).show(); return; }
            mTerminalView.post(() -> openContextMenu(mTerminalView));
        });

        /* 若抽屉里还有 snippet_button，点它等价于底部 ☆ */
        View sb = findViewById(R.id.snippet_button);
        if (sb != null) sb.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (mSnippetMgr != null) {
                if (mSnippetMgr.isExpanded()) mSnippetMgr.collapse(); else mSnippetMgr.expand();
            }
        });
    }

    /* ================= 底部 Session 标签栏（原样） ================= */

    private void setupTabAddButton() {
        if (mTabAddButton == null) return;
        mTabAddButton.setOnClickListener(v -> {
            ensureTabBarVisible();
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        });
        mTabAddButton.setOnLongClickListener(v -> {
            ensureTabBarVisible();
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm,
                t -> mTermuxTerminalSessionActivityClient.addNewSession(false, t),
                R.string.action_new_session_failsafe,
                t -> mTermuxTerminalSessionActivityClient.addNewSession(true, t),
                -1, null, null);
            return true;
        });
    }

    private void initTabBarHeight() {
        if (mTabBarRoot == null) return;
        int h = mTabBarRoot.getHeight();
        if (h <= 0) h = (int) (36 * getResources().getDisplayMetrics().density);
        mTabBarOriginalHeight = h;
        applyTabBarVisibility();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void renderTabBar() {
        if (mTabContainer == null || mTabBarRoot == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        java.util.List sessions = service.getTermuxSessions();
        applyTabBarVisibility();
        if (sessions.isEmpty()) return;

        mTabBarRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.tab_bar_background));
        mTabContainer.removeAllViews();
        TerminalSession current = getCurrentSession();

        int d = (int) getResources().getDisplayMetrics().density;
        int tabH = 36 * d, padH = 12 * d, padV = 4 * d, gap = 2 * d;
        int activeBg   = ContextCompat.getColor(this, R.color.tab_active_bg);
        int inactiveBg = ContextCompat.getColor(this, R.color.tab_inactive_bg);
        int activeTx   = ContextCompat.getColor(this, R.color.tab_active_text);
        int inactiveTx = ContextCompat.getColor(this, R.color.tab_inactive_text);
        int closeNorm  = ContextCompat.getColor(this, R.color.tab_close_normal);
        int closePress = ContextCompat.getColor(this, R.color.tab_close_press);

        for (int i = 0; i < sessions.size(); i++) {
            Object o = sessions.get(i);
            if (!(o instanceof com.termux.shared.termux.shell.command.runner.terminal.TermuxSession)) continue;
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts =
                (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession) o;
            TerminalSession s = ts.getTerminalSession();
            if (s == null) continue;
            final TerminalSession click = s;
            final boolean isActive = current != null && current == s;

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(padH, padV, 6 * d, padV);
            tab.setBackgroundColor(isActive ? activeBg : inactiveBg);

            TextView txt = new TextView(this);
            txt.setText(s.mSessionName != null ? s.mSessionName : "Session " + (i + 1));
            txt.setTextColor(isActive ? activeTx : inactiveTx);
            txt.setTextSize(12);
            txt.setSingleLine(true);
            txt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            txt.setPadding(0, 0, 10 * d, 0);
            txt.setOnClickListener(v -> {
                mTermuxTerminalSessionActivityClient.setCurrentSession(click);
                renderTabBar();
                ensureTabBarVisible();
            });
            txt.setOnLongClickListener(v -> { renameSession(click); return true; });

            TextView x = new TextView(this);
            x.setText("×");
            x.setTextColor(closeNorm);
            x.setTextSize(16);
            x.setPadding(6 * d, 0, 6 * d, 0);
            x.setClickable(true);
            x.setFocusable(true);
            x.setOnClickListener(v -> closeSession(click));
            x.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) x.setTextColor(closePress);
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                    x.setTextColor(closeNorm);
                return false;
            });

            tab.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tab.addView(x);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, tabH);
            tlp.setMargins(gap, 0, gap, 0);
            mTabContainer.addView(tab, tlp);
        }
    }

    @SuppressWarnings("rawtypes")
    private void closeSession(TerminalSession session) {
        if (session == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        int idx = service.getIndexOfSession(session);
        session.finishIfRunning();
        final int fidx = idx;
        mTerminalView.postDelayed(() -> {
            session.write("\n");
            service.removeTermuxSession(session);
            int n = service.getTermuxSessionsSize();
            if (n == 0) finishActivityIfNotFinishing();
            else {
                int newIdx = Math.max(0, Math.min(fidx, n - 1));
                Object o = service.getTermuxSession(newIdx);
                if (o instanceof com.termux.shared.termux.shell.command.runner.terminal.TermuxSession) {
                    com.termux.shared.termux.shell.command.runner.terminal.TermuxSession next =
                        (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession) o;
                    if (next.getTerminalSession() != null) {
                        mTerminalView.attachSession(next.getTerminalSession());
                        mTermuxTerminalSessionActivityClient.setCurrentSession(next.getTerminalSession());
                    }
                }
            }
            mTerminalView.post(() -> { ensureTabBarVisible(); renderTabBar(); });
        }, 10);
    }

    private void renameSession(TerminalSession session) {
        if (session == null) return;
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(session.mSessionName);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
            .setTitle("重命名会话")
            .setView(input)
            .setPositiveButton("确定", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) { session.mSessionName = name; renderTabBar(); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /* ================= Session 栏显隐 ================= */

    private void toggleTabBarFromMenu() {
        mTabBarVisible = !mTabBarVisible;
        if (mTabBarVisible) renderTabBar();
        else applyTabBarVisibility();
        Logger.showToast(this, "Session 栏：" + (mTabBarVisible ? "显示" : "隐藏"), false);
    }

    private void applyTabBarVisibility() {
        if (mTabBarRoot == null || mTabBarOriginalHeight == 0) return;
        TermuxService service = getTermuxService();
        boolean has = service != null && !service.getTermuxSessions().isEmpty();
        mTabBarRoot.setVisibility(has ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams lp = mTabBarRoot.getLayoutParams();
        lp.height = mTabBarVisible ? mTabBarOriginalHeight : 0;
        mTabBarRoot.setLayoutParams(lp);
    }

    public void ensureTabBarVisible() {
        if (mTabBarRoot == null) return;
        if (!mTabBarVisible) { mTabBarVisible = true; applyTabBarVisibility(); renderTabBar(); }
    }

    /* ================= 长按菜单 ================= */

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
        TerminalSession cur = getCurrentSession();
        if (cur == null) return;
        boolean af = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (af) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (af) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        MenuItem kill = menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
            getResources().getString(R.string.action_kill_process, cur.getPid()));
        kill.setEnabled(cur.isRunning());

        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_EXTRA_KEYS_ID, Menu.NONE, "快捷键栏（ESC/CTRL/ALT）")
            .setCheckable(true).setChecked(isTerminalToolbarShowing());
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_TAB_BAR_ID, Menu.NONE, "Session 栏（会话标签）")
            .setCheckable(true).setChecked(mTabBarVisible);

        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on)
            .setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();
        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID: mTermuxTerminalViewClient.showUrlSelection(); return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID: mTermuxTerminalViewClient.shareSessionTranscript(); return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT: mTermuxTerminalViewClient.shareSelectedText(); return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME: mTerminalView.requestAutoFillUsername(); return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD: mTerminalView.requestAutoFillPassword(); return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID: onResetTerminalSession(session); return true;
            case CONTEXT_MENU_KILL_PROCESS_ID: showKillSessionDialog(session); return true;
            case CONTEXT_MENU_TOGGLE_EXTRA_KEYS_ID: toggleTerminalToolbar(); return true;
            case CONTEXT_MENU_TOGGLE_TAB_BAR_ID: toggleTabBarFromMenu(); return true;
            case CONTEXT_MENU_STYLING_ID: showStylingDialog(); return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                boolean on = !mTerminalView.getKeepScreenOn();
                mTerminalView.setKeepScreenOn(on);
                mPreferences.setKeepScreenOn(on);
                return true;
            case CONTEXT_MENU_HELP_ID: ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class)); return true;
            case CONTEXT_MENU_SETTINGS_ID: ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)); return true;
            case CONTEXT_MENU_REPORT_ID: mTermuxTerminalViewClient.reportIssueFromTranscript(); return true;
            default: return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.title_confirm_kill_process)
            .setPositiveButton(android.R.string.yes, (d, w) -> { d.dismiss(); session.finishIfRunning(); })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent i = new Intent();
        i.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try { startActivity(i); }
        catch (ActivityNotFoundException | IllegalArgumentException e) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (d, w) -> ActivityUtils.startActivity(this,
                        new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    public void requestStoragePermission(boolean callback) {
        final int rc = callback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
        new Thread(() -> {
            if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                TermuxActivity.this, rc, !callback)) {
                if (callback) Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                    getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));
                TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
            } else if (callback) {
                Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                    getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
            }
        }).start();
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

    public int getNavBarHeight() { return mNavBarHeight; }
    public TermuxActivityRootView getTermuxActivityRootView() { return mTermuxActivityRootView; }
    public View getTermuxActivityBottomSpaceView() { return mTermuxActivityBottomSpaceView; }
    public ExtraKeysView getExtraKeysView() { return mExtraKeysView; }
    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() { return mTermuxTerminalExtraKeys; }
    public void setExtraKeysView(ExtraKeysView v) { mExtraKeysView = v; }
    public DrawerLayout getDrawer() { return (DrawerLayout) findViewById(R.id.drawer_layout); }
    public ViewPager getTerminalToolbarViewPager() { return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager); }
    public float getTerminalToolbarDefaultHeight() { return mTerminalToolbarDefaultHeight; }
    public boolean isTerminalViewSelected() { return getTerminalToolbarViewPager().getCurrentItem() == 0; }
    public boolean isTerminalToolbarTextInputViewSelected() { return getTerminalToolbarViewPager().getCurrentItem() == 1; }
    public void termuxSessionListNotifyUpdated() { mTermuxSessionListViewController.notifyDataSetChanged(); }
    public boolean isVisible() { return mIsVisible; }
    public boolean isOnResumeAfterOnCreate() { return mIsOnResumeAfterOnCreate; }
    public boolean isActivityRecreated() { return mIsActivityRecreated; }
    public TermuxService getTermuxService() { return mTermuxService; }
    public TerminalView getTerminalView() { return mTerminalView; }
    public TermuxTerminalViewClient getTermuxTerminalViewClient() { return mTermuxTerminalViewClient; }
    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() { return mTermuxTerminalSessionActivityClient; }

    @Nullable
    public TerminalSession getCurrentSession() {
        return mTerminalView != null ? mTerminalView.getCurrentSession() : null;
    }

    public TermuxAppSharedPreferences getPreferences() { return mPreferences; }
    public TermuxAppSharedProperties getProperties() { return mProperties; }

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
                    TermuxCrashUtils.notifyAppCrashFromCrashLogFile(ctx, LOG_TAG); break;
                case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                    reloadActivityStyling(i.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true)); break;
                case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                    requestStoragePermission(false); break;
            }
        }
    }

    private void reloadActivityStyling(boolean recreate) {
        if (mProperties != null) {
            reloadProperties();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setMargins();
        setTerminalToolbarHeight();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadActivityStyling();
        if (mTabBarRoot != null) {
            mTabBarRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.tab_bar_background));
            applyTabBarVisibility();
        }
        if (recreate) { Logger.logDebug(LOG_TAG, "Recreating activity"); recreate(); }
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
        /* ★ 返回键先收起收藏夹面板 */
        if (mSnippetMgr != null && mSnippetMgr.isExpanded()) {
            mSnippetMgr.collapse();
            return;
        }
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) getDrawer().closeDrawers();
        else finishActivityIfNotFinishing();
    }

    public void finishActivityIfNotFinishing() { if (!isFinishing()) finish(); }

    public void showToast(String text, boolean longDur) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(this, text, longDur ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(48));
        mLastToast.show();
    }

    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
}
