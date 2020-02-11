// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_UI_WEBUI_SETTINGS_CHROMEOS_SEARCH_SETTINGS_USER_ACTION_TRACKER_H_
#define CHROME_BROWSER_UI_WEBUI_SETTINGS_CHROMEOS_SEARCH_SETTINGS_USER_ACTION_TRACKER_H_

#include "base/time/time.h"

namespace chromeos {
namespace settings {

// Records user actions which measure the effort required to change a setting.
// This class is only meant to track actions from an individual settings
// session; if the settings window is closed and reopened again, a new instance
// should be created for that new session.
class SettingsUserActionTracker {
 public:
  SettingsUserActionTracker();
  SettingsUserActionTracker(const SettingsUserActionTracker& other) = delete;
  SettingsUserActionTracker& operator=(const SettingsUserActionTracker& other) =
      delete;
  ~SettingsUserActionTracker();

  void RecordPageFocus();
  void RecordPageBlur();
  void RecordClick();
  void RecordNavigation();
  void RecordSearch();
  void RecordSettingChange();

 private:
  void ResetMetricsCountersAndTimestamp();

  // Whether a setting has been changed since the window has been focused. Note
  // that if the user blurs the window, then refocuses it in less than a minute,
  // this value remains true; i.e., it flips back to false only when the user
  // has blurred the window for over a minute.
  bool has_changed_setting_ = false;

  // Time at which recording the current metric has started. If
  // |has_changed_setting_| is true, we're currently measuring the "subsequent
  // setting change" metric; otherwise, we're measuring the "first setting
  // change" metric.
  base::TimeTicks metric_start_time_;

  // Counters associated with the current metric.
  size_t num_clicks_since_start_time_ = 0u;
  size_t num_navigations_since_start_time_ = 0u;
  size_t num_searches_since_start_time_ = 0u;

  // The last time at which a page blur event was received; if no blur events
  // have been received, this field is_null().
  base::TimeTicks last_blur_timestamp_;
};

}  // namespace settings
}  // namespace chromeos

#endif  // CHROME_BROWSER_UI_WEBUI_SETTINGS_CHROMEOS_SEARCH_SETTINGS_USER_ACTION_TRACKER_H_
