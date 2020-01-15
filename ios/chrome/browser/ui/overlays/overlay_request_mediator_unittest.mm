// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/overlays/overlay_request_mediator.h"
#import "ios/chrome/browser/ui/overlays/overlay_request_mediator+subclassing.h"

#import "base/bind.h"
#include "ios/chrome/browser/overlays/public/overlay_callback_manager.h"
#include "ios/chrome/browser/overlays/public/overlay_request.h"
#include "ios/chrome/browser/overlays/public/overlay_response.h"
#include "ios/chrome/browser/overlays/public/overlay_response_support.h"
#include "ios/chrome/browser/overlays/test/fake_overlay_user_data.h"
#include "ios/chrome/browser/overlays/test/overlay_test_macros.h"
#include "testing/platform_test.h"
#import "third_party/ocmock/OCMock/OCMock.h"
#import "third_party/ocmock/gtest_support.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {
// InfoType used to create dispatched respones in test.
DEFINE_TEST_OVERLAY_RESPONSE_INFO(DispatchInfo);
}

// Test fixture for OverlayRequestMediator.
using OverlayRequestMediatorTest = PlatformTest;

// Tests that the mediator's request is reset after destruction.
TEST_F(OverlayRequestMediatorTest, ResetRequestAfterDestruction) {
  std::unique_ptr<OverlayRequest> request =
      OverlayRequest::CreateWithConfig<FakeOverlayUserData>();
  OverlayRequestMediator* mediator =
      [[OverlayRequestMediator alloc] initWithRequest:request.get()];
  EXPECT_EQ(request.get(), mediator.request);
  request = nullptr;
  EXPECT_EQ(nullptr, mediator.request);
}

// Tests that |-dispatchResponse:| correctly dispatches the response.
TEST_F(OverlayRequestMediatorTest, DispatchResponse) {
  std::unique_ptr<OverlayRequest> request =
      OverlayRequest::CreateWithConfig<FakeOverlayUserData>();
  // Add a dispatch callback that sets |dispatch_callback_executed| to true
  // upon receiving an OverlayResponse created with DispatchInfo.
  __block bool dispatch_callback_executed = false;
  std::unique_ptr<OverlayResponse> dispatched_response =
      OverlayResponse::CreateWithInfo<DispatchInfo>();
  OverlayResponse* response_copy = dispatched_response.get();
  request->GetCallbackManager()->AddDispatchCallback(
      OverlayDispatchCallback(base::BindRepeating(^(OverlayResponse* response) {
                                dispatch_callback_executed = true;
                                EXPECT_EQ(response_copy, response);
                              }),
                              DispatchInfo::ResponseSupport()));
  OverlayRequestMediator* mediator =
      [[OverlayRequestMediator alloc] initWithRequest:request.get()];
  // Dispatch the response and verify |dipatch_callback_executed|.
  [mediator dispatchResponse:std::move(dispatched_response)];

  EXPECT_TRUE(dispatch_callback_executed);
}

// Tests that |-dismissOverlay| stops the overlay.
TEST_F(OverlayRequestMediatorTest, DismissOverlay) {
  std::unique_ptr<OverlayRequest> request =
      OverlayRequest::CreateWithConfig<FakeOverlayUserData>();
  OverlayRequestMediator* mediator =
      [[OverlayRequestMediator alloc] initWithRequest:request.get()];
  id<OverlayRequestMediatorDelegate> delegate =
      OCMStrictProtocolMock(@protocol(OverlayRequestMediatorDelegate));
  mediator.delegate = delegate;
  OCMExpect([delegate stopOverlayForMediator:mediator]);

  [mediator dismissOverlay];

  EXPECT_OCMOCK_VERIFY(delegate);
}
