// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "third_party/blink/renderer/core/css/resolver/matched_properties_cache.h"

#include "third_party/blink/renderer/core/css/css_test_helpers.h"
#include "third_party/blink/renderer/core/css/resolver/style_resolver.h"
#include "third_party/blink/renderer/core/html/html_element.h"
#include "third_party/blink/renderer/core/style/computed_style.h"
#include "third_party/blink/renderer/core/testing/page_test_base.h"

namespace blink {

class MatchedPropertiesCacheTestKey {
  STACK_ALLOCATED();

 public:
  explicit MatchedPropertiesCacheTestKey(String block_text, unsigned hash)
      : key_(ParseBlock(block_text), hash) {
    DCHECK(key_.IsValid());
  }

  const MatchedPropertiesCache::Key& InnerKey() const { return key_; }

 private:
  const MatchResult& ParseBlock(String block_text) {
    result_.FinishAddingUARules();
    result_.FinishAddingUserRules();
    auto* set = css_test_helpers::ParseDeclarationBlock(block_text);
    result_.AddMatchedProperties(set);
    result_.FinishAddingAuthorRulesForTreeScope();
    return result_;
  }

  MatchResult result_;
  MatchedPropertiesCache::Key key_;
};

using TestKey = MatchedPropertiesCacheTestKey;

class MatchedPropertiesCacheTestCache {
  STACK_ALLOCATED();

 public:
  explicit MatchedPropertiesCacheTestCache(Document& document)
      : document_(document) {}

  ~MatchedPropertiesCacheTestCache() {
    // Required by DCHECK in ~MatchedPropertiesCache.
    cache_.Clear();
  }

  void Add(const TestKey& key,
           const ComputedStyle& style,
           const ComputedStyle& parent_style) {
    cache_.Add(key.InnerKey(), style, parent_style);
  }

  const CachedMatchedProperties* Find(const TestKey& key,
                                      const ComputedStyle& style,
                                      const ComputedStyle& parent_style) {
    StyleResolverState state(document_, *document_.body(), &parent_style,
                             &parent_style);
    state.SetStyle(ComputedStyle::Clone(style));
    return cache_.Find(key.InnerKey(), state);
  }

 private:
  MatchedPropertiesCache cache_;
  Document& document_;
};

using TestCache = MatchedPropertiesCacheTestCache;

class MatchedPropertiesCacheTest : public PageTestBase {
 public:
  scoped_refptr<ComputedStyle> CreateStyle() {
    return StyleResolver::InitialStyleForElement(GetDocument());
  }
};

TEST_F(MatchedPropertiesCacheTest, Miss) {
  TestCache cache(GetDocument());
  TestKey key("color:red", 1);

  auto style = CreateStyle();
  auto parent = CreateStyle();

  EXPECT_FALSE(cache.Find(key, *style, *parent));
}

TEST_F(MatchedPropertiesCacheTest, Hit) {
  TestCache cache(GetDocument());
  TestKey key("color:red", 1);

  auto style = CreateStyle();
  auto parent = CreateStyle();

  cache.Add(key, *style, *parent);
  EXPECT_TRUE(cache.Find(key, *style, *parent));
}

TEST_F(MatchedPropertiesCacheTest, HitOnlyForAddedEntry) {
  TestCache cache(GetDocument());

  auto style = CreateStyle();
  auto parent = CreateStyle();

  TestKey key1("color:red", 1);
  TestKey key2("display:block", 2);

  cache.Add(key1, *style, *parent);

  EXPECT_TRUE(cache.Find(key1, *style, *parent));
  EXPECT_FALSE(cache.Find(key2, *style, *parent));
}

}  // namespace blink
