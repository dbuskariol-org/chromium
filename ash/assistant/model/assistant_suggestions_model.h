// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_ASSISTANT_MODEL_ASSISTANT_SUGGESTIONS_MODEL_H_
#define ASH_ASSISTANT_MODEL_ASSISTANT_SUGGESTIONS_MODEL_H_

#include <memory>
#include <vector>

#include "base/component_export.h"
#include "base/macros.h"
#include "base/observer_list.h"
#include "chromeos/services/assistant/public/cpp/assistant_service.h"

namespace base {
class UnguessableToken;
}  // namespace base

namespace ash {

class AssistantSuggestionsModelObserver;

class COMPONENT_EXPORT(ASSISTANT_MODEL) AssistantSuggestionsModel {
 public:
  using AssistantSuggestion = chromeos::assistant::AssistantSuggestion;

  AssistantSuggestionsModel();
  ~AssistantSuggestionsModel();

  // Adds/removes the specified suggestions model |observer|.
  void AddObserver(AssistantSuggestionsModelObserver* observer) const;
  void RemoveObserver(AssistantSuggestionsModelObserver* observer) const;

  // Returns the AssistantSuggestion uniquely identified by |id|. Returns
  // nullptr if not found.
  const AssistantSuggestion* GetSuggestionById(
      const base::UnguessableToken& id) const;

  // Sets the cache of conversation starters.
  void SetConversationStarters(
      std::vector<AssistantSuggestion>&& conversation_starters);

  // Returns all cached conversation starters.
  const std::vector<AssistantSuggestion>& GetConversationStarters() const;

  // Sets the cache of onboarding suggestions.
  void SetOnboardingSuggestions(
      std::vector<AssistantSuggestion> onboarding_suggestions);

  // Returns all cached onboarding suggestions.
  const std::vector<AssistantSuggestion>& GetOnboardingSuggestions() const;

 private:
  void NotifyConversationStartersChanged();
  void NotifyOnboardingSuggestionsChanged();

  std::vector<AssistantSuggestion> conversation_starters_;
  std::vector<AssistantSuggestion> onboarding_suggestions_;

  mutable base::ObserverList<AssistantSuggestionsModelObserver> observers_;

  DISALLOW_COPY_AND_ASSIGN(AssistantSuggestionsModel);
};

}  // namespace ash

#endif  // ASH_ASSISTANT_MODEL_ASSISTANT_SUGGESTIONS_MODEL_H_
