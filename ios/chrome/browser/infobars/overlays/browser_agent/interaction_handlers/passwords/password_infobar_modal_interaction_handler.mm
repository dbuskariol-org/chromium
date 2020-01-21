// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/infobars/overlays/browser_agent/interaction_handlers/passwords/password_infobar_modal_interaction_handler.h"

#include "ios/chrome/browser/infobars/infobar_ios.h"
#import "ios/chrome/browser/infobars/overlays/browser_agent/interaction_handlers/passwords/password_infobar_modal_overlay_request_callback_installer.h"
#import "ios/chrome/browser/passwords/ios_chrome_save_password_infobar_delegate.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

PasswordInfobarModalInteractionHandler::
    PasswordInfobarModalInteractionHandler() = default;

PasswordInfobarModalInteractionHandler::
    ~PasswordInfobarModalInteractionHandler() = default;

#pragma mark - Public

void PasswordInfobarModalInteractionHandler::UpdateCredentials(
    InfoBarIOS* infobar,
    NSString* username,
    NSString* password) {
  GetDelegate(infobar)->UpdateCredentials(username, password);
}

void PasswordInfobarModalInteractionHandler::NeverSaveCredentials(
    InfoBarIOS* infobar) {
  GetDelegate(infobar)->Cancel();
}

void PasswordInfobarModalInteractionHandler::PresentPasswordsSettings(
    InfoBarIOS* infobar) {
  // TODO(crbug.com/1033154): Show the passwords settings.
}

void PasswordInfobarModalInteractionHandler::PerformMainAction(
    InfoBarIOS* infobar) {
  infobar->set_accepted(GetDelegate(infobar)->Accept());
}

void PasswordInfobarModalInteractionHandler::InfobarVisibilityChanged(
    InfoBarIOS* infobar,
    bool visible) {
  if (visible) {
    GetDelegate(infobar)->InfobarPresenting(/*automatic=*/NO);
  } else {
    GetDelegate(infobar)->InfobarDismissed();
  }
}

#pragma mark - Private

std::unique_ptr<InfobarModalOverlayRequestCallbackInstaller>
PasswordInfobarModalInteractionHandler::CreateModalInstaller() {
  return std::make_unique<PasswordInfobarModalOverlayRequestCallbackInstaller>(
      this);
}

IOSChromeSavePasswordInfoBarDelegate*
PasswordInfobarModalInteractionHandler::GetDelegate(InfoBarIOS* infobar) {
  IOSChromeSavePasswordInfoBarDelegate* delegate =
      IOSChromeSavePasswordInfoBarDelegate::FromInfobarDelegate(
          infobar->delegate());
  DCHECK(delegate);
  return delegate;
}
