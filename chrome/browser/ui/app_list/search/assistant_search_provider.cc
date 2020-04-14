// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/app_list/search/assistant_search_provider.h"

#include "ash/public/cpp/app_list/app_list_metrics.h"
#include "ash/public/cpp/app_list/app_list_types.h"
#include "base/strings/utf_string_conversions.h"
#include "base/unguessable_token.h"
#include "chrome/browser/ui/app_list/search/chrome_search_result.h"

namespace app_list {

namespace {

constexpr char kIdPrefix[] = "googleassistant://";

// AssistantSearchResult -------------------------------------------------------

// TODO(b:153165835): Populate metadata from an actual conversation starter.
class AssistantSearchResult : public ChromeSearchResult {
 public:
  AssistantSearchResult() {
    set_id(kIdPrefix + base::UnguessableToken::Create().ToString());
    SetDisplayIndex(ash::SearchResultDisplayIndex::kFirstIndex);
    SetDisplayType(ash::SearchResultDisplayType::kChip);
    SetResultType(ash::AppListSearchResultType::kAssistantChip);
    SetTitle(base::UTF8ToUTF16("AssistantSearchResult"));
  }

  AssistantSearchResult(const AssistantSearchResult&) = delete;
  AssistantSearchResult& operator=(const AssistantSearchResult&) = delete;
  ~AssistantSearchResult() override = default;

 private:
  // ChromeSearchResult:
  ash::SearchResultType GetSearchResultType() const override {
    return ash::SearchResultType::ASSISTANT;
  }

  // TODO(b:153166883): Handle opening Assistant result.
  void Open(int event_flags) override { NOTIMPLEMENTED(); }
};

}  // namespace

// AssistantSearchProvider -----------------------------------------------------

AssistantSearchProvider::AssistantSearchProvider() {
  // TODO(b:153165835): Populate results from conversation starters cache.
  // NOTE: We'll only create a result if we meet a confidence score threshold.
  SearchProvider::Results results;
  results.push_back(std::make_unique<AssistantSearchResult>());
  SwapResults(&results);
}

AssistantSearchProvider::~AssistantSearchProvider() = default;

// TODO(b:153167085): Implement refresh logic to fetch fresh results.

}  // namespace app_list
