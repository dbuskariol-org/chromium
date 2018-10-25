// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.collection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.chrome.browser.widget.bottomsheet.EmptyBottomSheetObserver;

import java.util.ArrayList;
import java.util.List;

public class CollectionManager {
    private static final String TAG = "CollectionManager";

    private static final String PREFERENCES_NAME = "collection_starred";
    private final SharedPreferences mSharedPreferences;
    private final Context mContext;
    private final BottomSheetController mBottomSheetController;

    private final List<CollectionItem> mAll = new ArrayList<>();
    private final List<CollectionItem> mStarred = new ArrayList<>();
    private CollectionBottomSheetContent mContent;
    private CollectionItemRecyclerViewAdapter mAllAdapter;
    private CollectionItemRecyclerViewAdapter mStarredAdapter;

    // TODO: Move to resource. Maybe minify.
    static private final String sScript = "function getLargeImages(root, largerThan) {\n"
            + "  const candidates = root.querySelectorAll('img');\n"
            + "  images = [];\n"
            + "  for (const image of candidates) {\n"
            + "    if (image.offsetHeight < largerThan) continue;\n"
            + "    images.push(image);\n"
            + "  }\n"
            + "  return images;\n"
            + "}\n"
            + "\n"
            + "function getVisibleElements(list) {\n"
            + "  visible = []\n"
            + "  for (const ele of list) {\n"
            + "    if (ele.offsetHeight == 0 || ele.offsetHeight == 0) continue;\n"
            + "    visible.push(ele);\n"
            + "  }\n"
            + "  return visible;\n"
            + "}\n"
            + "\n"
            + "function extractImage(item) {\n"
            + "  // Sometimes an item contains small icons.\n"
            + "  const images = getLargeImages(item, 30);\n"
            + "  if (images.length != 1) {\n"
            + "    return null;\n"
            + "  }\n"
            + "  const image = images[0];\n"
            + "  // TODO: Do we want data:image?\n"
            + "  if (image.src && !image.src.startsWith('data:')) {\n"
            + "    return image.src;\n"
            + "  }\n"
            + "  sourceSet = image.getAttribute('data-search-image-source-set');\n"
            + "  console.assert(sourceSet.includes(' '), 'image extraction error', image);\n"
            + "  imageUrl = sourceSet.split(' ')[0];\n"
            + "  console.assert(imageUrl.length > 0, 'image extraction error', sourceSet);\n"
            + "  return imageUrl;\n"
            + "}\n"
            + "\n"
            + "function extractUrl(item) {\n"
            + "  anchors = item.querySelectorAll('a');\n"
            + "  console.assert(anchors.length >= 1, 'url extraction error', item);\n"
            + "  for (const anchor of anchors) {\n"
            + "    console.assert(anchor.href === anchors[0].href, 'url extraction error', anchor"
            + ".href, anchors[0].href);\n"
            + "  }\n"
            + "  return anchors[0].href;\n"
            + "}\n"
            + "\n"
            + "function extractTitle(item) {\n"
            + "  titles = getVisibleElements(item.querySelectorAll('h5'));\n"
            + "  if (titles.length > 1) {\n"
            + "    // \"Sponsored\" also has class sx-title\n"
            + "    titles = item.querySelectorAll('.sx-title.sx-title-inline');\n"
            + "  }\n"
            + "  if (titles.length == 0) {\n"
            + "    // Desktop version\n"
            + "    titles = item.querySelectorAll('h2');\n"
            + "  }\n"
            + "  console.assert(titles.length == 1, 'titles extraction error', item);\n"
            + "  title = titles[0].textContent.trim();\n"
            + "  return title;\n"
            + "}\n"
            + "\n"
            + "function extractPrice(item) {\n"
            + "  prices = item.querySelectorAll('.a-price.a-offscreen');\n"
            + "  if (prices.length == 0) {\n"
            + "    // Mobile version\n"
            + "    prices = item.querySelectorAll('.a-price .a-offscreen');\n"
            + "    if (prices.length != 1) {\n"
            + "      prices = item.querySelectorAll('.a-color-price');\n"
            + "      if (prices.length > 1) {\n"
            + "        // \"Only X left\" has class a-color-price as well.\n"
            + "        prices = item.querySelectorAll('.a-color-price.a-text-bold');\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "  console.assert(prices.length <= 1, 'price extraction error', item);\n"
            + "  if (prices.length == 1) {\n"
            + "    return prices[0].innerText;\n"
            + "  }\n"
            + "  // Strict heuristic to search for price elements.\n"
            + "  for (const span of item.querySelectorAll('span')) {\n"
            + "    if (span.textContent.trim().match(/^\\$[\\d.,]+$/))\n"
            + "      return span.textContent;\n"
            + "  }\n"
            + "  return null;\n"
            + "}\n"
            + "\n"
            + "function extractItem(item) {\n"
            + "  imageUrl = extractImage(item);\n"
            + "  if (imageUrl == null) return null;\n"
            + "  url = extractUrl(item);\n"
            + "  title = extractTitle(item);\n"
            + "  price = extractPrice(item);\n"
            + "\n"
            + "  return {'url': url, 'imageUrl': imageUrl, 'title': title, 'price': price};\n"
            + "}\n"
            + "\n"
            + "function extractAllItems() {\n"
            + "  extracted = []\n"
            + "  items = document.querySelectorAll('.sx-table-item');\n"
            + "  if (items.length == 0) {\n"
            + "    // Desktop version\n"
            + "    items = document.querySelectorAll('.s-result-item, .s-result-card');\n"
            + "  }\n"
            + "  for (const item of items) {\n"
            + "    if (item.offsetHeight < 50) continue;\n"
            + "    if (item.querySelectorAll('.s-result-card').length > 0) continue;\n"
            + "    if (item.querySelectorAll('img').length == 0) continue;\n"
            + "    const extraction = extractItem(item);\n"
            + "    if (extraction != null) {\n"
            + "      extracted.push(extraction);\n"
            + "    }\n"
            + "  }\n"
            + "  return extracted;\n"
            + "}\n"
            + "\n"
            + "extractAllItems();\n";

