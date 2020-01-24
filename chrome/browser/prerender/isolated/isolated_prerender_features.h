// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_PRERENDER_ISOLATED_ISOLATED_PRERENDER_FEATURES_H_
#define CHROME_BROWSER_PRERENDER_ISOLATED_ISOLATED_PRERENDER_FEATURES_H_

#include "base/feature_list.h"

namespace features {

// TODO(robertogden): Migrate other IsolatedPrerender features here from
// chrome/common.

extern const base::Feature kIsolatedPrerenderUsesProxy;

}  // namespace features

#endif  // CHROME_BROWSER_PRERENDER_ISOLATED_ISOLATED_PRERENDER_FEATURES_H_
