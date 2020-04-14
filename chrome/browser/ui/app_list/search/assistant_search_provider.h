// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_UI_APP_LIST_SEARCH_ASSISTANT_SEARCH_PROVIDER_H_
#define CHROME_BROWSER_UI_APP_LIST_SEARCH_ASSISTANT_SEARCH_PROVIDER_H_

#include "chrome/browser/ui/app_list/search/search_provider.h"

namespace app_list {

// A search provider implementation serving results from Assistant.
// NOTE: This is currently only used to provide a single search result when
// launcher chip integration is enabled from Assistant's internal cache of
// conversation starters.
class AssistantSearchProvider : public SearchProvider {
 public:
  AssistantSearchProvider();
  AssistantSearchProvider(const AssistantSearchProvider&) = delete;
  AssistantSearchProvider& operator=(const AssistantSearchProvider&) = delete;
  ~AssistantSearchProvider() override;

  // SearchProvider:
  void Start(const base::string16& query) override {}
};

}  // namespace app_list

#endif  // CHROME_BROWSER_UI_APP_LIST_SEARCH_ASSISTANT_SEARCH_PROVIDER_H_
