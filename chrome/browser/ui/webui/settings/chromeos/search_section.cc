// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/settings/chromeos/search_section.h"

#include <memory>
#include <vector>

#include "ash/public/cpp/assistant/assistant_state.h"
#include "base/no_destructor.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "chrome/browser/chromeos/assistant/assistant_util.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/ui/webui/chromeos/assistant_optin/assistant_optin_utils.h"
#include "chrome/browser/ui/webui/settings/chromeos/google_assistant_handler.h"
#include "chrome/browser/ui/webui/settings/chromeos/search/search_tag_registry.h"
#include "chrome/browser/ui/webui/settings/search_engines_handler.h"
#include "chrome/browser/ui/webui/webui_util.h"
#include "chrome/common/url_constants.h"
#include "chrome/grit/generated_resources.h"
#include "chromeos/components/quick_answers/quick_answers_client.h"
#include "chromeos/constants/chromeos_features.h"
#include "chromeos/services/assistant/public/cpp/assistant_prefs.h"
#include "chromeos/services/assistant/public/cpp/features.h"
#include "components/language/core/browser/pref_names.h"
#include "components/prefs/pref_service.h"
#include "content/public/browser/web_ui_data_source.h"
#include "third_party/icu/source/common/unicode/locid.h"
#include "ui/base/l10n/l10n_util.h"
#include "ui/base/webui/web_ui_util.h"
#include "ui/chromeos/devicetype_utils.h"

namespace chromeos {
namespace settings {
namespace {

const std::vector<SearchConcept>& GetSearchPageSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_PREFERRED_SEARCH_ENGINE,
       mojom::kSearchAndAssistantSectionPath,
       mojom::SearchResultIcon::kMagnifyingGlass,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kPreferredSearchEngine}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSubpage,
       {.subpage = mojom::Subpage::kAssistant}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantOnSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT_TURN_OFF,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantOnOff},
       {IDS_OS_SETTINGS_TAG_ASSISTANT_TURN_OFF_ALT1,
        SearchConcept::kAltTagEnd}},
      {IDS_OS_SETTINGS_TAG_ASSISTANT_PREFERRED_INPUT,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantVoiceInput}},
      {IDS_OS_SETTINGS_TAG_ASSISTANT_NOTIFICATIONS,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantNotifications}},
      {IDS_OS_SETTINGS_TAG_ASSISTANT_RELATED_INFO,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantRelatedInfo}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantOffSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT_TURN_ON,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kMedium,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantOnOff},
       {IDS_OS_SETTINGS_TAG_ASSISTANT_TURN_ON_ALT1, SearchConcept::kAltTagEnd}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantQuickAnswersSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT_QUICK_ANSWERS,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantQuickAnswers}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantHotwordDspSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT_OK_GOOGLE,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kAssistantOkGoogle},
       {IDS_OS_SETTINGS_TAG_ASSISTANT_OK_GOOGLE_ALT1,
        IDS_OS_SETTINGS_TAG_ASSISTANT_OK_GOOGLE_ALT2,
        SearchConcept::kAltTagEnd}},
  });
  return *tags;
}

const std::vector<SearchConcept>& GetAssistantVoiceMatchSearchConcepts() {
  static const base::NoDestructor<std::vector<SearchConcept>> tags({
      {IDS_OS_SETTINGS_TAG_ASSISTANT_TRAIN_VOICE_MODEL,
       mojom::kAssistantSubpagePath,
       mojom::SearchResultIcon::kAssistant,
       mojom::SearchResultDefaultRank::kLow,
       mojom::SearchResultType::kSetting,
       {.setting = mojom::Setting::kTrainAssistantVoiceModel}},
  });
  return *tags;
}

bool IsVoiceMatchAllowed() {
  return !assistant::features::IsVoiceMatchDisabled();
}

