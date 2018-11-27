// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.journey;

import android.annotation.TargetApi;
import android.support.v4.util.ArraySet;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Journey {
    public static class Autotab {
        private static final String KEY_PINNED_AUTOTABS = "PINNED_AUTOTABS_SET";

        private static Set<String> sPinnedAutotabs;

        public final long timestamp;
        public final String url;
        private boolean mPinned;

        @TargetApi(16)
        Autotab(long timestamp, String url) {
            if (sPinnedAutotabs == null) {
                sPinnedAutotabs = ContextUtils.getAppSharedPreferences().getStringSet(
                        KEY_PINNED_AUTOTABS, new ArraySet<>());
            }
            this.timestamp = timestamp;
            this.url = url;
            if (sPinnedAutotabs.contains(String.valueOf(timestamp))) this.setPinned(true);
        }

        public boolean isPinned() {
            return mPinned;
        }

        @TargetApi(16)
        public void setPinned(boolean pinned) {
            mPinned = pinned;
            if (mPinned) {
                sPinnedAutotabs.add(String.valueOf(timestamp));
            } else {
                sPinnedAutotabs.remove(String.valueOf(timestamp));
            }
            ContextUtils.getAppSharedPreferences()
                    .edit()
                    .putStringSet(KEY_PINNED_AUTOTABS, sPinnedAutotabs)
                    .apply();
        }
    }

    private final LongSparseArray<Autotab> mImportantPageMap;

    private String mJourneyID;
    private long mRootTimestamp;
    private long mLastFetchTime;
    private String mLastSwitchedFrom;

    public static boolean isTimestampPinned(long timestamp) {
        if (Autotab.sPinnedAutotabs == null) return false;
        return Autotab.sPinnedAutotabs.contains(String.valueOf(timestamp));
    }

    Journey(long timestamp) {
        mRootTimestamp = timestamp;
        mImportantPageMap = new LongSparseArray<>();
    }

    public boolean isEmpty() {
        return mImportantPageMap.size() == 0;
    }

    public boolean hasJourneyInfo() {
        return mImportantPageMap.size() > 1;
    }

    public long getLastFetchTime() {
        return mLastFetchTime;
    }

    public void setLastFetchTime(long lastFetched) {
        mLastFetchTime = lastFetched;
    }

    public long getRootTimestamp() {
        return mRootTimestamp;
    }

    public void updateRootTimestamp(long rootTimestamp) {
        mRootTimestamp = rootTimestamp;
    }

    public String getID() {
        return mJourneyID;
    }

    public void updateID(String id) {
        mJourneyID = id;
    }

    public boolean addPageload(long timestamp, String url) {
        android.util.Log.e("LOSTJOURNEY",
                "Journey.java#addPageload : Trying to add timestamp : " + timestamp
                        + "to journey ID " + mJourneyID + "  and url is " + url);
        if (mImportantPageMap.get(timestamp, null) != null) return false;
        android.util.Log.e("LOSTJOURNEY",
                "Journey.java#addPageload : Couldnt find timestamp : " + timestamp
                        + "in journey ID " + mJourneyID);
        int index = indexOfUrl(url);
        if (index != -1) {
            long previousTimestamp = mImportantPageMap.keyAt(index);
            android.util.Log.e("LOSTJOURNEY",
                    "Journey.java#addPageload : Found same url as : " + timestamp + " at timestamp "
                            + previousTimestamp);
            if (previousTimestamp > timestamp) {
                return false;
            } else {
                mImportantPageMap.removeAt(index);
            }
        }
        android.util.Log.e("LOSTJOURNEY",
                "Journey.java#addPageload : Adding : " + timestamp + "to journey ID " + mJourneyID);
        mImportantPageMap.put(timestamp, new Autotab(timestamp, url));
        return true;
    }

    public List<Long> getTimestampsList(Tab tab) {
        String url = tab.getUrl();

        List<Long> pinned = getAllPinnedAutotabs();
        List<Long> list = new ArrayList<>(pinned);

        long lastSwitchedTimestamp = 0;

        // Add the autotab we have switched from first.
        if (!TextUtils.isEmpty(mLastSwitchedFrom)) {
            int index = indexOfUrl(mLastSwitchedFrom);
            if (index != -1) lastSwitchedTimestamp = mImportantPageMap.keyAt(index);
            android.util.Log.e("LOSTJOURNEY",
                    "JourneyManager.java#Adding last switched from : " + mLastSwitchedFrom);
            if (!list.contains(lastSwitchedTimestamp) && lastSwitchedTimestamp != 0) {
                list.add(lastSwitchedTimestamp);
            }
        }

        android.util.Log.e("LOSTJOURNEY",
                "JourneyManager.java#Before adding rest list : " + list.toString()
                        + " for journey ID " + mJourneyID);

        // Get the timestamp for the current page if it has an autotab.
        int index = indexOfUrl(url);
        long currentTimestamp = index != -1 ? mImportantPageMap.keyAt(index) : -1;

        android.util.Log.e("LOSTJOURNEY",
                "JourneyManager.java#Current timestamp found as  : " + currentTimestamp);

        if (list.contains(currentTimestamp)) {
            android.util.Log.e("LOSTJOURNEY",
                    "JourneyManager.java#Current timestamp already in the list so removing");
            list.remove(currentTimestamp);
        }

        for (int i = 0; i < mImportantPageMap.size(); i++) {
            long timestamp = mImportantPageMap.keyAt(i);
            if (list.contains(timestamp) || timestamp == currentTimestamp) continue;
            list.add(timestamp);
        }

        int prioritySize = pinned.size()
                + (lastSwitchedTimestamp != 0 && !isTimestampPinned(lastSwitchedTimestamp) ? 1 : 0);
        android.util.Log.e("LOSTJOURNEY",
                "JourneyManager.java#Final list before sort: " + list.toString()
                        + " for journey ID " + mJourneyID);

        // Make sure that everything other than the first is time sorted.
        if (list.size() - prioritySize > 1) {
            android.util.Log.e("LOSTJOURNEY",
                    "JourneyManager.java#Sorting from : " + prioritySize + ":" + (list.size() - 1));
            Collections.sort(list.subList(prioritySize, list.size()), Collections.reverseOrder());
        }

        android.util.Log.e("LOSTJOURNEY", "JourneyManager.java#Final list : " + list.toString());
        return list;
    }

    private List<Long> getAllPinnedAutotabs() {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < mImportantPageMap.size(); i++) {
            if (mImportantPageMap.valueAt(i).isPinned()) list.add(mImportantPageMap.keyAt(i));
        }
        Collections.sort(list, Collections.reverseOrder());
        android.util.Log.e(
                "PINNING", "JourneyManager.java#getAllPinnedAutotabs : " + list.toString());
        return list;
    }

    private int indexOfUrl(String url) {
        for (int i = 0; i < mImportantPageMap.size(); i++) {
            if (UrlUtilities.urlsMatchIgnoringFragments(mImportantPageMap.valueAt(i).url, url))
                return i;
        }
        return -1;
    }

    public String onClickToAutotab(long timestamp, Tab tab) {
        String url = tab.getUrl();
        android.util.Log.e("CLICKING",
                "JourneyManager.java#onClickToAutotab : Clicking timestamp " + timestamp
                        + " and the journey has " + getTimestampsList(tab).contains(timestamp)
                        + " with total size " + mImportantPageMap.size());
        if (mImportantPageMap.get(timestamp, null) == null) return "";

        mLastSwitchedFrom = url;

        android.util.Log.e("CLICKING",
                "JourneyManager.java#onClickToAutotab : Will put timestamp " + mLastSwitchedFrom
                        + " with url " + url + " as first in line");
        return mImportantPageMap.get(timestamp).url;
    }

    public List<Long> onLongPressToAutotab(long timestamp, Tab tab) {
        android.util.Log.e("PINNING",
                "JourneyManager.java#onLongPressToAutotab : Autotab at  " + timestamp
                        + " long pressed");
        if (mImportantPageMap.get(timestamp, null) == null) return null;
        Autotab autotab = mImportantPageMap.get(timestamp);
        autotab.setPinned(!autotab.isPinned());
        android.util.Log.e("PINNING",
                "JourneyManager.java#onLongPressToAutotab : Autotab at  " + timestamp
                        + " pinned is " + autotab.isPinned());
        return getTimestampsList(tab);
    }
}
