// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_
#define IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_

#import <UIKit/UIKit.h>

#include "ios/chrome/app/application_delegate/startup_information.h"
#import "ios/chrome/app/application_delegate/tab_opening.h"
#import "ios/chrome/browser/procedural_block_types.h"
#import "ios/chrome/browser/url_loading/url_loading_params.h"

@class TabModel;

@protocol SceneControllerGuts

- (void)closeSettingsAnimated:(BOOL)animated
                   completion:(ProceduralBlock)completion;

- (void)dismissModalDialogsWithCompletion:(ProceduralBlock)completion
                           dismissOmnibox:(BOOL)dismissOmnibox;

- (void)openSelectedTabInMode:(ApplicationModeForTabOpening)tabOpeningTargetMode
            withUrlLoadParams:(const UrlLoadParams&)urlLoadParams
                   completion:(ProceduralBlock)completion;

- (void)openTabFromLaunchOptions:(NSDictionary*)launchOptions
              startupInformation:(id<StartupInformation>)startupInformation
                        appState:(AppState*)appState;

- (void)dismissModalsAndOpenSelectedTabInMode:
            (ApplicationModeForTabOpening)targetMode
                            withUrlLoadParams:
                                (const UrlLoadParams&)urlLoadParams
                               dismissOmnibox:(BOOL)dismissOmnibox
                                   completion:(ProceduralBlock)completion;

- (BOOL)shouldOpenNTPTabOnActivationOfTabModel:(TabModel*)tabModel;
@end

#endif  // IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_
