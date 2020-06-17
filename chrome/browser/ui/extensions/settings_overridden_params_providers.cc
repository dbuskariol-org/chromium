// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/extensions/settings_overridden_params_providers.h"

#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "chrome/browser/extensions/extension_web_ui.h"
#include "chrome/browser/extensions/ntp_overridden_bubble_delegate.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/common/webui_url_constants.h"
#include "chrome/grit/generated_resources.h"
#include "ui/base/l10n/l10n_util.h"

namespace settings_overridden_params {

base::Optional<ExtensionSettingsOverriddenDialog::Params>
GetNtpOverriddenParams(Profile* profile) {
  const extensions::Extension* extension =
      ExtensionWebUI::GetExtensionControllingURL(
          GURL(chrome::kChromeUINewTabURL), profile);
  if (!extension)
    return base::nullopt;

  // We deliberately re-use the same preference that the bubble UI uses. This
  // way, users won't see the bubble or dialog UI if they've already
  // acknowledged either version.
  const char* preference_name =
      extensions::NtpOverriddenBubbleDelegate::kNtpBubbleAcknowledged;

  constexpr char kHistogramName[] =
      "Extensions.SettingsOverridden.GenericNtpOverriddenDialogResult";

  // TODO(devlin): Adjust these messages based on the previous NTP.
  // TODO(devlin): Because of https://crbug.com/1080732, using the real strings
  // here results in bot failures (they are greedily optimized out). Use fake
  // strings for now, and switch these over when this is reached in a production
  // codepath.
  //
  // This should be IDS_EXTENSION_NTP_OVERRIDDEN_DIALOG_TITLE_GENERIC.
  base::string16 dialog_title =
      base::ASCIIToUTF16("Did you mean to change this page?");
  // This should be IDS_EXTENSION_NTP_OVERRIDDEN_DIALOG_BODY_GENERIC.
  base::string16 dialog_message = base::UTF8ToUTF16(base::StringPrintf(
      "This page was changed by the %s extension", extension->name().c_str()));

  return ExtensionSettingsOverriddenDialog::Params(
      extension->id(), preference_name, kHistogramName, std::move(dialog_title),
      std::move(dialog_message));
}

}  // namespace settings_overridden_params
