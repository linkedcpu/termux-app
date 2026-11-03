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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
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
 *  ★ 底部栏 左:右 = 4:1，标签项整条可点，右侧三点弹出「等标签宽、贴标签顶边」的抽屉菜单
 *  ★ 抽屉全程不抢输入焦点，软键盘保持原样不收起
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

    /* ========== 内嵌收藏夹 ========== */
    private SnippetManager mSnippetMgr;
    private View           mSnippetPanel;

    /* ========== ★ 标签抽屉（任意时刻最多一个） ========== */
    private PopupWindow mTabPopup;

    /**
     * ★ 弹窗底边 与 标签顶边 之间的呼吸缝（dp）。
     *   0 = 严丝合缝；6~10 是“悬浮小抽屉”的观感，按喜好调。
     *   缝靠背景 layer-list 的向上投影填层次，不会空得发飘。
     */
    private static final int TAB_DRAWER_GAP_DP = 6;

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

    /* ★ 标签抽屉菜单 id（PopupWindow 自行处理点击，仅作常量保留） */
    private static final int TAB_MENU_RENAME = 1001;
    private static final int TAB_MENU_RESET   = 1002;
    private static final int TAB_MENU_KILL    = 1003;
    private static final int TAB_MENU_CLOSE   = 1004;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String LOG_TAG = "TermuxActivity";

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
        dismissAnyTabPopup();                       // ★ 抽屉随界面不可见一起收掉
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onStop();
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
        dismissAnyTabPopup();
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

    private void setupDrawerButtons() {
        View mb = findViewById(R.id.drawer_menu_button);
        if (mb != null) mb.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (getCurrentSession() == null) { Toast.makeText(this, "还没有会话", Toast.LENGTH_SHORT).show(); return; }
            mTerminalView.post(() -> openContextMenu(mTerminalView));
        });

        View sb = findViewById(R.id.snippet_button);
        if (sb != null) sb.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (mSnippetMgr != null) {
                if (mSnippetMgr.isExpanded()) mSnippetMgr.collapse(); else mSnippetMgr.expand();
            }
        });
    }

    /* ========================================================= */
    /* ★ 底部栏：整条标签可点 + 右侧三点弹出等宽抽屉               */
    /* ========================================================= */

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
        if (h <= 0) h = (int) (40 * getResources().getDisplayMetrics().density);
        mTabBarOriginalHeight = h;
        applyTabBarVisibility();
    }

    /**
     * ★ 标签宽度 = 左侧(屏幕 4/5) ÷ 4 = 屏幕宽 / 5
     *   整条标签(root)响应切换；三点(more)弹出「等标签宽、贴标签顶边」的抽屉，键盘不动
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void renderTabBar() {
        if (mTabContainer == null || mTabBarRoot == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        java.util.List sessions = service.getTermuxSessions();
        applyTabBarVisibility();
        if (sessions.isEmpty()) return;

        mTabBarRoot.setBackgroundColor(0xFF141414);
        mTabContainer.removeAllViews();
        TerminalSession current = getCurrentSession();

        float density = getResources().getDisplayMetrics().density;
        int screenPx  = getResources().getDisplayMetrics().widthPixels;
      //  int leftPx    = screenPx * 4 / 5;          // 左侧 4 份
        int tabW      = 288;
        //Math.max((int) (48 * density), leftPx / 4);
        int gap       = (int) (0 * density);

        for (int i = 0; i < sessions.size(); i++) {
            Object o = sessions.get(i);
            if (!(o instanceof com.termux.shared.termux.shell.command.runner.terminal.TermuxSession)) continue;
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts =
                (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession) o;
            TerminalSession s = ts.getTerminalSession();
            if (s == null) continue;

            final TerminalSession target = s;
            final boolean isActive = current != null && current == s;

            View tab = getLayoutInflater().inflate(R.layout.item_session_tab, mTabContainer, false);
            View     root  = tab.findViewById(R.id.tab_item_root);
            View     line  = tab.findViewById(R.id.tab_top_line);
            ImageView icon  = tab.findViewById(R.id.tab_icon);
            TextView  title = tab.findViewById(R.id.tab_title);
            ImageView more  = tab.findViewById(R.id.tab_more);

            title.setText(s.mSessionName != null ? s.mSessionName : "Session " + (i + 1));

            if (isActive) {
                line.setVisibility(View.VISIBLE);
                root.setBackgroundColor(0xFF1E1E1E);
                title.setTextColor(0xFFFFFFFF);
                icon.setColorFilter(0xFF4ECDC4);
                more.setColorFilter(0xFF4ECDC4);
            } else {
                line.setVisibility(View.GONE);
                root.setBackgroundColor(0xFF0A0A0A);
                title.setTextColor(0xFF999999);
                icon.setColorFilter(0xFF666666);
                more.setColorFilter(0xFF777777);
            }

            // ★ 整条标签点击 → 切换会话（抽屉若开着先收掉）
            root.setOnClickListener(v -> {
                dismissAnyTabPopup();
                mTermuxTerminalSessionActivityClient.setCurrentSession(target);
                renderTabBar();
                ensureTabBarVisible();
            });

            // ★ 三点 → 弹出等标签宽的抽屉；先把该会话切到前台
            more.setOnClickListener(v -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (current != target) {
                    mTermuxTerminalSessionActivityClient.setCurrentSession(target);
                    renderTabBar();
                }
                showTabDrawer(more, target);
            });

            // ★ 长按整条无反应（消费掉，避免与点击打架）
            root.setOnLongClickListener(v -> true);

            LinearLayout.LayoutParams tlp =
                new LinearLayout.LayoutParams(tabW, LinearLayout.LayoutParams.MATCH_PARENT);
            tlp.setMargins(gap, 0, 0, 0);
            tab.setLayoutParams(tlp);
            mTabContainer.addView(tab, tlp);
        }
    }

    /* ================= ★ 标签抽屉（PopupWindow，键盘不收） ================= */

    /** 入口：等标签布局完成再量宽，避免 getWidth() == 0 */
    private void showTabDrawer(ImageView more, TerminalSession session) {
        if (session == null) return;
        final TerminalSession target = session;
        // 锚点取整条标签 root，抽屉宽度/对齐都以它为基准
        final View anchor = (View) more.getParent();
        dismissAnyTabPopup();
        anchor.post(() -> showTabDrawerAbove(anchor, target));
    }

    /**
     * ★ 关键：focusable=true 也能保住键盘 —— 靠下面两句
     *      setInputMethodMode(INPUT_METHOD_NOT_NEEDED)   窗口不碰输入法
     *      setSoftInputMode(SOFT_INPUT_STATE_UNCHANGED)  键盘状态不被切换
     * 兼容性好于 focusable=false（后者在部分 ROM 会吃掉第一次 ACTION_UP）。
     */
    private void showTabDrawerAbove(View anchor, TerminalSession target) {
        int anchorW = anchor.getWidth();
        if (anchorW <= 0) anchorW = dp(200);

        View content = getLayoutInflater().inflate(R.layout.popup_tab_menu, null);

        // ★ 宽度锁成标签宽，高度按内容量
        content.measure(
            View.MeasureSpec.makeMeasureSpec(anchorW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = anchorW;
        int popupH = content.getMeasuredHeight();

        mTabPopup = new PopupWindow(content, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // ★ 弹窗自身背景必须透明：圆角 + 阴影都在 content 的 bg_popup_menu 里画。
        //   若这里塞实色，5.x 上会把圆角裁成直角，还会和布局阴影叠成双影。
        mTabPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mTabPopup.setOutsideTouchable(true);
        mTabPopup.setAnimationStyle(0);                     // 无弹出动画 → 像标签栏原地展开，不“砸”下来

        // ★★★ 压住键盘的核心：窗口不碰输入法、键盘状态不被切换
        mTabPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mTabPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

        mTabPopup.setOnDismissListener(() -> mTabPopup = null);

        // 定位：★ 弹窗底边 = 标签顶边 − gap，中间空出一道缝
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(TAB_DRAWER_GAP_DP);
        int x = loc[0];
        int y = loc[1] - popupH - gap;
        if (y < getStatusBarHeight()) {
            // 上方顶出屏幕 → 翻到标签下方，缝留在标签底边之下，同样呼吸感
            y = loc[1] + anchor.getHeight() + gap;
        }

        mTabPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

        /* ---------- 各项点击：先收抽屉，再执行，焦点钉回终端 ---------- */
        content.findViewById(R.id.item_rename).setOnClickListener(v -> {
            dismissAnyTabPopup(); mTerminalView.requestFocus(); renameSession(target);
        });
        content.findViewById(R.id.item_reset).setOnClickListener(v -> {
            dismissAnyTabPopup(); mTerminalView.requestFocus(); onResetTerminalSession(target);
        });

        View kill = content.findViewById(R.id.item_kill);
        kill.setEnabled(target.isRunning());
        kill.setAlpha(target.isRunning() ? 1f : 0.4f);
        kill.setOnClickListener(v -> {
            if (!target.isRunning()) return;
            dismissAnyTabPopup();
            mTerminalView.requestFocus();
            showKillSessionDialog(target);
        });

        content.findViewById(R.id.item_close).setOnClickListener(v -> {
            dismissAnyTabPopup();
            // 关会话是异步收尾，post 回去把焦点还给终端，键盘不闪
            mTerminalView.post(() -> { mTerminalView.requestFocus(); closeSession(target); });
        });
    }

    private void dismissAnyTabPopup() {
        if (mTabPopup != null && mTabPopup.isShowing()) {
            mTabPopup.dismiss();
        }
        mTabPopup = null;
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
        // 空列表时不强行拉出一条空黑条
        TermuxService service = getTermuxService();
        if (service != null && service.getTermuxSessions().isEmpty()) return;
        if (!mTabBarVisible) { mTabBarVisible = true; applyTabBarVisibility(); renderTabBar(); }
    }

    /* ================= 长按菜单（原样） ================= */

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
            mTabBarRoot.setBackgroundColor(0xFF141414);
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
        // ★ 1) 先收标签抽屉：把焦点钉回终端，键盘保持
        if (mTabPopup != null && mTabPopup.isShowing()) {
            dismissAnyTabPopup();
            mTerminalView.requestFocus();
            return;
        }
        // 2) 再收收藏夹
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

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
