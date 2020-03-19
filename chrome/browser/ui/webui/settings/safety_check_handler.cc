// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/settings/safety_check_handler.h"

#include "base/bind.h"
#include "base/metrics/user_metrics.h"
#include "base/metrics/user_metrics_action.h"
#include "base/strings/string16.h"
#include "base/strings/utf_string_conversions.h"
#include "build/build_config.h"
#include "chrome/browser/extensions/api/passwords_private/passwords_private_delegate_factory.h"
#include "chrome/browser/password_manager/bulk_leak_check_service_factory.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/common/url_constants.h"
#include "chrome/grit/chromium_strings.h"
#include "chrome/grit/generated_resources.h"
#include "components/prefs/pref_service.h"
#include "components/safe_browsing/core/common/safe_browsing_prefs.h"
#include "extensions/browser/extension_prefs_factory.h"
#include "extensions/browser/extension_system.h"
#include "extensions/common/extension_id.h"
#include "ui/base/l10n/l10n_util.h"

#if defined(OS_CHROMEOS)
#include "ui/chromeos/devicetype_utils.h"
#endif

namespace {

// Constants for communication with JS.
constexpr char kUpdatesEvent[] = "safety-check-updates-status-changed";
constexpr char kPasswordsEvent[] = "safety-check-passwords-status-changed";
constexpr char kSafeBrowsingEvent[] =
    "safety-check-safe-browsing-status-changed";
constexpr char kExtensionsEvent[] = "safety-check-extensions-status-changed";
constexpr char kPerformSafetyCheck[] = "performSafetyCheck";
constexpr char kGetParentRanDisplayString[] = "getSafetyCheckRanDisplayString";
constexpr char kNewState[] = "newState";
constexpr char kDisplayString[] = "displayString";
constexpr char kPasswordsCompromised[] = "passwordsCompromised";
constexpr char kExtensionsReenabledByUser[] = "extensionsReenabledByUser";
constexpr char kExtensionsReenabledByAdmin[] = "extensionsReenabledByAdmin";

// Converts the VersionUpdater::Status to the UpdateStatus enum to be passed
// to the safety check frontend. Note: if the VersionUpdater::Status gets
// changed, this will fail to compile. That is done intentionally to ensure
// that the states of the safety check are always in sync with the
// VersionUpdater ones.
SafetyCheckHandler::UpdateStatus ConvertToUpdateStatus(
    VersionUpdater::Status status) {
  switch (status) {
    case VersionUpdater::CHECKING:
      return SafetyCheckHandler::UpdateStatus::kChecking;
    case VersionUpdater::UPDATED:
      return SafetyCheckHandler::UpdateStatus::kUpdated;
    case VersionUpdater::UPDATING:
      return SafetyCheckHandler::UpdateStatus::kUpdating;
    case VersionUpdater::NEED_PERMISSION_TO_UPDATE:
    case VersionUpdater::NEARLY_UPDATED:
      return SafetyCheckHandler::UpdateStatus::kRelaunch;
    case VersionUpdater::DISABLED:
    case VersionUpdater::DISABLED_BY_ADMIN:
      return SafetyCheckHandler::UpdateStatus::kDisabledByAdmin;
    case VersionUpdater::FAILED:
    case VersionUpdater::FAILED_CONNECTION_TYPE_DISALLOWED:
      return SafetyCheckHandler::UpdateStatus::kFailed;
    case VersionUpdater::FAILED_OFFLINE:
      return SafetyCheckHandler::UpdateStatus::kFailedOffline;
  }
}
}  // namespace

SafetyCheckHandler::SafetyCheckHandler() = default;

SafetyCheckHandler::~SafetyCheckHandler() = default;

