// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/reader_mode/reader_mode_icon_view.h"

#include "base/test/scoped_feature_list.h"
#include "chrome/browser/ui/page_action/page_action_icon_type.h"
#include "chrome/browser/ui/views/frame/browser_view.h"
#include "chrome/browser/ui/views/frame/toolbar_button_provider.h"
#include "chrome/test/base/in_process_browser_test.h"
#include "chrome/test/base/ui_test_utils.h"
#include "components/dom_distiller/core/dom_distiller_features.h"
#include "content/public/test/browser_test.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "ui/views/test/button_test_api.h"

namespace {

const char* kSimpleArticlePath = "/dom_distiller/simple_article.html";
const char* kNonArticlePath = "/dom_distiller/non_og_article.html";

class ReaderModeIconViewBrowserTest : public InProcessBrowserTest {
 protected:
  ReaderModeIconViewBrowserTest() : InProcessBrowserTest() {
    feature_list_.InitAndEnableFeature(dom_distiller::kReaderMode);
  }

  void SetUpOnMainThread() override {
    ASSERT_TRUE(embedded_test_server()->Start());
    reader_mode_icon_ =
        BrowserView::GetBrowserViewForBrowser(browser())
            ->toolbar_button_provider()
            ->GetPageActionIconView(PageActionIconType::kReaderMode);
    ASSERT_NE(nullptr, reader_mode_icon_);
  }

  PageActionIconView* reader_mode_icon_;

 private:
  base::test::ScopedFeatureList feature_list_;
};

// TODO(gilmanmh): Add tests for the following cases:
//  * Icon is visible on the distilled page.
//  * Icon is not visible on about://blank, both initially and after navigating
//    to a distillable page.
IN_PROC_BROWSER_TEST_F(ReaderModeIconViewBrowserTest,
                       IconVisibilityAdaptsToPageContents) {
  // The icon should not be visible by default, before navigation to any page
  // has occurred.
  const bool is_visible_before_navigation = reader_mode_icon_->GetVisible();
  EXPECT_FALSE(is_visible_before_navigation);

  // The icon should be hidden on pages that aren't distillable
  ui_test_utils::NavigateToURL(browser(),
                               embedded_test_server()->GetURL(kNonArticlePath));
  const bool is_visible_on_non_distillable_page =
      reader_mode_icon_->GetVisible();
  EXPECT_FALSE(is_visible_on_non_distillable_page);

  // The icon should appear after navigating to a distillable article.
  ui_test_utils::NavigateToURL(
      browser(), embedded_test_server()->GetURL(kSimpleArticlePath));
  const bool is_visible_on_article = reader_mode_icon_->GetVisible();
  EXPECT_TRUE(is_visible_on_article);

  // Navigating back to a non-distillable page hides the icon again.
  ui_test_utils::NavigateToURL(browser(),
                               embedded_test_server()->GetURL(kNonArticlePath));
  const bool is_visible_after_navigation_back_to_non_distillable_page =
      reader_mode_icon_->GetVisible();
  EXPECT_FALSE(is_visible_after_navigation_back_to_non_distillable_page);
}

}  // namespace
