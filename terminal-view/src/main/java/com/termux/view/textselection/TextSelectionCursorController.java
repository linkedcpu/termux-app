package com.termux.view.textselection;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.WcWidth;
import com.termux.view.R;
import com.termux.view.TerminalView;

public class TextSelectionCursorController implements CursorController {

    private final TerminalView terminalView;
    private final TextSelectionHandleView mStartHandle, mEndHandle;
    private final BergamotTranslator mBergamotTranslator;
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

    public TextSelectionCursorController(TerminalView terminalView) {
        this.terminalView = terminalView;
        mStartHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT);
        mEndHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT);
        mHandleHeight = Math.max(mStartHandle.getHandleHeight(), mEndHandle.getHandleHeight());
        mBergamotTranslator = new BergamotTranslator(terminalView);
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
                menu.add(Menu.NONE, ACTION_TRANSLATE, Menu.NONE, R.string.translate_text).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                menu.add(Menu.NONE, ACTION_INSERT, Menu.NONE, R.string.insert_text).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
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
                            mBergamotTranslator.translate(text.trim());
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
