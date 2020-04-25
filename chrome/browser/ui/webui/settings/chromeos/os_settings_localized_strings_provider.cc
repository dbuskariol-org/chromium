// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/settings/chromeos/os_settings_localized_strings_provider.h"

#include "ash/public/cpp/ash_features.h"
#include "ash/public/mojom/assistant_state_controller.mojom.h"
#include "base/command_line.h"
#include "base/feature_list.h"
#include "base/no_destructor.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "build/branding_buildflags.h"
#include "build/build_config.h"
#include "build/buildflag.h"
#include "chrome/browser/browser_process.h"
#include "chrome/browser/browser_process_platform_part.h"
#include "chrome/browser/chromeos/local_search_service/local_search_service.h"
#include "chrome/browser/chromeos/multidevice_setup/multidevice_setup_client_factory.h"
#include "chrome/browser/chromeos/policy/browser_policy_connector_chromeos.h"
#include "chrome/browser/chromeos/profiles/profile_helper.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/ui/webui/chromeos/network_element_localized_strings_provider.h"
#include "chrome/browser/ui/webui/management_ui.h"
#include "chrome/browser/ui/webui/policy_indicator_localized_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/accessibility_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/apps_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/bluetooth_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/crostini_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/date_time_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/device_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/files_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/internet_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/languages_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/multidevice_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/os_settings_features_util.h"
#include "chrome/browser/ui/webui/settings/chromeos/people_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/personalization_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/plugin_vm_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/printing_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/privacy_strings_provider.h"
#include "chrome/browser/ui/webui/settings/chromeos/search/search_concept.h"
#include "chrome/browser/ui/webui/settings/chromeos/search_strings_provider.h"
#include "chrome/browser/ui/webui/settings/shared_settings_localized_strings_provider.h"
#include "chrome/browser/ui/webui/webui_util.h"
#include "chrome/common/chrome_features.h"
#include "chrome/common/pref_names.h"
#include "chrome/common/url_constants.h"
#include "chrome/common/webui_url_constants.h"
#include "chrome/grit/chromium_strings.h"
#include "chrome/grit/generated_resources.h"
#include "chrome/grit/locale_settings.h"
#include "chromeos/constants/chromeos_features.h"
#include "chromeos/constants/chromeos_switches.h"
#include "chromeos/strings/grit/chromeos_strings.h"
#include "components/prefs/pref_service.h"
#include "components/strings/grit/components_strings.h"
#include "components/version_ui/version_ui_constants.h"
#include "content/public/browser/web_ui_data_source.h"
#include "content/public/common/content_switches.h"
#include "ui/base/l10n/l10n_util.h"
#include "ui/base/webui/web_ui_util.h"
#include "ui/chromeos/devicetype_utils.h"
#include "ui/display/types/display_constants.h"

