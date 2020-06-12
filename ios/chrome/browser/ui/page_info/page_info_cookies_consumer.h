// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_PAGE_INFO_PAGE_INFO_COOKIES_CONSUMER_H_
#define IOS_CHROME_BROWSER_UI_PAGE_INFO_PAGE_INFO_COOKIES_CONSUMER_H_

#import <Foundation/Foundation.h>

@class PageInfoCookiesDescription;

// Consumer for the page info Cookies.
@protocol PageInfoCookiesConsumer

// Called when Cookies option has changed.
- (void)cookiesOptionChangedToDescription:
    (PageInfoCookiesDescription*)description;

@end

#endif  // IOS_CHROME_BROWSER_UI_PAGE_INFO_PAGE_INFO_COOKIES_CONSUMER_H_
