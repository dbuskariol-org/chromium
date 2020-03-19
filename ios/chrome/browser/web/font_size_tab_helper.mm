// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/web/font_size_tab_helper.h"

#import <UIKit/UIKit.h>

#include "base/containers/adapters.h"
#include "base/strings/stringprintf.h"
#import "base/strings/sys_string_conversions.h"
#include "components/pref_registry/pref_registry_syncable.h"
#include "components/prefs/pref_service.h"
#include "components/prefs/scoped_user_pref_update.h"
#include "components/ukm/ios/ukm_url_recorder.h"
#include "ios/chrome/browser/application_context.h"
#include "ios/chrome/browser/browser_state/chrome_browser_state.h"
#include "ios/chrome/browser/pref_names.h"
#include "ios/chrome/browser/ui/util/dynamic_type_util.h"
#include "services/metrics/public/cpp/ukm_builders.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// Content size category to report UMA metrics.
// These values are persisted to logs. Entries should not be renumbered and
// numeric values should never be reused.
enum class IOSContentSizeCategory {
  kUnspecified = 0,
  kExtraSmall = 1,
  kSmall = 2,
  kMedium = 3,
  kLarge = 4,
  kExtraLarge = 5,
  kExtraExtraLarge = 6,
  kExtraExtraExtraLarge = 7,
  kAccessibilityMedium = 8,
  kAccessibilityLarge = 9,
  kAccessibilityExtraLarge = 10,
  kAccessibilityExtraExtraLarge = 11,
  kAccessibilityExtraExtraExtraLarge = 12,
  kMaxValue = kAccessibilityExtraExtraExtraLarge,
};

// Converts a UIKit content size category to a content size category for
// reporting.
IOSContentSizeCategory IOSContentSizeCategoryForCurrentUIContentSizeCategory() {
  UIContentSizeCategory size =
      UIApplication.sharedApplication.preferredContentSizeCategory;
  if ([size isEqual:UIContentSizeCategoryUnspecified]) {
    return IOSContentSizeCategory::kUnspecified;
  }
  if ([size isEqual:UIContentSizeCategoryExtraSmall]) {
    return IOSContentSizeCategory::kExtraSmall;
  }
  if ([size isEqual:UIContentSizeCategorySmall]) {
    return IOSContentSizeCategory::kSmall;
  }
  if ([size isEqual:UIContentSizeCategoryMedium]) {
    return IOSContentSizeCategory::kMedium;
  }
  if ([size isEqual:UIContentSizeCategoryLarge]) {
    return IOSContentSizeCategory::kLarge;
  }
  if ([size isEqual:UIContentSizeCategoryExtraLarge]) {
    return IOSContentSizeCategory::kExtraLarge;
  }
  if ([size isEqual:UIContentSizeCategoryExtraExtraLarge]) {
    return IOSContentSizeCategory::kExtraExtraLarge;
  }
  if ([size isEqual:UIContentSizeCategoryExtraExtraExtraLarge]) {
    return IOSContentSizeCategory::kExtraExtraExtraLarge;
  }
  if ([size isEqual:UIContentSizeCategoryAccessibilityMedium]) {
    return IOSContentSizeCategory::kAccessibilityMedium;
  }
  if ([size isEqual:UIContentSizeCategoryAccessibilityLarge]) {
    return IOSContentSizeCategory::kAccessibilityLarge;
  }
  if ([size isEqual:UIContentSizeCategoryAccessibilityExtraLarge]) {
    return IOSContentSizeCategory::kAccessibilityExtraLarge;
  }
  if ([size isEqual:UIContentSizeCategoryAccessibilityExtraExtraLarge]) {
    return IOSContentSizeCategory::kAccessibilityExtraExtraLarge;
  }
  if ([size isEqual:UIContentSizeCategoryAccessibilityExtraExtraExtraLarge]) {
    return IOSContentSizeCategory::kAccessibilityExtraExtraExtraLarge;
  }

  return IOSContentSizeCategory::kUnspecified;
}

}  // namespace

FontSizeTabHelper::~FontSizeTabHelper() {
  // Remove observer in destructor because |this| is captured by the usingBlock
  // in calling [NSNotificationCenter.defaultCenter
  // addObserverForName:object:queue:usingBlock] in constructor.
  [NSNotificationCenter.defaultCenter
      removeObserver:content_size_did_change_observer_];
}

FontSizeTabHelper::FontSizeTabHelper(web::WebState* web_state)
    : web_state_(web_state) {
  web_state->AddObserver(this);
  content_size_did_change_observer_ = [NSNotificationCenter.defaultCenter
      addObserverForName:UIContentSizeCategoryDidChangeNotification
                  object:nil
                   queue:nil
              usingBlock:^(NSNotification* _Nonnull note) {
                SetPageFontSize(GetFontSize());
              }];
}

// static
void FontSizeTabHelper::RegisterBrowserStatePrefs(
    user_prefs::PrefRegistrySyncable* registry) {
  registry->RegisterDictionaryPref(prefs::kIosUserZoomMultipliers);
}

void FontSizeTabHelper::ClearUserZoomPrefs(PrefService* pref_service) {
  pref_service->ClearPref(prefs::kIosUserZoomMultipliers);
}

void FontSizeTabHelper::SetPageFontSize(int size) {
  if (!CurrentPageSupportsTextZoom()) {
    return;
  }
  tab_helper_has_zoomed_ = true;
  if (web_state_->ContentIsHTML()) {
    NSString* js = [NSString
        stringWithFormat:@"__gCrWeb.accessibility.adjustFontSize(%d)", size];
    web_state_->ExecuteJavaScript(base::SysNSStringToUTF16(js));
  }
}

