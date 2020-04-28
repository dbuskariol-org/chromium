// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/safe_browsing/unsafe_resource_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

void RunUnsafeResourceCallback(
    const security_interstitials::UnsafeResource& resource,
    bool proceed,
    bool showed_interstitial) {
  DCHECK(resource.callback_thread);
  DCHECK(!resource.callback.is_null());
  resource.callback_thread->PostTask(
      FROM_HERE,
      base::BindOnce(resource.callback, proceed, showed_interstitial));
}
