// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/overlays/infobar_modal/infobar_modal_overlay_mediator.h"

#import "base/bind.h"
#include "ios/chrome/browser/overlays/public/infobar_modal/infobar_modal_overlay_responses.h"
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
// Request ConfigType used for tests.
DEFINE_TEST_OVERLAY_REQUEST_CONFIG(ModalConfig);
}

// Mediator used in tests.
@interface FakeInfobarModalOverlayMediator : InfobarModalOverlayMediator
@end

@implementation FakeInfobarModalOverlayMediator
+ (const OverlayRequestSupport*)requestSupport {
  return ModalConfig::RequestSupport();
}
@end

// Test fixture for InfobarModalOverlayMediator.
class InfobarModalOverlayMediatorTest : public PlatformTest {
 public:
  InfobarModalOverlayMediatorTest()
      : request_(OverlayRequest::CreateWithConfig<ModalConfig>()),
        delegate_(
            OCMStrictProtocolMock(@protocol(OverlayRequestMediatorDelegate))),
        mediator_([[FakeInfobarModalOverlayMediator alloc]
            initWithRequest:request_.get()]) {
    mediator_.delegate = delegate_;
  }
  ~InfobarModalOverlayMediatorTest() override {
    EXPECT_OCMOCK_VERIFY(delegate_);
  }

 protected:
  std::unique_ptr<OverlayRequest> request_;
  id<OverlayRequestMediatorDelegate> delegate_ = nil;
  InfobarModalOverlayMediator* mediator_ = nil;
};

// Tests that |-dismissInfobarModal| triggers dismissal via the delegate.
TEST_F(InfobarModalOverlayMediatorTest, DismissInfobarModal) {
  OCMExpect([delegate_ stopOverlayForMediator:mediator_]);
  [mediator_ dismissInfobarModal:nil];
}

// Tests that |-modalInfobarButtonWasAccepted| dispatches a main action response
// then dismisses the modal.
TEST_F(InfobarModalOverlayMediatorTest, ModalInfobarButtonWasAccepted) {
  // Add a dispatch callback that resets |main_action_callback_executed| to true
  // upon receiving an OverlayResponse created with an
  // InfobarModalMainActionResponse.
  __block bool main_action_callback_executed = false;
  request_->GetCallbackManager()->AddDispatchCallback(OverlayDispatchCallback(
      base::BindRepeating(^(OverlayResponse* response) {
        main_action_callback_executed = true;
      }),
      InfobarModalMainActionResponse::ResponseSupport()));
  OCMExpect([delegate_ stopOverlayForMediator:mediator_]);
  [mediator_ modalInfobarButtonWasAccepted:nil];
  EXPECT_TRUE(main_action_callback_executed);
}
