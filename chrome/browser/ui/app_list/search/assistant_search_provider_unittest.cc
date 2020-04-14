// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/app_list/search/assistant_search_provider.h"
#include "chrome/browser/ui/app_list/app_list_test_util.h"
#include "chrome/browser/ui/app_list/search/chrome_search_result.h"

namespace app_list {
namespace test {

// AssistantSearchProviderTest -------------------------------------------------

class AssistantSearchProviderTest : public AppListTestBase {
 public:
  AssistantSearchProviderTest() = default;
  AssistantSearchProviderTest(const AssistantSearchProviderTest&) = delete;
  AssistantSearchProviderTest& operator=(const AssistantSearchProviderTest&) =
      delete;
  ~AssistantSearchProviderTest() override = default;

  AssistantSearchProvider& search_provider() { return search_provider_; }

 private:
  AssistantSearchProvider search_provider_;
};

// Tests -----------------------------------------------------------------------

TEST_F(AssistantSearchProviderTest, ShouldHaveAnInitialChipResult) {
  ASSERT_EQ(1u, search_provider().results().size());

  const ChromeSearchResult& result = *search_provider().results().at(0);
  EXPECT_EQ(ash::SearchResultDisplayIndex::kFirstIndex, result.display_index());
  EXPECT_EQ(ash::SearchResultDisplayType::kChip, result.display_type());
  EXPECT_EQ(ash::AppListSearchResultType::kAssistantChip, result.result_type());
  EXPECT_EQ(ash::SearchResultType::ASSISTANT, result.GetSearchResultType());
}

}  // namespace test
}  // namespace app_list
