// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_CALLBACK_MANAGER_H_
#define IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_CALLBACK_MANAGER_H_

#include <memory>

#include "ios/chrome/browser/overlays/public/overlay_request_callbacks.h"
#include "ios/chrome/browser/overlays/public/overlay_user_data.h"

// Helper object owned by an OverlayRequest that is used to communicate overlay
// UI interaction information back to the overlay's requester.  Supports
// completion calllbacks, which are executed when the overlay is finished.
class OverlayCallbackManager {
 public:
  OverlayCallbackManager() = default;
  virtual ~OverlayCallbackManager() = default;

  // The completion response object for the request whose callbacks are being
  // managed by this object.  |response| is passed as the argument for
  // completion callbacks when the overlay UI is finished or the request is
  // cancelled.
  virtual void SetCompletionResponse(
      std::unique_ptr<OverlayResponse> response) = 0;
  virtual OverlayResponse* GetCompletionResponse() const = 0;

  // Adds a completion callback.  Provided callbacks are guaranteed to be
  // executed once with the completion response when the overlay UI is finished
  // or the request is cancelled.
  virtual void AddCompletionCallback(OverlayCompletionCallback callback) = 0;
};

#endif  // IOS_CHROME_BROWSER_OVERLAYS_PUBLIC_OVERLAY_CALLBACK_MANAGER_H_
