// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_CHROMEOS_INPUT_METHOD_ASSISTIVE_WINDOW_PROPERTIES_H_
#define CHROME_BROWSER_CHROMEOS_INPUT_METHOD_ASSISTIVE_WINDOW_PROPERTIES_H_

#include <vector>
#include "base/strings/string16.h"
#include "chrome/browser/chromeos/input_method/ui/assistive_delegate.h"

namespace chromeos {
struct AssistiveWindowProperties {
  AssistiveWindowProperties();
  ~AssistiveWindowProperties();

  ui::ime::AssistiveWindowType type;
  bool visible;
  std::string announce_string;
  std::vector<base::string16> candidates;
};

}  // namespace chromeos

#endif  //  CHROME_BROWSER_CHROMEOS_INPUT_METHOD_ASSISTIVE_WINDOW_PROPERTIES_H_