void FontSizeTabHelper::UserZoom(Zoom zoom) {
  DCHECK(CurrentPageSupportsTextZoom());
  double new_zoom_multiplier = NewMultiplierAfterZoom(zoom).value_or(1);
  StoreCurrentUserZoomMultiplier(new_zoom_multiplier);

  // Track when the user zooms to see if there are certain websites that are
  // broken when zooming.
  IOSContentSizeCategory content_size_category =
      IOSContentSizeCategoryForCurrentUIContentSizeCategory();
  ukm::UkmRecorder* ukm_recorder = GetApplicationContext()->GetUkmRecorder();
  ukm::SourceId source_id = ukm::GetSourceIdForWebStateDocument(web_state_);
  ukm::builders::IOS_PageZoomChanged(source_id)
      .SetContentSizeCategory(static_cast<int>(content_size_category))
      .SetUserZoomLevel(GetCurrentUserZoomMultiplier() * 100)
      .SetOverallZoomLevel(GetFontSize())
      .Record(ukm_recorder);

  SetPageFontSize(GetFontSize());
}

base::Optional<double> FontSizeTabHelper::NewMultiplierAfterZoom(
    Zoom zoom) const {
  static const std::vector<double> kZoomMultipliers = {
      0.5, 2.0 / 3.0, 0.75, 0.8, 0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0,
  };
  switch (zoom) {
    case ZOOM_RESET:
      return 1;
    case ZOOM_IN: {
      double current_multiplier = GetCurrentUserZoomMultiplier();
      // Find first multiplier greater than current.
      for (double multiplier : kZoomMultipliers) {
        if (multiplier > current_multiplier) {
          return multiplier;
        }
      }
      return base::nullopt;
    }
    case ZOOM_OUT: {
      double current_multiplier = GetCurrentUserZoomMultiplier();
      // Find first multiplier less than current.
      for (double multiplier : base::Reversed(kZoomMultipliers)) {
        if (multiplier < current_multiplier) {
          return multiplier;
        }
      }
      return base::nullopt;
    }
  }
}

bool FontSizeTabHelper::CanUserZoomIn() const {
  return NewMultiplierAfterZoom(ZOOM_IN).has_value();
}

bool FontSizeTabHelper::CanUserZoomOut() const {
  return NewMultiplierAfterZoom(ZOOM_OUT).has_value();
}

bool FontSizeTabHelper::CanUserResetZoom() const {
  base::Optional<double> new_multiplier = NewMultiplierAfterZoom(ZOOM_RESET);
  return new_multiplier.has_value() &&
         new_multiplier.value() != GetCurrentUserZoomMultiplier();
}

bool FontSizeTabHelper::IsTextZoomUIActive() const {
  return text_zoom_ui_active_;
}

void FontSizeTabHelper::SetTextZoomUIActive(bool active) {
  text_zoom_ui_active_ = active;
}

bool FontSizeTabHelper::CurrentPageSupportsTextZoom() const {
  return web_state_->ContentIsHTML();
}

int FontSizeTabHelper::GetFontSize() const {
  // Multiply by 100 as the web property needs a percentage.
  return SystemSuggestedFontSizeMultiplier() * GetCurrentUserZoomMultiplier() *
         100;
}

void FontSizeTabHelper::WebStateDestroyed(web::WebState* web_state) {
  web_state->RemoveObserver(this);
}

void FontSizeTabHelper::PageLoaded(
    web::WebState* web_state,
    web::PageLoadCompletionStatus load_completion_status) {
  DCHECK_EQ(web_state, web_state_);
  int size = GetFontSize();
  // Prevent any zooming errors by only zooming when necessary. This is mostly
  // when size != 100, but if zooming has happened before, then zooming to 100
  // may be necessary to reset a previous page to the correct zoom level.
  if (tab_helper_has_zoomed_ || size != 100) {
    SetPageFontSize(size);
  }
}

PrefService* FontSizeTabHelper::GetPrefService() const {
  ChromeBrowserState* chrome_browser_state =
      ChromeBrowserState::FromBrowserState(web_state_->GetBrowserState());
  return chrome_browser_state->GetPrefs();
}

std::string FontSizeTabHelper::GetCurrentUserZoomMultiplierKey() const {
  std::string content_size_category = base::SysNSStringToUTF8(
      UIApplication.sharedApplication.preferredContentSizeCategory);
  return base::StringPrintf("%s.%s", content_size_category.c_str(),
                            web_state_->GetLastCommittedURL().host().c_str());
}

double FontSizeTabHelper::GetCurrentUserZoomMultiplier() const {
  const base::Value* pref =
      GetPrefService()->Get(prefs::kIosUserZoomMultipliers);

  return pref->FindDoublePath(GetCurrentUserZoomMultiplierKey()).value_or(1);
}

void FontSizeTabHelper::StoreCurrentUserZoomMultiplier(double multiplier) {
  DictionaryPrefUpdate update(GetPrefService(), prefs::kIosUserZoomMultipliers);

  // Don't bother to store all the ones. This helps keep the pref dict clean.
  if (multiplier == 1) {
    update->RemovePath(GetCurrentUserZoomMultiplierKey());
  } else {
    update->SetDoublePath(GetCurrentUserZoomMultiplierKey(), multiplier);
  }
}

WEB_STATE_USER_DATA_KEY_IMPL(FontSizeTabHelper)
