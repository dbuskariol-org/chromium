// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/settings/privacy/privacy_coordinator.h"

#include "ios/chrome/browser/main/browser.h"
#import "ios/chrome/browser/ui/commands/command_dispatcher.h"
#import "ios/chrome/browser/ui/settings/privacy/privacy_table_view_controller.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

@interface PrivacyCoordinator () <
    PrivacyTableViewControllerPresentationDelegate>

@property(nonatomic, strong) PrivacyTableViewController* viewController;

@end

@implementation PrivacyCoordinator

@synthesize baseNavigationController = _baseNavigationController;

- (instancetype)initWithBaseNavigationController:
                    (UINavigationController*)navigationController
                                         browser:(Browser*)browser {
  if ([super initWithBaseViewController:navigationController browser:browser]) {
    _baseNavigationController = navigationController;
  }
  return self;
}

#pragma mark - ChromeCoordinator

- (void)start {
  self.viewController =
      [[PrivacyTableViewController alloc] initWithBrowser:self.browser];

  DCHECK(self.baseNavigationController);
  [self.baseNavigationController pushViewController:self.viewController
                                           animated:YES];
  self.viewController.presentationDelegate = self;
  self.viewController.dispatcher = static_cast<
      id<ApplicationCommands, BrowserCommands, BrowsingDataCommands>>(
      self.browser->GetCommandDispatcher());
}

- (void)stop {
  self.viewController = nil;
}

#pragma mark - PrivacyTableViewControllerPresentationDelegate

- (void)privacyTableViewControllerViewControllerWasRemoved:
    (PrivacyTableViewController*)controller {
  DCHECK_EQ(self.viewController, controller);
  [self.delegate privacyCoordinatorViewControllerWasRemoved:self];
}

@end