void AddGoogleAssistantStrings(content::WebUIDataSource* html_source) {
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
      {"googleAssistantPageTitle", IDS_SETTINGS_GOOGLE_ASSISTANT},
      {"googleAssistantEnableContext", IDS_ASSISTANT_SCREEN_CONTEXT_TITLE},
      {"googleAssistantEnableContextDescription",
       IDS_ASSISTANT_SCREEN_CONTEXT_DESC},
      {"googleAssistantEnableQuickAnswers",
       IDS_ASSISTANT_QUICK_ANSWERS_SETTING_TITLE},
      {"googleAssistantEnableQuickAnswersDescription",
       IDS_ASSISTANT_QUICK_ANSWERS_SETTING_DESC},
      {"googleAssistantEnableHotword",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD},
      {"googleAssistantEnableHotwordDescription",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD_DESCRIPTION},
      {"googleAssistantVoiceSettings",
       IDS_SETTINGS_GOOGLE_ASSISTANT_VOICE_SETTINGS},
      {"googleAssistantVoiceSettingsDescription",
       IDS_ASSISTANT_VOICE_MATCH_RECORDING},
      {"googleAssistantVoiceSettingsRetrainButton",
       IDS_SETTINGS_GOOGLE_ASSISTANT_VOICE_SETTINGS_RETRAIN},
      {"googleAssistantEnableHotwordWithoutDspDescription",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD_WITHOUT_DSP_DESCRIPTION},
      {"googleAssistantEnableHotwordWithoutDspRecommended",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD_WITHOUT_DSP_RECOMMENDED},
      {"googleAssistantEnableHotwordWithoutDspAlwaysOn",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD_WITHOUT_DSP_ALWAYS_ON},
      {"googleAssistantEnableHotwordWithoutDspOff",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_HOTWORD_WITHOUT_DSP_OFF},
      {"googleAssistantEnableNotification",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_NOTIFICATION},
      {"googleAssistantEnableNotificationDescription",
       IDS_SETTINGS_GOOGLE_ASSISTANT_ENABLE_NOTIFICATION_DESCRIPTION},
      {"googleAssistantLaunchWithMicOpen",
       IDS_SETTINGS_GOOGLE_ASSISTANT_LAUNCH_WITH_MIC_OPEN},
      {"googleAssistantLaunchWithMicOpenDescription",
       IDS_SETTINGS_GOOGLE_ASSISTANT_LAUNCH_WITH_MIC_OPEN_DESCRIPTION},
      {"googleAssistantSettings", IDS_SETTINGS_GOOGLE_ASSISTANT_SETTINGS},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  html_source->AddBoolean("hotwordDspAvailable", IsHotwordDspAvailable());
  html_source->AddBoolean("voiceMatchDisabled", !IsVoiceMatchAllowed());
}

}  // namespace

SearchSection::SearchSection(Profile* profile,
                             SearchTagRegistry* search_tag_registry)
    : OsSettingsSection(profile, search_tag_registry) {
  registry()->AddSearchTags(GetSearchPageSearchConcepts());

  ash::AssistantState* assistant_state = ash::AssistantState::Get();
  if (IsAssistantAllowed() && assistant_state) {
    registry()->AddSearchTags(GetAssistantSearchConcepts());

    assistant_state->AddObserver(this);
    UpdateAssistantSearchTags();
  }
}

SearchSection::~SearchSection() {
  ash::AssistantState* assistant_state = ash::AssistantState::Get();
  if (IsAssistantAllowed() && assistant_state)
    assistant_state->RemoveObserver(this);
}

void SearchSection::AddLoadTimeData(content::WebUIDataSource* html_source) {
  static constexpr webui::LocalizedString kLocalizedStrings[] = {
      {"osSearchEngineLabel", IDS_OS_SETTINGS_SEARCH_ENGINE_LABEL},
      {"searchGoogleAssistant", IDS_SETTINGS_SEARCH_GOOGLE_ASSISTANT},
      {"searchGoogleAssistantEnabled",
       IDS_SETTINGS_SEARCH_GOOGLE_ASSISTANT_ENABLED},
      {"searchGoogleAssistantDisabled",
       IDS_SETTINGS_SEARCH_GOOGLE_ASSISTANT_DISABLED},
      {"searchGoogleAssistantOn", IDS_SETTINGS_SEARCH_GOOGLE_ASSISTANT_ON},
      {"searchGoogleAssistantOff", IDS_SETTINGS_SEARCH_GOOGLE_ASSISTANT_OFF},
  };
  AddLocalizedStringsBulk(html_source, kLocalizedStrings);

  const bool is_assistant_allowed = IsAssistantAllowed();
  html_source->AddBoolean("isAssistantAllowed", is_assistant_allowed);
  html_source->AddBoolean("quickAnswersAvailable", IsQuickAnswersAllowed());
  html_source->AddLocalizedString("osSearchPageTitle",
                                  is_assistant_allowed
                                      ? IDS_SETTINGS_SEARCH_AND_ASSISTANT
                                      : IDS_SETTINGS_SEARCH);
  html_source->AddString("searchExplanation",
                         l10n_util::GetStringFUTF16(
                             IDS_SETTINGS_SEARCH_EXPLANATION,
                             base::ASCIIToUTF16(chrome::kOmniboxLearnMoreURL)));
  html_source->AddString(
      "osSearchEngineTooltip",
      ui::SubstituteChromeOSDeviceType(IDS_OS_SETTINGS_SEARCH_ENGINE_TOOLTIP));

  AddGoogleAssistantStrings(html_source);
}

void SearchSection::AddHandlers(content::WebUI* web_ui) {
  web_ui->AddMessageHandler(
      std::make_unique<::settings::SearchEnginesHandler>(profile()));
  web_ui->AddMessageHandler(
      std::make_unique<chromeos::settings::GoogleAssistantHandler>());
}

