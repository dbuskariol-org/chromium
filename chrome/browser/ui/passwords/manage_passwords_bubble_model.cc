// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/passwords/manage_passwords_bubble_model.h"

#include <stddef.h>

#include <algorithm>
#include <limits>
#include <memory>
#include <string>
#include <vector>

#include "base/metrics/field_trial_params.h"
#include "base/strings/utf_string_conversions.h"
#include "base/time/default_clock.h"
#include "chrome/app/vector_icons/vector_icons.h"
#include "chrome/browser/password_manager/password_store_factory.h"
#include "chrome/browser/password_manager/password_store_utils.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/sync/profile_sync_service_factory.h"
#include "chrome/browser/ui/passwords/manage_passwords_view_utils.h"
#include "chrome/browser/ui/passwords/passwords_model_delegate.h"
#include "chrome/grit/generated_resources.h"
#include "chrome/grit/theme_resources.h"
#include "components/password_manager/core/browser/password_bubble_experiment.h"
#include "components/password_manager/core/browser/password_feature_manager.h"
#include "components/password_manager/core/browser/password_form_metrics_recorder.h"
#include "components/password_manager/core/browser/password_manager_constants.h"
#include "components/password_manager/core/browser/password_store.h"
#include "components/password_manager/core/common/password_manager_features.h"
#include "components/password_manager/core/common/password_manager_pref_names.h"
#include "components/password_manager/core/common/password_manager_ui.h"
#include "components/prefs/pref_service.h"
#include "components/sync/driver/sync_service.h"
#include "content/public/browser/web_contents.h"
#include "ui/base/l10n/l10n_util.h"

namespace metrics_util = password_manager::metrics_util;

ManagePasswordsBubbleModel::ManagePasswordsBubbleModel(
    base::WeakPtr<PasswordsModelDelegate> delegate,
    DisplayReason display_reason)
    : delegate_(std::move(delegate)),
      interaction_reported_(false),
      are_passwords_revealed_when_bubble_is_opened_(false),
      metrics_recorder_(delegate_->GetPasswordFormMetricsRecorder()) {
  password_manager::metrics_util::UIDisplayDisposition display_disposition =
      metrics_util::AUTOMATIC_WITH_PASSWORD_PENDING;
  if (display_reason == USER_ACTION) {
    switch (state_) {
      case password_manager::ui::PENDING_PASSWORD_STATE:
      case password_manager::ui::PENDING_PASSWORD_UPDATE_STATE:
      case password_manager::ui::MANAGE_STATE:
      case password_manager::ui::CONFIRMATION_STATE:
      case password_manager::ui::CREDENTIAL_REQUEST_STATE:
      case password_manager::ui::AUTO_SIGNIN_STATE:
      case password_manager::ui::CHROME_SIGN_IN_PROMO_STATE:
      case password_manager::ui::INACTIVE_STATE:
        NOTREACHED();
        break;
    }
  } else {
    switch (state_) {
      case password_manager::ui::PENDING_PASSWORD_STATE:
      case password_manager::ui::PENDING_PASSWORD_UPDATE_STATE:
      case password_manager::ui::CONFIRMATION_STATE:
      case password_manager::ui::AUTO_SIGNIN_STATE:
      case password_manager::ui::MANAGE_STATE:
      case password_manager::ui::CREDENTIAL_REQUEST_STATE:
      case password_manager::ui::CHROME_SIGN_IN_PROMO_STATE:
      case password_manager::ui::INACTIVE_STATE:
        NOTREACHED();
        break;
    }
  }

  if (metrics_recorder_) {
    metrics_recorder_->RecordPasswordBubbleShown(
        delegate_->GetCredentialSource(), display_disposition);
  }
  metrics_util::LogUIDisplayDisposition(display_disposition);

  delegate_->OnBubbleShown();
}

ManagePasswordsBubbleModel::~ManagePasswordsBubbleModel() {
  if (!interaction_reported_)
    OnBubbleClosing();
}

void ManagePasswordsBubbleModel::OnBubbleClosing() {
  if (delegate_)
    delegate_->OnBubbleHidden();
  delegate_.reset();
  interaction_reported_ = true;
}

Profile* ManagePasswordsBubbleModel::GetProfile() const {
  content::WebContents* web_contents = GetWebContents();
  if (!web_contents)
    return nullptr;
  return Profile::FromBrowserContext(web_contents->GetBrowserContext());
}

content::WebContents* ManagePasswordsBubbleModel::GetWebContents() const {
  return delegate_ ? delegate_->GetWebContents() : nullptr;
}

void ManagePasswordsBubbleModel::SetClockForTesting(base::Clock* clock) {}
