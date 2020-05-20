// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/signin/signin_reauth_ui.h"

#include <string>

#include "base/logging.h"
#include "base/optional.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_avatar_icon_util.h"
#include "chrome/browser/signin/identity_manager_factory.h"
#include "chrome/browser/ui/webui/signin/signin_reauth_handler.h"
#include "chrome/common/webui_url_constants.h"
#include "chrome/grit/browser_resources.h"
#include "chrome/grit/generated_resources.h"
#include "components/signin/public/identity_manager/account_info.h"
#include "components/signin/public/identity_manager/consent_level.h"
#include "components/signin/public/identity_manager/identity_manager.h"
#include "content/public/browser/web_ui_data_source.h"
#include "google_apis/gaia/core_account_id.h"
#include "ui/base/webui/web_ui_util.h"
#include "ui/gfx/image/image.h"

namespace {

std::string GetAccountImageURL(Profile* profile) {
  auto* identity_manager = IdentityManagerFactory::GetForProfile(profile);
  // The current version of the reauth only supports the primary account.
  // TODO(crbug.com/1083429): generalize for arbitrary accounts by passing an
  // account id as a method parameter.
  CoreAccountId account_id =
      identity_manager->GetPrimaryAccountId(signin::ConsentLevel::kNotRequired);
  // Sync shouldn't be enabled. Otherwise, the primary account and the first
  // cookie account may diverge.
  DCHECK(!identity_manager->HasPrimaryAccount(signin::ConsentLevel::kSync));
  base::Optional<AccountInfo> account_info =
      identity_manager
          ->FindExtendedAccountInfoForAccountWithRefreshTokenByAccountId(
              account_id);

  return account_info && !account_info->account_image.IsEmpty()
             ? webui::GetBitmapDataUrl(account_info->account_image.AsBitmap())
             : profiles::GetPlaceholderAvatarIconUrl();
}

}  // namespace

SigninReauthUI::SigninReauthUI(content::WebUI* web_ui)
    : SigninWebDialogUI(web_ui) {
  Profile* profile = Profile::FromWebUI(web_ui);

  content::WebUIDataSource* source =
      content::WebUIDataSource::Create(chrome::kChromeUISigninReauthHost);
  source->UseStringsJs();
  source->EnableReplaceI18nInJS();
  source->AddString("accountImageUrl", GetAccountImageURL(profile));
  content::WebUIDataSource::Add(profile, source);
}

SigninReauthUI::~SigninReauthUI() = default;

void SigninReauthUI::InitializeMessageHandlerWithBrowser(Browser* browser) {
  web_ui()->AddMessageHandler(std::make_unique<SigninReauthHandler>(browser));
}