int SearchSection::GetSectionNameMessageId() const {
  return IsAssistantAllowed() ? IDS_SETTINGS_SEARCH_AND_ASSISTANT
                              : IDS_SETTINGS_SEARCH;
}

mojom::Section SearchSection::GetSection() const {
  return mojom::Section::kSearchAndAssistant;
}

mojom::SearchResultIcon SearchSection::GetSectionIcon() const {
  return mojom::SearchResultIcon::kMagnifyingGlass;
}

std::string SearchSection::GetSectionPath() const {
  return mojom::kSearchAndAssistantSectionPath;
}

void SearchSection::RegisterHierarchy(HierarchyGenerator* generator) const {
  generator->RegisterTopLevelSetting(mojom::Setting::kPreferredSearchEngine);

  // Assistant.
  generator->RegisterTopLevelSubpage(
      IDS_SETTINGS_GOOGLE_ASSISTANT, mojom::Subpage::kAssistant,
      mojom::SearchResultIcon::kAssistant,
      mojom::SearchResultDefaultRank::kMedium, mojom::kAssistantSubpagePath);
  static constexpr mojom::Setting kAssistantSettings[] = {
      mojom::Setting::kAssistantOnOff,
      mojom::Setting::kAssistantRelatedInfo,
      mojom::Setting::kAssistantQuickAnswers,
      mojom::Setting::kAssistantOkGoogle,
      mojom::Setting::kAssistantNotifications,
      mojom::Setting::kAssistantVoiceInput,
      mojom::Setting::kTrainAssistantVoiceModel,
  };
  RegisterNestedSettingBulk(mojom::Subpage::kAssistant, kAssistantSettings,
                            generator);
}

void SearchSection::OnAssistantConsentStatusChanged(int consent_status) {
  UpdateAssistantSearchTags();
}

void SearchSection::OnAssistantContextEnabled(bool enabled) {
  UpdateAssistantSearchTags();
}

void SearchSection::OnAssistantSettingsEnabled(bool enabled) {
  UpdateAssistantSearchTags();
}

void SearchSection::OnAssistantHotwordEnabled(bool enabled) {
  UpdateAssistantSearchTags();
}

bool SearchSection::IsAssistantAllowed() const {
  // NOTE: This will be false when the flag is disabled.
  return ::assistant::IsAssistantAllowedForProfile(profile()) ==
         chromeos::assistant::AssistantAllowedState::ALLOWED;
}

bool SearchSection::IsQuickAnswersAllowed() const {
  if (!features::IsQuickAnswersSettingToggleEnabled())
    return false;

  const PrefService* prefs = profile()->GetPrefs();
  std::string pref_locale =
      prefs->GetString(language::prefs::kApplicationLocale);
  // Also accept runtime locale which maybe an approximation of user's pref
  // locale.
  const std::string kRuntimeLocale = icu::Locale::getDefault().getName();

  base::ReplaceChars(pref_locale, "-", "_", &pref_locale);
  if (!::chromeos::quick_answers::QuickAnswersClient::
          IsQuickAnswersAllowedForLocale(pref_locale, kRuntimeLocale))
    return false;

  return true;
}

void SearchSection::UpdateAssistantSearchTags() {
  // Start without any Assistant search concepts, then add if needed below.
  registry()->RemoveSearchTags(GetAssistantOnSearchConcepts());
  registry()->RemoveSearchTags(GetAssistantOffSearchConcepts());
  registry()->RemoveSearchTags(GetAssistantQuickAnswersSearchConcepts());
  registry()->RemoveSearchTags(GetAssistantHotwordDspSearchConcepts());
  registry()->RemoveSearchTags(GetAssistantVoiceMatchSearchConcepts());

  ash::AssistantState* assistant_state = ash::AssistantState::Get();

  // The setting_enabled() function is the top-level enabled state. If this is
  // off, none of the sub-features are enabled.
  if (!assistant_state->settings_enabled() ||
      !assistant_state->settings_enabled().value()) {
    registry()->AddSearchTags(GetAssistantOffSearchConcepts());
    return;
  }

  registry()->AddSearchTags(GetAssistantOnSearchConcepts());

  if (IsQuickAnswersAllowed() && assistant_state->context_enabled() &&
      assistant_state->context_enabled().value()) {
    registry()->AddSearchTags(GetAssistantQuickAnswersSearchConcepts());
  }

  if (IsHotwordDspAvailable())
    registry()->AddSearchTags(GetAssistantHotwordDspSearchConcepts());

  if (IsVoiceMatchAllowed() && assistant_state->hotword_enabled() &&
      assistant_state->hotword_enabled().value() &&
      assistant_state->consent_status() &&
      assistant_state->consent_status().value() ==
          assistant::prefs::ConsentStatus::kActivityControlAccepted) {
    registry()->AddSearchTags(GetAssistantVoiceMatchSearchConcepts());
  }
}

}  // namespace settings
}  // namespace chromeos
