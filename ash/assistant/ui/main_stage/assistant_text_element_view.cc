// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ash/assistant/ui/main_stage/assistant_text_element_view.h"

#include <memory>

#include "ash/assistant/model/ui/assistant_text_element.h"
#include "ash/assistant/ui/assistant_ui_constants.h"
#include "base/strings/utf_string_conversions.h"
#include "ui/views/background.h"
#include "ui/views/controls/label.h"
#include "ui/views/layout/fill_layout.h"

namespace ash {

// AssistantTextElementView ----------------------------------------------------

AssistantTextElementView::AssistantTextElementView(
    const AssistantTextElement* text_element) {
  InitLayout(text_element);
}

AssistantTextElementView::~AssistantTextElementView() = default;

const char* AssistantTextElementView::GetClassName() const {
  return "AssistantTextElementView";
}

void AssistantTextElementView::ChildPreferredSizeChanged(views::View* child) {
  PreferredSizeChanged();
}

void AssistantTextElementView::InitLayout(
    const AssistantTextElement* text_element) {
  SetLayoutManager(std::make_unique<views::FillLayout>());

  // Label.
  views::Label* label = AddChildView(
      std::make_unique<views::Label>(base::UTF8ToUTF16(text_element->text())));
  label->SetAutoColorReadabilityEnabled(false);
  label->SetBackground(views::CreateSolidBackground(SK_ColorWHITE));
  label->SetEnabledColor(kTextColorPrimary);
  label->SetFontList(assistant::ui::GetDefaultFontList()
                         .DeriveWithSizeDelta(2)
                         .DeriveWithWeight(gfx::Font::Weight::MEDIUM));
  label->SetHorizontalAlignment(gfx::HorizontalAlignment::ALIGN_LEFT);
  label->SetMultiLine(true);
}

}  // namespace ash