void SafetyCheckHandler::PerformSafetyCheck() {
  AllowJavascript();
  base::RecordAction(base::UserMetricsAction("SafetyCheck.Started"));

  if (!version_updater_) {
    version_updater_.reset(VersionUpdater::Create(web_ui()->GetWebContents()));
  }
  DCHECK(version_updater_);
  CheckUpdates();

  CheckSafeBrowsing();

  if (!leak_service_) {
    leak_service_ = BulkLeakCheckServiceFactory::GetForProfile(
        Profile::FromWebUI(web_ui()));
  }
  DCHECK(leak_service_);
  if (!passwords_delegate_) {
    passwords_delegate_ =
        extensions::PasswordsPrivateDelegateFactory::GetForBrowserContext(
            Profile::FromWebUI(web_ui()), true);
  }
  DCHECK(passwords_delegate_);
  CheckPasswords();

  if (!extension_prefs_) {
    extension_prefs_ = extensions::ExtensionPrefsFactory::GetForBrowserContext(
        Profile::FromWebUI(web_ui()));
  }
  DCHECK(extension_prefs_);
  if (!extension_service_) {
    extension_service_ =
        extensions::ExtensionSystem::Get(Profile::FromWebUI(web_ui()))
            ->extension_service();
  }
  DCHECK(extension_service_);
  CheckExtensions();
}

SafetyCheckHandler::SafetyCheckHandler(
    std::unique_ptr<VersionUpdater> version_updater,
    password_manager::BulkLeakCheckService* leak_service,
    extensions::PasswordsPrivateDelegate* passwords_delegate,
    extensions::ExtensionPrefs* extension_prefs,
    extensions::ExtensionServiceInterface* extension_service)
    : version_updater_(std::move(version_updater)),
      leak_service_(leak_service),
      passwords_delegate_(passwords_delegate),
      extension_prefs_(extension_prefs),
      extension_service_(extension_service) {}

void SafetyCheckHandler::HandlePerformSafetyCheck(const base::ListValue* args) {
  PerformSafetyCheck();
}

void SafetyCheckHandler::HandleGetParentRanDisplayString(
    const base::ListValue* args) {
  const base::Value* callback_id;
  double timestampRanDouble;
  CHECK(args->Get(0, &callback_id));
  CHECK(args->GetDouble(1, &timestampRanDouble));

  ResolveJavascriptCallback(
      *callback_id, base::Value(GetStringForParentRan(timestampRanDouble)));
}

void SafetyCheckHandler::CheckUpdates() {
  version_updater_->CheckForUpdate(
      base::Bind(&SafetyCheckHandler::OnUpdateCheckResult,
                 base::Unretained(this)),
      VersionUpdater::PromoteCallback());
}

void SafetyCheckHandler::CheckSafeBrowsing() {
  PrefService* pref_service = Profile::FromWebUI(web_ui())->GetPrefs();
  const PrefService::Preference* pref =
      pref_service->FindPreference(prefs::kSafeBrowsingEnabled);
  SafeBrowsingStatus status;
  if (pref_service->GetBoolean(prefs::kSafeBrowsingEnabled)) {
    status = SafeBrowsingStatus::kEnabled;
  } else if (pref->IsManaged()) {
    status = SafeBrowsingStatus::kDisabledByAdmin;
  } else if (pref->IsExtensionControlled()) {
    status = SafeBrowsingStatus::kDisabledByExtension;
  } else {
    status = SafeBrowsingStatus::kDisabled;
  }
  OnSafeBrowsingCheckResult(status);
}

void SafetyCheckHandler::CheckPasswords() {
  // Remove |this| as an existing observer for BulkLeakCheck if it is
  // registered. This takes care of an edge case when safety check starts twice
  // on the same page. Normally this should not happen, but if it does, the
  // browser should not crash.
  observed_leak_check_.RemoveAll();
  observed_leak_check_.Add(leak_service_);
  passwords_delegate_->StartPasswordCheck();
  // In the case of no passwords, there is no state transition and no callback.
  // Because of that, it is necessary to check the state synchronously.
  if (leak_service_->state() !=
      password_manager::BulkLeakCheckService::State::kRunning) {
    OnStateChanged(leak_service_->state());
  }
}

