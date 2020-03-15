// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_SHELF_CONTEXTUAL_TOOLTIP_H_
#define ASH_SHELF_CONTEXTUAL_TOOLTIP_H_

#include "ash/ash_export.h"
#include "base/time/clock.h"
#include "components/prefs/pref_registry_simple.h"
#include "components/prefs/pref_service.h"

namespace ash {

namespace contextual_tooltip {

// Enumeration of all contextual tooltips.
enum class TooltipType {
  kDragHandle,
  kBackGesture,
  kHomeToOverview,
};

// Maximum number of times a user can be shown a contextual nudge if the user
// hasn't performed the gesture |kSuccessLimit| times successfully.
constexpr int kNotificationLimit = 3;
constexpr int kSuccessLimit = 7;
constexpr int kSuccessLimitHomeToOverview = 3;

// Minimum time between showing contextual nudges to the user.
constexpr base::TimeDelta kMinInterval = base::TimeDelta::FromDays(1);

// The amount of time a nudge is shown.
constexpr base::TimeDelta kNudgeShowDuration = base::TimeDelta::FromSeconds(5);

// The minimum amount of time that has to pass since showing drag handle nudge
// before showing the back gesture nudge.
constexpr base::TimeDelta kMinIntervalBetweenBackAndDragHandleNudge =
    base::TimeDelta::FromMinutes(1);

// Registers profile prefs.
ASH_EXPORT void RegisterProfilePrefs(PrefRegistrySimple* registry);

// Returns true if the contextual tooltip of |type| should be shown for the user
// with the given |prefs|.
ASH_EXPORT bool ShouldShowNudge(PrefService* prefs, TooltipType type);

// Checks whether the tooltip should be hidden after a timeout. Returns the
// timeout if it should, returns base::TimeDelta() if not.
ASH_EXPORT base::TimeDelta GetNudgeTimeout(PrefService* prefs,
                                           TooltipType type);

// Returns the number of counts that |type| nudge has been shown for the user
// with the given |prefs|.
ASH_EXPORT int GetShownCount(PrefService* prefs, TooltipType type);

// Increments the counter tracking the number of times the tooltip has been
// shown. Updates the last shown time for the tooltip.
ASH_EXPORT void HandleNudgeShown(PrefService* prefs, TooltipType type);

// Increments the counter tracking the number of times the tooltip's
// correpsonding gesture has been performed successfully.
ASH_EXPORT void HandleGesturePerformed(PrefService* prefs, TooltipType type);

// Sets whether drag handle nudge should be prevented from showing because the
// shelf is in hidden state.
ASH_EXPORT void SetDragHandleNudgeDisabledForHiddenShelf(bool nudge_disabled);

// Sets whether the back gesture nudge is being shown (back gesture can be
// visible before HandleNudgeShown gets called).
ASH_EXPORT void SetBackGestureNudgeShowing(bool showing);

// Resets all user prefs related to contextual tooltips.
ASH_EXPORT void ClearPrefs();

ASH_EXPORT void OverrideClockForTesting(base::Clock* test_clock);

ASH_EXPORT void ClearClockOverrideForTesting();

}  // namespace contextual_tooltip

}  // namespace ash

#endif  // ASH_SHELF_CONTEXTUAL_TOOLTIP_H_
