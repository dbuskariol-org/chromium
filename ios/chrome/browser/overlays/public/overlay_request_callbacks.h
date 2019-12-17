// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_REQUEST_CALLBACKS_H_
#define IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_REQUEST_CALLBACKS_H_

#include "base/callback.h"

class OverlayResponse;

// Completion callback for OverlayRequests.  If an overlay requires a completion
// block to be executed after its UI is dismissed, OverlayPresenter clients can
// provide a callback that uses the OverlayResponse provided to the request.
// |response| may be null if no response has been provided.
typedef base::OnceCallback<void(OverlayResponse* response)>
    OverlayCompletionCallback;

#endif  // IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_REQUEST_CALLBACKS_H_
