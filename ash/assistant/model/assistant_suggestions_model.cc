// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ash/assistant/model/assistant_suggestions_model.h"

#include <algorithm>

#include "ash/assistant/model/assistant_suggestions_model_observer.h"
#include "base/unguessable_token.h"

namespace ash {

AssistantSuggestionsModel::AssistantSuggestionsModel() = default;

AssistantSuggestionsModel::~AssistantSuggestionsModel() = default;

void AssistantSuggestionsModel::AddObserver(
    AssistantSuggestionsModelObserver* observer) const {
  observers_.AddObserver(observer);
}

void AssistantSuggestionsModel::RemoveObserver(
    AssistantSuggestionsModelObserver* observer) const {
  observers_.RemoveObserver(observer);
}

const chromeos::assistant::AssistantSuggestion*
AssistantSuggestionsModel::GetSuggestionById(
    const base::UnguessableToken& id) const {
  for (auto& conversation_starter : conversation_starters_) {
    if (conversation_starter.id == id)
      return &conversation_starter;
  }
  for (auto& onboarding_suggestion : onboarding_suggestions_) {
    if (onboarding_suggestion.id == id)
      return &onboarding_suggestion;
  }
  return nullptr;
}

void AssistantSuggestionsModel::SetConversationStarters(
    std::vector<AssistantSuggestion>&& conversation_starters) {
  conversation_starters_.clear();
  conversation_starters_.swap(conversation_starters);

  NotifyConversationStartersChanged();
}

const std::vector<chromeos::assistant::AssistantSuggestion>&
AssistantSuggestionsModel::GetConversationStarters() const {
  return conversation_starters_;
}

void AssistantSuggestionsModel::SetOnboardingSuggestions(
    std::vector<AssistantSuggestion> onboarding_suggestions) {
  onboarding_suggestions_.clear();
  onboarding_suggestions_.swap(onboarding_suggestions);

  NotifyOnboardingSuggestionsChanged();
}

const std::vector<chromeos::assistant::AssistantSuggestion>&
AssistantSuggestionsModel::GetOnboardingSuggestions() const {
  return onboarding_suggestions_;
}

void AssistantSuggestionsModel::NotifyConversationStartersChanged() {
  for (AssistantSuggestionsModelObserver& observer : observers_)
    observer.OnConversationStartersChanged(conversation_starters_);
}

void AssistantSuggestionsModel::NotifyOnboardingSuggestionsChanged() {
  const std::vector<AssistantSuggestion>& onboarding_suggestions =
      GetOnboardingSuggestions();

  for (AssistantSuggestionsModelObserver& observer : observers_)
    observer.OnOnboardingSuggestionsChanged(onboarding_suggestions);
}

}  // namespace ash
