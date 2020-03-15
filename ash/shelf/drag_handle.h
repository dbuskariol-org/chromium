// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_SHELF_DRAG_HANDLE_H_
#define ASH_SHELF_DRAG_HANDLE_H_

#include "ash/ash_export.h"
#include "ash/shelf/contextual_nudge.h"
#include "ash/shelf/shelf_widget.h"
#include "base/memory/weak_ptr.h"
#include "base/timer/timer.h"
#include "ui/compositor/layer_animator.h"
#include "ui/views/view.h"
#include "ui/views/view_targeter_delegate.h"

namespace ash {

class ASH_EXPORT DragHandle : public views::View,
                              public views::ViewTargeterDelegate {
 public:
  explicit DragHandle(int drag_handle_corner_radius);
  DragHandle(const DragHandle&) = delete;
  ~DragHandle() override;

  DragHandle& operator=(const DragHandle&) = delete;

  void SetColorAndOpacity(SkColor color, float opacity);

  // views::ViewTargeterDelegate:
  bool DoesIntersectRect(const views::View* target,
                         const gfx::Rect& rect) const override;

  // Animates drag handle and tooltip for drag handle teaching users that
  // swiping up on will take the user back to the home screen.
  // Returns whether the nudge has been shown.
  bool ShowDragHandleNudge();

  // Schedule showing the drag handle.
  void ScheduleShowDragHandleNudge();

  // Immediately begins the animation to return the drag handle back to its
  // original position and hide the tooltip.
  // |hidden_by_tap| - Whether the nudge is being hidden as a result of the user
  // tapping the nudge.
  void HideDragHandleNudge(bool hidden_by_tap);

  // views::View:
  void OnGestureEvent(ui::GestureEvent* event) override;
  gfx::Rect GetAnchorBoundsInScreen() const override;

  bool ShowingNudge() { return showing_nudge_; }

  bool has_show_drag_handle_timer_for_testing() {
    return show_drag_handle_nudge_timer_.IsRunning();
  }

  void fire_show_drag_handle_timer_for_testing() {
    show_drag_handle_nudge_timer_.FireNow();
  }

  bool has_hide_drag_handle_timer_for_testing() {
    return hide_drag_handle_nudge_timer_.IsRunning();
  }

  ContextualNudge* drag_handle_nudge_for_testing() {
    return drag_handle_nudge_;
  }

 private:
  // Animates tooltip for drag handle gesture.
  void ShowDragHandleTooltip();

  // Helper function to hide the drag handle nudge. Called by
  // |hide_drag_handle_nudge_timer_|.
  void HideDragHandleNudgeHelper(bool hidden_by_tap);

  // Helper function to animate the drag handle for the drag handle gesture
  // contextual nudge.
  void AnimateDragHandleShow();

  // Animates translation of the drag handle by |vertical_offset| over
  // |animation_time| using |strategy|.
  void ScheduleDragHandleTranslationAnimation(
      int vertical_offset,
      base::TimeDelta animation_time,
      gfx::Tween::Type tween_type,
      ui::LayerAnimator::PreemptionStrategy strategy);

  // Handler for tap gesture on the contextual nudge widget. It hides the nudge.
  void HandleTapOnNudge();

  // Timer to hide drag handle nudge if it has a timed life.
  base::OneShotTimer hide_drag_handle_nudge_timer_;

  // Timer to animate the drag handle and show the nudge.
  base::OneShotTimer show_drag_handle_nudge_timer_;

  bool showing_nudge_ = false;

  // A label used to educate users about swipe gestures on the drag handle.
  ContextualNudge* drag_handle_nudge_ = nullptr;

  base::WeakPtrFactory<DragHandle> weak_factory_{this};
};

}  // namespace ash

#endif  // ASH_SHELF_DRAG_HANDLE_H_