namespace chromeos {
namespace settings {
namespace {

std::vector<local_search_service::Data> ConceptVectorToDataVector(
    const std::vector<SearchConcept>& tags_group) {
  std::vector<local_search_service::Data> data_list;

  for (const auto& concept : tags_group) {
    std::vector<base::string16> search_tags;

    // Add the canonical tag.
    search_tags.push_back(
        l10n_util::GetStringUTF16(concept.canonical_message_id));

    // Add all alternate tags.
    for (size_t i = 0; i < SearchConcept::kMaxAltTagsPerConcept; ++i) {
      int curr_alt_tag = concept.alt_tag_ids[i];
      if (curr_alt_tag == SearchConcept::kAltTagEnd)
        break;
      search_tags.push_back(l10n_util::GetStringUTF16(curr_alt_tag));
    }

    // Note: A stringified version of the canonical tag message ID is used as
    // the identifier for this search data.
    data_list.emplace_back(base::NumberToString(concept.canonical_message_id),
                           search_tags);
  }

  return data_list;
}

bool IsDeviceManaged() {
  policy::BrowserPolicyConnectorChromeOS* connector =
      g_browser_process->platform_part()->browser_policy_connector_chromeos();
  return connector->IsEnterpriseManaged();
}

void AddCommonStrings(content::WebUIDataSource* html_source, Profile* profile) {
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
      {"add", IDS_ADD},
      {"advancedPageTitle", IDS_SETTINGS_ADVANCED},
      {"back", IDS_ACCNAME_BACK},
      {"basicPageTitle", IDS_SETTINGS_BASIC},
      {"cancel", IDS_CANCEL},
      {"clear", IDS_SETTINGS_CLEAR},
      {"close", IDS_CLOSE},
      {"confirm", IDS_CONFIRM},
      {"continue", IDS_SETTINGS_CONTINUE},
      {"controlledByExtension", IDS_SETTINGS_CONTROLLED_BY_EXTENSION},
      {"custom", IDS_SETTINGS_CUSTOM},
      {"delete", IDS_SETTINGS_DELETE},
      {"deviceOff", IDS_SETTINGS_DEVICE_OFF},
      {"deviceOn", IDS_SETTINGS_DEVICE_ON},
      {"disable", IDS_DISABLE},
      {"done", IDS_DONE},
      {"edit", IDS_SETTINGS_EDIT},
      {"extensionsLinkTooltip", IDS_SETTINGS_MENU_EXTENSIONS_LINK_TOOLTIP},
      {"learnMore", IDS_LEARN_MORE},
      {"menu", IDS_MENU},
      {"menuButtonLabel", IDS_SETTINGS_MENU_BUTTON_LABEL},
      {"moreActions", IDS_SETTINGS_MORE_ACTIONS},
      {"ok", IDS_OK},
      {"restart", IDS_SETTINGS_RESTART},
      {"save", IDS_SAVE},
      {"searchResultBubbleText", IDS_SEARCH_RESULT_BUBBLE_TEXT},
      {"searchResultsBubbleText", IDS_SEARCH_RESULTS_BUBBLE_TEXT},
      {"settings", IDS_SETTINGS_SETTINGS},
      {"settingsAltPageTitle", IDS_SETTINGS_ALT_PAGE_TITLE},
      {"subpageArrowRoleDescription", IDS_SETTINGS_SUBPAGE_BUTTON},
      {"notValidWebAddress", IDS_SETTINGS_NOT_VALID_WEB_ADDRESS},
      {"notValidWebAddressForContentType",
       IDS_SETTINGS_NOT_VALID_WEB_ADDRESS_FOR_CONTENT_TYPE},

      // Common font related strings shown in a11y and appearance sections.
      {"quickBrownFox", IDS_SETTINGS_QUICK_BROWN_FOX},
      {"verySmall", IDS_SETTINGS_VERY_SMALL_FONT},
      {"small", IDS_SETTINGS_SMALL_FONT},
      {"medium", IDS_SETTINGS_MEDIUM_FONT},
      {"large", IDS_SETTINGS_LARGE_FONT},
      {"veryLarge", IDS_SETTINGS_VERY_LARGE_FONT},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  html_source->AddBoolean("isGuest", features::IsGuestModeActive());

  html_source->AddBoolean("isSupervised", profile->IsSupervised());
}

void AddChromeOSUserStrings(content::WebUIDataSource* html_source,
                            Profile* profile) {
  user_manager::UserManager* user_manager = user_manager::UserManager::Get();

  const user_manager::User* user =
      chromeos::ProfileHelper::Get()->GetUserByProfile(profile);
  const user_manager::User* primary_user = user_manager->GetPrimaryUser();
  std::string primary_user_email = primary_user->GetAccountId().GetUserEmail();
  html_source->AddString("primaryUserEmail", primary_user_email);
  html_source->AddString("browserSettingsBannerText",
                         l10n_util::GetStringFUTF16(
                             IDS_SETTINGS_BROWSER_SETTINGS_BANNER,
                             base::ASCIIToUTF16(chrome::kChromeUISettingsURL)));
  html_source->AddBoolean(
      "isSecondaryUser",
      user && user->GetAccountId() != primary_user->GetAccountId());
  html_source->AddString(
      "secondaryUserBannerText",
      l10n_util::GetStringFUTF16(IDS_SETTINGS_SECONDARY_USER_BANNER,
                                 base::ASCIIToUTF16(primary_user_email)));

  if (!IsDeviceManaged() && !user_manager->IsCurrentUserOwner()) {
    html_source->AddString("ownerEmail",
                           user_manager->GetOwnerAccountId().GetUserEmail());
  }
}

void AddSearchInSettingsStrings(content::WebUIDataSource* html_source) {
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
      {"searchPrompt", IDS_SETTINGS_SEARCH_PROMPT},
      {"searchNoResults", IDS_SEARCH_NO_RESULTS},
      {"searchResults", IDS_SEARCH_RESULTS},
      {"searchResultSelected", IDS_OS_SEARCH_RESULT_ROW_A11Y_RESULT_SELECTED},
      {"searchResultsOne", IDS_OS_SEARCH_BOX_A11Y_ONE_RESULT},
      {"searchResultsNumber", IDS_OS_SEARCH_BOX_A11Y_RESULT_COUNT},
      // TODO(dpapad): IDS_DOWNLOAD_CLEAR_SEARCH and IDS_HISTORY_CLEAR_SEARCH
      // are identical, merge them to one and re-use here.
      {"clearSearch", IDS_DOWNLOAD_CLEAR_SEARCH},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  html_source->AddString(
      "searchNoOsResultsHelp",
      l10n_util::GetStringFUTF16(
          IDS_SETTINGS_SEARCH_NO_RESULTS_HELP,
          base::ASCIIToUTF16(chrome::kOsSettingsSearchHelpURL)));

  html_source->AddBoolean(
      "newOsSettingsSearch",
      base::FeatureList::IsEnabled(chromeos::features::kNewOsSettingsSearch));
}

void AddAboutStrings(content::WebUIDataSource* html_source, Profile* profile) {
  // Top level About page strings.
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
    {"aboutProductLogoAlt", IDS_SHORT_PRODUCT_LOGO_ALT_TEXT},
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
    {"aboutReportAnIssue", IDS_SETTINGS_ABOUT_PAGE_REPORT_AN_ISSUE},
#endif
    {"aboutRelaunch", IDS_SETTINGS_ABOUT_PAGE_RELAUNCH},
    {"aboutUpgradeCheckStarted", IDS_SETTINGS_ABOUT_UPGRADE_CHECK_STARTED},
    {"aboutUpgradeRelaunch", IDS_SETTINGS_UPGRADE_SUCCESSFUL_RELAUNCH},
    {"aboutUpgradeUpdating", IDS_SETTINGS_UPGRADE_UPDATING},
    {"aboutUpgradeUpdatingPercent", IDS_SETTINGS_UPGRADE_UPDATING_PERCENT},
    {"aboutGetHelpUsingChrome", IDS_SETTINGS_GET_HELP_USING_CHROME},
    {"aboutPageTitle", IDS_SETTINGS_ABOUT_PROGRAM},
    {"aboutProductTitle", IDS_PRODUCT_NAME},

    {"aboutEndOfLifeTitle", IDS_SETTINGS_ABOUT_PAGE_END_OF_LIFE_TITLE},
    {"aboutRelaunchAndPowerwash",
     IDS_SETTINGS_ABOUT_PAGE_RELAUNCH_AND_POWERWASH},
    {"aboutRollbackInProgress", IDS_SETTINGS_UPGRADE_ROLLBACK_IN_PROGRESS},
    {"aboutRollbackSuccess", IDS_SETTINGS_UPGRADE_ROLLBACK_SUCCESS},
    {"aboutUpgradeUpdatingChannelSwitch",
     IDS_SETTINGS_UPGRADE_UPDATING_CHANNEL_SWITCH},
    {"aboutUpgradeSuccessChannelSwitch",
     IDS_SETTINGS_UPGRADE_SUCCESSFUL_CHANNEL_SWITCH},
    {"aboutTPMFirmwareUpdateTitle",
     IDS_SETTINGS_ABOUT_TPM_FIRMWARE_UPDATE_TITLE},
    {"aboutTPMFirmwareUpdateDescription",
     IDS_SETTINGS_ABOUT_TPM_FIRMWARE_UPDATE_DESCRIPTION},

    // About page, channel switcher dialog.
    {"aboutChangeChannel", IDS_SETTINGS_ABOUT_PAGE_CHANGE_CHANNEL},
    {"aboutChangeChannelAndPowerwash",
     IDS_SETTINGS_ABOUT_PAGE_CHANGE_CHANNEL_AND_POWERWASH},
    {"aboutDelayedWarningMessage",
     IDS_SETTINGS_ABOUT_PAGE_DELAYED_WARNING_MESSAGE},
    {"aboutDelayedWarningTitle", IDS_SETTINGS_ABOUT_PAGE_DELAYED_WARNING_TITLE},
    {"aboutPowerwashWarningMessage",
     IDS_SETTINGS_ABOUT_PAGE_POWERWASH_WARNING_MESSAGE},
    {"aboutPowerwashWarningTitle",
     IDS_SETTINGS_ABOUT_PAGE_POWERWASH_WARNING_TITLE},
    {"aboutUnstableWarningMessage",
     IDS_SETTINGS_ABOUT_PAGE_UNSTABLE_WARNING_MESSAGE},
    {"aboutUnstableWarningTitle",
     IDS_SETTINGS_ABOUT_PAGE_UNSTABLE_WARNING_TITLE},
    {"aboutChannelDialogBeta", IDS_SETTINGS_ABOUT_PAGE_DIALOG_CHANNEL_BETA},
    {"aboutChannelDialogDev", IDS_SETTINGS_ABOUT_PAGE_DIALOG_CHANNEL_DEV},
    {"aboutChannelDialogStable", IDS_SETTINGS_ABOUT_PAGE_DIALOG_CHANNEL_STABLE},

    // About page, update warning dialog.
    {"aboutUpdateWarningMessage",
     IDS_SETTINGS_ABOUT_PAGE_UPDATE_WARNING_MESSAGE},
    {"aboutUpdateWarningTitle", IDS_SETTINGS_ABOUT_PAGE_UPDATE_WARNING_TITLE},

    // Detailed build information
    {"aboutBuildDetailsTitle", IDS_OS_SETTINGS_ABOUT_PAGE_BUILD_DETAILS},
    {"aboutChannelBeta", IDS_SETTINGS_ABOUT_PAGE_CURRENT_CHANNEL_BETA},
    {"aboutChannelCanary", IDS_SETTINGS_ABOUT_PAGE_CURRENT_CHANNEL_CANARY},
    {"aboutChannelDev", IDS_SETTINGS_ABOUT_PAGE_CURRENT_CHANNEL_DEV},
    {"aboutChannelLabel", IDS_SETTINGS_ABOUT_PAGE_CHANNEL},
    {"aboutChannelStable", IDS_SETTINGS_ABOUT_PAGE_CURRENT_CHANNEL_STABLE},
    {"aboutCheckForUpdates", IDS_SETTINGS_ABOUT_PAGE_CHECK_FOR_UPDATES},
    {"aboutCurrentlyOnChannel", IDS_SETTINGS_ABOUT_PAGE_CURRENT_CHANNEL},
    {"aboutDetailedBuildInfo", IDS_SETTINGS_ABOUT_PAGE_DETAILED_BUILD_INFO},
    {version_ui::kApplicationLabel, IDS_PRODUCT_NAME},
    {version_ui::kPlatform, IDS_PLATFORM_LABEL},
    {version_ui::kFirmwareVersion, IDS_VERSION_UI_FIRMWARE_VERSION},
    {version_ui::kARC, IDS_ARC_LABEL},
    {"aboutBuildDetailsCopyTooltipLabel",
     IDS_OS_SETTINGS_ABOUT_PAGE_BUILD_DETAILS_COPY_TOOLTIP_LABEL},
    {"aboutIsArcStatusTitle", IDS_OS_SETTINGS_ABOUT_ARC_STATUS_TITLE},
    {"aboutIsDeveloperModeTitle", IDS_OS_SETTINGS_ABOUT_DEVELOPER_MODE},
    {"isEnterpriseManagedTitle",
     IDS_OS_SETTINGS_ABOUT_PAGE_ENTERPRISE_ENNROLLED_TITLE},
    {"aboutOsPageTitle", IDS_SETTINGS_ABOUT_OS},
    {"aboutGetHelpUsingChromeOs", IDS_SETTINGS_GET_HELP_USING_CHROME_OS},
    {"aboutOsProductTitle", IDS_PRODUCT_OS_NAME},
    {"aboutReleaseNotesOffline", IDS_SETTINGS_ABOUT_PAGE_RELEASE_NOTES},
    {"aboutShowReleaseNotes", IDS_SETTINGS_ABOUT_PAGE_SHOW_RELEASE_NOTES},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  html_source->AddString("aboutTPMFirmwareUpdateLearnMoreURL",
                         chrome::kTPMFirmwareUpdateLearnMoreURL);
  html_source->AddString(
      "aboutUpgradeUpToDate",
      ui::SubstituteChromeOSDeviceType(IDS_SETTINGS_UPGRADE_UP_TO_DATE));
  html_source->AddString("managementPage",
                         ManagementUI::GetManagementPageSubtitle(profile));
}

void AddResetStrings(content::WebUIDataSource* html_source) {
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
      {"resetPageTitle", IDS_SETTINGS_RESET},
      {"powerwashTitle", IDS_SETTINGS_FACTORY_RESET},
      {"powerwashDialogTitle", IDS_SETTINGS_FACTORY_RESET_HEADING},
      {"powerwashDialogButton", IDS_SETTINGS_RESTART},
      {"powerwashButton", IDS_SETTINGS_FACTORY_RESET_BUTTON_LABEL},
      {"powerwashDialogExplanation", IDS_SETTINGS_FACTORY_RESET_WARNING},
      {"powerwashLearnMoreUrl", IDS_FACTORY_RESET_HELP_URL},
      {"powerwashButtonRoleDescription",
       IDS_SETTINGS_FACTORY_RESET_BUTTON_ROLE},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  html_source->AddString(
      "powerwashDescription",
      l10n_util::GetStringFUTF16(IDS_SETTINGS_FACTORY_RESET_DESCRIPTION,
                                 l10n_util::GetStringUTF16(IDS_PRODUCT_NAME)));
}

}  // namespace

OsSettingsLocalizedStringsProvider::OsSettingsLocalizedStringsProvider(
    Profile* profile,
    local_search_service::LocalSearchService* local_search_service,
    multidevice_setup::MultiDeviceSetupClient* multidevice_setup_client,
    syncer::SyncService* sync_service,
    SupervisedUserService* supervised_user_service,
    KerberosCredentialsManager* kerberos_credentials_manager,
    ArcAppListPrefs* arc_app_list_prefs)
    : index_(local_search_service->GetIndex(
          local_search_service::IndexId::kCrosSettings)) {
  // Add per-page string providers.
  // TODO(khorimoto): Add providers for the remaining pages.
  per_page_providers_.push_back(
      std::make_unique<InternetStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<BluetoothStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(std::make_unique<MultiDeviceStringsProvider>(
      profile, /*delegate=*/this, multidevice_setup_client));
  per_page_providers_.push_back(std::make_unique<PeopleStringsProvider>(
      profile, /*delegate=*/this, sync_service, supervised_user_service,
      kerberos_credentials_manager));
  per_page_providers_.push_back(
      std::make_unique<DeviceStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<PersonalizationStringsProvider>(
          profile, /*delegate=*/this, profile->GetPrefs()));
  per_page_providers_.push_back(
      std::make_unique<SearchStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(std::make_unique<AppsStringsProvider>(
      profile, /*delegate=*/this, profile->GetPrefs(), arc_app_list_prefs));
  per_page_providers_.push_back(std::make_unique<CrostiniStringsProvider>(
      profile, /*delegate=*/this, profile->GetPrefs()));
  per_page_providers_.push_back(std::make_unique<PluginVmStringsProvider>(
      profile, /*delegate=*/this, profile->GetPrefs()));
  per_page_providers_.push_back(
      std::make_unique<DateTimeStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<PrivacyStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<LanguagesStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<FilesStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(
      std::make_unique<PrintingStringsProvider>(profile, /*delegate=*/this));
  per_page_providers_.push_back(std::make_unique<AccessibilityStringsProvider>(
      profile, /*delegate=*/this, profile->GetPrefs()));
}

OsSettingsLocalizedStringsProvider::~OsSettingsLocalizedStringsProvider() =
    default;

void OsSettingsLocalizedStringsProvider::AddOsLocalizedStrings(
    content::WebUIDataSource* html_source,
    Profile* profile) {
  for (const auto& per_page_provider : per_page_providers_)
    per_page_provider->AddUiStrings(html_source);

  // TODO(khorimoto): Migrate these to OsSettingsPerPageStringsProvider
  // instances.
  AddAboutStrings(html_source, profile);
  AddChromeOSUserStrings(html_source, profile);
  AddCommonStrings(html_source, profile);
  AddResetStrings(html_source);
  AddSearchInSettingsStrings(html_source);

  policy_indicator::AddLocalizedStrings(html_source);

  html_source->UseStringsJs();
}

const SearchConcept*
OsSettingsLocalizedStringsProvider::GetCanonicalTagMetadata(
    int canonical_message_id) const {
  const auto it = canonical_id_to_metadata_map_.find(canonical_message_id);
  if (it == canonical_id_to_metadata_map_.end())
    return nullptr;
  return it->second;
}

void OsSettingsLocalizedStringsProvider::Shutdown() {
  index_ = nullptr;

  // Delete all per-page providers, since some of them depend on KeyedServices.
  per_page_providers_.clear();
}

void OsSettingsLocalizedStringsProvider::AddSearchTags(
    const std::vector<SearchConcept>& tags_group) {
  // Note: Can be null after Shutdown().
  if (!index_)
    return;

  index_->AddOrUpdate(ConceptVectorToDataVector(tags_group));

  // Add each concept to the map. Note that it is safe to take the address of
  // each concept because all concepts are allocated via static
  // base::NoDestructor objects in the Get*SearchConcepts() helper functions.
  for (const auto& concept : tags_group)
    canonical_id_to_metadata_map_[concept.canonical_message_id] = &concept;
}

void OsSettingsLocalizedStringsProvider::RemoveSearchTags(
    const std::vector<SearchConcept>& tags_group) {
  // Note: Can be null after Shutdown().
  if (!index_)
    return;

  std::vector<std::string> ids;
  for (const auto& concept : tags_group) {
    canonical_id_to_metadata_map_.erase(concept.canonical_message_id);
    ids.push_back(base::NumberToString(concept.canonical_message_id));
  }

  index_->Delete(ids);
}

}  // namespace settings
}  // namespace chromeos