void SafetyCheckHandler::CheckExtensions() {
  extensions::ExtensionIdList extensions;
  extension_prefs_->GetExtensions(&extensions);
  int blocklisted = 0;
  int reenabled_by_user = 0;
  int reenabled_by_admin = 0;
  for (auto extension_id : extensions) {
    extensions::BlacklistState state =
        extension_prefs_->GetExtensionBlacklistState(extension_id);
    if (state == extensions::BLACKLISTED_UNKNOWN) {
      // If any of the extensions are in the unknown blacklist state, that means
      // there was an error the last time the blacklist was fetched. That means
      // the results cannot be relied upon.
      OnExtensionsCheckResult(ExtensionsStatus::kError, Blocklisted(0),
                              ReenabledUser(0), ReenabledAdmin(0));
      return;
    }
    if (state == extensions::NOT_BLACKLISTED) {
      continue;
    }
    ++blocklisted;
    if (!extension_service_->IsExtensionEnabled(extension_id)) {
      continue;
    }
    if (extension_service_->UserCanDisableInstalledExtension(extension_id)) {
      ++reenabled_by_user;
    } else {
      ++reenabled_by_admin;
    }
  }
  if (blocklisted == 0) {
    OnExtensionsCheckResult(ExtensionsStatus::kNoneBlocklisted, Blocklisted(0),
                            ReenabledUser(0), ReenabledAdmin(0));
  } else if (reenabled_by_user == 0 && reenabled_by_admin == 0) {
    OnExtensionsCheckResult(ExtensionsStatus::kBlocklistedAllDisabled,
                            Blocklisted(blocklisted), ReenabledUser(0),
                            ReenabledAdmin(0));
  } else if (reenabled_by_user > 0 && reenabled_by_admin == 0) {
    OnExtensionsCheckResult(ExtensionsStatus::kBlocklistedReenabledAllByUser,
                            Blocklisted(blocklisted),
                            ReenabledUser(reenabled_by_user),
                            ReenabledAdmin(0));
  } else if (reenabled_by_admin > 0 && reenabled_by_user == 0) {
    OnExtensionsCheckResult(ExtensionsStatus::kBlocklistedReenabledAllByAdmin,
                            Blocklisted(blocklisted), ReenabledUser(0),
                            ReenabledAdmin(reenabled_by_admin));
  } else {
    OnExtensionsCheckResult(ExtensionsStatus::kBlocklistedReenabledSomeByUser,
                            Blocklisted(blocklisted),
                            ReenabledUser(reenabled_by_user),
                            ReenabledAdmin(reenabled_by_admin));
  }
}

void SafetyCheckHandler::OnUpdateCheckResult(VersionUpdater::Status status,
                                             int progress,
                                             bool rollback,
                                             const std::string& version,
                                             int64_t update_size,
                                             const base::string16& message) {
  UpdateStatus update_status = ConvertToUpdateStatus(status);
  base::DictionaryValue event;
  event.SetIntKey(kNewState, static_cast<int>(update_status));
  event.SetStringKey(kDisplayString, GetStringForUpdates(update_status));
  FireWebUIListener(kUpdatesEvent, event);
}

void SafetyCheckHandler::OnSafeBrowsingCheckResult(
    SafetyCheckHandler::SafeBrowsingStatus status) {
  base::DictionaryValue event;
  event.SetIntKey(kNewState, static_cast<int>(status));
  event.SetStringKey(kDisplayString, GetStringForSafeBrowsing(status));
  FireWebUIListener(kSafeBrowsingEvent, event);
}

void SafetyCheckHandler::OnPasswordsCheckResult(PasswordsStatus status,
                                                int num_compromised) {
  base::DictionaryValue event;
  event.SetIntKey(kNewState, static_cast<int>(status));
  if (status == PasswordsStatus::kCompromisedExist) {
    event.SetIntKey(kPasswordsCompromised, num_compromised);
  }
  event.SetStringKey(kDisplayString,
                     GetStringForPasswords(status, num_compromised));
  FireWebUIListener(kPasswordsEvent, event);
}

