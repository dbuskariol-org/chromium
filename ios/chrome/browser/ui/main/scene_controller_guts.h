// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_
#define IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_

#import <UIKit/UIKit.h>

#import "ios/chrome/app/application_delegate/tab_opening.h"
#import "ios/chrome/browser/procedural_block_types.h"
#import "ios/chrome/browser/url_loading/url_loading_params.h"

@protocol SceneControllerGuts

- (void)closeSettingsAnimated:(BOOL)animated
                   completion:(ProceduralBlock)completion;

- (void)dismissModalDialogsWithCompletion:(ProceduralBlock)completion
                           dismissOmnibox:(BOOL)dismissOmnibox;

- (void)openSelectedTabInMode:(ApplicationModeForTabOpening)tabOpeningTargetMode
            withUrlLoadParams:(const UrlLoadParams&)urlLoadParams
                   completion:(ProceduralBlock)completion;

@end

#endif  // IOS_CHROME_BROWSER_UI_MAIN_SCENE_CONTROLLER_GUTS_H_
