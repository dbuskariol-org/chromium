// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/settings/chromeos/search/search_tag_registry.h"

#include "base/no_destructor.h"
#include "chrome/browser/chromeos/local_search_service/index.h"
#include "chrome/browser/chromeos/local_search_service/local_search_service.h"
#include "chrome/browser/ui/webui/settings/chromeos/constants/routes.mojom.h"
#include "chrome/browser/ui/webui/settings/chromeos/search/search_concept.h"
#include "chrome/grit/generated_resources.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace chromeos {
namespace settings {
namespace {

class FakeObserver : public SearchTagRegistry::Observer {
 public:
  FakeObserver() = default;
  ~FakeObserver() override = default;

  size_t num_calls() const { return num_calls_; }

 private:
  // SearchTagRegistry::Observer:
  void OnRegistryUpdated() override { ++num_calls_; }

  size_t num_calls_ = 0;
};

// Note: Copied from printing_section.cc but does not need to stay in sync with
// it.
const std::vector<SearchConcept>& GetPrintingSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_PRINTING_ADD_PRINTER,
       mojom::kPrintingDetailsSubpagePath,
       mojom::SearchResultIcon::kPrinter,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAddPrinter}},
      {IDS_OS_SETTINGS_TAG_PRINTING_SAVED_PRINTERS,
       mojom::kPrintingDetailsSubpagePath,
       mojom::SearchResultIcon::kPrinter,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kSavedPrinters}},
      {IDS_OS_SETTINGS_TAG_PRINTING,
       mojom::kPrintingDetailsSubpagePath,
       mojom::SearchResultIcon::kPrinter,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSubpage,
       {.subpage = mojom::Subpage::kPrintingDetails},
       {IDS_OS_SETTINGS_TAG_PRINTING_ALT1, IDS_OS_SETTINGS_TAG_PRINTING_ALT2,
        SearchConcept::kAltTagEnd}},
  });
  return *tags;
}

}  // namespace

class SearchTagRegistryTest : public testing::Test {
 protected:
  SearchTagRegistryTest() : search_tag_registry_(&local_search_service_) {}
  ~SearchTagRegistryTest() override = default;

  // testing::Test:
  void SetUp() override {
    search_tag_registry_.AddObserver(&observer_);
    index_ = local_search_service_.GetIndex(
        local_search_service::IndexId::kCrosSettings);
  }

  void TearDown() override { search_tag_registry_.RemoveObserver(&observer_); }

  local_search_service::LocalSearchService local_search_service_;
  SearchTagRegistry search_tag_registry_;
  FakeObserver observer_;
  local_search_service::Index* index_;
};

TEST_F(SearchTagRegistryTest, AddAndRemove) {
  // Add search tags; size of the index should increase.
  search_tag_registry_.AddSearchTags(GetPrintingSearchConcepts());
  EXPECT_EQ(3u, index_->GetSize());
  EXPECT_EQ(1u, observer_.num_calls());

  std::string first_tag_id =
      SearchTagRegistry::ToResultId(GetPrintingSearchConcepts()[0]);

  // Tags added should be available via GetTagMetadata().
  const SearchConcept* add_printer_concept =
      search_tag_registry_.GetTagMetadata(first_tag_id);
  ASSERT_TRUE(add_printer_concept);
  EXPECT_EQ(mojom::Setting::kAddPrinter, add_printer_concept->id.setting);

  // Remove search tag; size should go back to 0.
  search_tag_registry_.RemoveSearchTags(GetPrintingSearchConcepts());
  EXPECT_EQ(0u, index_->GetSize());
  EXPECT_EQ(2u, observer_.num_calls());

  // The tag should no longer be accessible via GetTagMetadata().
  add_printer_concept = search_tag_registry_.GetTagMetadata(first_tag_id);
  ASSERT_FALSE(add_printer_concept);
}

}  // namespace settings
}  // namespace chromeos