void SafetyCheckHandler::OnExtensionsCheckResult(
    ExtensionsStatus status,
    Blocklisted blocklisted,
    ReenabledUser reenabled_user,
    ReenabledAdmin reenabled_admin) {
  base::DictionaryValue event;
  event.SetIntKey(kNewState, static_cast<int>(status));
  if (status == ExtensionsStatus::kBlocklistedReenabledAllByUser ||
      status == ExtensionsStatus::kBlocklistedReenabledSomeByUser) {
    event.SetIntKey(kExtensionsReenabledByUser, reenabled_user.value());
  }
  if (status == ExtensionsStatus::kBlocklistedReenabledAllByAdmin ||
      status == ExtensionsStatus::kBlocklistedReenabledSomeByUser) {
    event.SetIntKey(kExtensionsReenabledByAdmin, reenabled_admin.value());
  }
  event.SetStringKey(kDisplayString,
                     GetStringForExtensions(status, Blocklisted(blocklisted),
                                            reenabled_user, reenabled_admin));
  FireWebUIListener(kExtensionsEvent, event);
}

base::string16 SafetyCheckHandler::GetStringForUpdates(UpdateStatus status) {
  switch (status) {
    case UpdateStatus::kChecking:
      return l10n_util::GetStringUTF16(IDS_SETTINGS_SAFETY_CHECK_RUNNING);
    case UpdateStatus::kUpdated:
#if defined(OS_CHROMEOS)
      return ui::SubstituteChromeOSDeviceType(IDS_SETTINGS_UPGRADE_UP_TO_DATE);
#else
      return l10n_util::GetStringUTF16(IDS_SETTINGS_UPGRADE_UP_TO_DATE);
#endif
    case UpdateStatus::kUpdating:
      return l10n_util::GetStringUTF16(IDS_SETTINGS_UPGRADE_UPDATING);
    case UpdateStatus::kRelaunch:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_UPGRADE_SUCCESSFUL_RELAUNCH);
    case UpdateStatus::kDisabledByAdmin:
      return l10n_util::GetStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_UPDATES_DISABLED_BY_ADMIN,
          base::ASCIIToUTF16(chrome::kWhoIsMyAdministratorHelpURL));
    case UpdateStatus::kFailedOffline:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_UPDATES_FAILED_OFFLINE);
    case UpdateStatus::kFailed:
      return l10n_util::GetStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_UPDATES_FAILED,
          base::ASCIIToUTF16(chrome::kChromeFixUpdateProblems));
  }
}

base::string16 SafetyCheckHandler::GetStringForSafeBrowsing(
    SafeBrowsingStatus status) {
  switch (status) {
    case SafeBrowsingStatus::kChecking:
      return l10n_util::GetStringUTF16(IDS_SETTINGS_SAFETY_CHECK_RUNNING);
    case SafeBrowsingStatus::kEnabled:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_SAFE_BROWSING_ENABLED);
    case SafeBrowsingStatus::kDisabled:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_SAFE_BROWSING_DISABLED);
    case SafeBrowsingStatus::kDisabledByAdmin:
      return l10n_util::GetStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_SAFE_BROWSING_DISABLED_BY_ADMIN,
          base::ASCIIToUTF16(chrome::kWhoIsMyAdministratorHelpURL));
    case SafeBrowsingStatus::kDisabledByExtension:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_SAFE_BROWSING_DISABLED_BY_EXTENSION);
  }
}

