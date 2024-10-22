// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import androidx.annotation.Nullable;

import org.chromium.base.annotations.NativeMethods;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.Origin;

import java.nio.ByteBuffer;

/**
 * Bridge into native serialization, deserialization, and management of {@link WebContentsState}.
 */
public class WebContentsStateBridge {
    /**
     * Creates a WebContents from the buffer.
     * @param isHidden Whether or not the tab initially starts hidden.
     * @return Pointer A WebContents object.
     */
    public static WebContents restoreContentsFromByteBuffer(
            WebContentsState webContentsState, boolean isHidden) {
        return WebContentsStateBridgeJni.get().restoreContentsFromByteBuffer(
                webContentsState.buffer(), webContentsState.version(), isHidden);
    }

    /**
     * Deletes navigation entries from this buffer matching predicate.
     * @param predicate Handle for a deletion predicate interpreted by native code.
    Only valid during this call frame.
     * @return WebContentsState A new state or null if nothing changed.
     */
    @Nullable
    public static WebContentsState deleteNavigationEntries(
            WebContentsState webContentsState, long predicate) {
        ByteBuffer newBuffer = WebContentsStateBridgeJni.get().deleteNavigationEntries(
                webContentsState.buffer(), webContentsState.version(), predicate);
        if (newBuffer == null) return null;
        WebContentsState newState = new WebContentsState(newBuffer);
        newState.setVersion(WebContentsState.CONTENTS_STATE_CURRENT_VERSION);
        return newState;
    }

    /**
     * Creates a WebContentsState for a tab that will be loaded lazily.
     * @param url URL that is pending.
     * @param referrerUrl URL for the referrer.
     * @param referrerPolicy Policy for the referrer.
     * @param initiatorOrigin Initiator of the navigation.
     * @param isIncognito Whether or not the state is meant to be incognito (e.g. encrypted).
     * @return ByteBuffer that represents a state representing a single pending URL.
     */
    public static ByteBuffer createSingleNavigationStateAsByteBuffer(String url, String referrerUrl,
            int referrerPolicy, @Nullable Origin initiatorOrigin, boolean isIncognito) {
        return WebContentsStateBridgeJni.get().createSingleNavigationStateAsByteBuffer(
                url, referrerUrl, referrerPolicy, initiatorOrigin, isIncognito);
    }

    /**
     * Returns the WebContents' state as a ByteBuffer.
     * @param tab Tab to pickle.
     * @return ByteBuffer containing the state of the WebContents.
     */
    public static ByteBuffer getContentsStateAsByteBuffer(Tab tab) {
        return WebContentsStateBridgeJni.get().getContentsStateAsByteBuffer(tab);
    }

    /** @return Title currently being displayed in the saved state's current entry. */
    public static String getDisplayTitleFromState(WebContentsState contentsState) {
        return WebContentsStateBridgeJni.get().getDisplayTitleFromByteBuffer(
                contentsState.buffer(), contentsState.version());
    }

    /** @return URL currently being displayed in the saved state's current entry. */
    public static String getVirtualUrlFromState(WebContentsState contentsState) {
        return WebContentsStateBridgeJni.get().getVirtualUrlFromByteBuffer(
                contentsState.buffer(), contentsState.version());
    }

    public static void createHistoricalTabFromContents(WebContents webContents) {
        WebContentsStateBridgeJni.get().createHistoricalTabFromContents(webContents);
    }

    @NativeMethods
    interface Natives {
        WebContents restoreContentsFromByteBuffer(
                ByteBuffer buffer, int savedStateVersion, boolean initiallyHidden);
        ByteBuffer getContentsStateAsByteBuffer(Tab tab);
        ByteBuffer deleteNavigationEntries(ByteBuffer state, int saveStateVersion, long predicate);
        ByteBuffer createSingleNavigationStateAsByteBuffer(String url, String referrerUrl,
                int referrerPolicy, Origin initiatorOrigin, boolean isIncognito);
        String getDisplayTitleFromByteBuffer(ByteBuffer state, int savedStateVersion);
        String getVirtualUrlFromByteBuffer(ByteBuffer state, int savedStateVersion);
        void createHistoricalTabFromContents(WebContents webContents);
    }
}
