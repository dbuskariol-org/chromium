// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;

import org.chromium.base.Callback;
import org.chromium.base.supplier.Supplier;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.GlobalDiscardableReferencePool;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.image_fetcher.ImageFetcher;
import org.chromium.chrome.browser.image_fetcher.ImageFetcherConfig;
import org.chromium.chrome.browser.image_fetcher.ImageFetcherFactory;
import org.chromium.chrome.browser.omnibox.UrlBarEditingTextStateProvider;
import org.chromium.chrome.browser.omnibox.suggestions.answer.AnswerSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.basic.BasicSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.basic.SuggestionHost;
import org.chromium.chrome.browser.omnibox.suggestions.clipboard.ClipboardSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.editurl.EditUrlSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.entity.EntitySuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.header.HeaderProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.tail.TailSuggestionProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.tiles.TileSuggestionProcessor;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.share.ShareDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.ui.favicon.LargeIconBridge;
import org.chromium.components.browser_ui.util.ConversionUtils;
import org.chromium.components.query_tiles.QueryTile;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Builds DropdownItemViewInfo list from AutocompleteResult for the Suggestions list. */
class DropdownItemViewInfoListBuilder {
    private static final int MAX_IMAGE_CACHE_SIZE = 500 * ConversionUtils.BYTES_PER_KILOBYTE;
    @Px
    private static final int DROPDOWN_HEIGHT_UNKNOWN = -1;
    private static final int DEFAULT_SIZE_OF_VISIBLE_GROUP = 5;

    private final List<SuggestionProcessor> mPriorityOrderedSuggestionProcessors;

    private HeaderProcessor mHeaderProcessor;
    private ActivityTabProvider mActivityTabProvider;
    private Supplier<ShareDelegate> mShareDelegateSupplier;
    private ImageFetcher mImageFetcher;
    private LargeIconBridge mIconBridge;
    @Px
    private int mDropdownHeight;
    private boolean mEnableAdaptiveSuggestionsCount;

    DropdownItemViewInfoListBuilder() {
        mPriorityOrderedSuggestionProcessors = new ArrayList<>();
        mDropdownHeight = DROPDOWN_HEIGHT_UNKNOWN;
    }

    /**
     * Initialize the Builder with default set of suggestion processors.
     *
     * @param context Current context.
     * @param host Component creating suggestion view delegates and responding to suggestion events.
     * @param delegate Component facilitating interactions with UI and Autocomplete mechanism.
     * @param textProvider Provider of querying/editing the Omnibox.
     * @param queryTileSuggestionCallback Callback responding to QueryTile events.
     */
    void initDefaultProcessors(Context context, SuggestionHost host, AutocompleteDelegate delegate,
            UrlBarEditingTextStateProvider textProvider,
            Callback<List<QueryTile>> queryTileSuggestionCallback) {
        assert mPriorityOrderedSuggestionProcessors.size() == 0 : "Processors already initialized.";

        final Supplier<ImageFetcher> imageFetcherSupplier = () -> mImageFetcher;
        final Supplier<LargeIconBridge> iconBridgeSupplier = () -> mIconBridge;
        final Supplier<Tab> tabSupplier =
                () -> mActivityTabProvider == null ? null : mActivityTabProvider.get();
        final Supplier<ShareDelegate> shareSupplier =
                () -> mShareDelegateSupplier == null ? null : mShareDelegateSupplier.get();

        mHeaderProcessor = new HeaderProcessor(context, host, delegate);
        registerSuggestionProcessor(new EditUrlSuggestionProcessor(
                context, host, delegate, iconBridgeSupplier, tabSupplier, shareSupplier));
        registerSuggestionProcessor(
                new AnswerSuggestionProcessor(context, host, textProvider, imageFetcherSupplier));
        registerSuggestionProcessor(
                new ClipboardSuggestionProcessor(context, host, iconBridgeSupplier));
        registerSuggestionProcessor(
                new EntitySuggestionProcessor(context, host, imageFetcherSupplier));
        registerSuggestionProcessor(new TailSuggestionProcessor(context, host));
        registerSuggestionProcessor(
                new TileSuggestionProcessor(context, queryTileSuggestionCallback));
        registerSuggestionProcessor(
                new BasicSuggestionProcessor(context, host, textProvider, iconBridgeSupplier));
    }

