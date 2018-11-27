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
import android.support.v7.widget.AppCompatImageButton;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.compositor.layouts.phone.TabGroupList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.content_public.browser.LoadUrlParams;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionManager {
    private static final String TAG = "CollectionManager";

    private static final String PREFERENCES_NAME = "collection_starred";
    private final SharedPreferences mSharedPreferences;
    private final Context mContext;
    private final ChromeActivity mActivity;
    private final FragmentManager mFragmentManager;
    private final BottomSheetController mBottomSheetController;

    public static final boolean COLLECT_TO_LIST = false;

    private final Map<Tab, Boolean> mHasItems = new HashMap<>();
    private final Map<Tab, Boolean> mHasOverlay = new HashMap<>();
    private final List<CollectionItem> mAll = new ArrayList<>();
    private final List<CollectionItem> mStarred = new ArrayList<>();
    private final Set<WeakReference<Tab>> mTabs = new HashSet<>();
    private CollectionBottomSheetContent mContent;
    private CollectionItemRecyclerViewAdapter mAllAdapter;
    private CollectionItemRecyclerViewAdapter mStarredAdapter;

    private TextView mTitle;

    @SuppressLint("StaticFieldLeak")
    private static CollectionManager sInstance;

    // TODO: Move to resource. Maybe minify.
    // Do not edit this string directly. The source of truth is merchant-list-extraction.js
    static private final String sScript = "let overlayClassName = 'clank_shopping_overlay';\n"
            + "let showClassName = 'show_' + overlayClassName;\n"
            + "\n"
            + "// Aliexpress uses 'US $12.34' format in the price.\n"
            + "// Macy's uses \"$12.34 to 56.78\" format.\n"
            + "let priceCleanupPrefix = 'sale|with offer|only|our price|now|starting at';\n"
            + "let priceCleanupPostfix = '(/(each|set))';\n"
            + "let priceRegexTemplate =\n"
            + "  '((reg|regular|orig|from|' + priceCleanupPrefix + ')\\\\s+)?' +\n"
            + "   '(\\\\d+\\\\s*/\\\\s*)?(US(D)?\\\\s*)?' +\n"
            + "   '\\\\$[\\\\d.,]+(\\\\s+(to|-|–)\\\\s+(\\\\$)?[\\\\d.,]+)?' +\n"
            + "   priceCleanupPostfix + '?';\n"
            + "let priceRegexFull = new RegExp('^' + priceRegexTemplate + '$', 'i');\n"
            + "let priceRegex = new RegExp(priceRegexTemplate, 'i');\n"
            + "let priceCleanupRegex = new RegExp('^((' + priceCleanupPrefix + ')\\\\s+)|' + "
            + "priceCleanupPostfix + '$', 'i');\n"
            + "\n"
            + "function getLazyLoadingURL(image) {\n"
            + "  // FIXME: some lazy images in Nordstrom and Staples don't have URLs in the DOM.\n"
            + "  // TODO: add more lazy-loading attributes.\n"
            + "  for (const attribute of ['data-src', 'data-img-url', 'data-config-src', "
            + "'data-echo', 'data-lazy']) {\n"
            + "    let url = image.getAttribute(attribute);\n"
            + "    if (url == null) continue;\n"
            + "    if (url.substr(0, 2) == '//') url = 'https:' + url;\n"
            + "    if (url.substr(0, 4) != 'http') continue;\n"
            + "    return url;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function getLargeImages(root, atLeast, relaxed = false) {\n"
            + "  let candidates = root.querySelectorAll('img');\n"
            + "  if (candidates.length == 0) {\n"
            + "    // Aliexpress\n"
            + "    candidates = root.querySelectorAll('amp-img');\n"
            + "  }\n"
            + "  images = [];\n"
            + "  function shouldStillKeep(image) {\n"
            + "    if (!relaxed) return false;\n"
            + "    if (image.getAttribute('aria-hidden') == 'true') return true;\n"
            + "    if (getLazyLoadingURL(image) != null) return true;\n"
            + "    return false;\n"
            + "  }\n"
            + "  for (const image of candidates) {\n"
            + "    console.log('offsetHeight', image, image.offsetHeight);\n"
            + "    if (image.offsetHeight < atLeast) {\n"
            + "      if (!shouldStillKeep(image))\n"
            + "        continue;\n"
            + "    }\n"
            + "    if (window.getComputedStyle(image)['visibility'] == 'hidden')\n"
            + "      continue;\n"
            + "    if (image.parentElement.parentElement.className == overlayClassName)\n"
            + "      continue;\n"
            + "    images.push(image);\n"
            + "  }\n"
            + "  return images;\n"
            + "}\n"
            + "\n"
            + "function getVisibleElements(list) {\n"
            + "  visible = [];\n"
            + "  for (const ele of list) {\n"
            + "    if (ele.offsetHeight == 0 || ele.offsetHeight == 0) continue;\n"
            + "    visible.push(ele);\n"
            + "  }\n"
            + "  return visible;\n"
            + "}\n"
            + "\n"
            + "function extractImage(item) {\n"
            + "  // Sometimes an item contains small icons, which need to be filtered out.\n"
            + "  // TODO: two pass getLargeImages() is probably too slow.\n"
            + "  let images = getLargeImages(item, 40);\n"
            + "  if (images.length == 0) images = getLargeImages(item, 30, true);\n"
            + "  console.assert(images.length == 1, 'image extraction error', item, images);\n"
            + "  if (images.length != 1) {\n"
            + "    return null;\n"
            + "  }\n"
            + "  const image = images[0];\n"
            + "  const lazyUrl = getLazyLoadingURL(image);\n"
            + "  if (lazyUrl != null) return lazyUrl;\n"
            + "\n"
            + "  // If |image| is <amp-img>, image.src won't work.\n"
            + "  //console.log(\"image src\", image.getAttribute('src'));\n"
            + "  const src = image.getAttribute('src');\n"
            + "  if (src != null) {\n"
            + "    // data: images are usually placeholders.\n"
            + "    // Even if it's valid, we prefer http(s) URLs.\n"
            + "    if (!src.startsWith('data:'))\n"
            + "      return src;\n"
            + "  }\n"
            + "  sourceSet = image.getAttribute('data-search-image-source-set');\n"
            + "  if (sourceSet == null) return null;\n"
            + "  console.assert(sourceSet.includes(' '), 'image extraction error', image);\n"
            + "  // TODO: Pick the one with right pixel density?\n"
            + "  imageUrl = sourceSet.split(' ')[0];\n"
            + "  console.assert(imageUrl.length > 0, 'image extraction error', sourceSet);\n"
            + "  return imageUrl;\n"
            + "}\n"
            + "\n"
            + "function extractUrl(item) {\n"
            + "  let anchors;\n"
            + "  if (item.tagName == 'A') {\n"
            + "    anchors = [item];\n"
            + "  } else {\n"
            + "    anchors = item.querySelectorAll('a');\n"
            + "  }\n"
            + "  console.assert(anchors.length >= 1, 'url extraction error', item);\n"
            + "  if (anchors.length == 0) {\n"
            + "    return null;\n"
            + "  }\n"
            + "  const filtered = [];\n"
            + "  for (const anchor of anchors) {\n"
            + "    if (anchor.href.match(/\\/#$/)) continue;\n"
            + "    // href=\"javascript:\" would be sanitized when serialized to MHTML.\n"
            + "    if (anchor.href.match(/^javascript:/)) continue;\n"
            + "    if (anchor.href == '') {\n"
            + "      // For Sears\n"
            + "      let href = anchor.getAttribute('bot-href');\n"
            + "      if (href != null && href.length > 0) {\n"
            + "        // Resolve to absolute URL.\n"
            + "        anchor.href = href;\n"
            + "        href = anchor.href;\n"
            + "        anchor.removeAttribute('href');\n"
            + "        if (href != '') return href;\n"
            + "      }\n"
            + "      continue;\n"
            + "    }\n"
            + "    filtered.push(anchor);\n"
            + "    // TODO: This returns the first URL in DOM order.\n"
            + "    //       Use the one with largest area instead?\n"
            + "    return anchor.href;\n"
            + "  }\n"
            + "  if (filtered.length == 0) return null;\n"
            + "  return filtered.reduce(function(a, b) {\n"
            + "      return a.offsetHeight * a.offsetWidth > b.offsetHeight * b.offsetWidth ? a :"
            + " b;\n"
            + "  }).href;\n"
            + "}\n"
            + "\n"
            + "function isInlineDisplay(element) {\n"
            + "  const display = window.getComputedStyle(element)['display'];\n"
            + "  return display.indexOf('inline') != -1;\n"
            + "}\n"
            + "\n"
            + "function childElementCountExcludingInline(element) {\n"
            + "  let count = 0;\n"
            + "  for (const child of element.children) {\n"
            + "    if (isInlineDisplay(child)) count += 1;\n"
            + "  }\n"
            + "  return count;\n"
            + "}\n"
            + "\n"
            + "function hasNonInlineDescendentsInclusive(element) {\n"
            + "  if (!isInlineDisplay(element)) return true;\n"
            + "  return hasNonInlineDescendents(element);\n"
            + "}\n"
            + "\n"
            + "function hasNonInlineDescendents(element) {\n"
            + "  for (const child of element.children) {\n"
            + "    if (hasNonInlineDescendentsInclusive(child)) return true;\n"
            + "  }\n"
            + "  return false;\n"
            + "}\n"
            + "\n"
            + "function hasNonWhiteTextNodes(element) {\n"
            + "  for (const child of element.childNodes) {\n"
            + "    if (child.nodeType != document.TEXT_NODE) continue;\n"
            + "    if (child.nodeValue.trim() != '') return true;\n"
            + "  }\n"
            + "  return false;\n"
            + "}\n"
            + "\n"
            + "// Concat classNames and IDs of ancestors up to |maxDepth|, while not containing\n"
            + "// |excludingElement|.\n"
            + "// If |excludingElement| is already a descendent of |element|, still return the\n"
            + "// className of |element|.\n"
            + "// |maxDepth| include current level, so maxDepth = 1 means just |element|.\n"
            + "// maxDepth >= 3 causes error in Walmart deals if not deducting \"price\".\n"
            + "function ancestorIdAndClassNames(element, excludingElement, maxDepth = 3) {\n"
            + "  let name = '';\n"
            + "  let depth = 0;\n"
            + "  while (true) {\n"
            + "    name += element.className + element.id;\n"
            + "    element = element.parentElement;\n"
            + "    depth += 1;\n"
            + "    if (depth >= maxDepth) break;\n"
            + "    if (!element) break;\n"
            + "    if (element.contains(excludingElement)) break;\n"
            + "  }\n"
            + "  return name;\n"
            + "}\n"
            + "\n"
            + "/*\n"
            + "  Returns top-ranked element with the following criteria, with decreasing "
            + "priority:\n"
            + "  - score based on whether ancestorIdAndClassNames contains \"title\", \"price\", "
            + "etc.\n"
            + "  - largest area\n"
            + "  - largest font size\n"
            + "  - longest text\n"
            + " */\n"
            + "function chooseTitle(elementArray) {\n"
            + "  return elementArray.reduce(function(a, b) {\n"
            + "    const titleRegex = /name|title|truncate|desc|brand/i;\n"
            + "    const negativeRegex = /price|model/i;\n"
            + "    const a_str = ancestorIdAndClassNames(a, b);\n"
            + "    const b_str = ancestorIdAndClassNames(b, a);\n"
            + "    const a_score = (a_str.match(titleRegex) != null) - (a_str.match"
            + "(negativeRegex) != null);\n"
            + "    const b_score = (b_str.match(titleRegex) != null) - (b_str.match"
            + "(negativeRegex) != null);\n"
            + "    console.log('className score', a_score, b_score, a_str, b_str, a, b);\n"
            + "\n"
            + "    if (a_score != b_score) {\n"
            + "      return a_score > b_score ? a : b;\n"
            + "    }\n"
            + "\n"
            + "    // Use getBoundingClientRect() to avoid int rounding error in "
            + "offsetHeight/Width.\n"
            + "    const a_area = a.getBoundingClientRect().width * a.getBoundingClientRect()"
            + ".height;\n"
            + "    const b_area = b.getBoundingClientRect().width * b.getBoundingClientRect()"
            + ".height;\n"
            + "    console.log('getBoundingClientRect', a.getBoundingClientRect(), b"
            + ".getBoundingClientRect(), a, b);\n"
            + "\n"
            + "    if (a_area != b_area) {\n"
            + "      return a_area > b_area ? a : b;\n"
            + "    }\n"
            + "\n"
            + "    const a_size = parseFloat(window.getComputedStyle(a)['font-size']);\n"
            + "    const b_size = parseFloat(window.getComputedStyle(b)['font-size']);\n"
            + "    console.log('font size', a_size, b_size, a, b);\n"
            + "\n"
            + "    if (a_size != b_size) {\n"
            + "      return a_size > b_size ? a : b;\n"
            + "    }\n"
            + "\n"
            + "    return a.innerText.length > b.innerText.length ? a : b;\n"
            + "  });\n"
            + "}\n"
            + "\n"
            + "function extractTitle(item) {\n"
            + "  if (false) {\n"
            + "    // Carousel in Amazon mobile\n"
            + "    // The heuristic peeks into the title attribute, which is invisible.\n"
            + "    // Probably not general enough to use.\n"
            + "    let titles = item.querySelectorAll('a.a-link-normal');\n"
            + "    if (titles.length > 0) {\n"
            + "      title = titles[0].title;\n"
            + "      if (title != null) {\n"
            + "        title = title.trim();\n"
            + "        if (title.length > 0) return title;\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "\n"
            + "  const possible_titles = item.querySelectorAll('a, span, p, div, h2, h3, h4, h5, "
            + "strong');\n"
            + "  let titles = [];\n"
            + "  for (const title of possible_titles) {\n"
            + "    if (hasNonInlineDescendents(title) &&\n"
            + "        !hasNonWhiteTextNodes(title)) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    if (title.innerText.trim() == '') continue;\n"
            + "    if (title.innerText.trim().toLowerCase() == 'sponsored') continue;\n"
            + "    if (title.childElementCount > 0) {\n"
            + "      if (title.textContent.trim() == title.lastElementChild.textContent.trim()\n"
            + "            || title.textContent.trim() == title.firstElementChild.textContent"
            + ".trim()) {\n"
            + "        continue;\n"
            + "      }\n"
            + "    }\n"
            + "    // Aliexpress has many items without title. Without the following filter,\n"
            + "    // the title would be the price.\n"
            + "    //if (title.innerText.trim().match(priceRegexFull)) continue;\n"
            + "    titles.push(title);\n"
            + "  }\n"
            + "  if (titles.length > 1) {\n"
            + "    console.log('all generic titles', item, titles);\n"
            + "    titles = [chooseTitle(titles)];\n"
            + "  }\n"
            + "\n"
            + "  console.log('titles', item, titles);\n"
            + "  console.assert(titles.length == 1, 'titles extraction error', item, titles);\n"
            + "  if (titles.length != 1) return null;\n"
            + "  title = titles[0].innerText.trim();\n"
            + "  return title;\n"
            + "}\n"
            + "\n"
            + "function adjustBeautifiedCents(priceElement) {\n"
            + "  const text = priceElement.innerText.trim().replace(/\\/(each|set)$/i, '');\n"
            + "  let cents;\n"
            + "  const children = priceElement.children;\n"
            + "  for (let i = children.length - 1; i >= 0; i--) {\n"
            + "    const t = children[i].innerText.trim();\n"
            + "    if (t == \"\") continue;\n"
            + "    if (t.indexOf('/') != -1) continue;\n"
            + "    cents = t;\n"
            + "    break;\n"
            + "  }\n"
            + "  if (cents == null) return null;\n"
            + "  console.log('cents', cents, priceElement);\n"
            + "  if (cents.length == 2\n"
            + "      && cents == text.slice(-cents.length)\n"
            + "      && text.slice(-3, -2).match(/\\d/)) {\n"
            + "    return text.substr(0, text.length - cents.length) + '.' + cents;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function anyLineThroughInAncentry(element, maxDepth = 2) {\n"
            + "  let depth = 0;\n"
            + "  while (element != null && element.tagName != 'BODY') {\n"
            + "    if (window.getComputedStyle(element)['text-decoration'].indexOf"
            + "('line-through') != -1)\n"
            + "      return true;\n"
            + "    element = element.parentElement;\n"
            + "    depth += 1;\n"
            + "    if (depth >= maxDepth) break;\n"
            + "  }\n"
            + "  return false;\n"
            + "}\n"
            + "\n"
            + "function forgivingParseFloat(str) {\n"
            + "  return parseFloat(str.replace(priceCleanupRegex, '').replace(/^[$]*/, ''));\n"
            + "}\n"
            + "\n"
            + "function choosePrice(priceArray) {\n"
            + "  if (priceArray.length == 0) return null;\n"
            + "  return priceArray.reduce(function(a, b) {\n"
            + "    // Positive tags\n"
            + "    for (const pattern of ['with offer', 'sale', 'now']) {\n"
            + "      const a_val = a.toLowerCase().indexOf(pattern) != -1;\n"
            + "      const b_val = b.toLowerCase().indexOf(pattern) != -1;\n"
            + "      if (a_val != b_val) {\n"
            + "        return a_val > b_val ? a : b;\n"
            + "      }\n"
            + "    }\n"
            + "    // Negative tags\n"
            + "    for (const pattern of ['/set', '/each']) {\n"
            + "      const a_val = a.toLowerCase().indexOf(pattern) != -1;\n"
            + "      const b_val = b.toLowerCase().indexOf(pattern) != -1;\n"
            + "      if (a_val != b_val) {\n"
            + "        return a_val < b_val ? a : b;\n"
            + "      }\n"
            + "    }\n"
            + "    // Guess the smallest numerical value.\n"
            + "    // The tags like \"now\" don't always fall inside element boundary.\n"
            + "    // See Nordstrom/homepage-eager.mhtml.\n"
            + "    return forgivingParseFloat(a) > forgivingParseFloat(b) ? b : a;\n"
            + "  }).replace(priceCleanupRegex, '');\n"
            + "}\n"
            + "\n"
            + "function extractPrice(item) {\n"
            + "  // Etsy mobile\n"
            + "  const prices = item.querySelectorAll(`\n"
            + "      .currency-value\n"
            + "  `);\n"
            + "  if (prices.length == 1) {\n"
            + "    let ans = prices[0].textContent.trim();\n"
            + "    if (ans.match(/^\\d/)) ans = '$' + ans; // for Etsy\n"
            + "    if (ans != '') return ans;\n"
            + "  }\n"
            + "  // Generic heuristic to search for price elements.\n"
            + "  let captured_prices = [];\n"
            + "  for (const price of item.querySelectorAll('span, p, div, h3')) {\n"
            + "    const candidate = price.innerText.trim();\n"
            + "    if (!candidate.match(priceRegexFull)) continue;\n"
            + "    console.log('price candidate', candidate, price);\n"
            + "    if (price.childElementCount > 0) {\n"
            + "      // Avoid matching the parent element of the real price element.\n"
            + "      // Otherwise adjustBeautifiedCents would break.\n"
            + "      if (price.innerText.trim() == price.lastElementChild.innerText.trim()\n"
            + "            || price.innerText.trim() == price.firstElementChild.innerText.trim())"
            + " {\n"
            + "        // If the wanted child is not scanned, change the querySelectorAll string.\n"
            + "        console.log('skip redundant parent', price);\n"
            + "        continue;\n"
            + "      }\n"
            + "    }\n"
            + "    // TODO: check child elements recursively.\n"
            + "    if (anyLineThroughInAncentry(price)) {\n"
            + "      console.log('line-through', price);\n"
            + "      continue;\n"
            + "    }\n"
            + "    // for Amazon and HomeDepot\n"
            + "    if (candidate.indexOf('.') == -1 && price.lastElementChild != null) {\n"
            + "      const adjusted = adjustBeautifiedCents(price);\n"
            + "      if (adjusted != null) return adjusted;\n"
            + "    }\n"
            + "    captured_prices.push(candidate);\n"
            + "  }\n"
            + "  console.log('captured_prices', captured_prices);\n"
            + "  return choosePrice(captured_prices);\n"
            + "}\n"
            + "\n"
            + "function extractItem(item) {\n"
            + "  imageUrl = extractImage(item);\n"
            + "  if (imageUrl == null) {\n"
            + "    console.warn('no images found', item);\n"
            + "    return null;\n"
            + "  }\n"
            + "  url = extractUrl(item);\n"
            + "  // Some items in Sears and Staples only have ng-click or onclick handlers,\n"
            + "  // so it's impossible to extract URL.\n"
            + "  if (url == null) {\n"
            + "    console.warn('no url found', item);\n"
            + "    return null;\n"
            + "  }\n"
            + "  title = extractTitle(item);\n"
            + "  if (title == null) {\n"
            + "    console.warn('no title found', item);\n"
            + "    return null;\n"
            + "  }\n"
            + "  price = extractPrice(item);\n"
            + "  // eBay \"You may also like\" and \"Guides\" are not product items.\n"
            + "  // Not having price is one hint.\n"
            + "  // FIXME: \"Also viewed\" items in Gap doesn't have prices.\n"
            + "  if (price == null) {\n"
            + "    console.warn('no price found', item);\n"
            + "    return null;\n"
            + "  }\n"
            + "  return {'url': url, 'imageUrl': imageUrl, 'title': title, 'price': price};\n"
            + "}\n"
            + "\n"
            + "function styleForOverlay() {\n"
            + "  // TODO: \"back\" might break because of missing style.\n"
            + "  // TODO: insert <style> element instead for convenience?\n"
            + "  if (this.done) return;\n"
            + "  this.done = true;\n"
            + "\n"
            + "  const style = document.createElement('style');\n"
            + "  document.head.appendChild(style);\n"
            + "  style.sheet.insertRule('body.show_' + overlayClassName +\n"
            + "    ' .' + overlayClassName + `\n"
            + "    {\n"
            + "      box-sizing: border-box;\n"
            + "      position: absolute;\n"
            + "      top: 0;\n"
            + "      left: 0;\n"
            + "      height: 100%;\n"
            + "      width: 100%;\n"
            + "      background: rgba(77, 144, 254, 0.3);\n"
            + "      border: solid rgb(77, 144, 254) 1px;\n"
            + "      border-radius: 10px;\n"
            + "      display: block;\n"
            + "      text-align: right;\n"
            + "      /* pointer-events: none; */ /* touch pass through */\n"
            + "    }\n"
            + "  `, 0);\n"
            + "  style.sheet.insertRule('body.show_' + overlayClassName +\n"
            + "    ' .' + overlayClassName + `:before\n"
            + "    {\n"
            + "      content: \"\";\n"
            + "      position: absolute;\n"
            + "      top: 0;\n"
            + "      bottom: 0;\n"
            + "      left: 0;\n"
            + "      right: 0;\n"
            + "      z-index: -1;\n"
            + "      box-shadow: -5px 3px 10px 2px gray;\n"
            + "    }\n"
            + "  `, 0);\n"
            + "  style.sheet.insertRule('body.show_' + overlayClassName +\n"
            + "    ' .' + overlayClassName + ` div\n"
            + "    {\n"
            + "      pointer-events: auto;\n"
            + "      display: inline-block; /* to shrink the width */\n"
            + "      padding: 8px; /* slightly larger touch area */\n"
            + "    }\n"
            + "  `, 0);\n"
            + "  style.sheet.insertRule('.' + overlayClassName + `\n"
            + "    {\n"
            + "      display: none;\n"
            + "    }\n"
            + "  `, 0);\n"
            + "}\n"
            + "\n"
            + "function createOverlay(item, starred) {\n"
            + "  let image;\n"
            + "  if (item.lastElementChild.className !== overlayClassName) {\n"
            + "    //console.log('create overlay for', item);\n"
            + "    // Need this relative/absolute pair for 100% width/height to work.\n"
            + "    item.style.position = 'relative';\n"
            + "    // TODO: traverse up and use the first bg color?\n"
            + "    item.style.backgroundColor = 'white';\n"
            + "\n"
            + "    const overlay = document.createElement('div');\n"
            + "    overlay.className = overlayClassName;\n"
            + "    const imageContainer = document.createElement('div');\n"
            + "    overlay.addEventListener('click', toggleItem, false);\n"
            + "    image = document.createElement('img');\n"
            + "    image.width = 36;\n"
            + "    imageContainer.appendChild(image);\n"
            + "    overlay.appendChild(imageContainer);\n"
            + "    item.appendChild(overlay);\n"
            + "  } else {\n"
            + "    image = item.lastElementChild.querySelectorAll('img')[0];\n"
            + "  }\n"
            + "  if (starred != null) {\n"
            + "    image.dataSaved = starred;\n"
            + "  }\n"
            + "  updateBookmarkIcon(image);\n"
            + "}\n"
            + "\n"
            + "function updateBookmarkIcon(img) {\n"
            + "  if (img.dataSaved) {\n"
            + "    img.src = 'https://material.io/tools/icons/static/icons/baseline-check-24px"
            + ".svg';\n"
            + "  } else {\n"
            + "    img.src = 'https://material.io/tools/icons/static/icons/baseline-add_circle"
            + "-24px.svg';\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function toggleItem(e) {\n"
            + "  console.log(e);\n"
            + "  // Necessary when the overlay is added to an anchor element.\n"
            + "  e.preventDefault();\n"
            + "  e.stopPropagation();\n"
            + "\n"
            + "  const img = this.getElementsByTagName('img')[0];\n"
            + "  if (img.dataSaved) return;\n"
            + "  img.dataSaved = !img.dataSaved;\n"
            + "  updateBookmarkIcon(img);\n"
            + "\n"
            + "  const item = img.parentElement.parentElement.parentElement;\n"
            + "  console.assert(item.lastElementChild.className === overlayClassName, 'wrong "
            + "item', item);\n"
            + "  const extracted = extractItem(item);\n"
            + "  extracted['starred'] = img.dataSaved;\n"
            + "  const payload = encodeURIComponent(JSON.stringify(extracted));\n"
            + "  console.log(item);\n"
            + "  console.log(extractItem(item));\n"
            + "  window.location.href = \"intent:\" + payload +\n"
            + "      \"#Intent;package=com.google.android.apps.chrome;action=com.google.chrome"
            + ".COLLECT;end;\";\n"
            + "}\n"
            + "\n"
            + "function commonAncestor(a, b) {\n"
            + "  while (!a.contains(b)) {\n"
            + "    a = a.parentElement;\n"
            + "  }\n"
            + "  return a;\n"
            + "}\n"
            + "\n"
            + "function commonAncestorList(list) {\n"
            + "  return list.reduce(function(a, b) {\n"
            + "      return commonAncestor(a, b);\n"
            + "  });\n"
            + "}\n"
            + "\n"
            + "function hasOverlap(target, list) {\n"
            + "  for (const element of list) {\n"
            + "    if (element.contains(target) || target.contains(element)) {\n"
            + "      return true;\n"
            + "    }\n"
            + "  }\n"
            + "  return false;\n"
            + "}\n"
            + "\n"
            + "function extractOneItem(item, extracted_items, processed, output, overlay) {\n"
            + "  //console.log('trying', item);\n"
            + "  if (item.childElementCount == 0 && item.parentElement.tagName != 'BODY') {\n"
            + "    // Amazone store page uses overlay <a>.\n"
            + "    item = item.parentElement;\n"
            + "    if (item == null) return;\n"
            + "  }\n"
            + "  // scrollHeight could be 0 while getBoundingClientRect().height > 0.\n"
            + "  if (item.getBoundingClientRect().height < 50) {\n"
            + "    console.log('too short', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  if (item.scrollHeight > 1000) {\n"
            + "    console.log('too tall', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  if (item.getBoundingClientRect().height > 1000) {\n"
            + "    console.log('too tall', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  if (item.querySelectorAll('img, amp-img').length == 0)  {\n"
            + "    console.log('no image', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  if (!item.textContent.match(priceRegex)) {\n"
            + "    console.log('no price', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  //console.log('extracted_items', extracted_items);\n"
            + "  if (hasOverlap(item, extracted_items)) {\n"
            + "    console.log('overlap', item);\n"
            + "    return;\n"
            + "  }\n"
            + "  if (processed.has(item)) return;\n"
            + "  processed.add(item);\n"
            + "  console.log('trying', item);\n"
            + "  const extraction = extractItem(item);\n"
            + "  if (extraction != null) {\n"
            + "    output.set(item, extraction);\n"
            + "    extracted_items.push(item);\n"
            + "    if (overlay) {\n"
            + "      const starred = typeof clank_all_starred_keys === \"object\" && "
            + "extraction['imageUrl'] in clank_all_starred_keys;\n"
            + "      //console.log(starred, extraction['imageUrl'], clank_all_starred_keys);\n"
            + "      createOverlay(item, null);\n"
            + "      styleForOverlay();\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function documentPositionComparator (a, b) {\n"
            + "  if (a === b) return 0;\n"
            + "  const position = a.compareDocumentPosition(b);\n"
            + "\n"
            + "  if (position & Node.DOCUMENT_POSITION_FOLLOWING || position & Node"
            + ".DOCUMENT_POSITION_CONTAINED_BY) {\n"
            + "    return -1;\n"
            + "  } else if (position & Node.DOCUMENT_POSITION_PRECEDING || position & Node"
            + ".DOCUMENT_POSITION_CONTAINS) {\n"
            + "    return 1;\n"
            + "  } else {\n"
            + "    return 0;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function extractAllItems(overlay=false) {\n"
            + "  // Amazon mobile\n"
            + "  items = document.querySelectorAll(`\n"
            + "      .sx-table-item,\n"
            + "      .a-carousel-card, /* this has lots of false positives! */\n"
            + "      .mwebCarousel__slide,\n"
            + "      .ci-multi-asin-deals-card-row,\n"
            + "      .gwm-Secondary-row,\n"
            + "      .gwm-MultiAsinCard-row,\n"
            + "      .p13n-sc-list-item,\n"
            + "      .gwm-Card, /* this has lots of false positives! */\n"
            + "      .a-list-item .a-link-normal /* hard to handle */\n"
            + "  `);\n"
            + "  if (items.length == 0) {\n"
            + "    // Amazon desktop\n"
            + "    items = document.querySelectorAll('.s-result-item, .s-result-card');\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // eBay mobile\n"
            + "    // s-item for SRP; b-tile for \"Best Selling\", etc\n"
            + "    items = document.querySelectorAll(`\n"
            + "        .s-item,\n"
            + "        .b-tile,\n"
            + "        .card-item-wrapper,\n"
            + "        .similar-item,\n"
            + "        .mfe-reco,\n"
            + "        div.col\n"
            + "    `);\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // Aliexpress mobile\n"
            + "    items = document.querySelectorAll(`\n"
            + "        .grid-qp,\n"
            + "        .amp-carousel,\n"
            + "        .detail-product-item\n"
            + "    `);\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // Bestbuy mobile\n"
            + "    items = document.querySelectorAll(`\n"
            + "        .sku-item,\n"
            + "        .item-container,\n"
            + "        .product-card,\n"
            + "        .cyp-item,\n"
            + "        .accessory-item,\n"
            + "        .offer-column,\n"
            + "        .offer-row\n"
            + "    `);\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // HomeDepot mobile\n"
            + "    items = document.querySelectorAll(`\n"
            + "        .pod-PLP__item,\n"
            + "        .owl-item\n"
            + "    `);\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // Costco mobile\n"
            + "    // .product has many false positives, so Costco block need to be at the end.\n"
            + "    // .productThumbnail is for Macy's (merged in cuz they use .hl-product).\n"
            + "    items = document.querySelectorAll(`\n"
            + "        .product,\n"
            + "        .hl-product,\n"
            + "        .productThumbnail,\n"
            + "        .slick-slide\n"
            + "    `);\n"
            + "  }\n"
            + "  if (items.length == 0) {\n"
            + "    // Generic pattern\n"
            + "    const candidates = new Set();\n"
            + "    items = document.querySelectorAll('a');\n"
            + "\n"
            + "    const urlMap = new Map();\n"
            + "    for (const item of items) {\n"
            + "      if (!urlMap.has(item.href)) {\n"
            + "        urlMap.set(item.href, new Set());\n"
            + "      }\n"
            + "      urlMap.get(item.href).add(item);\n"
            + "      candidates.add(item);\n"
            + "    }\n"
            + "\n"
            + "    for (const [key, value] of urlMap) {\n"
            + "      const ancestor = commonAncestorList(Array.from(value));\n"
            + "      //console.log('urlMap entries', key, value, ancestor);\n"
            + "      if (!candidates.has(ancestor))\n"
            + "        candidates.add(ancestor);\n"
            + "    }\n"
            + "    const ancestors = new Set();\n"
            + "    // TODO: optimize this part.\n"
            + "    for (let depth = 0; depth < 1; depth++) {\n"
            + "      for (const item of candidates) {\n"
            + "        for (let i = 0; i < depth; i++) {\n"
            + "          item = item.parentElement;\n"
            + "          if (!item) break;\n"
            + "        }\n"
            + "        if (item) ancestors.add(item);\n"
            + "      }\n"
            + "    }\n"
            + "    items = Array.from(ancestors);\n"
            + "  }\n"
            + "  console.log(items);\n"
            + "  const outputMap = new Map();\n"
            + "  const processed = new Set();\n"
            + "  const extracted_items = [];\n"
            + "  for (const item of items) {\n"
            + "    extractOneItem(item, extracted_items, processed, outputMap, overlay);\n"
            + "  }\n"
            + "  const keysInDocOrder = Array.from(outputMap.keys()).sort"
            + "(documentPositionComparator);\n"
            + "  const output = [];\n"
            + "  for (const key of keysInDocOrder) {\n"
            + "    output.push(outputMap.get(key));\n"
            + "  }\n"
            + "  return output;\n"
            + "}\n"
            + "\n"
            + "function toggleOverlay() {\n"
            + "  document.body.classList.toggle(showClassName);\n"
            + "}\n"
            + "\n"
            + "function getOverlayVisibility() {\n"
            + "  return document.body.classList.contains(showClassName);\n"
            + "}\n"
            + "\n"
            + "function setOverlayVisibility(isVisible) {\n"
            + "  if (isVisible) {\n"
            + "    document.body.classList.add(showClassName);\n"
            + "  } else {\n"
            + "    document.body.classList.remove(showClassName);\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function extractItems() {\n"
            + "  return {'items': extractAllItems(true), 'isOverlayVisible': getOverlayVisibility"
            + "()};\n"
            + "}\n"
            + "\n";

    public CollectionManager(
            ChromeActivity chromeActivity, BottomSheetController bottomSheetController) {
        mActivity = chromeActivity;
        mContext = chromeActivity;
        mFragmentManager = chromeActivity.getSupportFragmentManager();
        mBottomSheetController = bottomSheetController;
        mSharedPreferences = mContext.getSharedPreferences(PREFERENCES_NAME, 0);
        // TODO: async loading should mostly be OK.
        new Handler().post(this::loadStarredItems);
        sInstance = this;

        final TabModelObserver tabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, @TabModel.TabSelectionType int type, int lastId) {
                // Necessary to handle switching to another tab.
                Log.e("", "Collection onShown tagId = %d", tab.getId());
                if (!UrlUtilities.isHttpOrHttps(tab.getUrl())) return;
                TabGroupList tabGroupList =
                        mActivity.getTabModelSelector().getCurrentModel().getTabGroupList();
                if (tabGroupList.getAllTabIdsInSameGroup(tab.getId()).size() > 1) return;
                Log.e(TAG, "mHasItems = " + mHasItems + " tab = " + tab);
                Boolean hasItems = mHasItems.get(tab);
                if (hasItems == null) return;
                if (hasItems) {
                    // TODO: hacky delay to wait until onSceneChange() is done in
                    // ActivityTabProvider, and all animations to settle down. Might be moot after
                    // converting to infobar.
                    ThreadUtils.postOnUiThreadDelayed(
                            () -> showBottomSheet(mFragmentManager, tab), 600);
                }
            }
        };
        chromeActivity.getCurrentTabModel().addObserver(tabModelObserver);

        new TabModelSelectorTabObserver(chromeActivity.getTabModelSelector()) {
            @Override
            public void onLoadStopped(Tab tab, boolean toDifferentDocument) {
                // TODO: seems to be a little bit over-triggering
                // Identical to onDidFinishNavigation with isInMainFrame and hasCommitted.
                Log.e("", "Collection onLoadStopped tagId = %d, toDifferentDocument %b",
                        tab.getId(), toDifferentDocument);
                if (!UrlUtilities.isHttpOrHttps(tab.getUrl())) return;
                TabGroupList tabGroupList =
                        mActivity.getTabModelSelector().getCurrentModel().getTabGroupList();
                if (tabGroupList.getAllTabIdsInSameGroup(tab.getId()).size() > 1) return;
                // TODO: use a faster triggering logic than the full extraction.
                extractItems(tab, null, (hasItem) -> {
                    mHasItems.put(tab, hasItem);
                    Log.e(TAG, "put mHasItems = " + mHasItems + " tab = " + tab);
                    if (hasItem) {
                        showBottomSheet(mFragmentManager, tab);
                    }
                });
            }
        };
    }

    static public CollectionManager getInstance() {
        return sInstance;
    }

    private void closeBottomSheet() {
        mBottomSheetController.hideContent(mContent, true);
    }

    public void toggleItem(String json) {
        CollectionItem item = null;
        try {
            item = parseJsonItem(new JSONObject(json));
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        if (item == null) return;

        if (!COLLECT_TO_LIST) {
            LoadUrlParams loadUrlParams = new LoadUrlParams(item.url);
            Tab tab = mActivity.getActivityTab();
            tab.getTabModelSelector().openNewTab(loadUrlParams,
                    TabModel.TabLaunchType.FROM_LONGPRESS_BACKGROUND, tab, tab.isIncognito());
            closeBottomSheet();
            return;
        }
        if (item.starred) {
            addStarredItem(item);
        } else {
            removeStarredItem(item);
        }
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

    static private CollectionItem parseJsonItem(JSONObject c) {
        try {
            return new CollectionItem(c.getString("url"), c.getString("imageUrl"),
                    c.getString("title"), c.getString("price"), c.optBoolean("starred", false));
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        return null;
    }

    static private List<CollectionItem> parseJson(JSONArray json) {
        List<CollectionItem> items = new ArrayList<>();
        try {
            for (int i = 0; i < json.length(); i++) {
                JSONObject c = json.getJSONObject(i);
                items.add(parseJsonItem(c));
            }
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        return items;
    }

    private boolean isAllowedUrl(String url) {
        return url.startsWith("http");
        /*
        return (url.startsWith("https://www.amazon.com") || url.startsWith("https://www.ebay.com")
                || url.startsWith("https://m.ebay.com"));
        */
    }

    private void updateItems(final Tab tab) {
        if (!isAllowedUrl(tab.getUrl())) return;
        if (tab.getWebContents() == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("clank_all_starred_keys = {");
        for (CollectionItem item : mStarred) {
            sb.append("\"");
            sb.append(item.imageUrl);
            sb.append("\":0, ");
        }
        sb.append("};\n");
        sb.append(sScript);
        sb.append("extractAllItems(true)");

        // TODO: evaluateJavaScriptForTests should not be used in prod.
        tab.getWebContents().evaluateJavaScriptForTests(sb.toString(), null);
    }

    private void extractItems(
            final Tab tab, Boolean showOverlay, Callback<Boolean> hasItemsCallback) {
        if (!isAllowedUrl(tab.getUrl())) return;
        if (tab.getWebContents() == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("clank_all_starred_keys = {");
        for (CollectionItem item : mStarred) {
            sb.append("\"");
            sb.append(item.imageUrl);
            sb.append("\":0, ");
        }
        sb.append("};\n");
        sb.append(sScript);
        if (showOverlay != null) sb.append("setOverlayVisibility(" + showOverlay + ");\n");
        sb.append("extractItems();");

        // TODO: evaluateJavaScriptForTests should not be used in prod.
        tab.getWebContents().evaluateJavaScriptForTests(sb.toString(), (json) -> {
            Log.e(TAG, "Collection json: %s", json);
            List<CollectionItem> items;
            boolean isOverlayVisible;
            try {
                JSONObject root = new JSONObject(json);
                items = parseJson(root.getJSONArray("items"));
                isOverlayVisible = root.getBoolean("isOverlayVisible");
            } catch (final JSONException e) {
                Log.e(TAG, "JSON parsing error: " + e.getMessage());
                return;
            }
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
                //Log.e(TAG, "Collection size = %d", mAll.size());
            }
            if (hasItemsCallback != null) hasItemsCallback.onResult(items.size() > 0);
            mHasOverlay.put(tab, isOverlayVisible);
            updateInfobarTitle();
        });
        mTabs.add(new WeakReference<>(tab));
    }

    public void setOverlayVisibility(boolean visible) {
        final Tab tab = mActivity.getActivityTab();
        if (tab == null) return;
        if (!isAllowedUrl(tab.getUrl())) return;
        if (tab.getWebContents() == null) return;

        // TODO: evaluateJavaScriptForTests should not be used in prod.
        tab.getWebContents().evaluateJavaScriptForTests(
                "setOverlayVisibility(" + visible + ");", null);
        mHasOverlay.put(tab, visible);
        updateInfobarTitle();
    }

    // TODO: do we only show the list on shopping sites? If so, how to get it on non-shopping sites?
    public void showBottomSheet(FragmentManager fragmentManager, final Tab tab) {
        if (tab.getWebContents() == null) return;
        showBottomSheet(fragmentManager);
        //extractItems(tab, false, v -> {mToggle.setEnabled(v);});
        //extractItems(tab, false, null);
    }

    public Boolean hasOverlayOnCurrentTab() {
        Boolean hasOverlay = mHasOverlay.get(mActivity.getActivityTab());
        if (hasOverlay == null) return false;
        return hasOverlay;
    }

    @SuppressLint("SetTextI18n")
    private void updateInfobarTitle() {
        Boolean hasOverlay = mHasOverlay.get(mActivity.getActivityTab());
        if (hasOverlay == null) return;

        if (mTitle == null) return;
        if (hasOverlay) {
            mTitle.setText("Tap highlighted links to open");
        } else {
            mTitle.setText("Compare products in a tab group");
        }
    }

    private void showBottomSheet(FragmentManager fragmentManager) {
        if (mContent == null) {
            CollectionPagerAdapter adapter = new CollectionPagerAdapter(fragmentManager, this);

            View container = LayoutInflater.from(mContext).inflate(
                    R.layout.collection_main, mBottomSheetController.getBottomSheet(), false);
            ViewPager pager =
                    (ViewPager) container; // container.findViewById(R.id.collection_pager);
            pager.setAdapter(adapter);

            if (CollectionPagerAdapter.ONLY_SHOW_STARRED) {
                View tabLayout = container.findViewById(R.id.collection_tab);
                tabLayout.setVisibility(View.GONE);
            }

            View toolbarView = LayoutInflater.from(mContext).inflate(
                    R.layout.collection_toolbar, mBottomSheetController.getBottomSheet(), false);

            //toolbarView.setOnClickListener(v -> { mBottomSheetController.expandSheet(); });

            AppCompatImageButton closeButton = toolbarView.findViewById(R.id.close_button);
            closeButton.setOnClickListener(v -> {
                closeBottomSheet();
                setOverlayVisibility(false);
            });

            mTitle = toolbarView.findViewById(R.id.title);
            mTitle.setOnClickListener((v) -> {
                if (hasOverlayOnCurrentTab()) return;
                setOverlayVisibility(true);
            });

            /*
            mBottomSheetController.getBottomSheet().addObserver(new EmptyBottomSheetObserver() {
                @Override
                public void onSheetOpened(@BottomSheet.StateChangeReason int reason) {
                    closeButton.setImageResource(R.drawable.ic_expand_more_black_24dp);
                    mToggle.setVisibility(View.GONE);
                }

                @Override
                public void onSheetClosed(@BottomSheet.StateChangeReason int reason) {
                    closeButton.setImageResource(R.drawable.ic_expand_less_black_24dp);
                    mToggle.setVisibility(View.VISIBLE);
                }
            });
            */

            mContent = new CollectionBottomSheetContent(pager, toolbarView);
        }
        mBottomSheetController.requestShowContent(mContent, true);
        updateInfobarTitle();
        Log.e(TAG, "Collection requestShowContent");
    }

    void addStarredItem(CollectionItem item) {
        mStarredAdapter.addOrUpdateItem(item);
        if (mAllAdapter != null) mAllAdapter.updateItem(item);

        updateAllTabs();
        saveStarredItems();
    }

    void removeStarredItem(CollectionItem item) {
        mStarredAdapter.removeItem(item);
        if (mAllAdapter != null) mAllAdapter.updateItem(item);

        updateAllTabs();
        saveStarredItems();
    }

    private void loadStarredItems() {
        String json = mSharedPreferences.getString("starred", "[]");
        try {
            mStarred.addAll(parseJson(new JSONArray(json)));
        } catch (final JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
        for (CollectionItem item : mStarred) {
            item.starred = true;
        }
        // Nullity checking is needed because async loading leads to racing conditions.
        if (mStarredAdapter != null) mStarredAdapter.notifyDataSetChanged();
    }

    private void saveStarredItems() {
        mSharedPreferences.edit().putString("starred", serializeToJson(mStarred)).apply();
    }

    private void updateAllTabs() {
        for (WeakReference<Tab> tabWeakReference : mTabs) {
            Tab tab = tabWeakReference.get();
            if (tab == null) continue;
            Log.e(TAG, "weiyinchen update tab " + tab.getUrl());
            updateItems(tab);
        }
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
