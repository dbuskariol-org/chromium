// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/passwords/test/mock_ios_chrome_save_passwords_infobar_delegate.h"

#include "base/memory/ptr_util.h"
#include "base/strings/sys_string_conversions.h"
#include "components/password_manager/core/browser/mock_password_form_manager_for_ui.h"
#include "components/password_manager/core/browser/password_manager_metrics_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {
std::unique_ptr<password_manager::PasswordFormManagerForUI> CreateFormManager(
    autofill::PasswordForm* form) {
  DCHECK(form);
  std::unique_ptr<password_manager::MockPasswordFormManagerForUI> form_manager =
      std::make_unique<password_manager::MockPasswordFormManagerForUI>();
  EXPECT_CALL(*form_manager, GetPendingCredentials())
      .WillRepeatedly(testing::ReturnRef(*form));
  EXPECT_CALL(*form_manager, GetMetricsRecorder())
      .WillRepeatedly(testing::Return(nullptr));
  EXPECT_CALL(*form_manager, GetCredentialSource())
      .WillRepeatedly(testing::Return(
          password_manager::metrics_util::CredentialSourceType::kUnknown));
  return form_manager;
}
}  // namespace

// static
std::unique_ptr<MockIOSChromeSavePasswordInfoBarDelegate>
MockIOSChromeSavePasswordInfoBarDelegate::Create(NSString* username,
                                                 NSString* password) {
  std::unique_ptr<autofill::PasswordForm> form =
      std::make_unique<autofill::PasswordForm>();
  form->username_value = base::SysNSStringToUTF16(username);
  form->password_value = base::SysNSStringToUTF16(password);
  return base::WrapUnique(
      new MockIOSChromeSavePasswordInfoBarDelegate(std::move(form)));
}

MockIOSChromeSavePasswordInfoBarDelegate::
    MockIOSChromeSavePasswordInfoBarDelegate(
        std::unique_ptr<autofill::PasswordForm> form)
    : IOSChromeSavePasswordInfoBarDelegate(
          /*is_sync_user=*/false,
          /*password_update=*/false,
          CreateFormManager(form.get())),
      form_(std::move(form)) {}

MockIOSChromeSavePasswordInfoBarDelegate::
    ~MockIOSChromeSavePasswordInfoBarDelegate() = default;