    void destroy() {
        if (mImageFetcher != null) {
            mImageFetcher.destroy();
            mImageFetcher = null;
        }

        if (mIconBridge != null) {
            mIconBridge.destroy();
            mIconBridge = null;
        }
    }

    /**
     * Register new processor to process OmniboxSuggestions.
     * Processors will be tried in the same order as they were added.
     *
     * @param processor SuggestionProcessor that handles OmniboxSuggestions.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void registerSuggestionProcessor(SuggestionProcessor processor) {
        mPriorityOrderedSuggestionProcessors.add(processor);
    }

    /**
     * Specify instance of the HeaderProcessor that will be used to run tests.
     *
     * @param processor Header processor used to build suggestion headers.
     */
    void setHeaderProcessorForTest(HeaderProcessor processor) {
        mHeaderProcessor = processor;
    }

    /**
     * Notify that the current User profile has changed.
     *
     * @param profile Current user profile.
     */
    void setProfile(Profile profile) {
        if (mIconBridge != null) {
            mIconBridge.destroy();
            mIconBridge = null;
        }

        if (mImageFetcher != null) {
            mImageFetcher.destroy();
            mImageFetcher = null;
        }

        mIconBridge = new LargeIconBridge(profile);
        mImageFetcher = ImageFetcherFactory.createImageFetcher(ImageFetcherConfig.IN_MEMORY_ONLY,
                GlobalDiscardableReferencePool.getReferencePool(), MAX_IMAGE_CACHE_SIZE);
    }

    /**
     * Notify that the current Share delegate supplier has changed.
     *
     * @param shareDelegateSupplier Share facility supplier.
     */
    void setShareDelegateSupplier(Supplier<ShareDelegate> shareDelegateSupplier) {
        mShareDelegateSupplier = shareDelegateSupplier;
    }

    /**
     * Specify new Activity tab provider.
     *
     * @param provider Tab provider.
     */
    void setActivityTabProvider(ActivityTabProvider provider) {
        mActivityTabProvider = provider;
    }

    /**
     * Specify dropdown list height in pixels.
     * The height is subsequentially used to determine number of visible suggestions and perform
     * partial suggestion ordering based on their visibility.
     *
     * Note that this mechanism is effective as long as grouping is not in use in zero-prefix
     * context. At the time this mechanism was created, zero-prefix context never presented mixed
     * URL and (non-reactive) search suggestions, but instead presented either a list of specialized
     * suggestions (eg. clipboard, query tiles) mixed with reactive suggestions, a plain list of
     * search suggestions, or a plain list of recent URLs.
     * This gives us the chance to measure the height of the dropdown list before the actual
     * grouping takes effect.
     * If the above situation changes, we may need to revisit the logic here, and possibly cache the
     * heights in different states (eg. portrait mode, split screen etc) to get better results.
     *
     * @param dropdownHeight Updated height of the dropdown item list.
     */
    void setDropdownHeight(@Px int dropdownHeight) {
        mDropdownHeight = dropdownHeight;
    }

    /**
     * Respond to URL bar focus change.
     *
     * @param hasFocus Indicates whether URL bar is now focused.
     */
    void onUrlFocusChange(boolean hasFocus) {
        if (!hasFocus && mImageFetcher != null) {
            mImageFetcher.clear();
        }

        mHeaderProcessor.onUrlFocusChange(hasFocus);
        for (int index = 0; index < mPriorityOrderedSuggestionProcessors.size(); index++) {
            mPriorityOrderedSuggestionProcessors.get(index).onUrlFocusChange(hasFocus);
        }
    }

