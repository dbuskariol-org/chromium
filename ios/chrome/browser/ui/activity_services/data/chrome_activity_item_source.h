// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_ACTIVITY_SERVICES_DATA_CHROME_ACTIVITY_ITEM_SOURCE_H_
#define IOS_CHROME_BROWSER_UI_ACTIVITY_SERVICES_DATA_CHROME_ACTIVITY_ITEM_SOURCE_H_

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_item_thumbnail_generator.h"

// Base protocol for activity item sources in Chrome.
// TODO(crbug.com/1068606): Rename implementation classes to specify Chrome.
@protocol ChromeActivityItemSource <UIActivityItemSource>

// Property for the set of activity types that we want to be excluded from the
// activity view when this item source is part of the activity items.
@property(nonatomic, readonly) NSSet* excludedActivityTypes;

@end

// Returns an image to the UIActivities that can take advantage of it.
@interface UIActivityImageSource : NSObject <ChromeActivityItemSource>

// Default initializer. |image| must not be nil.
- (instancetype)initWithImage:(UIImage*)image;

@end

// This UIActivityItemSource-conforming object conforms to UTType public.url so
// it can be used with other Social Sharing Extensions as well. The |shareURL|
// is the URL shared with Social Sharing Extensions. The |subject| is used by
// Mail applications to pre-fill in the subject line. The |thumbnailGenerator|
// is used to provide thumbnails to extensions that request one.
@interface UIActivityURLSource : NSObject <ChromeActivityItemSource>

// Default initializer. |shareURL|, |subject|, and |thumbnailGenerator| must not
// be nil.
- (instancetype)initWithShareURL:(NSURL*)shareURL
                         subject:(NSString*)subject
              thumbnailGenerator:
                  (ChromeActivityItemThumbnailGenerator*)thumbnailGenerator;

@end

#endif  // IOS_CHROME_BROWSER_UI_ACTIVITY_SERVICES_DATA_CHROME_ACTIVITY_ITEM_SOURCE_H_
