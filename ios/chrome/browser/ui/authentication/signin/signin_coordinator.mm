// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ios/chrome/browser/ui/authentication/signin/signin_coordinator.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

using signin_metrics::AccessPoint;
using signin_metrics::PromoAction;

@implementation SigninCoordinator

+ (instancetype)
    userSigninCoordinatorWithBaseViewController:
        (UIViewController*)viewController
                                        browser:(Browser*)browser
                                       identity:(ChromeIdentity*)identity
                                    accessPoint:(AccessPoint)accessPoint
                                    promoAction:(PromoAction)promoAction {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

+ (instancetype)
    firstRunCoordinatorWithBaseViewController:(UIViewController*)viewController
                                      browser:(Browser*)browser
                                syncPresenter:(id<SyncPresenter>)syncPresenter {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

+ (instancetype)
    upgradeSigninPromoCoordinatorWithBaseViewController:
        (UIViewController*)viewController
                                                browser:(Browser*)browser {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

+ (instancetype)
    advancedSettingsSigninCoordinatorWithBaseViewController:
        (UIViewController*)viewController
                                                    browser:(Browser*)browser {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

+ (instancetype)addAccountCoordinatorWithBaseViewController:
                    (UIViewController*)viewController
                                                    browser:(Browser*)browser
                                                accessPoint:
                                                    (AccessPoint)accessPoint {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

+ (instancetype)
    reAuthenticationCoordinatorWithBaseViewController:
        (UIViewController*)viewController
                                              browser:(Browser*)browser

                                          accessPoint:(AccessPoint)accessPoint
                                          promoAction:(PromoAction)promoAction {
  // TODO(crbug.com/971989): Needs implementation.
  NOTIMPLEMENTED();
  return nil;
}

- (void)interruptWithAction:(SigninCoordinatorInterruptAction)action
                 completion:(ProceduralBlock)completion {
  // This method needs to be implemented in the subclass.
  NOTREACHED();
}

@end
