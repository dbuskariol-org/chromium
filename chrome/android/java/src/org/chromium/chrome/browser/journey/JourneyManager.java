// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.journey;

import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.content.Context;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.SparseArray;

import org.chromium.base.Callback;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.RenderFrameHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JNINamespace("journey")
public class JourneyManager {
    public interface JourneyUpdateListener { void onJourneyMapUpdated(); }

    private final Map<Long, Journey> mTimestampToJourneyMap;
    private final Map<String, Journey> mIDToJourneyMap;
    private final TabModelSelectorTabObserver mObserver;
    private final TabContentManager mTabContentManager;
    private final Map<Integer, Runnable> mFetchLaterRunnables;
    private final int mAutotabsSelectionType;


    private long mNativeJourneyManager;
    private JourneyUpdateListener mListener;
    private boolean mTabStateInitialized;
    private boolean mActiveTabFetchedAfterRestore;
    private boolean mFetchAllDone;
    
    private Map<Long, Integer> mRetryCountMap = new HashMap<>();


    /**
     *
     */
    public JourneyManager(final TabModelSelector selector, TabContentManager tabContentManager) {
        mFetchLaterRunnables = new HashMap<>();

        mObserver = new TabModelSelectorTabObserver(selector) {
            @Override
            public void onNavigationEntryCommitted(Tab tab, int index, boolean isNewRoot) {
                android.util.Log.e("TASK", "onNavigationEntryCommitted with root timestamp "+tab.getCurrentRootTimestamp());

                // Ignore reloads and for tab restores map the journey for the restores timestamp
                // to the timestamp of the restored entry. These will eventually join behind the
                // scenes, but there is more client-server fixes to make that possible.
                if (isLastCommitAReloadOrRestore(tab)) {
                    android.util.Log.e("Yusuf",
                            "JourneyManager.java#onNavigationEntryCommitted : Last entry is a restore  ");
                    return;
                }

                // We should ignore the first two entries on a new task if there is already a
                // non-empty journey for the previous task. This ensures we don't churn automaps
                // while figuring out which journey the new task belongs to.
                if (didTabJustStartANewTask(tab)) return;
                fetchJourneyInfo(tab);
            }

            @Override
            public void onTabRestored(Tab tab) {
                fetchJourneyInfo(tab);
                mActiveTabFetchedAfterRestore = true;
                if (mTabStateInitialized) {
                    postRestoreFetch(selector.getCurrentModel());
                }
            }
        };
        selector.getCurrentModel().addObserver(new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, int type, int lastId) {
                if (type == (TabSelectionType.FROM_EXIT)
                        || type == (TabSelectionType.FROM_NEW)) {
                    return;
                }
                if (tab.isBeingRestored() || tab.isFrozen()) return;
                if (lastId == tab.getId()) return;
                android.util.Log.e(
                        "Yusuf", "JourneyManager.java#didSelectTab : Fetching for selected tab");
                fetchJourneyInfo(tab);
            }
        });

        selector.addObserver(new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabStateInitialized() {
                mTabStateInitialized = true;
                if (mActiveTabFetchedAfterRestore) {
                    postRestoreFetch(selector.getCurrentModel());
                }
            }
        });
        mTabContentManager = tabContentManager;
        String autotabsSelectionType = CommandLine.getInstance().getSwitchValue(ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC, "");
        if (ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC_LEAVES.equals(autotabsSelectionType)) {
            mAutotabsSelectionType = 0;
        } else if (ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC_BRANCHES.equals(autotabsSelectionType)) {
            mAutotabsSelectionType = 1;
        } else if (ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC_LAST_IN_TAB.equals(autotabsSelectionType)) {
            mAutotabsSelectionType = 2;
        } else if (ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC_LAST_IN_TASK.equals(autotabsSelectionType)) {
            mAutotabsSelectionType = 3;
        } else if (ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC_LLiTNS.equals(autotabsSelectionType)) {
            mAutotabsSelectionType = 4;
        } else {
                mAutotabsSelectionType = -1;
        }
        mNativeJourneyManager =
                nativeInit(Profile.getLastUsedProfile().getOriginalProfile(), tabContentManager, mAutotabsSelectionType);
        mTimestampToJourneyMap = new HashMap<>();
        mIDToJourneyMap = new HashMap<>();
    }


    private void postRestoreFetch(final TabList list) {
        if (mFetchAllDone) return;
        mFetchAllDone = true;
        int count = list.getCount();
            for (int i = count - 1; i >= 0; i--) {
                Tab tab = list.getTabAt(i);
                if ((tab.getTimestampMillis() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24) >= 7f) continue;
                ThreadUtils.postOnUiThreadDelayed(() -> {
                            fetchJourneyInfo(tab);
                 }, 2000 + 3000*(count - i));
                android.util.Log.e("RESTORE", "Looking at tab "+i+" with timestamp "+tab.getCurrentRootTimestamp());
            }

    }

    public void setListener(JourneyUpdateListener listener) {
        mListener = listener;
    }

    private void fetchJourneyInfo(final Tab tab) {
        android.util.Log.e("journey_info",
                "JourneyManager.java#FETCHING START " + CommandLine.getInstance().getSwitchValue(ChromeSwitches.AUTOTABS_IMPORTANT_PAGES_HEURISTIC));
        // Use the root timestamp for a task if we can reach it. That way, we don't have to use
        // a new journey for every entry.
        long timestamp = tab.getCurrentRootTimestamp();
        if (timestamp == 0) return;
        android.util.Log.e("journey_info",
                "JourneyManager.java#FETCHING START Not rejected because of 0 timestamp");
        Journey journey = mTimestampToJourneyMap.get(timestamp);
        if (journey == null) journey = new Journey(timestamp);
        long now = System.currentTimeMillis();
        boolean journeyTooNew = !journey.hasJourneyInfo() && ((now - timestamp / 1000) < 20000);
        boolean alreadyHasRecentFetch = journey.hasJourneyInfo() && now - journey.getLastFetchTime() < 30000;
        int delayedMs = 0;
        if (journeyTooNew) delayedMs = (int) (20000 - now + timestamp / 1000);
        if (alreadyHasRecentFetch) delayedMs = (int) (30500 - now + journey.getLastFetchTime());
        android.util.Log.e("journey_info",
                "JourneyManager.java#fetchJourneyInfo : Too new check is  "+now+":"+timestamp+":"+journeyTooNew+":"+delayedMs);
        if (alreadyHasRecentFetch || journeyTooNew) {
            if (mFetchLaterRunnables.get(tab.getId()) == null) {
                Runnable runnable = () -> {
                    fetchJourneyInfo(tab);
                    mFetchLaterRunnables. put(tab.getId(), null);
                };
                mFetchLaterRunnables.put(tab.getId(), null);
                ThreadUtils.postOnUiThreadDelayed(runnable, delayedMs);
                android.util.Log.e("journey_info",
                        "JourneyManager.java#fetchJourneyInfo : Posted delayed fetch for "
                                + timestamp);
            }
            return;
        }
        long[] timestamps = new long[1];
        timestamps[0] = timestamp;
        nativeFetchJourneyInfo(mNativeJourneyManager, timestamps);
        journey.setLastFetchTime(now);
        mTimestampToJourneyMap.put(timestamp, journey);
        android.util.Log.e("journey_info",
                "JourneyManager.java#addAutoTabForTimestamp : Fetching journey info for "
                        + timestamp);
    }

    @CalledByNative
    private void emptyJourneyFetched(long sourceTimestamp) {
        Integer count = mRetryCountMap.get(sourceTimestamp);
        if (count == null || count < 6) {
            long[] timestamps = new long[1];
            timestamps[0] = sourceTimestamp;
            Runnable runnable = () -> {
                nativeFetchJourneyInfo(mNativeJourneyManager, timestamps);
            };
            if (count == null) count = 0;
            ThreadUtils.postOnUiThreadDelayed(runnable, Math.min(count * 10000, 20000) + 10000);
            android.util.Log.e("Yusuf","Posting delayed fetch for "+sourceTimestamp+" after empty journey received due to no timestamp");

            mRetryCountMap.put(sourceTimestamp, count++);
        }

    }

    @CalledByNative
    private void addAutoTabForTimestamp(
            long sourceTimestamp, long autotabTimestamp, String url, String journeyID) {
        if (mIDToJourneyMap.containsKey(journeyID)) {
            Journey existingJourney = mIDToJourneyMap.get(journeyID);
            existingJourney.addPageload(autotabTimestamp, url);
            mTimestampToJourneyMap.put(sourceTimestamp, existingJourney);
            android.util.Log.e("Yusuf",
                    "JourneyManager.java#addAutoTabForTimestamp : Putting existing journey with ID "
                            + journeyID + " to map with " + sourceTimestamp
                            + " after adding new autotab for " + autotabTimestamp + " and url "
                            + url);

        } else {
            Journey journey = mTimestampToJourneyMap.get(sourceTimestamp);
            journey.addPageload(autotabTimestamp, url);
            journey.updateID(journeyID);
            mIDToJourneyMap.put(journeyID, journey);
            android.util.Log.e("Yusuf",
                    "JourneyManager.java#addAutoTabForTimestamp : Mapping new journey with ID "
                            + journeyID + " to map with " + sourceTimestamp
                            + " after adding new autotab for " + autotabTimestamp + " and url "
                            + url);
        }
        android.util.Log.e("LOSTJOURNEY",
                "JourneyManager.java#getAutoTabInfoFor : Timestamp map has " +mTimestampToJourneyMap.size()+" and ID map has "+mIDToJourneyMap.size()+ " journeys");
    }

    @CalledByNative
    private String getThumbnailUrlForUrl(String url) {
        return "";
    }

    public List<Long> getAutoTabInfoFor(Tab tab) {
        long timestamp = tab.getCurrentRootTimestamp();
        List<Long> timestamps = new ArrayList<>();
        if (timestamp == 0) return timestamps;
        Journey journey = mTimestampToJourneyMap.get(timestamp);
        android.util.Log.e("LOSTJOURNEY",
                "JourneyManager.java#getAutoTabInfoFor : Timestamp map has " +mTimestampToJourneyMap.size()+" and ID map has "+mIDToJourneyMap.size()+ " journeys");
        if (journey == null) {
            android.util.Log.e("Yusuf",
                    "JourneyManager.java#getAutoTabInfoFor : Cant find journey for tab id "+ tab.getId() + " with timestamp " + timestamp);
        }
        android.util.Log.e("Yusuf",
                "JourneyManager.java#addAutoTabForTimestamp : Getting info for tab id "
                        + tab.getId() + " with timestamp " + timestamp + " and size is "
                        + (mTimestampToJourneyMap.get(timestamp) == null
                                          ? 0
                                          : mTimestampToJourneyMap.get(timestamp)
                                                    .getTimestampsList(tab)
                                                    .size()));
        if (journey == null) {
            return timestamps;
        }
        return journey.getTimestampsList(tab);
    }

    public List<Long> onLongPressToAutotab(
            TabModelSelector selector, int currentIndex, long timestampForAutotab) {
        Tab tab = selector.getModel(false).getTabAt(currentIndex);
        long taskTimestamp = tab.getCurrentRootTimestamp();
        assert taskTimestamp != 0;
        Journey journey = mTimestampToJourneyMap.get(taskTimestamp);
        assert journey != null;
        Vibrator vb = (Vibrator) ContextUtils.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        vb.vibrate(200);
        return journey.onLongPressToAutotab(timestampForAutotab, tab);
    }

    public int onClickToAutotab(
            TabModelSelector selector, int currentIndex, long timestampForAutotab) {
        Tab tab = selector.getModel(false).getTabAt(currentIndex);
        long taskTimestamp = tab.getCurrentRootTimestamp();
        android.util.Log.e("CLICK",
                "JourneyManager.java#onClickToAutotab : Task timestamp is  " + taskTimestamp+" with currentIndex "+currentIndex+ " and tab url "+tab.getUrl());
        assert taskTimestamp != 0;
        Journey journey = mTimestampToJourneyMap.get(taskTimestamp);
        assert journey != null;
        String url = journey.onClickToAutotab(timestampForAutotab, tab);
        android.util.Log.e("CLICK",
                "JourneyManager.java#onClickToAutotab : CLICK HIT AUTOTAB WITH URL " + url);
        TabModel model = selector.getModel(false);
        int switchedIndex = -1;
        for (int i = model.getCount() - 1; i >= 0; i--) {
            if (TextUtils.equals(model.getTabAt(i).getUrl(), url)) switchedIndex = i;
        }

        android.util.Log.e("CLICK",
                "JourneyManager.java#onClickToAutotab : Switch index after searching throught tabs is " + switchedIndex);

        if (switchedIndex == -1) {
            for (int i = model.getCount() - 1; i >= 0; i--) {
                int entryIndex = getIndexOfEntryWithUrl(model.getTabAt(i), url);
                if (entryIndex != -1) {
                    tab.getWebContents().getNavigationController().goToIndex(entryIndex);
                    switchedIndex = i;
                    break;
                }
            }
            android.util.Log.e("CLICK",
                    "JourneyManager.java#onClickToAutotab : Switch index after searching throught entries is " + switchedIndex);
            if (switchedIndex != -1) {
                RecordUserAction.record("Autotabs.AutotabSource.Client");
            }

        } else {
            RecordUserAction.record("Autotabs.AutotabSource.Tab");
        }

        if (switchedIndex == -1) {
            tab.loadUrl(new LoadUrlParams(url));
            RecordUserAction.record("Autotabs.AutotabSource.User");
            switchedIndex = currentIndex;
            android.util.Log.e("CLICK",
                    "JourneyManager.java#onClickToAutotab : Loading page on current tab ");
        }
        return switchedIndex;
    }

    public void destroy() {
        if (mNativeJourneyManager == 0) return;
        nativeDestroy(mNativeJourneyManager);
        mNativeJourneyManager = 0;
    }
    private static boolean entryStartsANewTask(NavigationEntry entry) {
        assert entry != null;
        return (entry.getTransition() & 0xFF) == 1 || (entry.getTransition() & 0xFF) == 5;
    }

    private static int getIndexOfEntryWithUrl(Tab tab, String url) {
        if (tab == null || tab.getWebContents() == null || tab.getWebContents().getNavigationController() == null) {
            return -1;
        }

        if (TextUtils.isEmpty(url)) return -1;

        NavigationController controller = tab.getWebContents().getNavigationController();
        for (int i = 0; i < controller.getLastCommittedEntryIndex(); i++) {
            if (controller.getEntryAtIndex(i).getUrl().equals(url)) return i;
        }
        return -1;
    }

    private static int getIndexForLastCommit(Tab tab) {
        if (tab == null) return -1;
        if (tab.getWebContents() == null
                || tab.getWebContents().getNavigationController() == null) {
            return -1;
        }

        NavigationController controller = tab.getWebContents().getNavigationController();
        return controller.getLastCommittedEntryIndex();
    }



    private static boolean isLastCommitAReloadOrRestore(Tab tab) {
        NavigationController controller = tab.getWebContents().getNavigationController();
        int lastIndex = controller.getLastCommittedEntryIndex();
        return (controller.getEntryAtIndex(lastIndex).getTransition() & 0xFF) == 8;
    }

    private boolean didTabJustStartANewTask(Tab tab) {
        if (tab == null || tab.getWebContents() == null
                || tab.getWebContents().getNavigationController() == null) {
            return true;
        }
        NavigationController controller = tab.getWebContents().getNavigationController();
        int lastIndex = controller.getLastCommittedEntryIndex();
        if (lastIndex > 1) {
            Journey journeyForPreviousEntry =
                    mTimestampToJourneyMap.get(tab.getCurrentRootTimestamp());
            boolean previousJourneyHasInfo =
                    journeyForPreviousEntry != null && journeyForPreviousEntry.hasJourneyInfo();
            if (entryStartsANewTask(controller.getEntryAtIndex(lastIndex))
                    && previousJourneyHasInfo) {
                return true;
            }
            if (lastIndex > 2) {
                journeyForPreviousEntry =
                        mTimestampToJourneyMap.get(tab.getPreviousRootTimestamp());
                previousJourneyHasInfo =
                        journeyForPreviousEntry != null && journeyForPreviousEntry.hasJourneyInfo();
                if (entryStartsANewTask(controller.getEntryAtIndex(lastIndex - 1))
                        && previousJourneyHasInfo) {
                    return true;
                }
            }
        }
        return false;
    }

    private native long nativeInit(Profile profile, TabContentManager tabContentManager, int autotabsSelectionType);
    private native void nativeDestroy(long nativeJourneyManager);
    private native void nativeFetchJourneyInfo(long nativeJourneyManager, long[] timestamp);
}