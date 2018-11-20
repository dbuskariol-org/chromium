let overlayClassName = 'clank_shopping_overlay';
let showClassName = 'show_' + overlayClassName;

// Aliexpress uses 'US $12.34' format in the price.
// Macy's uses "$12.34 to 56.78" format.
let priceCleanupPrefix = 'sale|with offer|only|our price|now|starting at';
let priceCleanupPostfix = '(/(each|set))';
let priceRegexTemplate =
  '((reg|regular|orig|from|' + priceCleanupPrefix + ')\\s+)?' +
   '(\\d+\\s*/\\s*)?(US(D)?\\s*)?' +
   '\\$[\\d.,]+(\\s+(to|-|–)\\s+(\\$)?[\\d.,]+)?' +
   priceCleanupPostfix + '?';
let priceRegexFull = new RegExp('^' + priceRegexTemplate + '$', 'i');
let priceRegex = new RegExp(priceRegexTemplate, 'i');
let priceCleanupRegex = new RegExp('^((' + priceCleanupPrefix + ')\\s+)|' + priceCleanupPostfix + '$', 'i');

function getLazyLoadingURL(image) {
  // FIXME: some lazy images in Nordstrom and Staples don't have URLs in the DOM.
  // TODO: add more lazy-loading attributes.
  for (const attribute of ['data-src', 'data-img-url', 'data-config-src', 'data-echo', 'data-lazy']) {
    let url = image.getAttribute(attribute);
    if (url == null) continue;
    if (url.substr(0, 2) == '//') url = 'https:' + url;
    if (url.substr(0, 4) != 'http') continue;
    return url;
  }
}

function getLargeImages(root, atLeast, relaxed = false) {
  let candidates = root.querySelectorAll('img');
  if (candidates.length == 0) {
    // Aliexpress
    candidates = root.querySelectorAll('amp-img');
  }
  images = [];
  function shouldStillKeep(image) {
    if (!relaxed) return false;
    if (image.getAttribute('aria-hidden') == 'true') return true;
    if (getLazyLoadingURL(image) != null) return true;
    return false;
  }
  for (const image of candidates) {
    console.log('offsetHeight', image, image.offsetHeight);
    if (image.offsetHeight < atLeast) {
      if (!shouldStillKeep(image))
        continue;
    }
    if (window.getComputedStyle(image)['visibility'] == 'hidden')
      continue;
    if (image.parentElement.parentElement.className == overlayClassName)
      continue;
    images.push(image);
  }
  return images;
}

function getVisibleElements(list) {
  visible = [];
  for (const ele of list) {
    if (ele.offsetHeight == 0 || ele.offsetHeight == 0) continue;
    visible.push(ele);
  }
  return visible;
}

function extractImage(item) {
  // Sometimes an item contains small icons, which need to be filtered out.
  // TODO: two pass getLargeImages() is probably too slow.
  let images = getLargeImages(item, 40);
  if (images.length == 0) images = getLargeImages(item, 30, true);
  console.assert(images.length == 1, 'image extraction error', item, images);
  if (images.length != 1) {
    return null;
  }
  const image = images[0];
  const lazyUrl = getLazyLoadingURL(image);
  if (lazyUrl != null) return lazyUrl;

  // If |image| is <amp-img>, image.src won't work.
  //console.log("image src", image.getAttribute('src'));
  const src = image.getAttribute('src');
  if (src != null) {
    // data: images are usually placeholders.
    // Even if it's valid, we prefer http(s) URLs.
    if (!src.startsWith('data:'))
      return src;
  }
  sourceSet = image.getAttribute('data-search-image-source-set');
  if (sourceSet == null) return null;
  console.assert(sourceSet.includes(' '), 'image extraction error', image);
  // TODO: Pick the one with right pixel density?
  imageUrl = sourceSet.split(' ')[0];
  console.assert(imageUrl.length > 0, 'image extraction error', sourceSet);
  return imageUrl;
}