    /** Signals that native initialization has completed. */
    void onNativeInitialized() {
        mEnableAdaptiveSuggestionsCount =
                ChromeFeatureList.isEnabled(ChromeFeatureList.OMNIBOX_ADAPTIVE_SUGGESTIONS_COUNT);

        mHeaderProcessor.onNativeInitialized();
        for (int index = 0; index < mPriorityOrderedSuggestionProcessors.size(); index++) {
            mPriorityOrderedSuggestionProcessors.get(index).onNativeInitialized();
        }
    }

    /**
     * Build ListModel for new set of Omnibox suggestions.
     *
     * @param autocompleteResult New set of suggestions.
     * @return List of DropdownItemViewInfo representing the corresponding content of the
     *          suggestions list.
     */
    @NonNull
    List<DropdownItemViewInfo> buildDropdownViewInfoList(AutocompleteResult autocompleteResult) {
        mHeaderProcessor.onSuggestionsReceived();
        for (int index = 0; index < mPriorityOrderedSuggestionProcessors.size(); index++) {
            mPriorityOrderedSuggestionProcessors.get(index).onSuggestionsReceived();
        }

        final List<OmniboxSuggestion> newSuggestions = autocompleteResult.getSuggestionsList();
        final int newSuggestionsCount = newSuggestions.size();
        final List<DropdownItemViewInfo> viewInfoList = new ArrayList<>();

        // Match suggestions with their corresponding processors.
        final List<Pair<OmniboxSuggestion, SuggestionProcessor>> suggestionsPairedWithProcessors =
                new ArrayList<>();
        for (int index = 0; index < newSuggestionsCount; index++) {
            final OmniboxSuggestion suggestion = newSuggestions.get(index);
            final SuggestionProcessor processor = getProcessorForSuggestion(suggestion, index);
            suggestionsPairedWithProcessors.add(new Pair<>(suggestion, processor));
        }

        // When Adaptive Suggestions are set, perform partial grouping by search vs url.
        if (mEnableAdaptiveSuggestionsCount) {
            int numVisibleSuggestions = getVisibleSuggestionsCount(suggestionsPairedWithProcessors);
            // TODO(crbug.com/1073169): this should either infer the count from UI height or supply
            // the default value if height is not known. For the time being we group the entire list
            // to mimic the native behavior.
            groupSuggestionsBySearchVsURL(suggestionsPairedWithProcessors, numVisibleSuggestions);
        }

        // Build ViewInfo structures.
        int currentGroup = OmniboxSuggestion.INVALID_GROUP;
        for (int index = 0; index < newSuggestionsCount; index++) {
            final Pair<OmniboxSuggestion, SuggestionProcessor> suggestionAndProcessorPair =
                    suggestionsPairedWithProcessors.get(index);
            final OmniboxSuggestion suggestion = suggestionAndProcessorPair.first;
            final SuggestionProcessor processor = suggestionAndProcessorPair.second;

            if (suggestion.getGroupId() != currentGroup) {
                currentGroup = suggestion.getGroupId();
                final PropertyModel model = mHeaderProcessor.createModel();
                mHeaderProcessor.populateModel(model, currentGroup,
                        autocompleteResult.getGroupHeaders().get(currentGroup));
                viewInfoList.add(new DropdownItemViewInfo(mHeaderProcessor, model, currentGroup));
            }

            final PropertyModel model = processor.createModel();
            processor.populateModel(suggestion, model, index);
            viewInfoList.add(new DropdownItemViewInfo(processor, model, currentGroup));
        }
        return viewInfoList;
    }