    public CollectionManager(Context context, BottomSheetController bottomSheetController) {
        mContext = context;
        mBottomSheetController = bottomSheetController;
        mSharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, 0);
        // TODO: async loading should mostly be OK.
        new Handler().post(this ::loadStarredItems);
    }

    static public boolean isEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.COLLECTION_FOR_SHOPPING);
    }

    static private String serializeToJson(List<CollectionItem> items) {
        JSONArray root = new JSONArray();
        try {
            for (CollectionItem item : items) {
                JSONObject c = new JSONObject();
                c.put("url", item.url);
                c.put("imageUrl", item.imageUrl);
                c.put("title", item.title);
                c.put("price", item.price);
                root.put(c);
            }
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        Log.e(TAG, "Collection serialized starred = %s", root.toString());

        return root.toString();
    }

    static private List<CollectionItem> parseJson(String json) {
        List<CollectionItem> items = new ArrayList<>();
        try {
            JSONArray root = new JSONArray(json);
            for (int i = 0; i < root.length(); i++) {
                JSONObject c = root.getJSONObject(i);
                items.add(new CollectionItem(c.getString("url"), c.getString("imageUrl"),
                        c.getString("title"), c.getString("price"), false));
            }
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        return items;
    }

    private void extractItems(final Tab tab) {
        if (!tab.getUrl().startsWith("https://www.amazon.com")) return;
        if (tab.getWebContents() == null) return;

        // TODO: evaluateJavaScriptForTests should not be used in prod.
        tab.getWebContents().evaluateJavaScriptForTests(sScript, (json) -> {
            Log.e(TAG, "Collection json: %s", json);
            List<CollectionItem> items = parseJson(json);
            if (items.size() != 0) {
                Log.e(TAG, "Collection size = %d", items.size());
                for (CollectionItem item : items) {
                    int index =
                            CollectionItemRecyclerViewAdapter.findItemByImageUrl(mStarred, item);
                    item.starred = (index >= 0);
                }
                mAll.clear();
                mAll.addAll(items);
                if (mAllAdapter != null) mAllAdapter.notifyDataSetChanged();
                Log.e(TAG, "Collection size = %d", mAll.size());
            }
        });
    }

    // TODO: do we only show the list on shopping sites? If so, how to get it on non-shopping sites?
    public void showBottomSheet(FragmentManager fragmentManager, final Tab tab) {
        if (tab.getWebContents() == null) return;
        showBottomSheet(fragmentManager);
        extractItems(tab);
    }

    @SuppressLint("SetTextI18n")
    private void showBottomSheet(FragmentManager fragmentManager) {
        if (mContent == null) {
            CollectionPagerAdapter adapter = new CollectionPagerAdapter(fragmentManager, this);

            View container = LayoutInflater.from(mContext).inflate(
                    R.layout.collection_main, mBottomSheetController.getBottomSheet(), false);
            ViewPager pager =
                    (ViewPager) container; // container.findViewById(R.id.collection_pager);
            pager.setAdapter(adapter);

            View toolbarView = LayoutInflater.from(mContext).inflate(
                    R.layout.collection_toolbar, mBottomSheetController.getBottomSheet(), false);

            toolbarView.setOnClickListener(v -> { mBottomSheetController.expandSheet(); });

            View closeButton = toolbarView.findViewById(R.id.close_button);
            closeButton.setOnClickListener(v -> { mBottomSheetController.unexpandSheet(); });

            mBottomSheetController.getBottomSheet().addObserver(new EmptyBottomSheetObserver() {
                @Override
                public void onSheetOpened(@BottomSheet.StateChangeReason int reason) {
                    closeButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onSheetClosed(@BottomSheet.StateChangeReason int reason) {
                    closeButton.setVisibility(View.INVISIBLE);
                }
            });

            TextView title = toolbarView.findViewById(R.id.title);
            title.setText("Collection for Shopping");

            mContent = new CollectionBottomSheetContent(pager, toolbarView, false);
        }
        mBottomSheetController.requestShowContent(mContent, false);
        Log.e(TAG, "Collection requestShowContent");
    }

    void addStarredItem(CollectionItem item) {
        mStarredAdapter.addOrUpdateItem(item);
        mAllAdapter.updateItem(item);

        saveStarredItems();
    }

    void removeStarredItem(CollectionItem item) {
        mStarredAdapter.removeItem(item);
        mAllAdapter.updateItem(item);

        saveStarredItems();
    }

    private void loadStarredItems() {
        String json = mSharedPreferences.getString("starred", "[]");
        mStarred.addAll(parseJson(json));
        for (CollectionItem item : mStarred) {
            item.starred = true;
        }
        // Nullity checking is needed because async loading leads to racing conditions.
        if (mStarredAdapter != null) mStarredAdapter.notifyDataSetChanged();
    }

    private void saveStarredItems() {
        mSharedPreferences.edit().putString("starred", serializeToJson(mStarred)).apply();
    }

    List<CollectionItem> getAllItems() {
        Log.e(TAG, "Collection all size = %d", mAll.size());
        return mAll;
    }

    List<CollectionItem> getStarredItems() {
        Log.e(TAG, "Collection starred size = %d", mStarred.size());
        return mStarred;
    }

    void setAllAdapter(CollectionItemRecyclerViewAdapter adapter) {
        mAllAdapter = adapter;
    }

    void setStarAdapter(CollectionItemRecyclerViewAdapter adapter) {
        mStarredAdapter = adapter;
    }

    void destroy() {
        Log.e(TAG, "Collection destroy");
        mAllAdapter = null;
        mStarredAdapter = null;
    }

    /**
     * A dummy item representing a piece of price.
     */
    public static class CollectionItem {
        public final String url;
        public final String imageUrl;
        public final String title;
        public final String price;
        public Boolean starred;

        public CollectionItem(
                String url, String imageUrl, String title, String price, Boolean starred) {
            this.url = url;
            this.imageUrl = imageUrl;
            this.title = title;
            this.price = price;
            this.starred = starred;
        }

        @Override
        public String toString() {
            return price;
        }
    }
}
