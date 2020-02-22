// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/content_setting_bubble_contents.h"

#include "chrome/browser/ui/content_settings/content_setting_bubble_model.h"
#include "chrome/test/base/testing_profile.h"
#include "chrome/test/views/chrome_views_test_base.h"
#include "content/public/test/web_contents_tester.h"
#include "ui/views/bubble/bubble_dialog_delegate_view.h"

using ContentSettingBubbleContentsTest = ChromeViewsTestBase;

class TestContentSettingBubbleModel : public ContentSettingBubbleModel {
 public:
  TestContentSettingBubbleModel(Delegate* delegate,
                                content::WebContents* web_contents)
      : ContentSettingBubbleModel(delegate, web_contents) {
    AddListItem(
        ListItem(nullptr, base::string16(), base::string16(), false, false, 0));
  }
};

// Regression test for http://crbug.com/1050801 .
TEST_F(ContentSettingBubbleContentsTest, NullDeref) {
  TestingProfile profile;
  std::unique_ptr<content::WebContents> web_contents =
      content::WebContentsTester::CreateTestWebContents(&profile, nullptr);
  auto model = std::make_unique<TestContentSettingBubbleModel>(
      nullptr, web_contents.get());

  auto contents = std::make_unique<ContentSettingBubbleContents>(
      std::move(model), web_contents.get(), nullptr,
      views::BubbleBorder::TOP_LEFT);

  views::Widget::InitParams params =
      CreateParams(views::Widget::InitParams::TYPE_WINDOW);
  params.ownership = views::Widget::InitParams::WIDGET_OWNS_NATIVE_WIDGET;
  views::Widget widget;
  widget.Init(std::move(params));
  contents->set_parent_window(widget.GetNativeView());

  // Should not crash.
  views::BubbleDialogDelegateView::CreateBubble(contents.release())->CloseNow();
}
