// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/passwords/test/mock_ios_chrome_save_passwords_infobar_delegate.h"

#include "components/password_manager/core/browser/mock_password_form_manager_for_ui.h"
#include "components/password_manager/core/browser/password_manager_metrics_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

// static
std::unique_ptr<MockIOSChromeSavePasswordInfoBarDelegate>
MockIOSChromeSavePasswordInfoBarDelegate::Create() {
  std::unique_ptr<password_manager::MockPasswordFormManagerForUI> form =
      std::make_unique<password_manager::MockPasswordFormManagerForUI>();
  EXPECT_CALL(*form, GetMetricsRecorder())
      .WillRepeatedly(testing::Return(nullptr));
  EXPECT_CALL(*form, GetCredentialSource())
      .WillRepeatedly(testing::Return(
          password_manager::metrics_util::CredentialSourceType::kUnknown));
  return std::make_unique<MockIOSChromeSavePasswordInfoBarDelegate>(
      /*is_sync_user=*/false,
      /*password_update=*/false, std::move(form));
}

MockIOSChromeSavePasswordInfoBarDelegate::
    MockIOSChromeSavePasswordInfoBarDelegate(
        bool is_sync_user,
        bool password_update,
        std::unique_ptr<password_manager::PasswordFormManagerForUI>
            form_to_save)
    : IOSChromeSavePasswordInfoBarDelegate(is_sync_user,
                                           password_update,
                                           std::move(form_to_save)) {}

MockIOSChromeSavePasswordInfoBarDelegate::
    ~MockIOSChromeSavePasswordInfoBarDelegate() = default;