function extractUrl(item) {
  let anchors;
  if (item.tagName == 'A') {
    anchors = [item];
  } else {
    anchors = item.querySelectorAll('a');
  }
  console.assert(anchors.length >= 1, 'url extraction error', item);
  if (anchors.length == 0) {
    return null;
  }
  const filtered = [];
  for (const anchor of anchors) {
    if (anchor.href.match(/\/#$/)) continue;
    // href="javascript:" would be sanitized when serialized to MHTML.
    if (anchor.href.match(/^javascript:/)) continue;
    if (anchor.href == '') {
      // For Sears
      let href = anchor.getAttribute('bot-href');
      if (href != null && href.length > 0) {
        // Resolve to absolute URL.
        anchor.href = href;
        href = anchor.href;
        anchor.removeAttribute('href');
        if (href != '') return href;
      }
      continue;
    }
    filtered.push(anchor);
    // TODO: This returns the first URL in DOM order.
    //       Use the one with largest area instead?
    return anchor.href;
  }
  if (filtered.length == 0) return null;
  return filtered.reduce(function(a, b) {
      return a.offsetHeight * a.offsetWidth > b.offsetHeight * b.offsetWidth ? a : b;
  }).href;
}

function isInlineDisplay(element) {
  const display = window.getComputedStyle(element)['display'];
  return display.indexOf('inline') != -1;
}

function childElementCountExcludingInline(element) {
  let count = 0;
  for (const child of element.children) {
    if (isInlineDisplay(child)) count += 1;
  }
  return count;
}

function hasNonInlineDescendentsInclusive(element) {
  if (!isInlineDisplay(element)) return true;
  return hasNonInlineDescendents(element);
}

function hasNonInlineDescendents(element) {
  for (const child of element.children) {
    if (hasNonInlineDescendentsInclusive(child)) return true;
  }
  return false;
}

function hasNonWhiteTextNodes(element) {
  for (const child of element.childNodes) {
    if (child.nodeType != document.TEXT_NODE) continue;
    if (child.nodeValue.trim() != '') return true;
  }
  return false;
}

// Concat classNames and IDs of ancestors up to |maxDepth|, while not containing
// |excludingElement|.
// If |excludingElement| is already a descendent of |element|, still return the
// className of |element|.
// |maxDepth| include current level, so maxDepth = 1 means just |element|.
// maxDepth >= 3 causes error in Walmart deals if not deducting "price".
function ancestorIdAndClassNames(element, excludingElement, maxDepth = 3) {
  let name = '';
  let depth = 0;
  while (true) {
    name += element.className + element.id;
    element = element.parentElement;
    depth += 1;
    if (depth >= maxDepth) break;
    if (!element) break;
    if (element.contains(excludingElement)) break;
  }
  return name;
}

/*
  Returns top-ranked element with the following criteria, with decreasing priority:
  - score based on whether ancestorIdAndClassNames contains "title", "price", etc.
  - largest area
  - largest font size
  - longest text
 */
function chooseTitle(elementArray) {
  return elementArray.reduce(function(a, b) {
    const titleRegex = /name|title|truncate|desc|brand/i;
    const negativeRegex = /price|model/i;
    const a_str = ancestorIdAndClassNames(a, b);
    const b_str = ancestorIdAndClassNames(b, a);
    const a_score = (a_str.match(titleRegex) != null) - (a_str.match(negativeRegex) != null);
    const b_score = (b_str.match(titleRegex) != null) - (b_str.match(negativeRegex) != null);
    console.log('className score', a_score, b_score, a_str, b_str, a, b);

    if (a_score != b_score) {
      return a_score > b_score ? a : b;
    }

    // Use getBoundingClientRect() to avoid int rounding error in offsetHeight/Width.
    const a_area = a.getBoundingClientRect().width * a.getBoundingClientRect().height;
    const b_area = b.getBoundingClientRect().width * b.getBoundingClientRect().height;
    console.log('getBoundingClientRect', a.getBoundingClientRect(), b.getBoundingClientRect(), a, b);

    if (a_area != b_area) {
      return a_area > b_area ? a : b;
    }

    const a_size = parseFloat(window.getComputedStyle(a)['font-size']);
    const b_size = parseFloat(window.getComputedStyle(b)['font-size']);
    console.log('font size', a_size, b_size, a, b);

    if (a_size != b_size) {
      return a_size > b_size ? a : b;
    }

    return a.innerText.length > b.innerText.length ? a : b;
  });
}

function extractTitle(item) {
  if (false) {
    // Carousel in Amazon mobile
    // The heuristic peeks into the title attribute, which is invisible.
    // Probably not general enough to use.
    let titles = item.querySelectorAll('a.a-link-normal');
    if (titles.length > 0) {
      title = titles[0].title;
      if (title != null) {
        title = title.trim();
        if (title.length > 0) return title;
      }
    }
  }

  const possible_titles = item.querySelectorAll('a, span, p, div, h2, h3, h4, h5, strong');
  let titles = [];
  for (const title of possible_titles) {
    if (hasNonInlineDescendents(title) &&
        !hasNonWhiteTextNodes(title)) {
      continue;
    }
    if (title.innerText.trim() == '') continue;
    if (title.innerText.trim().toLowerCase() == 'sponsored') continue;
    if (title.childElementCount > 0) {
      if (title.textContent.trim() == title.lastElementChild.textContent.trim()
            || title.textContent.trim() == title.firstElementChild.textContent.trim()) {
        continue;
      }
    }
    // Aliexpress has many items without title. Without the following filter,
    // the title would be the price.
    //if (title.innerText.trim().match(priceRegexFull)) continue;
    titles.push(title);
  }
  if (titles.length > 1) {
    console.log('all generic titles', item, titles);
    titles = [chooseTitle(titles)];
  }

  console.log('titles', item, titles);
  console.assert(titles.length == 1, 'titles extraction error', item, titles);
  if (titles.length != 1) return null;
  title = titles[0].innerText.trim();
  return title;
}

function adjustBeautifiedCents(priceElement) {
  const text = priceElement.innerText.trim().replace(/\/(each|set)$/i, '');
  let cents;
  const children = priceElement.children;
  for (let i = children.length - 1; i >= 0; i--) {
    const t = children[i].innerText.trim();
    if (t == "") continue;
    if (t.indexOf('/') != -1) continue;
    cents = t;
    break;
  }
  if (cents == null) return null;
  console.log('cents', cents, priceElement);
  if (cents.length == 2
      && cents == text.slice(-cents.length)
      && text.slice(-3, -2).match(/\d/)) {
    return text.substr(0, text.length - cents.length) + '.' + cents;
  }
}

function anyLineThroughInAncentry(element, maxDepth = 2) {
  let depth = 0;
  while (element != null && element.tagName != 'BODY') {
    if (window.getComputedStyle(element)['text-decoration'].indexOf('line-through') != -1)
      return true;
    element = element.parentElement;
    depth += 1;
    if (depth >= maxDepth) break;
  }
  return false;
}

function forgivingParseFloat(str) {
  return parseFloat(str.replace(priceCleanupRegex, '').replace(/^[$]*/, ''));
}

function choosePrice(priceArray) {
  if (priceArray.length == 0) return null;
  return priceArray.reduce(function(a, b) {
    // Positive tags
    for (const pattern of ['with offer', 'sale', 'now']) {
      const a_val = a.toLowerCase().indexOf(pattern) != -1;
      const b_val = b.toLowerCase().indexOf(pattern) != -1;
      if (a_val != b_val) {
        return a_val > b_val ? a : b;
      }
    }
    // Negative tags
    for (const pattern of ['/set', '/each']) {
      const a_val = a.toLowerCase().indexOf(pattern) != -1;
      const b_val = b.toLowerCase().indexOf(pattern) != -1;
      if (a_val != b_val) {
        return a_val < b_val ? a : b;
      }
    }
    // Guess the smallest numerical value.
    // The tags like "now" don't always fall inside element boundary.
    // See Nordstrom/homepage-eager.mhtml.
    return forgivingParseFloat(a) > forgivingParseFloat(b) ? b : a;
  }).replace(priceCleanupRegex, '');
}

function extractPrice(item) {
  // Etsy mobile
  const prices = item.querySelectorAll(`
      .currency-value
  `);
  if (prices.length == 1) {
    let ans = prices[0].textContent.trim();
    if (ans.match(/^\d/)) ans = '$' + ans; // for Etsy
    if (ans != '') return ans;
  }
  // Generic heuristic to search for price elements.
  let captured_prices = [];
  for (const price of item.querySelectorAll('span, p, div, h3')) {
    const candidate = price.innerText.trim();
    if (!candidate.match(priceRegexFull)) continue;
    console.log('price candidate', candidate, price);
    if (price.childElementCount > 0) {
      // Avoid matching the parent element of the real price element.
      // Otherwise adjustBeautifiedCents would break.
      if (price.innerText.trim() == price.lastElementChild.innerText.trim()
            || price.innerText.trim() == price.firstElementChild.innerText.trim()) {
        // If the wanted child is not scanned, change the querySelectorAll string.
        console.log('skip redundant parent', price);
        continue;
      }
    }
    // TODO: check child elements recursively.
    if (anyLineThroughInAncentry(price)) {
      console.log('line-through', price);
      continue;
    }
    // for Amazon and HomeDepot
    if (candidate.indexOf('.') == -1 && price.lastElementChild != null) {
      const adjusted = adjustBeautifiedCents(price);
      if (adjusted != null) return adjusted;
    }
    captured_prices.push(candidate);
  }
  console.log('captured_prices', captured_prices);
  return choosePrice(captured_prices);
}

function extractItem(item) {
  imageUrl = extractImage(item);
  if (imageUrl == null) {
    console.warn('no images found', item);
    return null;
  }
  url = extractUrl(item);
  // Some items in Sears and Staples only have ng-click or onclick handlers,
  // so it's impossible to extract URL.
  if (url == null) {
    console.warn('no url found', item);
    return null;
  }
  title = extractTitle(item);
  if (title == null) {
    console.warn('no title found', item);
    return null;
  }
  price = extractPrice(item);
  // eBay "You may also like" and "Guides" are not product items.
  // Not having price is one hint.
  // FIXME: "Also viewed" items in Gap doesn't have prices.
  if (price == null) {
    console.warn('no price found', item);
    return null;
  }
  return {'url': url, 'imageUrl': imageUrl, 'title': title, 'price': price};
}

function styleForOverlay() {
  // TODO: "back" might break because of missing style.
  // TODO: insert <style> element instead for convenience?
  if (this.done) return;
  this.done = true;

  const style = document.createElement('style');
  document.head.appendChild(style);
  style.sheet.insertRule('body.show_' + overlayClassName +
    ' .' + overlayClassName + `
    {
      box-sizing: border-box;
      position: absolute;
      top: 0;
      left: 0;
      height: 100%;
      width: 100%;
      background: rgba(77, 144, 254, 0.3);
      border: solid rgb(77, 144, 254) 1px;
      border-radius: 10px;
      display: block;
      text-align: right;
      /* pointer-events: none; */ /* touch pass through */
    }
  `, 0);
  style.sheet.insertRule('body.show_' + overlayClassName +
    ' .' + overlayClassName + `:before
    {
      content: "";
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: -1;
      box-shadow: -5px 3px 10px 2px gray;
    }
  `, 0);
  style.sheet.insertRule('body.show_' + overlayClassName +
    ' .' + overlayClassName + ` div
    {
      pointer-events: auto;
      display: inline-block; /* to shrink the width */
      padding: 8px; /* slightly larger touch area */
    }
  `, 0);
  style.sheet.insertRule('.' + overlayClassName + `
    {
      display: none;
    }
  `, 0);
}

function createOverlay(item, starred) {
  let image;
  if (item.lastElementChild.className !== overlayClassName) {
    //console.log('create overlay for', item);
    // Need this relative/absolute pair for 100% width/height to work.
    item.style.position = 'relative';
    // TODO: traverse up and use the first bg color?
    item.style.backgroundColor = 'white';

    const overlay = document.createElement('div');
    overlay.className = overlayClassName;
    const imageContainer = document.createElement('div');
    overlay.addEventListener('click', toggleItem, false);
    image = document.createElement('img');
    image.width = 36;
    imageContainer.appendChild(image);
    overlay.appendChild(imageContainer);
    item.appendChild(overlay);
  } else {
    image = item.lastElementChild.querySelectorAll('img')[0];
  }
  if (starred != null) {
    image.dataSaved = starred;
  }
  updateBookmarkIcon(image);
}

function updateBookmarkIcon(img) {
  if (img.dataSaved) {
    img.src = 'https://material.io/tools/icons/static/icons/baseline-check-24px.svg';
  } else {
    img.src = 'https://material.io/tools/icons/static/icons/baseline-add_circle-24px.svg';
  }
}

function toggleItem(e) {
  console.log(e);
  // Necessary when the overlay is added to an anchor element.
  e.preventDefault();
  e.stopPropagation();

  const img = this.getElementsByTagName('img')[0];
  if (img.dataSaved) return;
  img.dataSaved = !img.dataSaved;
  updateBookmarkIcon(img);

  const item = img.parentElement.parentElement.parentElement;
  console.assert(item.lastElementChild.className === overlayClassName, 'wrong item', item);
  const extracted = extractItem(item);
  extracted['starred'] = img.dataSaved;
  const payload = encodeURIComponent(JSON.stringify(extracted));
  console.log(item);
  console.log(extractItem(item));
  window.location.href = "intent:" + payload +
      "#Intent;package=com.google.android.apps.chrome;action=com.google.chrome.COLLECT;end;";
}

function commonAncestor(a, b) {
  while (!a.contains(b)) {
    a = a.parentElement;
  }
  return a;
}

function commonAncestorList(list) {
  return list.reduce(function(a, b) {
      return commonAncestor(a, b);
  });
}

function hasOverlap(target, list) {
  for (const element of list) {
    if (element.contains(target) || target.contains(element)) {
      return true;
    }
  }
  return false;
}

function extractOneItem(item, extracted_items, processed, output, overlay) {
  //console.log('trying', item);
  if (item.childElementCount == 0 && item.parentElement.tagName != 'BODY') {
    // Amazone store page uses overlay <a>.
    item = item.parentElement;
    if (item == null) return;
  }
  // scrollHeight could be 0 while getBoundingClientRect().height > 0.
  if (item.getBoundingClientRect().height < 50) {
    console.log('too short', item);
    return;
  }
  if (item.scrollHeight > 1000) {
    console.log('too tall', item);
    return;
  }
  if (item.getBoundingClientRect().height > 1000) {
    console.log('too tall', item);
    return;
  }
  if (item.querySelectorAll('img, amp-img').length == 0)  {
    console.log('no image', item);
    return;
  }
  if (!item.textContent.match(priceRegex)) {
    console.log('no price', item);
    return;
  }
  //console.log('extracted_items', extracted_items);
  if (hasOverlap(item, extracted_items)) {
    console.log('overlap', item);
    return;
  }
  if (processed.has(item)) return;
  processed.add(item);
  console.log('trying', item);
  const extraction = extractItem(item);
  if (extraction != null) {
    output.set(item, extraction);
    extracted_items.push(item);
    if (overlay) {
      const starred = typeof clank_all_starred_keys === "object" && extraction['imageUrl'] in clank_all_starred_keys;
      //console.log(starred, extraction['imageUrl'], clank_all_starred_keys);
      createOverlay(item, null);
      styleForOverlay();
    }
  }
}

function documentPositionComparator (a, b) {
  if (a === b) return 0;
  const position = a.compareDocumentPosition(b);

  if (position & Node.DOCUMENT_POSITION_FOLLOWING || position & Node.DOCUMENT_POSITION_CONTAINED_BY) {
    return -1;
  } else if (position & Node.DOCUMENT_POSITION_PRECEDING || position & Node.DOCUMENT_POSITION_CONTAINS) {
    return 1;
  } else {
    return 0;
  }
}

function extractAllItems(overlay=false) {
  // Amazon mobile
  items = document.querySelectorAll(`
      .sx-table-item,
      .a-carousel-card, /* this has lots of false positives! */
      .mwebCarousel__slide,
      .ci-multi-asin-deals-card-row,
      .gwm-Secondary-row,
      .gwm-MultiAsinCard-row,
      .p13n-sc-list-item,
      .gwm-Card, /* this has lots of false positives! */
      .a-list-item .a-link-normal /* hard to handle */
  `);
  if (items.length == 0) {
    // Amazon desktop
    items = document.querySelectorAll('.s-result-item, .s-result-card');
  }
  if (items.length == 0) {
    // eBay mobile
    // s-item for SRP; b-tile for "Best Selling", etc
    items = document.querySelectorAll(`
        .s-item,
        .b-tile,
        .card-item-wrapper,
        .similar-item,
        .mfe-reco,
        div.col
    `);
  }
  if (items.length == 0) {
    // Aliexpress mobile
    items = document.querySelectorAll(`
        .grid-qp,
        .amp-carousel,
        .detail-product-item
    `);
  }
  if (items.length == 0) {
    // Bestbuy mobile
    items = document.querySelectorAll(`
        .sku-item,
        .item-container,
        .product-card,
        .cyp-item,
        .accessory-item,
        .offer-column,
        .offer-row
    `);
  }
  if (items.length == 0) {
    // HomeDepot mobile
    items = document.querySelectorAll(`
        .pod-PLP__item,
        .owl-item
    `);
  }
  if (items.length == 0) {
    // Costco mobile
    // .product has many false positives, so Costco block need to be at the end.
    // .productThumbnail is for Macy's (merged in cuz they use .hl-product).
    items = document.querySelectorAll(`
        .product,
        .hl-product,
        .productThumbnail,
        .slick-slide
    `);
  }
  if (items.length == 0) {
    // Generic pattern
    const candidates = new Set();
    items = document.querySelectorAll('a');

    const urlMap = new Map();
    for (const item of items) {
      if (!urlMap.has(item.href)) {
        urlMap.set(item.href, new Set());
      }
      urlMap.get(item.href).add(item);
      candidates.add(item);
    }

    for (const [key, value] of urlMap) {
      const ancestor = commonAncestorList(Array.from(value));
      //console.log('urlMap entries', key, value, ancestor);
      if (!candidates.has(ancestor))
        candidates.add(ancestor);
    }
    const ancestors = new Set();
    // TODO: optimize this part.
    for (let depth = 0; depth < 1; depth++) {
      for (const item of candidates) {
        for (let i = 0; i < depth; i++) {
          item = item.parentElement;
          if (!item) break;
        }
        if (item) ancestors.add(item);
      }
    }
    items = Array.from(ancestors);
  }
  console.log(items);
  const outputMap = new Map();
  const processed = new Set();
  const extracted_items = [];
  for (const item of items) {
    extractOneItem(item, extracted_items, processed, outputMap, overlay);
  }
  const keysInDocOrder = Array.from(outputMap.keys()).sort(documentPositionComparator);
  const output = [];
  for (const key of keysInDocOrder) {
    output.push(outputMap.get(key));
  }
  return output;
}

function toggleOverlay() {
  document.body.classList.toggle(showClassName);
}

function getOverlayVisibility() {
  return document.body.classList.contains(showClassName);
}

function setOverlayVisibility(isVisible) {
  if (isVisible) {
    document.body.classList.add(showClassName);
  } else {
    document.body.classList.remove(showClassName);
  }
}

function extractItems() {
  return {'items': extractAllItems(true), 'isOverlayVisible': getOverlayVisibility()};
}

extracted_results = extractAllItems(true);
console.table(extracted_results);
