// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/safe_browsing/core/realtime/policy_engine.h"

#include "base/feature_list.h"
#include "base/metrics/histogram_macros.h"
#include "build/build_config.h"
#include "components/prefs/pref_service.h"
#include "components/safe_browsing/core/common/safe_browsing_prefs.h"
#include "components/safe_browsing/core/common/safebrowsing_constants.h"
#include "components/safe_browsing/core/features.h"
#include "components/sync/base/user_selectable_type.h"
#include "components/sync/driver/sync_service.h"
#include "components/sync/driver/sync_service_utils.h"
#include "components/sync/driver/sync_user_settings.h"
#include "components/unified_consent/pref_names.h"
#include "components/user_prefs/user_prefs.h"

#if defined(OS_ANDROID)
#include "base/metrics/field_trial_params.h"
#include "base/system/sys_info.h"
#endif

namespace safe_browsing {

#if defined(OS_ANDROID)
const int kDefaultMemoryLowerThresholdMb = 4096;
// By default, the upper threshold shouldn't be in effect.
const int kDefaultMemoryUpperThresholdMb = INT_MAX;
#endif

// static
bool RealTimePolicyEngine::IsUrlLookupEnabled() {
  if (!base::FeatureList::IsEnabled(kRealTimeUrlLookupEnabled))
    return false;
#if defined(OS_ANDROID)
  // On Android, performs real time URL lookup only if
  // |kRealTimeUrlLookupEnabled| is enabled, and system memory is between the
  // upper threshold and lower threshold.
  int memory_lower_threshold_mb = base::GetFieldTrialParamByFeatureAsInt(
      kRealTimeUrlLookupEnabled, kRealTimeUrlLookupMemoryLowerThresholdMb,
      kDefaultMemoryLowerThresholdMb);
  int memory_upper_threshold_mb = base::GetFieldTrialParamByFeatureAsInt(
      kRealTimeUrlLookupEnabled, kRealTimeUrlLookupMemoryUpperThresholdMb,
      kDefaultMemoryUpperThresholdMb);
  return base::SysInfo::AmountOfPhysicalMemoryMB() >=
             memory_lower_threshold_mb &&
         base::SysInfo::AmountOfPhysicalMemoryMB() <= memory_upper_threshold_mb;
#else
  return true;
#endif
}

// static
bool RealTimePolicyEngine::IsUrlLookupEnabledForEp() {
  return base::FeatureList::IsEnabled(kRealTimeUrlLookupEnabledForEP);
}

// static
bool RealTimePolicyEngine::IsUserMbbOptedIn(PrefService* pref_service) {
  return pref_service->GetBoolean(
      unified_consent::prefs::kUrlKeyedAnonymizedDataCollectionEnabled);
}

// static
bool RealTimePolicyEngine::IsUserEpOptedIn(PrefService* pref_service) {
  return IsEnhancedProtectionEnabled(*pref_service);
}

// static
// TODO(crbug.com/1050859): Remove this method.
bool RealTimePolicyEngine::IsEnabledByPolicy() {
  return false;
}

// static
bool RealTimePolicyEngine::CanPerformFullURLLookup(PrefService* pref_service,
                                                   bool is_off_the_record) {
  if (is_off_the_record)
    return false;

  if (IsEnabledByPolicy())
    return true;

  if (IsUrlLookupEnabledForEp() && IsUserEpOptedIn(pref_service))
    return true;

  return IsUrlLookupEnabled() && IsUserMbbOptedIn(pref_service);
}

// static
bool RealTimePolicyEngine::CanPerformFullURLLookupWithToken(
    PrefService* pref_service,
    bool is_off_the_record,
    syncer::SyncService* sync_service) {
  if (!CanPerformFullURLLookup(pref_service, is_off_the_record)) {
    return false;
  }

  if (IsUrlLookupEnabledForEp() && IsUserEpOptedIn(pref_service))
    return true;

  if (!base::FeatureList::IsEnabled(kRealTimeUrlLookupEnabledWithToken)) {
    return false;
  }

  // |sync_service| can be null in Incognito, and also be set to null by a
  // cmdline param.
  if (!sync_service) {
    return false;
  }

  // Full URL lookup with token is enabled when the user is syncing their
  // browsing history without a custom passphrase.
  return syncer::GetUploadToGoogleState(
             sync_service, syncer::ModelType::HISTORY_DELETE_DIRECTIVES) ==
             syncer::UploadState::ACTIVE &&
         !sync_service->GetUserSettings()->IsUsingSecondaryPassphrase();
}

// static
bool RealTimePolicyEngine::CanPerformFullURLLookupForResourceType(
    ResourceType resource_type) {
  UMA_HISTOGRAM_ENUMERATION("SafeBrowsing.RT.ResourceTypes.Requested",
                            resource_type);
  return resource_type == ResourceType::kMainFrame;
}

}  // namespace safe_browsing
