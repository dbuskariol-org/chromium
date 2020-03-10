// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/macros.h"
#include "base/scoped_observer.h"
#include "base/test/bind_test_util.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "ui/base/material_design/material_design_controller.h"

namespace ui {

namespace {

class TestObserver {
 public:
  explicit TestObserver(MaterialDesignController* controller)
      : md_subscription_(controller->RegisterCallback(
            base::BindLambdaForTesting([this]() { ++touch_ui_changes_; }))) {}
  ~TestObserver() = default;

  int touch_ui_changes() const { return touch_ui_changes_; }

 private:
  int touch_ui_changes_ = 0;
  std::unique_ptr<ui::MaterialDesignController::Subscription> md_subscription_;
};

}  // namespace

// Verifies that non-touch is the default.
TEST(MaterialDesignControllerTest, DefaultIsNonTouch) {
  MaterialDesignController controller;
  EXPECT_FALSE(controller.touch_ui());
}

// Verifies that kDisabled maps to non-touch.
TEST(MaterialDesignControllerTest, DisabledIsNonTouch) {
  MaterialDesignController controller(
      MaterialDesignController::TouchUiState::kDisabled);
  EXPECT_FALSE(controller.touch_ui());
}

// Verifies that kAuto maps to non-touch (the default).
TEST(MaterialDesignControllerTest, AutoIsNonTouch) {
  MaterialDesignController controller(
      MaterialDesignController::TouchUiState::kAuto);
  EXPECT_FALSE(controller.touch_ui());
}

// Verifies that kEnabled maps to touch.
TEST(MaterialDesignControllerTest, EnabledIsNonTouch) {
  MaterialDesignController controller(
      MaterialDesignController::TouchUiState::kEnabled);
  EXPECT_TRUE(controller.touch_ui());
}

// Verifies that when the mode is set to non-touch and the tablet mode toggles,
// the touch UI state does not change.
TEST(MaterialDesignControllerTest, TabletToggledOnTouchUiDisabled) {
  MaterialDesignController controller(
      MaterialDesignController::TouchUiState::kDisabled);
  TestObserver observer(&controller);

  controller.OnTabletModeToggled(true);
  EXPECT_FALSE(controller.touch_ui());
  EXPECT_EQ(0, observer.touch_ui_changes());

  controller.OnTabletModeToggled(false);
  EXPECT_FALSE(controller.touch_ui());
  EXPECT_EQ(0, observer.touch_ui_changes());
}

// Verifies that when the mode is set to auto and the tablet mode toggles, the
// touch UI state changes and the observer gets called back.
TEST(MaterialDesignControllerTest, TabletToggledOnTouchUiAuto) {
  MaterialDesignController controller(
      MaterialDesignController::TouchUiState::kAuto);
  TestObserver observer(&controller);

  controller.OnTabletModeToggled(true);
  EXPECT_TRUE(controller.touch_ui());
  EXPECT_EQ(1, observer.touch_ui_changes());

  controller.OnTabletModeToggled(false);
  EXPECT_FALSE(controller.touch_ui());
  EXPECT_EQ(2, observer.touch_ui_changes());
}

}  // namespace ui