base::string16 SafetyCheckHandler::GetStringForPasswords(PasswordsStatus status,
                                                         int num_compromised) {
  switch (status) {
    case PasswordsStatus::kChecking:
      return l10n_util::GetStringUTF16(IDS_SETTINGS_SAFETY_CHECK_RUNNING);
    case PasswordsStatus::kSafe:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_SAFE);
    case PasswordsStatus::kCompromisedExist:
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_COMPROMISED, num_compromised);
    case PasswordsStatus::kOffline:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_OFFLINE);
    case PasswordsStatus::kNoPasswords:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_NO_PASSWORDS);
    case PasswordsStatus::kSignedOut:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_SIGNED_OUT);
    case PasswordsStatus::kQuotaLimit:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_QUOTA_LIMIT);
    case PasswordsStatus::kTooManyPasswords:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_TOO_MANY_PASSWORDS);
    case PasswordsStatus::kError:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PASSWORDS_ERROR);
  }
}

base::string16 SafetyCheckHandler::GetStringForExtensions(
    ExtensionsStatus status,
    Blocklisted blocklisted,
    ReenabledUser reenabled_user,
    ReenabledAdmin reenabled_admin) {
  switch (status) {
    case ExtensionsStatus::kChecking:
      return l10n_util::GetStringUTF16(IDS_SETTINGS_SAFETY_CHECK_RUNNING);
    case ExtensionsStatus::kError:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_ERROR);
    case ExtensionsStatus::kNoneBlocklisted:
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_SAFE);
    case ExtensionsStatus::kBlocklistedAllDisabled:
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_BLOCKLISTED_OFF,
          blocklisted.value());
    case ExtensionsStatus::kBlocklistedReenabledAllByUser:
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_BLOCKLISTED_ON_USER,
          reenabled_user.value());
    case ExtensionsStatus::kBlocklistedReenabledSomeByUser:
      // TODO(crbug/1060625): Make string concatenation with a period
      // internationalized (see go/i18n-concatenation).
      return l10n_util::GetPluralStringFUTF16(
                 IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_BLOCKLISTED_ON_USER,
                 reenabled_user.value()) +
             base::ASCIIToUTF16(". ") +
             l10n_util::GetPluralStringFUTF16(
                 IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_BLOCKLISTED_ON_ADMIN,
                 reenabled_admin.value()) +
             base::ASCIIToUTF16(".");
    case ExtensionsStatus::kBlocklistedReenabledAllByAdmin:
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_EXTENSIONS_BLOCKLISTED_ON_ADMIN,
          reenabled_admin.value());
  }
}

base::string16 SafetyCheckHandler::GetStringForParentRan(double timestamp_ran) {
  return SafetyCheckHandler::GetStringForParentRan(timestamp_ran,
                                                   base::Time::Now());
}

base::string16 SafetyCheckHandler::GetStringForParentRan(
    double timestamp_ran,
    base::Time system_time) {
  const base::Time timeRan = base::Time::FromJsTime(timestamp_ran);
  base::Time::Exploded timeRanExploded;
  timeRan.LocalExplode(&timeRanExploded);

  base::Time::Exploded systemTimeExploded;
  system_time.LocalExplode(&systemTimeExploded);

  const base::Time timeYesterday = system_time - base::TimeDelta::FromDays(1);
  base::Time::Exploded timeYesterdayExploded;
  timeYesterday.LocalExplode(&timeYesterdayExploded);

  const auto timeDiff = system_time - timeRan;
  if (timeRanExploded.year == systemTimeExploded.year &&
      timeRanExploded.month == systemTimeExploded.month &&
      timeRanExploded.day_of_month == systemTimeExploded.day_of_month) {
    // Safety check ran today.
    const int timeDiffInMinutes = timeDiff.InMinutes();
    if (timeDiffInMinutes == 0) {
      return l10n_util::GetStringUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PARENT_PRIMARY_LABEL_AFTER);
    } else if (timeDiffInMinutes < 60) {
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PARENT_PRIMARY_LABEL_AFTER_MINS,
          timeDiffInMinutes);
    } else {
      return l10n_util::GetPluralStringFUTF16(
          IDS_SETTINGS_SAFETY_CHECK_PARENT_PRIMARY_LABEL_AFTER_HOURS,
          timeDiffInMinutes / 60);
    }
  } else if (timeRanExploded.year == timeYesterdayExploded.year &&
             timeRanExploded.month == timeYesterdayExploded.month &&
             timeRanExploded.day_of_month ==
                 timeYesterdayExploded.day_of_month) {
    // Safety check ran yesterday.
    return l10n_util::GetStringUTF16(
        IDS_SETTINGS_SAFETY_CHECK_PARENT_PRIMARY_LABEL_AFTER_YESTERDAY);
  } else {
    // Safety check ran longer ago than yesterday.
    // TODO(crbug.com/1015841): While a minor issue, this is not be the ideal
    // way to calculate the days passed since safety check ran. For example,
    // <48 h might still be 2 days ago.
    const int timeDiffInDays = timeDiff.InDays();
    return l10n_util::GetPluralStringFUTF16(
        IDS_SETTINGS_SAFETY_CHECK_PARENT_PRIMARY_LABEL_AFTER_DAYS,
        timeDiffInDays);
  }
}

