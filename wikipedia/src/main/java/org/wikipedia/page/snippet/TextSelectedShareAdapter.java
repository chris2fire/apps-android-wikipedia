package org.wikipedia.page.snippet;

import android.annotation.TargetApi;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import org.wikipedia.WikipediaApp;
import org.wikipedia.page.PageActivity;
import org.wikipedia.util.ApiUtil;

/**
 * Allows sharing selected text in the WebView.
 */
public class TextSelectedShareAdapter extends ShareHandler {
    private ActionMode webViewActionMode;
    private ClipboardManager clipboard;
    private ClipboardManager.OnPrimaryClipChangedListener clipChangedListener;
    private MenuItem copyMenuItem;
    private MenuItem shareItem;
    private boolean expectClipChange = false;

    @TargetApi(11)
    public TextSelectedShareAdapter(PageActivity parentActivity) {
        super(parentActivity);
        clipboard = (ClipboardManager) parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        // add our clipboard listener, so that we'll get an event when the text
        // is copied onto it...
        clipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                // get the text from the clipboard!
                if (expectClipChange && clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip().getItemCount() > 0) {

                    expectClipChange = false;
                    CharSequence selectedText = clipboard.getPrimaryClip().getItemAt(0)
                                                         .coerceToText(getActivity());
                    Log.d("Share", ">>> Clipboard text: " + selectedText);

                    // Share the clipboard text!
                    shareSnippet(selectedText, false);
                    getFunnel().logShareTap(selectedText.toString());
                }
            }
        };
        clipboard.addPrimaryClipChangedListener(clipChangedListener);
    }

    @Override
    @TargetApi(11)
    public void onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipChangedListener);
        super.onDestroy();
    }

    /**
     * Since API <11 doesn't provide a long-press context for the WebView anyway, and we're
     * using clipboard features that are only supported in API 11+, we'll mark this whole
     * method as TargetApi(11), so that the IDE doesn't get upset.
     * @param mode ActionMode under which this context is starting.
     */
    public void onTextSelected(final ActionMode mode) {
        webViewActionMode = mode;
        Menu menu = mode.getMenu();

        // Find the context menu items from the WebView's action mode.
        // The most practical way to do this seems to be to get the resource name of the
        // menu item, and see if it contains "copy", "share", etc. which appears to remain
        // consistent throughout the various APIs.
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            try {
                String resourceName = getActivity().getResources().getResourceName(item.getItemId());
                if (resourceName.contains("copy")) {
                    copyMenuItem = item;
                } else if (resourceName.contains("share")) {
                    shareItem = item;
                }
                // In APIs lower than 21, some of the action mode icons may not respect the
                // current theme, so we need to manually tint those icons.
                if (!ApiUtil.hasLollipop()) {
                    fixMenuItemTheme(item);
                }
            } catch (Resources.NotFoundException e) {
                // Looks like some devices don't provide access to these menu items through
                // the context of the app, in which case, there's nothing we can do...
            }
        }

        // if we were unable to find the Copy or Share items, then we won't be able to offer
        // our custom Share functionality, so just fall back to the WebView's default sharing.
        if (copyMenuItem == null || shareItem == null) {
            return;
        }

        // intercept share menu...
        shareItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (copyMenuItem != null) {
                    // programmatically invoke the copy-to-clipboard action...
                    expectClipChange = true;
                    webViewActionMode.getMenu()
                            .performIdentifierAction(copyMenuItem.getItemId(), 0);
                    // this will trigger a state-change event in the Clipboard, which we'll
                    // catch with our listener above.
                }
                // leave context mode...
                if (webViewActionMode != null) {
                    webViewActionMode.finish();
                    webViewActionMode = null;
                }
                return true;
            }
        });

        createFunnel();
        getFunnel().logHighlight();
    }

    private void fixMenuItemTheme(MenuItem item) {
        if (item != null && item.getIcon() != null) {
            WikipediaApp.getInstance().setDrawableTint(item.getIcon(), Color.WHITE);
        }
    }
}
