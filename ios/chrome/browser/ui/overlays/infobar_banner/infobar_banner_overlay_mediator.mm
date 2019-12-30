// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/overlays/infobar_banner/infobar_banner_overlay_mediator.h"

#import <UIKit/UIKit.h>

#include "base/logging.h"
#import "base/strings/sys_string_conversions.h"
#import "ios/chrome/browser/overlays/public/common/infobars/infobar_banner_overlay_request_config.h"
#import "ios/chrome/browser/overlays/public/common/infobars/infobar_overlay_request_config.h"
#include "ios/chrome/browser/overlays/public/infobar_banner/infobar_banner_overlay_responses.h"
#include "ios/chrome/browser/overlays/public/overlay_callback_manager.h"
#include "ios/chrome/browser/overlays/public/overlay_request.h"
#include "ios/chrome/browser/overlays/public/overlay_response.h"
#import "ios/chrome/browser/ui/infobars/banners/infobar_banner_consumer.h"
#import "ios/chrome/browser/ui/overlays/infobar_banner/infobar_banner_overlay_mediator+consumer_support.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

@interface InfobarBannerOverlayMediator ()
// The banner config from the request.
@property(nonatomic, readonly) InfobarBannerOverlayRequestConfig* config;
@end

@implementation InfobarBannerOverlayMediator

- (instancetype)initWithRequest:(OverlayRequest*)request {
  if (self = [super initWithRequest:request]) {
    DCHECK(request->GetConfig<InfobarBannerOverlayRequestConfig>());
  }
  return self;
}

#pragma mark - Accessors

- (void)setConsumer:(id<InfobarBannerConsumer>)consumer {
  if (_consumer == consumer)
    return;
  _consumer = consumer;
  if (_consumer)
    [self configureConsumer];
}

- (InfobarBannerOverlayRequestConfig*)config {
  return self.request
             ? self.request->GetConfig<InfobarBannerOverlayRequestConfig>()
             : nullptr;
}

#pragma mark - InfobarBannerDelegate

- (void)bannerInfobarButtonWasPressed:(UIButton*)sender {
  // Notify the model layer to perform the infobar's main action before
  // dismissing the banner.
  [self dispatchResponseAndStopOverlay:OverlayResponse::CreateWithInfo<
                                           InfobarBannerMainActionResponse>()];
}

- (void)dismissInfobarBannerForUserInteraction:(BOOL)userInitiated {
  if (self.request) {
    // Add a completion response notifying the requesting code of whether the
    // dismissal was user-initiated.  Provided to the request's completion
    // callbacks that are executed when the UI is finished being dismissed.
    self.request->GetCallbackManager()->SetCompletionResponse(
        OverlayResponse::CreateWithInfo<InfobarBannerCompletionResponse>(
            userInitiated));
  }
  [self.delegate stopOverlayForMediator:self];
}

- (void)presentInfobarModalFromBanner {
  // Notify the model layer to show the infobar modal before dismissing the
  // banner.
  [self dispatchResponseAndStopOverlay:OverlayResponse::CreateWithInfo<
                                           InfobarBannerShowModalResponse>()];
}

- (void)infobarBannerWasDismissed {
  // Only needed in legacy implementation.  Dismissal completion cleanup occurs
  // in InfobarBannerOverlayCoordinator.
}

#pragma mark - Private

// Dispatches |response| through the OverlayRequest, then stops the overlay UI.
- (void)dispatchResponseAndStopOverlay:
    (std::unique_ptr<OverlayResponse>)response {
  if (self.request)
    self.request->GetCallbackManager()->DispatchResponse(std::move(response));
  [self.delegate stopOverlayForMediator:self];
}

@end

@implementation InfobarBannerOverlayMediator (ConsumerSupport)

- (void)configureConsumer {
  // TODO(crbug.com/1030357): Add NOTREACHED() here to enforce that subclasses
  // implement.
  InfobarBannerOverlayRequestConfig* config = self.config;
  if (!config)
    return;
  [self.consumer
      setBannerAccessibilityLabel:config->banner_accessibility_label()];
  [self.consumer setButtonText:config->button_text()];
  [self.consumer setIconImage:[UIImage imageNamed:config->icon_image_name()]];
  [self.consumer setPresentsModal:config->presents_modal()];
  [self.consumer setTitleText:config->title_text()];
  [self.consumer setSubtitleText:config->subtitle_text()];
}

@end