void SafetyCheckHandler::OnStateChanged(
    password_manager::BulkLeakCheckService::State state) {
  using password_manager::BulkLeakCheckService;
  switch (state) {
    case BulkLeakCheckService::State::kIdle:
    case BulkLeakCheckService::State::kCanceled: {
      size_t num_compromised =
          passwords_delegate_->GetCompromisedCredentials().size();
      if (num_compromised == 0) {
        OnPasswordsCheckResult(PasswordsStatus::kSafe, 0);
      } else {
        OnPasswordsCheckResult(PasswordsStatus::kCompromisedExist,
                               num_compromised);
      }
      break;
    }
    case BulkLeakCheckService::State::kRunning:
      OnPasswordsCheckResult(PasswordsStatus::kChecking, 0);
      // Non-terminal state, so nothing else needs to be done.
      return;
    case BulkLeakCheckService::State::kSignedOut:
      OnPasswordsCheckResult(PasswordsStatus::kSignedOut, 0);
      break;
    case BulkLeakCheckService::State::kNetworkError:
      OnPasswordsCheckResult(PasswordsStatus::kOffline, 0);
      break;
    case BulkLeakCheckService::State::kQuotaLimit:
      OnPasswordsCheckResult(PasswordsStatus::kQuotaLimit, 0);
      break;
    case BulkLeakCheckService::State::kTokenRequestFailure:
    case BulkLeakCheckService::State::kHashingFailure:
    case BulkLeakCheckService::State::kServiceError:
      OnPasswordsCheckResult(PasswordsStatus::kError, 0);
      break;
  }
  // TODO(crbug.com/1015841): implement detecting the following states if it is
  // possible: kNoPasswords, kQuotaLimit, and kTooManyPasswords.

  // Stop observing the leak service in all terminal states.
  observed_leak_check_.Remove(leak_service_);
}

void SafetyCheckHandler::OnCredentialDone(
    const password_manager::LeakCheckCredential& credential,
    password_manager::IsLeaked is_leaked) {
  // Do nothing because we only want to know the total number of compromised
  // credentials at the end of the bulk leak check.
}

void SafetyCheckHandler::OnJavascriptAllowed() {}

void SafetyCheckHandler::OnJavascriptDisallowed() {
  // Remove |this| as an observer for BulkLeakCheck. This takes care of an edge
  // case when the page is reloaded while the password check is in progress and
  // another safety check is started. Otherwise |observed_leak_check_|
  // automatically calls RemoveAll() on destruction.
  observed_leak_check_.RemoveAll();
}

void SafetyCheckHandler::RegisterMessages() {
  web_ui()->RegisterMessageCallback(
      kPerformSafetyCheck,
      base::BindRepeating(&SafetyCheckHandler::HandlePerformSafetyCheck,
                          base::Unretained(this)));
  web_ui()->RegisterMessageCallback(
      kGetParentRanDisplayString,
      base::BindRepeating(&SafetyCheckHandler::HandleGetParentRanDisplayString,
                          base::Unretained(this)));
}