    /**
     * @param suggestionsPairedWithProcessors List of suggestions and their matching processors.
     * @return Number of suggestions immediately visible to the user upon presenting the list.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    int getVisibleSuggestionsCount(
            List<Pair<OmniboxSuggestion, SuggestionProcessor>> suggestionsPairedWithProcessors) {
        // For cases where we don't know how many suggestions can fit in the visile screen area,
        // make an assumption regarding the group size.
        if (mDropdownHeight == DROPDOWN_HEIGHT_UNKNOWN) {
            return Math.min(suggestionsPairedWithProcessors.size(), DEFAULT_SIZE_OF_VISIBLE_GROUP);
        }

        @Px
        int calculatedSuggestionsHeight = 0;
        int lastVisibleIndex;
        for (lastVisibleIndex = 0; lastVisibleIndex < suggestionsPairedWithProcessors.size();
                lastVisibleIndex++) {
            if (calculatedSuggestionsHeight >= mDropdownHeight) break;

            final Pair<OmniboxSuggestion, SuggestionProcessor> pair =
                    suggestionsPairedWithProcessors.get(lastVisibleIndex);
            final SuggestionProcessor processor = pair.second;
            calculatedSuggestionsHeight += processor.getMinimumViewHeight();
        }
        return lastVisibleIndex;
    }

    /**
     * Group suggestions in-place by Search vs URL.
     * Creates two subgroups:
     * - Group 1 contains items visible, or partially visible to the user,
     * - Group 2 contains items that are not visible at the time user interacts with the suggestions
     *   list.
     *
     * @param suggestionsPairedWithProcessors List of suggestions and their matching processors.
     * @param numVisibleSuggestions Number of suggestions that are visible to the user.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void groupSuggestionsBySearchVsURL(
            List<Pair<OmniboxSuggestion, SuggestionProcessor>> suggestionsPairedWithProcessors,
            int numVisibleSuggestions) {
        // Native counterpart ensures that suggestion with group headers always end up at the end of
        // the list. This guarantees that these suggestions are both grouped at the end of the list
        // and that there's nothing more we should do about them.
        // See AutocompleteController::UpdateHeaders().
        int firstIndexWithHeader;
        for (firstIndexWithHeader = 0;
                firstIndexWithHeader < suggestionsPairedWithProcessors.size();
                firstIndexWithHeader++) {
            if (suggestionsPairedWithProcessors.get(firstIndexWithHeader).first.getGroupId()
                    != OmniboxSuggestion.INVALID_GROUP) {
                break;
            }
        }

        // Make sure we do not accidentally rearrange grouped suggestions.
        if (numVisibleSuggestions > firstIndexWithHeader) {
            numVisibleSuggestions = firstIndexWithHeader;
        }

        if (numVisibleSuggestions == 0) return;

        final Comparator<Pair<OmniboxSuggestion, SuggestionProcessor>> comparator =
                (pair1, pair2) -> {
            if (pair1.first.isSearchSuggestion() != pair2.first.isSearchSuggestion()) {
                return pair1.first.isSearchSuggestion() ? -1 : 1;
            }
            return pair2.first.getRelevance() - pair1.first.getRelevance();
        };

        // Note: the first match is always the default match. We do not want to sort it.
        Collections.sort(
                suggestionsPairedWithProcessors.subList(1, numVisibleSuggestions), comparator);
        Collections.sort(suggestionsPairedWithProcessors.subList(
                                 numVisibleSuggestions, firstIndexWithHeader),
                comparator);
    }

    /**
     * Search for Processor that will handle the supplied suggestion at specific position.
     *
     * @param suggestion The suggestion to be processed.
     * @param position Position of the suggestion in the list.
     */
    private SuggestionProcessor getProcessorForSuggestion(
            OmniboxSuggestion suggestion, int position) {
        for (int index = 0; index < mPriorityOrderedSuggestionProcessors.size(); index++) {
            SuggestionProcessor processor = mPriorityOrderedSuggestionProcessors.get(index);
            if (processor.doesProcessSuggestion(suggestion, position)) return processor;
        }
        assert false : "No default handler for suggestions";
        return null;
    }
}
