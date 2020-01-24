// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/prerender/isolated/isolated_prerender_features.h"

namespace features {

// Forces all isolated prerenders to be proxied through a CONNECT tunnel.
const base::Feature kIsolatedPrerenderUsesProxy{
    "IsolatedPrerenderUsesProxy", base::FEATURE_DISABLED_BY_DEFAULT};

}  // namespace features
