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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
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
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.Arrays;

/**
 * A terminal emulator activity.
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

    /* ========== TabBar（新增） ========== */
    private HorizontalScrollView mTabScrollView;
    private LinearLayout mTabContainer;

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

        /* ========== TabBar: 绑定（新增） ========== */
        mTabScrollView = findViewById(R.id.tab_scroll_view);
        mTabContainer = findViewById(R.id.tab_container);

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

        /* ========== TabBar: 初始渲染（新增） ========== */
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
        /* ========== TabBar: resume时刷新（新增） ========== */
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
        /* ========== TabBar: service连上后刷新（新增） ========== */
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

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
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
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        if (!TermuxActivity.this.isFinishing()) { finish(); }
    }

    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

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
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
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
    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() { return mTermuxTerminalSessionActivityClient; }

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

    /* ======================== */
    /* ========== TabBar ========== */
    /* ======================== */

    public void renderTabBar() {
        if (mTabContainer == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;

        mTabContainer.removeAllViews();
        java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions =
                service.getTermuxSessions();
        TerminalSession current = getCurrentSession();

        int tabHeight = (int) (36 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < sessions.size(); i++) {
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts = sessions.get(i);
            TerminalSession s = ts.getTerminalSession();
            if (s == null) continue;

            final TerminalSession clickSession = s;

            // Tab 容器
            LinearLayout tabLayout = new LinearLayout(this);
            tabLayout.setOrientation(LinearLayout.HORIZONTAL);
            tabLayout.setGravity(Gravity.CENTER_VERTICAL);
            int padH = (int) (12 * getResources().getDisplayMetrics().density);
            int padV = (int) (4 * getResources().getDisplayMetrics().density);
            tabLayout.setPadding(padH, padV, (int)(6 * getResources().getDisplayMetrics().density), padV);

            if (current != null && current == s) {
                tabLayout.setBackgroundColor(Color.parseColor("#1F6FEB"));
            } else {
                tabLayout.setBackgroundColor(Color.parseColor("#222222"));
            }

            // 会话名
            TextView tabText = new TextView(this);
            tabText.setText(s.mSessionName != null ? s.mSessionName : "Session " + (i + 1));
            tabText.setTextColor(Color.WHITE);
            tabText.setTextSize(12);
            tabText.setSingleLine(true);
            tabText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tabText.setPadding(0, 0, (int)(10 * getResources().getDisplayMetrics().density), 0);

            // 单击切换
            tabText.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.setCurrentSession(clickSession);
                }
                renderTabBar();
            });

            // 长按重命名
            tabText.setOnLongClickListener(v -> {
                renameSession(clickSession);
                return true;
            });

            // × 关闭按钮
            TextView closeBtn = new TextView(this);
            closeBtn.setText("×");
            closeBtn.setTextColor(Color.LTGRAY);
            closeBtn.setTextSize(16);
            int btnPad = (int)(6 * getResources().getDisplayMetrics().density);
            closeBtn.setPadding(btnPad, 0, btnPad, 0);
            closeBtn.setOnClickListener(v -> {
                closeSession(clickSession);
            });

            // 关闭按钮按下变红
            closeBtn.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        closeBtn.setTextColor(Color.RED);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        closeBtn.setTextColor(Color.LTGRAY);
                        break;
                }
                return false;
            });

            closeBtn.setClickable(true);
            closeBtn.setFocusable(true);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tabLayout.addView(tabText, textParams);
            tabLayout.addView(closeBtn);

            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tabParams.setMargins((int)(1 * getResources().getDisplayMetrics().density), 0,
                    (int)(1 * getResources().getDisplayMetrics().density), 0);
            mTabContainer.addView(tabLayout, tabParams);
        }

        // ＋ 新建按钮
        TextView addTab = new TextView(this);
        addTab.setText(" ＋ ");
        int addPadH = (int)(16 * getResources().getDisplayMetrics().density);
        int addPadV = (int)(0 * getResources().getDisplayMetrics().density);
        addTab.setPadding(addPadH, addPadV, addPadH, addPadV);
        addTab.setTextColor(Color.LTGRAY);
        addTab.setTextSize(16);
        addTab.setOnClickListener(v -> {
            if (mTermuxTerminalSessionActivityClient != null) {
                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            }
        });
        mTabContainer.addView(addTab);
    }

    /**
     * 关闭 session：走 Termux 正常退出流程 → 自动按回车 → Tab 消失
     */
    private void closeSession(TerminalSession session) {
        if (session == null) return;
        TermuxService service = getTermuxService();
        if (service == null) return;

        int index = service.getIndexOfSession(session);
        int size = service.getTermuxSessionsSize();

        // 1. 杀进程（走 Termux 正常退出流程，会显示 press Enter）
        session.finishIfRunning();

        // 2. 延迟自动按回车，跳过 press Enter 提示
        mTerminalView.postDelayed(() -> {
            // 自动替用户敲回车
            session.write("\n");

            // 3. 从列表移除
            service.removeTermuxSession(session);

            // 4. 切到下一个 session
            int newSize = service.getTermuxSessionsSize();
            if (newSize == 0) {
                finishActivityIfNotFinishing();
            } else {
                int newIndex = Math.max(0, Math.min(index, newSize - 1));
                com.termux.shared.termux.shell.command.runner.terminal.TermuxSession next =
                        service.getTermuxSession(newIndex);
                if (next != null && next.getTerminalSession() != null) {
                    mTerminalView.attachSession(next.getTerminalSession());
                    if (mTermuxTerminalSessionActivityClient != null) {
                        mTermuxTerminalSessionActivityClient.setCurrentSession(
                                next.getTerminalSession());
                    }
                }
            }

            // 5. 刷新
            mTerminalView.post(() -> renderTabBar());
        }, 10);
    }

    /**
     * 重命名 session
     */
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
                if (!name.isEmpty()) {
                    session.mSessionName = name;
                    renderTabBar();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
