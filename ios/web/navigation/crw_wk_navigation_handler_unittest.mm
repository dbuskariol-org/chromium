// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// #import "ios/web/web_state/ui/crw_web_controller.h"

#import <WebKit/WebKit.h>

#include <memory>
#include <utility>

#include "base/strings/sys_string_conversions.h"
#import "ios/web/navigation/navigation_manager_impl.h"
#import "ios/web/public/test/fakes/test_web_view_content_view.h"
#import "ios/web/public/test/web_test_with_web_state.h"
#import "ios/web/test/fakes/crw_fake_back_forward_list.h"
#import "ios/web/test/web_test_with_web_controller.h"
#import "ios/web/web_state/web_state_impl.h"
#import "testing/gtest_mac.h"
#include "third_party/ocmock/OCMock/OCMock.h"
#include "third_party/ocmock/gtest_support.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace web {
namespace {

const char kTestURLString[] = "http://www.google.com/";

}  // namespace

// Test fixture for testing CRWWebController. Stubs out web view.
class CRWWkNavigationHandlerTest : public WebTestWithWebController {
 protected:
  CRWWkNavigationHandlerTest() {}

  void SetUp() override {
    WebTestWithWebController::SetUp();

    fake_wk_list_ = [[CRWFakeBackForwardList alloc] init];
    mock_web_view_ = CreateMockWebView(fake_wk_list_);
    scroll_view_ = [[UIScrollView alloc] init];
    SetWebViewURL(@(kTestURLString));
    [[[mock_web_view_ stub] andReturn:scroll_view_] scrollView];

    TestWebViewContentView* web_view_content_view =
        [[TestWebViewContentView alloc] initWithMockWebView:mock_web_view_
                                                 scrollView:scroll_view_];
    [web_controller() injectWebViewContentView:web_view_content_view];
  }

  void TearDown() override {
    EXPECT_OCMOCK_VERIFY(mock_web_view_);
    WebTestWithWebController::TearDown();
  }

  // The value for web view OCMock objects to expect for |-setFrame:|.
  CGRect GetExpectedWebViewFrame() const {
    CGSize container_view_size =
        UIApplication.sharedApplication.keyWindow.bounds.size;
    container_view_size.height -=
        CGRectGetHeight([UIApplication sharedApplication].statusBarFrame);
    return {CGPointZero, container_view_size};
  }

  void SetWebViewURL(NSString* url_string) {
    test_url_ = [NSURL URLWithString:url_string];
  }

  // Creates WebView mock.
  UIView* CreateMockWebView(CRWFakeBackForwardList* wk_list) {
    WKWebView* result = [OCMockObject mockForClass:[WKWebView class]];

    OCMStub([result backForwardList]).andReturn(wk_list);
    // This uses |andDo| rather than |andReturn| since the URL it returns needs
    // to change when |test_url_| changes.
    OCMStub([result URL]).andDo(^(NSInvocation* invocation) {
      [invocation setReturnValue:&test_url_];
    });
    OCMStub(
        [result setNavigationDelegate:[OCMArg checkWithBlock:^(id delegate) {
                  navigation_delegate_ = delegate;
                  return YES;
                }]]);
    OCMStub([result serverTrust]);
    OCMStub([result setUIDelegate:OCMOCK_ANY]);
    OCMStub([result frame]).andReturn(UIScreen.mainScreen.bounds);
    OCMStub([result setCustomUserAgent:OCMOCK_ANY]);
    OCMStub([result customUserAgent]);
    OCMStub([static_cast<WKWebView*>(result) loadRequest:OCMOCK_ANY]);
    OCMStub([static_cast<WKWebView*>(result) loadFileURL:OCMOCK_ANY
                                 allowingReadAccessToURL:OCMOCK_ANY]);
    OCMStub([result setFrame:GetExpectedWebViewFrame()]);
    OCMStub([result addObserver:OCMOCK_ANY
                     forKeyPath:OCMOCK_ANY
                        options:0
                        context:nullptr]);
    OCMStub([result removeObserver:OCMOCK_ANY forKeyPath:OCMOCK_ANY]);
    OCMStub([result evaluateJavaScript:OCMOCK_ANY
                     completionHandler:OCMOCK_ANY]);
    OCMStub([result allowsBackForwardNavigationGestures]);
    OCMStub([result setAllowsBackForwardNavigationGestures:NO]);
    OCMStub([result setAllowsBackForwardNavigationGestures:YES]);
    OCMStub([result isLoading]);
    OCMStub([result stopLoading]);
    OCMStub([result removeFromSuperview]);
    OCMStub([result hasOnlySecureContent]).andReturn(YES);
    OCMStub([(WKWebView*)result title]).andReturn(@"Title");

    return result;
  }

  __weak id<WKNavigationDelegate> navigation_delegate_;
  UIScrollView* scroll_view_;
  id mock_web_view_;
  CRWFakeBackForwardList* fake_wk_list_;
  NSURL* test_url_;
};

// Tests several permutation of WKWebViewNavigation delegate calls, to make
// sure there's no crash and that the code behaves well when they are
// out of order.
TEST_F(CRWWkNavigationHandlerTest, FuzzyWKWebViewNavigation) {
  NSString* start = @"start";
  NSString* commit = @"commit";
  NSString* finish = @"finish";

  NSArray<NSArray<NSString*>*>* test_runs = @[
    @[ @"http://www.a.com/", start, finish ],
    @[ @"http://www.b.com/", start, commit, finish ],
    @[ @"http://www.c.com/", start, commit ],
    @[ @"http://www.d.com/", start, commit, finish ],
  ];

  // Call start/commit/finish steps in different orders, and adding to the
  // navigation stack.
  for (NSArray<NSString*>* test_run in test_runs) {
    NSString* url = test_run[0];

    NSObject* navigation = nil;

    for (NSUInteger j = 1; j < test_run.count; j++) {
      NSString* step = test_run[j];

      if (step == start) {
        SetWebViewURL(url);
        navigation = [[NSObject alloc] init];
        [navigation_delegate_ webView:mock_web_view_
            didStartProvisionalNavigation:static_cast<WKNavigation*>(
                                              navigation)];
      }

      if (step == commit) {
        [navigation_delegate_ webView:mock_web_view_
                  didCommitNavigation:static_cast<WKNavigation*>(navigation)];
      }

      if (step == finish) {
        [navigation_delegate_ webView:mock_web_view_
                  didFinishNavigation:static_cast<WKNavigation*>(navigation)];
      }
    }

    NavigationManager* nav_manager = web_state()->GetNavigationManager();
    NavigationItem* item = nav_manager->GetLastCommittedItem();
    EXPECT_TRUE(
        [url isEqualToString:base::SysUTF8ToNSString(item->GetURL().spec())])
        << "Failed test run for " << base::SysNSStringToUTF8(url);
  }
}

}  // namespace web
