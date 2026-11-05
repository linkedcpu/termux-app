package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.TermuxConstants;
import com.termux.app.TermuxService;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.terminal.io.BellHandler;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class TermuxTerminalSessionActivityClient extends TermuxTerminalSessionClientBase {

    /**
     * ★ 改为可变引用。onDestroy 时置 null，彻底切断对 Activity 的持有，
     *   避免 Service 长活导致 TermuxActivity 泄漏。
     */
    private TermuxActivity mActivity;
    private static final int MAX_SESSIONS = 8;
    private SoundPool mBellSoundPool;
    private int mBellSoundId;
    private static final String LOG_TAG = "TermuxTerminalSessionActivityClient";

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
    }

    /** ★ 必须是 public：TermuxActivity 在 com.termux.app，跨包调用 */
    public void detachFromActivity() {
        mActivity = null;
    }

    /** ★ 统一 UI 投递：已销毁的窗口绝不 post */
    private void ui(@NonNull Runnable r) {
        if (mActivity == null || mActivity.isDestroyed()) return;
        mActivity.runOnUiThread(r);
    }

    public void onCreate() {
        checkForFontAndColors();
    }

    public void onStart() {
        if (mActivity == null || mActivity.getTermuxService() == null) return;
        setCurrentSession(getCurrentStoredSessionOrLast());
        termuxSessionListNotifyUpdated();
        mActivity.getTerminalView().onScreenUpdated();
    }

    public void onResume() {
        loadBellSoundPool();
    }

    public void onStop() {
        setCurrentStoredSession();
        releaseBellSoundPool();
    }

    public void onReloadActivityStyling() {
        checkForFontAndColors();
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (mActivity == null || !mActivity.isVisible()) return;
        if (mActivity.getCurrentSession() == changedSession)
            mActivity.getTerminalView().onScreenUpdated();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (mActivity == null || !mActivity.isVisible()) return;
        if (updatedSession != mActivity.getCurrentSession()) {
            mActivity.showToast(toToastTitle(updatedSession), true);
        }
        termuxSessionListNotifyUpdated();
        ui(() -> mActivity.renderTabBar());
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null || service.wantsToStop()) {
            mActivity.finishActivityIfNotFinishing();
            return;
        }
        int index = service.getIndexOfSession(finishedSession);
        boolean isPluginExecutionCommandWithPendingResult = false;
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult =
                    termuxSession.getExecutionCommand().isPluginExecutionCommandWithPendingResult();
        }
        if (mActivity.isVisible() && finishedSession != mActivity.getCurrentSession()) {
            if (index >= 0)
                mActivity.showToast(toToastTitle(finishedSession) + " - exited", true);
        }
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            if (service.getTermuxSessionsSize() > 1 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        } else {
            if (finishedSession.getExitStatus() == 0
                    || finishedSession.getExitStatus() == 130
                    || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        }
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (mActivity == null || !mActivity.isVisible()) return;
        ShareUtils.copyTextToClipboard(mActivity, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (mActivity == null || !mActivity.isVisible()) return;
        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null) mActivity.getTerminalView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (mActivity == null || !mActivity.isVisible()) return;
        switch (mActivity.getProperties().getBellBehaviour()) {
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE:
                BellHandler.getInstance(mActivity).doBell();
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP:
                loadBellSoundPool();
                if (mBellSoundPool != null)
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                break;
        }
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mActivity != null && mActivity.getCurrentSession() == changedSession)
            updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        if (mActivity == null) return;
        if (enabled && !mActivity.isVisible()) return;
        mActivity.getTerminalView().setTerminalCursorBlinkerState(enabled, false);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null) termuxSession.getExecutionCommand().mPid = pid;
    }

    public void onResetTerminalSession() {
        if (mActivity != null)
            mActivity.getTerminalView().setTerminalCursorBlinkerState(true, true);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return mActivity != null ? mActivity.getProperties().getTerminalCursorStyle() : null;
    }

    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool =
                    new SoundPool.Builder()
                            .setMaxStreams(1)
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                            .build())
                            .build();
            try {
                mBellSoundId = mBellSoundPool.load(mActivity, com.termux.shared.R.raw.bell, 1);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }

    public void setCurrentSession(TerminalSession session) {
        if (session == null || mActivity == null) return;
        if (mActivity.getTerminalView().attachSession(session)) {
            notifyOfSessionChange();
        }
        checkAndScrollToSession(session);
        updateBackgroundColor();
        ui(() -> mActivity.renderTabBar());
    }

    void notifyOfSessionChange() {
        if (mActivity == null || !mActivity.isVisible()) return;
        if (!mActivity.getProperties().areTerminalSessionChangeToastsDisabled()) {
            TerminalSession session = mActivity.getCurrentSession();
            mActivity.showToast(toToastTitle(session), false);
        }
    }

    public void switchToSession(boolean forward) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        TerminalSession currentTerminalSession = mActivity.getCurrentSession();
        int index = service.getIndexOfSession(currentTerminalSession);
        int size = service.getTermuxSessionsSize();
        if (forward) {
            if (++index >= size) index = 0;
        } else {
            if (--index < 0) index = size - 1;
        }
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) setCurrentSession(termuxSession.getTerminalSession());
    }

    public void switchToSession(int index) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) setCurrentSession(termuxSession.getTerminalSession());
    }

    @SuppressLint("InflateParams")
    public void renameSession(final TerminalSession sessionToRename) {
        if (sessionToRename == null || mActivity == null) return;
        TextInputDialogUtils.textInput(
                mActivity,
                R.string.title_rename_session,
                sessionToRename.mSessionName,
                R.string.action_rename_session_confirm,
                text -> {
                    renameSession(sessionToRename, text);
                    termuxSessionListNotifyUpdated();
                },
                -1, null, -1, null, null);
    }

    private void renameSession(TerminalSession sessionToRename, String text) {
        if (sessionToRename == null || mActivity == null) return;
        sessionToRename.mSessionName = text;
        TermuxService service = mActivity.getTermuxService();
        if (service != null) {
            TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename);
            if (termuxSession != null) termuxSession.getExecutionCommand().shellName = text;
        }
    }

    public void addNewSession(boolean isFailSafe, String sessionName) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.title_max_terminals_reached)
                    .setMessage(R.string.msg_max_terminals_reached)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        TerminalSession currentSession = mActivity.getCurrentSession();
        String workingDirectory = currentSession == null
                ? mActivity.getProperties().getDefaultWorkingDirectory()
                : currentSession.getCwd();
        TermuxSession newTermuxSession =
                service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName);
        if (newTermuxSession == null) return;
        setCurrentSession(newTermuxSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
        ui(() -> mActivity.renderTabBar());
    }

    public void setCurrentStoredSession() {
        if (mActivity == null) return;
        TerminalSession currentSession = mActivity.getCurrentSession();
        if (currentSession != null)
            mActivity.getPreferences().setCurrentSession(currentSession.mHandle);
        else mActivity.getPreferences().setCurrentSession(null);
    }

    public TerminalSession getCurrentStoredSessionOrLast() {
        if (mActivity == null) return null;
        TerminalSession stored = getCurrentStoredSession();
        if (stored != null) return stored;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        TermuxSession termuxSession = service.getLastTermuxSession();
        return termuxSession != null ? termuxSession.getTerminalSession() : null;
    }

    private TerminalSession getCurrentStoredSession() {
        if (mActivity == null) return null;
        String sessionHandle = mActivity.getPreferences().getCurrentSession();
        if (sessionHandle == null) return null;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        return service.getTerminalSessionForHandle(sessionHandle);
    }

    public void removeFinishedSession(TerminalSession finishedSession) {
        if (mActivity == null) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        int index = service.removeTermuxSession(finishedSession);
        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            mActivity.finishActivityIfNotFinishing();
        } else {
            if (index >= size) index = size - 1;
            TermuxSession termuxSession = service.getTermuxSession(index);
            if (termuxSession != null) setCurrentSession(termuxSession.getTerminalSession());
        }
        ui(() -> mActivity.renderTabBar());
    }

    /**
     * ★ 抽屉 session 列表已移除：通知刷新统一转交底部 SessionTabBar。
     *   保留方法签名，外部调用方（TermuxActivity 等）零改动。
     */
    public void termuxSessionListNotifyUpdated() {
        if (mActivity != null) mActivity.renderTabBar();
    }

    /**
     * ★ 把当前 session 对应的标签滚到可视区并居中。
     *
     * 全程只用 android.view.View / ViewGroup，不引用 HorizontalScrollView 类型；
     * 滚动通过反射调用 HorizontalScrollView#smoothScrollTo(int,int) 完成，
     * 规避构建环境里 `cannot find symbol: HorizontalScrollView` 的误报。
     * 反射失败时静默降级为“仅刷新高亮”，不崩溃。
     *
     * ★ lambda 内不修改任何外层局部变量（effectively final），避免 javac 报错。
     */
    public void checkAndScrollToSession(@Nullable TerminalSession session) {
        if (mActivity == null || mActivity.isDestroyed()) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null || session == null) return;

        // ★ 外层只读一次，effectively final
        final int initialIdx = service.getIndexOfSession(session);
        if (initialIdx < 0) return;

        mActivity.getTerminalView().post(() -> {
            if (mActivity == null || mActivity.isDestroyed()) return;

            View scroll = mActivity.findViewById(R.id.tab_scroll_view);
            ViewGroup container = mActivity.findViewById(R.id.tab_container);
            if (scroll == null || container == null) return;

            int childCount = container.getChildCount();
            if (childCount <= 0) return;

            // ★ 新的局部变量，只在 lambda 内生效，合法赋值
            int childIdx = initialIdx;
            if (childIdx >= childCount) childIdx = childCount - 1;

            View tab = container.getChildAt(childIdx);
            if (tab == null) return;

            int sw = scroll.getWidth();
            int left = tab.getLeft();
            int right = tab.getRight();
            if (sw <= 0) {
                smoothScrollTo(scroll, left, 0);
                return;
            }
            int target = left - (sw - (right - left)) / 2;
            int maxScroll = Math.max(0, container.getWidth() - sw);
            smoothScrollTo(scroll, Math.max(0, Math.min(target, maxScroll)), 0);
        });
    }

    /** 反射调用 HorizontalScrollView.smoothScrollTo(int, int)，失败静默忽略 */
    @SuppressWarnings({"deprecation", "unchecked"})
    private static void smoothScrollTo(@NonNull View scroll, int x, int y) {
        try {
            Class<?> hsv = Class.forName("android.widget.HorizontalScrollView");
            if (!hsv.isInstance(scroll)) return;
            hsv.getMethod("smoothScrollTo", int.class, int.class).invoke(scroll, x, y);
        } catch (Throwable ignored) {
            // 环境不支持反射调用时，至少不影响其他流程
        }
    }

    String toToastTitle(TerminalSession session) {
        if (mActivity == null) return null;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        int idx = service.getIndexOfSession(session);
        if (idx < 0) return null;
        StringBuilder sb = new StringBuilder("[" + (idx + 1) + "]");
        if (!TextUtils.isEmpty(session.mSessionName)) sb.append(" ").append(session.mSessionName);
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            sb.append(session.mSessionName == null ? " " : "\n").append(title);
        }
        return sb.toString();
    }

    public void checkForFontAndColors() {
        try {
            File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
            File fontFile = TermuxConstants.TERMUX_FONT_FILE;
            final Properties props = new Properties();
            if (colorsFile.isFile()) {
                try (InputStream in = new FileInputStream(colorsFile)) {
                    props.load(in);
                }
            }
            TerminalColors.COLOR_SCHEME.updateWith(props);
            if (mActivity != null) {
                TerminalSession session = mActivity.getCurrentSession();
                if (session != null && session.getEmulator() != null) {
                    session.getEmulator().mColors.reset();
                }
                updateBackgroundColor();
                Typeface tf = (fontFile.exists() && fontFile.length() > 0)
                        ? Typeface.createFromFile(fontFile)
                        : Typeface.MONOSPACE;
                mActivity.getTerminalView().setTypeface(tf);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in checkForFontAndColors()", e);
        }
    }

    public void updateBackgroundColor() {
        if (mActivity == null || !mActivity.isVisible()) return;
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            mActivity.getWindow().getDecorView()
                    .setBackgroundColor(
                            session.getEmulator()
                                    .mColors
                                    .mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }
}
