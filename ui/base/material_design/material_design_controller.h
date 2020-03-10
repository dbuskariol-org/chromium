// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef UI_BASE_MATERIAL_DESIGN_MATERIAL_DESIGN_CONTROLLER_H_
#define UI_BASE_MATERIAL_DESIGN_MATERIAL_DESIGN_CONTROLLER_H_

#include <string>

#include "base/callback_list.h"
#include "build/build_config.h"
#include "ui/base/ui_base_export.h"

#if defined(OS_WIN)
namespace gfx {
class SingletonHwndObserver;
}
#endif

namespace ui {

// Central controller to handle material design modes.
class UI_BASE_EXPORT MaterialDesignController {
 public:
  using CallbackList = base::CallbackList<void()>;
  using Subscription = CallbackList::Subscription;

  enum class TouchUiState {
    kDisabled,
    kAuto,
    kEnabled,
  };

  class UI_BASE_EXPORT TouchUiScoperForTesting {
   public:
    explicit TouchUiScoperForTesting(
        bool enabled,
        MaterialDesignController* controller = GetInstance());
    TouchUiScoperForTesting(const TouchUiScoperForTesting&) = delete;
    TouchUiScoperForTesting& operator=(const TouchUiScoperForTesting&) = delete;
    ~TouchUiScoperForTesting();

   private:
    MaterialDesignController* const controller_;
    const TouchUiState old_state_;
  };

  static MaterialDesignController* GetInstance();

  explicit MaterialDesignController(
      TouchUiState touch_ui_state = TouchUiState::kAuto);
  MaterialDesignController(const MaterialDesignController&) = delete;
  MaterialDesignController& operator=(const MaterialDesignController&) = delete;
  ~MaterialDesignController();

  bool touch_ui() const {
    return (touch_ui_state_ == TouchUiState::kEnabled) ||
           ((touch_ui_state_ == TouchUiState::kAuto) && tablet_mode_);
  }

  std::unique_ptr<Subscription> RegisterCallback(
      const base::RepeatingClosure& closure);

  void OnTabletModeToggled(bool enabled);

 private:
  TouchUiState SetTouchUiState(TouchUiState touch_ui_state);

  bool tablet_mode_ = false;
  TouchUiState touch_ui_state_;

#if defined(OS_WIN)
  std::unique_ptr<gfx::SingletonHwndObserver> singleton_hwnd_observer_;
#endif

  CallbackList callback_list_;
};

}  // namespace ui

#endif  // UI_BASE_MATERIAL_DESIGN_MATERIAL_DESIGN_CONTROLLER_H_
