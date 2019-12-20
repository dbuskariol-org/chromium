// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_ASSISTANT_UI_MAIN_STAGE_ASSISTANT_UI_ELEMENT_VIEW_H_
#define ASH_ASSISTANT_UI_MAIN_STAGE_ASSISTANT_UI_ELEMENT_VIEW_H_

#include <string>

#include "base/component_export.h"
#include "ui/views/view.h"

namespace ash {

// Base class for a visual representation of an AssistantUiElement. It is a
// child view of UiElementContainerView.
class COMPONENT_EXPORT(ASSISTANT_UI) AssistantUiElementView
    : public views::View {
 public:
  explicit AssistantUiElementView(AssistantUiElementView& copy) = delete;
  AssistantUiElementView& operator=(AssistantUiElementView& assign) = delete;
  ~AssistantUiElementView() override = default;

  // Returns a string representation of this view for testing.
  virtual std::string ToStringForTesting() const = 0;

 protected:
  AssistantUiElementView() = default;
};

}  // namespace ash

#endif  // ASH_ASSISTANT_UI_MAIN_STAGE_ASSISTANT_UI_ELEMENT_VIEW_H_
