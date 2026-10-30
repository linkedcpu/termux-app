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
import android.text.InputType;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.view.inputmethod.InputMethodManager;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 底部栏右侧：＋ 新建 / ☆ 收藏夹 / ⚙ 设置(弹长按菜单)
 *   ☆ 收藏夹界面：深色卡片 + 圆角 + 等宽命令 + 搜索过滤 + 条数徽标
 *   收藏夹路径：~/.termux/snippets.json
 *     (= /data/data/<包名>/files/home/.termux/snippets.json)
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

    /* ========== 底部栏 ========== */
    private View         mTabBarRoot;
    private LinearLayout mTabContainer;
    private TextView     mTabAddButton;

    private boolean mTabBarVisible = true;
    private int     mTabBarOriginalHeight;

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

    /* ==================== 命令收藏夹 ==================== */
    private static final String SNIPPETS_FILE = "snippets.json";
    private final List<Snippet> mSnippets = new ArrayList<>();
    private AlertDialog mSnippetSheet;

    static class Snippet {
        final String name;
        final String cmd;
        Snippet(String n, String c) { name = n; cmd = c; }
        JSONObject toJson() throws org.json.JSONException {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("cmd", cmd); return o;
        }
        static Snippet fromJson(JSONObject o) throws org.json.JSONException {
            return new Snippet(o.getString("name"), o.getString("cmd"));
        }
    }
    /* ==================================================== */

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
        if (mPreferences == null) {
            mIsInvalidState = true;
            return;
        }

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

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();
        setTerminalToolbarView(savedInstanceState);
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();
        setupTabAddButton();
        setupDrawerButtons();
        mTabBarRoot.post(this::initTabBarHeight);

        loadSnippets();

        registerForContextMenu(mTerminalView);
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
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
        loadSnippets();   // ★ 从终端切回来也重载收藏夹
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
        saveSnippets();
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
        dismissSnippetSheet();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
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
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {}
                });
            } else {
                finishActivityIfNotFinishing();
            }
        } else {
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
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

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityTheme() {
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
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
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        setTerminalToolbarHeight();
        String savedTextInput = null;
        if (savedInstanceState != null) savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public boolean toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return false;
        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
        return showNow;
    }

    private boolean isTerminalToolbarShowing() {
        final ViewPager vp = getTerminalToolbarViewPager();
        return vp != null && vp.getVisibility() == View.VISIBLE;
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        if (settingsButton == null) return;
        settingsButton.setOnClickListener(v ->
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)));
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        if (newSessionButton == null) return;
        newSessionButton.setOnClickListener(v ->
            mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        View kb = findViewById(R.id.toggle_keyboard_button);
        if (kb == null) return;
        kb.setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
    }

    /* ========================================================= */
    /* ========== 底部栏的 ☆ 收藏夹 / ⚙ 设置 =============== */
    /* ========================================================= */

    private void setupDrawerButtons() {
        View snippetBtn = findViewById(R.id.snippet_button);
        if (snippetBtn != null) snippetBtn.setOnClickListener(v -> showSnippetSheet());

        View menuBtn = findViewById(R.id.drawer_menu_button);
        if (menuBtn != null) menuBtn.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (getCurrentSession() == null) {
                Toast.makeText(this, "还没有会话", Toast.LENGTH_SHORT).show();
                return;
            }
            mTerminalView.post(() -> openContextMenu(mTerminalView));
        });
    }

    /* ========================================================= */
    /* ================ 底部栏：Session 标签 ================== */
    /* ========================================================= */

    private void setupTabAddButton() {
        if (mTabAddButton == null) return;
        mTabAddButton.setOnClickListener(v -> {
            ensureTabBarVisible();
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        });
        mTabAddButton.setOnLongClickListener(v -> {
            ensureTabBarVisible();
            TextInputDialogUtils.textInput(TermuxActivity.this,
                R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm,
                text -> { if (mTermuxTerminalSessionActivityClient != null)
                    mTermuxTerminalSessionActivityClient.addNewSession(false, text); },
                R.string.action_new_session_failsafe,
                text -> { if (mTermuxTerminalSessionActivityClient != null)
                    mTermuxTerminalSessionActivityClient.addNewSession(true, text); },
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

    public void renderTabBar() {
        if (mTabContainer == null || mTabBarRoot == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions =
                service.getTermuxSessions();

        applyTabBarVisibility();
        if (sessions.isEmpty()) return;

        mTabBarRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.tab_bar_background));
        mTabContainer.removeAllViews();
        TerminalSession current = getCurrentSession();

        int d = (int) getResources().getDisplayMetrics().density;
        int tabHeight = 36 * d, padH = 12 * d, padV = 4 * d, gap = 2 * d;
        int activeBg    = ContextCompat.getColor(this, R.color.tab_active_bg);
        int inactiveBg  = ContextCompat.getColor(this, R.color.tab_inactive_bg);
        int activeText  = ContextCompat.getColor(this, R.color.tab_active_text);
        int inactiveText= ContextCompat.getColor(this, R.color.tab_inactive_text);
        int closeNormal = ContextCompat.getColor(this, R.color.tab_close_normal);
        int closePress  = ContextCompat.getColor(this, R.color.tab_close_press);

        for (int i = 0; i < sessions.size(); i++) {
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts = sessions.get(i);
            TerminalSession s = ts.getTerminalSession();
            if (s == null) continue;
            final TerminalSession click = s;
            final boolean isActive = (current != null && current == s);

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(padH, padV, 6 * d, padV);
            tab.setBackgroundColor(isActive ? activeBg : inactiveBg);

            TextView txt = new TextView(this);
            txt.setText(s.mSessionName != null ? s.mSessionName : "Session " + (i + 1));
            txt.setTextColor(isActive ? activeText : inactiveText);
            txt.setTextSize(12);
            txt.setSingleLine(true);
            txt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            txt.setPadding(0, 0, 10 * d, 0);
            txt.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.setCurrentSession(click);
                    renderTabBar();
                    ensureTabBarVisible();
                }
            });
            txt.setOnLongClickListener(v -> { renameSession(click); return true; });

            TextView x = new TextView(this);
            x.setText("×");
            x.setTextColor(closeNormal);
            x.setTextSize(16);
            x.setPadding(6 * d, 0, 6 * d, 0);
            x.setClickable(true);
            x.setFocusable(true);
            x.setOnClickListener(v -> closeSession(click));
            x.setOnTouchListener((v, e) -> {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: x.setTextColor(closePress); break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: x.setTextColor(closeNormal); break;
                }
                return false;
            });

            tab.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tab.addView(x);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, tabHeight);
            lp.setMargins(gap, 0, gap, 0);
            mTabContainer.addView(tab, lp);
        }
    }

    private void closeSession(TerminalSession session) {
        if (session == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;
        int index = service.getIndexOfSession(session);
        session.finishIfRunning();
        mTerminalView.postDelayed(() -> {
            session.write("\n");
            service.removeTermuxSession(session);
            int newSize = service.getTermuxSessionsSize();
            if (newSize == 0) {
                finishActivityIfNotFinishing();
            } else {
                int newIndex = Math.max(0, Math.min(index, newSize - 1));
                com.termux.shared.termux.shell.command.runner.terminal.TermuxSession next =
                        service.getTermuxSession(newIndex);
                if (next != null && next.getTerminalSession() != null) {
                    mTerminalView.attachSession(next.getTerminalSession());
                    if (mTermuxTerminalSessionActivityClient != null)
                        mTermuxTerminalSessionActivityClient.setCurrentSession(next.getTerminalSession());
                }
            }
            mTerminalView.post(() -> { ensureTabBarVisible(); renderTabBar(); });
        }, 10);
    }

    private void renameSession(TerminalSession session) {
        if (session == null) return;
        EditText input = new EditText(this);
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

    /* ========================================================= */
    /* ========== Session 栏显隐（菜单里那条） =============== */
    /* ========================================================= */

    private void toggleTabBarFromMenu() {
        mTabBarVisible = !mTabBarVisible;
        if (mTabBarVisible) renderTabBar();
        else applyTabBarVisibility();
        Logger.showToast(this, "Session 栏：" + (mTabBarVisible ? "显示" : "隐藏"), false);
    }

    private void applyTabBarVisibility() {
        if (mTabBarRoot == null || mTabBarOriginalHeight == 0) return;
        TermuxService service = getTermuxService();
        boolean hasSessions = service != null && !service.getTermuxSessions().isEmpty();
        mTabBarRoot.setVisibility(hasSessions ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams lp = mTabBarRoot.getLayoutParams();
        lp.height = mTabBarVisible ? mTabBarOriginalHeight : 0;
        mTabBarRoot.setLayoutParams(lp);
    }

    public void ensureTabBarVisible() {
        if (mTabBarRoot == null) return;
        if (!mTabBarVisible) {
            mTabBarVisible = true;
            applyTabBarVisibility();
            renderTabBar();
        }
    }

    /* ========================================================= */
    /* ================ 长按菜单（官方入口） ================== */
    /* ========================================================= */

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;
        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
            getResources().getString(R.string.action_kill_process, getCurrentSession().getPid()))
            .setEnabled(currentSession.isRunning());

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
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON: toggleKeepScreenOn(); return true;
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
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> { dialog.dismiss(); session.finishIfRunning(); });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));
                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) { requestStoragePermission(true); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) { requestStoragePermission(true); }
    }

    public int getNavBarHeight() { return mNavBarHeight; }
    public TermuxActivityRootView getTermuxActivityRootView() { return mTermuxActivityRootView; }
    public View getTermuxActivityBottomSpaceView() { return mTermuxActivityBottomSpaceView; }
    public ExtraKeysView getExtraKeysView() { return mExtraKeysView; }
    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() { return mTermuxTerminalExtraKeys; }
    public void setExtraKeysView(ExtraKeysView extraKeysView) { mExtraKeysView = extraKeysView; }
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

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null) return mTerminalView.getCurrentSession();
        else return null;
    }

    public TermuxAppSharedPreferences getPreferences() { return mPreferences; }
    public TermuxAppSharedProperties getProperties() { return mProperties; }

    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;
        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);
                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG); return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true)); return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        requestStoragePermission(false); return;
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
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
        if (recreateActivity) { Logger.logDebug(LOG_TAG, "Recreating activity"); TermuxActivity.this.recreate(); }
    }

    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) getDrawer().closeDrawers();
        else finishActivityIfNotFinishing();
    }

    public void finishActivityIfNotFinishing() {
        if (!TermuxActivity.this.isFinishing()) finish();
    }

    /** ★ toast 改到底部，避免被键盘顶起的输入框挡住 */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(48));
        mLastToast.show();
    }

    /* ========================================================= */
    /* ================== 命令收藏夹 · 界面优化版 ============= */
    /* ========================================================= */

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showSnippetSheet() {
        dismissSnippetSheet();
        loadSnippets();

        Context ctx = this;
        int radius = dp(14), padH = dp(16), gap = dp(10);
        int surface = getColorSafe(R.color.snippet_surface, 0xFF262626);
        int stroke  = getColorSafe(R.color.snippet_stroke,  0xFF3a3a3a);
        int textCol = ContextCompat.getColor(this, android.R.color.primary_text_dark);
        int hintCol = ContextCompat.getColor(this, android.R.color.secondary_text_dark);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_dark));

        /* ---------- 标题 + 条数徽标 ---------- */
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("命令收藏夹");
        title.setTextColor(textCol);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = new TextView(ctx);
        badge.setText(String.valueOf(mSnippets.size()));
        badge.setTextColor(hintCol);
        badge.setTextSize(12);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        badge.setBackground(createPill(stroke, dp(10)));
        head.addView(badge);
        root.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(spacer(ctx, dp(10)));

        /* ---------- ① 收藏输入行 ---------- */
        EditText input = new EditText(ctx);
        input.setHint("输入命令，回车收藏（不会执行）");
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setTextColor(textCol);
        input.setHintTextColor(hintCol);
        input.setTextSize(14);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setBackground(createField(surface, stroke, radius));
        input.setPadding(padH, dp(12), padH, dp(12));
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(spacer(ctx, gap));

        /* ---------- ② ★ 搜索框：放大镜 + 清空 ✕ ---------- */
        LinearLayout searchWrap = new LinearLayout(ctx);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setBackground(createField(surface, stroke, radius));
        searchWrap.setPadding(dp(4), 0, dp(4), 0);

        TextView magnifier = new TextView(ctx);
        magnifier.setText("🔍");
        magnifier.setTextSize(14);
        magnifier.setPadding(padH, 0, dp(4), 0);
        searchWrap.addView(magnifier);

        EditText search = new EditText(ctx);
        search.setHint("搜索名称或命令…");
        search.setSingleLine(true);
        search.setFocusable(true);
        search.setFocusableInTouchMode(true);
        search.setTextColor(textCol);
        search.setHintTextColor(hintCol);
        search.setTextSize(14);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NONE); // 回车不存命令
        search.setBackground(null);
        search.setPadding(0, dp(11), 0, dp(11));
        searchWrap.addView(search, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView clearBtn = new TextView(ctx);
        clearBtn.setText("✕");
        clearBtn.setTextSize(14);
        clearBtn.setTextColor(hintCol);
        clearBtn.setPadding(dp(8), 0, padH, 0);
        clearBtn.setClickable(true);
        clearBtn.setFocusable(true);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> { search.setText(""); search.requestFocus(); });
        searchWrap.addView(clearBtn);

        root.addView(searchWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(spacer(ctx, gap));

        /* ---------- ③ 列表（卡片容器 + 内部滚动） ---------- */
        ScrollView listScroller = new ScrollView(ctx);
        listScroller.setVerticalScrollBarEnabled(true);
        listScroller.setScrollbarFadingEnabled(false);
        listScroller.setFillViewport(true);
        listScroller.setBackground(createField(surface, stroke, radius));
        listScroller.setPadding(dp(4), dp(6), dp(4), dp(6));

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroller.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(listScroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        /* ---------- 绘制 + 徽标 / 清空键联动 ---------- */
        final String[] curFilter = {""};
        Runnable rebuild = new Runnable() {
            @Override
            public void run() {
                drawSnippetList(list, curFilter[0]);
                badge.setText(countShown(curFilter[0]) == mSnippets.size()
                        ? String.valueOf(mSnippets.size())
                        : countShown(curFilter[0]) + " / " + mSnippets.size());
                clearBtn.setVisibility(curFilter[0].isEmpty() ? View.GONE : View.VISIBLE);
            }
        };
        drawSnippetList(list, "");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                curFilter[0] = s.toString().trim().toLowerCase();
                drawSnippetList(list, curFilter[0]);
                int shown = countShown(curFilter[0]);
                badge.setText(shown == mSnippets.size()
                        ? String.valueOf(shown) : shown + " / " + mSnippets.size());
                clearBtn.setVisibility(curFilter[0].isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            String t = input.getText().toString().trim();
            if (!t.isEmpty()) {
                String name = t.split("\\s+")[0];
                if (name.length() > 18) name = name.substring(0, 18);
                mSnippets.add(0, new Snippet(name, t));
                saveSnippets();
                input.setText("");
                search.setText("");
                curFilter[0] = "";
                rebuild.run();
                showToast("已收藏：" + name, false);
            }
            return true;
        });

        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle(null)   // 标题挪进内容区，整体更整
            .setView(root)
            .setNegativeButton("关闭", null)
            .create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final int[] tries = {0};
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            Window win = dlg.getWindow();
            if (win != null) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            if (!imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) && tries[0] < 8) {
                tries[0]++;
                input.postDelayed(() -> {
                    if (input.hasFocus()) {
                        Window w2 = dlg.getWindow();
                        if (w2 != null) w2.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 120);
            }
        });
        dlg.setOnShowListener(d -> input.requestFocus());

        mSnippetSheet = dlg;
        dlg.show();
    }

    /**
     * 画列表：圆角行 + 名称粗体 + 命令等宽小字 + 右箭头。
     * 空库 / 零命中都有空态提示；长按菜单新增「复制命令」。
     */
    private void drawSnippetList(LinearLayout list, String filter) {
        Context ctx = this;
        list.removeAllViews();
        String f = (filter == null) ? "" : filter.trim().toLowerCase();
        int textCol = ContextCompat.getColor(this, android.R.color.primary_text_dark);
        int hintCol = ContextCompat.getColor(this, android.R.color.secondary_text_dark);
        int accent  = ContextCompat.getColor(this, android.R.color.holo_green_light);
        int surface = getColorSafe(R.color.snippet_surface, 0xFF262626);
        int stroke  = getColorSafe(R.color.snippet_stroke,  0xFF3a3a3a);
        int radius  = dp(10);

        if (mSnippets.isEmpty()) {
            list.addView(emptyState(ctx, "📭  还没有收藏", "先输入一条命令，回车即可收藏", hintCol));
            return;
        }

        boolean hasMatch = false;
        for (int i = 0; i < mSnippets.size(); i++) {
            final Snippet s = mSnippets.get(i);
            final int idx = i;
            if (!f.isEmpty()
                    && !s.name.toLowerCase().contains(f)
                    && !s.cmd.toLowerCase().contains(f)) continue;
            hasMatch = true;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(createField(surface, stroke, radius));

            LinearLayout body = new LinearLayout(ctx);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(0, 0, dp(10), 0);

            TextView name = new TextView(ctx);
            name.setText("▸ " + s.name);
            name.setTextColor(textCol);
            name.setTextSize(14);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            body.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView cmd = new TextView(ctx);
            cmd.setText(s.cmd);
            cmd.setTextColor(hintCol);
            cmd.setTextSize(12);
            cmd.setTypeface(android.graphics.Typeface.MONOSPACE);   // ★ 命令等宽
            cmd.setSingleLine(true);
            cmd.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            body.addView(cmd, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            row.addView(body, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView arrow = new TextView(ctx);
            arrow.setText("❯");
            arrow.setTextColor(accent);
            arrow.setTextSize(15);
            arrow.setPadding(dp(4), 0, 0, 0);
            row.addView(arrow);

            final String keep = f;
            row.setOnClickListener(v -> {
                TerminalSession cur = getCurrentSession();
                if (cur == null) { showToast("没有活跃会话", false); return; }
                cur.write(s.cmd);
                dismissSnippetSheet();
                showToast("已写入：" + s.name, false);
            });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle(s.name)
                    .setItems(new CharSequence[]{"置顶", "删除", "复制命令"}, (d, which) -> {
                        switch (which) {
                            case 0: mSnippets.remove(idx); mSnippets.add(0, s); break;
                            case 1: mSnippets.remove(idx); break;
                            case 2:
                                ClipboardManager cm =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(ClipData.newPlainText("cmd", s.cmd));
                                    showToast("已复制：" + s.cmd, false);
                                }
                                return;
                        }
                        saveSnippets();
                        drawSnippetList(list, keep);   // ★ 保持当前过滤词
                    }).show();
                return true;
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            list.addView(spacer(ctx, dp(6)));   // 卡片间距，替代生硬分割线
        }

        if (!hasMatch) {
            list.addView(emptyState(ctx, "🔎  没有匹配",
                    "「" + f + "」什么都没搜到，换个词试试", hintCol));
        }
    }

    private void dismissSnippetSheet() {
        if (mSnippetSheet != null) { mSnippetSheet.dismiss(); mSnippetSheet = null; }
    }

    private LinearLayout emptyState(Context ctx, String big, String sub, int subColor) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(8), dp(28), dp(8), dp(28));
        TextView a = new TextView(ctx);
        a.setText(big); a.setTextColor(subColor); a.setTextSize(15);
        a.setPadding(0, 0, 0, dp(6));
        box.addView(a);
        TextView b = new TextView(ctx);
        b.setText(sub); b.setTextColor(subColor); b.setTextSize(12);
        box.addView(b);
        return box;
    }

    private android.graphics.drawable.Drawable createField(int fill, int strokeColor, int radiusPx) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(1)), strokeColor);
        return d;
    }

    private android.graphics.drawable.Drawable createPill(int fill, int radiusPx) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fill);
        return d;
    }

    private View spacer(Context ctx, int hPx) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hPx));
        return v;
    }

    private int countShown(String filter) {
        String f = (filter == null) ? "" : filter.trim().toLowerCase();
        if (f.isEmpty()) return mSnippets.size();
        int n = 0;
        for (Snippet s : mSnippets)
            if (s.name.toLowerCase().contains(f) || s.cmd.toLowerCase().contains(f)) n++;
        return n;
    }

    private int getColorSafe(int resId, int fallback) {
        try { return ContextCompat.getColor(this, resId); }
        catch (Exception e) { return fallback; }
    }

    /**
     * 收藏夹路径：~/.termux/snippets.json
     *   .../files/home/.termux/snippets.json  ←→  终端 $HOME/.termux
     */
    private File getSnippetsFile() {
        File dir = new File(getApplicationContext().getFilesDir(), "home/.termux");
        dir.mkdirs();
        return new File(dir, SNIPPETS_FILE);
    }

    private void loadSnippets() {
        mSnippets.clear();
        try {
            File f = getSnippetsFile();
            if (!f.exists()) return;
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (s.trim().isEmpty()) return;
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) mSnippets.add(Snippet.fromJson(arr.getJSONObject(i)));
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "load snippets skipped: " + e.getMessage());
        }
    }

    private void saveSnippets() {
        try {
            File f = getSnippetsFile();
            JSONArray arr = new JSONArray();
            for (Snippet sn : mSnippets) arr.put(sn.toJson());
            String tmp = f.getPath() + ".tmp";
            try (FileWriter w = new FileWriter(tmp)) { w.write(arr.toString(2)); }
            new File(tmp).renameTo(f);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "save snippets failed", e);
        }
    }
}
