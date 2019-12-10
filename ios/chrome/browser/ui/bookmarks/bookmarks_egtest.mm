// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#include "base/ios/ios_util.h"
#include "base/strings/sys_string_conversions.h"
#import "base/test/ios/wait_util.h"
#include "components/prefs/pref_service.h"
#include "components/strings/grit/components_strings.h"
#include "ios/chrome/browser/browser_state/chrome_browser_state.h"
#include "ios/chrome/browser/pref_names.h"
#import "ios/chrome/browser/ui/authentication/signin_earl_grey_ui.h"
#import "ios/chrome/browser/ui/authentication/signin_earlgrey_utils.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_earl_grey_utils.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_path_cache.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_ui_constants.h"
#import "ios/chrome/browser/ui/popup_menu/popup_menu_constants.h"
#import "ios/chrome/browser/ui/table_view/feature_flags.h"
#include "ios/chrome/grit/ios_strings.h"
#import "ios/chrome/test/app/chrome_test_util.h"
#import "ios/chrome/test/earl_grey/accessibility_util.h"
#import "ios/chrome/test/earl_grey/chrome_earl_grey.h"
#import "ios/chrome/test/earl_grey/chrome_earl_grey_ui.h"
#import "ios/chrome/test/earl_grey/chrome_matchers.h"
#import "ios/chrome/test/earl_grey/chrome_test_case.h"
#import "ios/public/provider/chrome/browser/signin/fake_chrome_identity.h"
#import "ios/public/provider/chrome/browser/signin/fake_chrome_identity_service.h"
#import "ios/testing/earl_grey/earl_grey_test.h"
#import "ios/web/public/test/http_server/http_server.h"
#include "ui/base/l10n/l10n_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

using chrome_test_util::BookmarksMenuButton;
using chrome_test_util::ButtonWithAccessibilityLabel;
using chrome_test_util::ButtonWithAccessibilityLabelId;
using chrome_test_util::CancelButton;
using chrome_test_util::ContextMenuCopyButton;
using chrome_test_util::OmniboxText;
using chrome_test_util::PrimarySignInButton;
using chrome_test_util::SecondarySignInButton;

// Bookmark integration tests for Chrome.
@interface BookmarksTestCase : ChromeTestCase
@end

@implementation BookmarksTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];
  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksTestCase Tests

// Verifies that adding a bookmark and removing a bookmark via the UI properly
// updates the BookmarkModel.
- (void)testAddRemoveBookmark {
  const GURL bookmarkedURL = web::test::HttpServer::MakeUrl(
      "http://ios/testing/data/http_server_files/pony.html");
  std::string expectedURLContent = bookmarkedURL.GetContent();
  NSString* bookmarkTitle = @"my bookmark";

  [ChromeEarlGrey loadURL:bookmarkedURL];
  [[EarlGrey selectElementWithMatcher:OmniboxText(expectedURLContent)]
      assertWithMatcher:grey_notNil()];

  // Add the bookmark from the UI.
  [BookmarkEarlGreyUtils bookmarkCurrentTabWithTitle:bookmarkTitle];

  // Verify the bookmark is set.
  [BookmarkEarlGreyUtils assertBookmarksWithTitle:bookmarkTitle
                                    expectedCount:1];

  // Verify the star is lit.
  if (![ChromeEarlGrey isCompactWidth]) {
    [[EarlGrey
        selectElementWithMatcher:grey_accessibilityLabel(
                                     l10n_util::GetNSString(IDS_TOOLTIP_STAR))]
        assertWithMatcher:grey_notNil()];
  }

  // Open the BookmarkEditor.
  if ([ChromeEarlGrey isCompactWidth]) {
    [ChromeEarlGreyUI openToolsMenu];
    [[[EarlGrey
        selectElementWithMatcher:grey_allOf(grey_accessibilityID(
                                                kToolsMenuEditBookmark),
                                            grey_sufficientlyVisible(), nil)]
           usingSearchAction:grey_scrollInDirection(kGREYDirectionDown, 200)
        onElementWithMatcher:grey_accessibilityID(
                                 kPopupMenuToolsMenuTableViewId)]
        performAction:grey_tap()];
  } else {
    [[EarlGrey
        selectElementWithMatcher:grey_accessibilityLabel(
                                     l10n_util::GetNSString(IDS_TOOLTIP_STAR))]
        performAction:grey_tap()];
  }

  // Delete the Bookmark.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditDeleteButtonIdentifier)]
      performAction:grey_tap()];

  // Verify the bookmark is not in the BookmarkModel.
  [BookmarkEarlGreyUtils assertBookmarksWithTitle:bookmarkTitle
                                    expectedCount:0];

  // Verify the the page is no longer bookmarked.
  if ([ChromeEarlGrey isCompactWidth]) {
    [ChromeEarlGreyUI openToolsMenu];
    [[[EarlGrey
        selectElementWithMatcher:grey_allOf(grey_accessibilityID(
                                                kToolsMenuAddToBookmarks),
                                            grey_sufficientlyVisible(), nil)]
           usingSearchAction:grey_scrollInDirection(kGREYDirectionDown, 200)
        onElementWithMatcher:grey_accessibilityID(
                                 kPopupMenuToolsMenuTableViewId)]
        assertWithMatcher:grey_notNil()];
    // After veryfing, close the ToolsMenu by tapping on its button.
    [ChromeEarlGreyUI openToolsMenu];
  } else {
    [[EarlGrey
        selectElementWithMatcher:grey_accessibilityLabel(
                                     l10n_util::GetNSString(IDS_TOOLTIP_STAR))]
        assertWithMatcher:grey_notNil()];
  }
  // Close the opened tab.
  [chrome_test_util::BrowserCommandDispatcherForMainBVC() closeCurrentTab];
}

// Test to set bookmarks in multiple tabs.
- (void)testBookmarkMultipleTabs {
  const GURL firstURL = web::test::HttpServer::MakeUrl(
      "http://ios/testing/data/http_server_files/pony.html");
  const GURL secondURL = web::test::HttpServer::MakeUrl(
      "http://ios/testing/data/http_server_files/destination.html");
  [ChromeEarlGrey loadURL:firstURL];
  [ChromeEarlGrey openNewTab];
  [ChromeEarlGrey loadURL:secondURL];

  [BookmarkEarlGreyUtils bookmarkCurrentTabWithTitle:@"my bookmark"];
  [BookmarkEarlGreyUtils assertBookmarksWithTitle:@"my bookmark"
                                    expectedCount:1];
}

// Tests that changes to the parent folder from the Single Bookmark Editor
// are saved to the bookmark only when saving the results.
- (void)testMoveDoesSaveOnSave {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Edit through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_longPress()];
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      performAction:grey_tap()];

  // Tap the Folder button.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Create a new folder.
  [BookmarkEarlGreyUtils addFolderWithName:nil];

  // Verify that the editor is present.  Uses notNil() instead of
  // sufficientlyVisible() because the large title in the navigation bar causes
  // less than 75% of the table view to be visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Check that the new folder doesn't contain the bookmark.
  [BookmarkEarlGreyUtils assertChildCount:0 ofFolderWithName:@"New Folder"];

  // Tap the Done button.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditDoneButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];

  // Check that the new folder contains the bookmark.
  [BookmarkEarlGreyUtils assertChildCount:1 ofFolderWithName:@"New Folder"];

  // Close bookmarks
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Check that the new folder still contains the bookmark.
  [BookmarkEarlGreyUtils assertChildCount:1 ofFolderWithName:@"New Folder"];
}

// Tests that keyboard commands are registered when a bookmark is added as it
// shows only a snackbar.
- (void)testKeyboardCommandsRegistered_AddBookmark {
  // Add the bookmark.
  [BookmarkEarlGreyUtils starCurrentTab];
  GREYAssertTrue([ChromeEarlGrey registeredKeyCommandCount] > 0,
                 @"Some keyboard commands are registered.");
}

// Tests that keyboard commands are not registered when a bookmark is edited, as
// the edit screen is presented modally.
- (void)testKeyboardCommandsNotRegistered_EditBookmark {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Select single URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Edit the bookmark.
  if (![ChromeEarlGrey isCompactWidth]) {
    [[EarlGrey selectElementWithMatcher:StarButton()] performAction:grey_tap()];
  } else {
    [ChromeEarlGreyUI openToolsMenu];
    [[[EarlGrey
        selectElementWithMatcher:grey_allOf(grey_accessibilityID(
                                                kToolsMenuEditBookmark),
                                            grey_sufficientlyVisible(), nil)]
           usingSearchAction:grey_scrollInDirection(kGREYDirectionDown, 200)
        onElementWithMatcher:grey_accessibilityID(
                                 kPopupMenuToolsMenuTableViewId)]
        performAction:grey_tap()];
  }
  GREYAssertTrue([ChromeEarlGrey registeredKeyCommandCount] == 0,
                 @"No keyboard commands are registered.");
}

// Test that swiping left to right navigate back.
- (void)testNavigateBackWithGesture {
  // Disabled on iPad as there is not "navigate back" gesture.
  if ([ChromeEarlGrey isIPadIdiom]) {
    EARL_GREY_TEST_SKIPPED(@"Test not applicable for iPad");
  }

// TODO(crbug.com/768339): This test is faling on devices because
// grey_swipeFastInDirectionWithStartPoint does not work.
#if !TARGET_IPHONE_SIMULATOR
  EARL_GREY_TEST_DISABLED(@"Test disabled on devices.");
#endif

  if (@available(iOS 13, *)) {
    // Navigate back side swipe gesture does not work on iOS13 simulator. This
    // is not specific to Bookmarks. The issue is that the gesture needs to
    // start offscreen, and EG cannot replicate that.
    // TODO(crbug.com/978877): Fix the bug in EG and enable the test.
    EARL_GREY_TEST_DISABLED(@"Test disabled on iOS 13.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Make sure Mobile Bookmarks is not present. Also check the button Class to
  // avoid matching the "back" NavigationBar button.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Mobile Bookmarks"),
                                   grey_kindOfClassName(@"UITableViewCell"),
                                   nil)] assertWithMatcher:grey_nil()];

  // Open the first folder, to be able to go back twice on the bookmarks.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Back twice using swipe left gesture.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeTableViewIdentifier)]
      performAction:grey_swipeFastInDirectionWithStartPoint(kGREYDirectionRight,
                                                            0.01, 0.5)];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeTableViewIdentifier)]
      performAction:grey_swipeFastInDirectionWithStartPoint(kGREYDirectionRight,
                                                            0.01, 0.5)];

  // Check we navigated back to the Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Mobile Bookmarks")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Tests that the bookmark context bar is shown in MobileBookmarks.
- (void)testBookmarkContextBarShown {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify the context bar is shown.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Verify the context bar's leading and trailing buttons are shown.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeLeadingButtonIdentifier)]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      assertWithMatcher:grey_notNil()];
}

- (void)testBookmarkContextBarInSingleSelectionModes {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify the context bar is shown.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Verify context bar shows disabled "Delete" disabled "More" enabled
  // "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Select single URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Verify context bar shows enabled "Delete" enabled "More" enabled "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Unselect all.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Verify context bar shows disabled "Delete" disabled "More" enabled
  // "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Select single Folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Verify context bar shows enabled "Delete" enabled "More" enabled "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Unselect all.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Verify context bar shows disabled "Delete" disabled "More" enabled
  // "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Cancel edit mode
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

- (void)testBookmarkContextBarInMultipleSelectionModes {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify the context bar is shown.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Multi select URL and folders.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Verify context bar shows enabled "Delete" enabled "More" enabled "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Unselect Folder 1, so that Second URL is selected.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Verify context bar shows enabled "Delete" enabled "More" enabled
  // "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Unselect all, but one Folder - Folder 1 is selected.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  // Unselect URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Verify context bar shows enabled "Delete" enabled "More" enabled "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Unselect all.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Verify context bar shows disabled "Delete" disabled "More" enabled
  // "Cancel".
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarCancelString])]
      assertWithMatcher:grey_allOf(grey_notNil(), grey_enabled(), nil)];

  // Cancel edit mode
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

// Tests when total height of bookmarks exceeds screen height.
- (void)testBookmarksExceedsScreenHeight {
  [BookmarkEarlGreyUtils setupBookmarksWhichExceedsScreenHeight];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify bottom URL is not visible before scrolling to bottom (make sure
  // setupBookmarksWhichExceedsScreenHeight works as expected).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Bottom URL")]
      assertWithMatcher:grey_notVisible()];

  // Verify the top URL is visible (isn't covered by the navigation bar).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Top URL")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Test new folder could be created.  This verifies bookmarks scrolled to
  // bottom successfully for folder name editng.
  NSString* newFolderTitle = @"New Folder 1";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:YES];
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];
}

// TODO(crbug.com/801453): Folder name is not commited as expected in this test.
// Tests the new folder name is committed when "hide keyboard" button is
// pressed. (iPad specific)
- (void)DISABLED_testNewFolderNameCommittedWhenKeyboardDismissedOnIpad {
  // Tablet only (handset keyboard does not have "hide keyboard" button).
  if (![ChromeEarlGrey isIPadIdiom]) {
    EARL_GREY_TEST_SKIPPED(@"Test not supported on iPhone");
  }
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Create a new folder and type "New Folder 1" without pressing return.
  NSString* newFolderTitle = @"New Folder 1";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Tap on the "hide keyboard" button.
  id<GREYMatcher> hideKeyboard = grey_accessibilityLabel(@"Hide keyboard");
  [[EarlGrey selectElementWithMatcher:hideKeyboard] performAction:grey_tap()];

  // Tap on "New Folder 1".
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"New Folder 1")]
      performAction:grey_tap()];

  // Verify the empty background appears. (If "New Folder 1" is commited,
  // tapping on it will enter it and see a empty background.  Instead of
  // re-editing it (crbug.com/794155)).
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];
}

- (void)testEmptyBackgroundAndSelectButton {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Enter Folder 1.1 (which is empty)
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1.1")]
      performAction:grey_tap()];

  // Verify the empty background appears.
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];

  // Come back to Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Mobile Bookmarks")]
      performAction:grey_tap()];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select every URL and folder.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"First URL")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1.1")]
      performAction:grey_tap()];

  // Tap delete on context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify edit mode is close automatically (context bar switched back to
  // default state) and select button is disabled.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:NO
                                                        newFolderEnabled:YES];

  // Verify the empty background appears.
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];
}

- (void)testCachePositionIsRecreated {
  [BookmarkEarlGreyUtils setupBookmarksWhichExceedsScreenHeight];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Select Folder 1.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];

  // Verify Bottom 1 is not visible before scrolling to bottom (make sure
  // setupBookmarksWhichExceedsScreenHeight works as expected).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Bottom 1")]
      assertWithMatcher:grey_notVisible()];

  // Scroll to the bottom so that Bottom 1 is visible.
  [BookmarkEarlGreyUtils scrollToBottom];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Bottom 1")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Close bookmarks
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Ensure the Bottom 1 of Folder 1 is visible.  That means both folder and
  // scroll position are restored successfully.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityLabel(@"Bottom 1, 127.0.0.1")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Verify root node is opened when cache position is deleted.
- (void)testCachePositionIsResetWhenNodeIsDeleted {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Select Folder 1.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];

  // Select Folder 2.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 2")]
      performAction:grey_tap()];

  // Close bookmarks, it will store Folder 2 as the cache position.
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Delete Folder 2.
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 2"];

  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Ensure the root node is opened, by verifying Mobile Bookmarks is seen in a
  // table cell.
  [BookmarkEarlGreyUtils verifyBookmarkFolderIsSeen:@"Mobile Bookmarks"];
}

// Verify root node is opened when cache position is a permanent node and is
// empty.
- (void)testCachePositionIsResetWhenNodeIsPermanentAndEmpty {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Close bookmarks, it will store Mobile Bookmarks as the cache position.
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Delete all bookmarks and folders under Mobile Bookmarks.
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 1.1"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 1"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"French URL"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Second URL"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"First URL"];

  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Ensure the root node is opened, by verifying Mobile Bookmarks is seen in a
  // table cell.
  [BookmarkEarlGreyUtils verifyBookmarkFolderIsSeen:@"Mobile Bookmarks"];
}

- (void)testCachePositionIsRecreatedWhenNodeIsMoved {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Select Folder 1.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];

  // Select Folder 2.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 2")]
      performAction:grey_tap()];

  // Select Folder 3
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 3")]
      performAction:grey_tap()];

  // Close bookmarks
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Move Folder 3 under Folder 1.
  [BookmarkEarlGreyUtils moveBookmarkWithTitle:@"Folder 3"
                             toFolderWithTitle:@"Folder 1"];

  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Go back 1 level to Folder 1.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Folder 1")]
      performAction:grey_tap()];

  // Ensure we are at Folder 1, by verifying folders at this level.
  [BookmarkEarlGreyUtils verifyBookmarkFolderIsSeen:@"Folder 2"];
}

// Tests that chrome://bookmarks is disabled.
- (void)testBookmarksURLDisabled {
  const std::string kChromeBookmarksURL = "chrome://bookmarks";
  [ChromeEarlGrey loadURL:GURL(kChromeBookmarksURL)];

  // Verify chrome://bookmarks appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(kChromeBookmarksURL)]
      assertWithMatcher:grey_notNil()];

  // Verify that the resulting page is an error page.
  std::string errorMessage = net::ErrorToShortString(net::ERR_INVALID_URL);
  [ChromeEarlGrey waitForWebStateContainingText:errorMessage];
}

- (void)testSwipeDownToDismiss {
  if (!base::ios::IsRunningOnOrLater(13, 0, 0)) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iOS 12 and lower.");
  }
  if (!IsCollectionsCardPresentationStyleEnabled()) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on when feature flag is off.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // Check that the TableView is presented.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeTableViewIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Swipe TableView down.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeTableViewIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionDown)];

  // Check that the TableView has been dismissed.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeTableViewIdentifier)]
      assertWithMatcher:grey_nil()];
}

// TODO(crbug.com/695749): Add egtests for:
// 1. Spinner background.
// 2. Reorder bookmarks. (make sure it won't clear the row selection on table)
// 3. Test new folder name is committed when name editing is interrupted by
//    tapping context bar buttons.

@end

// Bookmark entries integration tests for Chrome.
@interface BookmarksEntriesTestCase : ChromeTestCase
@end

@implementation BookmarksEntriesTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];
  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksEntriesTestCase Tests

- (void)testUndoDeleteBookmarkFromSwipe {
  // TODO(crbug.com/851227): On Compact Width, the bookmark cell is being
  // deleted by grey_swipeFastInDirection.
  // grey_swipeFastInDirectionWithStartPoint doesn't work either and it might
  // fail on devices. Disabling this test under these conditions on the
  // meantime.
  if (![ChromeEarlGrey isCompactWidth]) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iPad.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Swipe action on the URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify context bar does not change when "Delete" shows up.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
  // Delete it.
  [[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      performAction:grey_tap()];

  // Wait until it's gone.
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Second URL"];

  // Press undo
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];

  // Verify it's back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_notNil()];

  // Verify context bar remains in default state.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

// TODO(crbug.com/781445): Re-enable this test on 32-bit.
#if defined(ARCH_CPU_64_BITS)
#define MAYBE_testSwipeToDeleteDisabledInEditMode \
  testSwipeToDeleteDisabledInEditMode
#else
#define MAYBE_testSwipeToDeleteDisabledInEditMode \
  FLAKY_testSwipeToDeleteDisabledInEditMode
#endif
- (void)testSwipeToDeleteDisabledInEditMode {
  // TODO(crbug.com/851227): On non Compact Width  the bookmark cell is being
  // deleted by grey_swipeFastInDirection.
  // grey_swipeFastInDirectionWithStartPoint doesn't work either and it might
  // fail on devices. Disabling this test under these conditions on the
  // meantime.
  if (![ChromeEarlGrey isCompactWidth]) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iPad on iOS11.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Swipe action on the URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify the delete confirmation button shows up.
  [[[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      inRoot:grey_kindOfClassName(@"UITableView")]
      assertWithMatcher:grey_notNil()];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Verify the delete confirmation button is gone after entering edit mode.
  [[[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      inRoot:grey_kindOfClassName(@"UITableView")]
      assertWithMatcher:grey_nil()];

  // Swipe action on "Second URL".  This should not bring out delete
  // confirmation button as swipe-to-delete is disabled in edit mode.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify the delete confirmation button doesn't appear.
  [[[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      inRoot:grey_kindOfClassName(@"UITableView")]
      assertWithMatcher:grey_nil()];

  // Cancel edit mode
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  // Swipe action on the URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify the delete confirmation button shows up. (swipe-to-delete is
  // re-enabled).
  [[[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      inRoot:grey_kindOfClassName(@"UITableView")]
      assertWithMatcher:grey_notNil()];
}

- (void)testContextMenuForSingleURLSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  [BookmarkEarlGreyUtils verifyContextMenuForSingleURL];
}

// Verify Edit Text functionality on single URL selection.
- (void)testEditTextOnSingleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // 1. Edit the bookmark title at edit page.

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      performAction:grey_tap()];

  // Modify the title.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
             modifyTextField:@"Title Field_textField"
                          to:@"n5"
                 dismissWith:kBookmarkEditNavigationBarDoneButtonIdentifier];

  // Verify that the bookmark was updated.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"n5")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Press undo and verify old URL is back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];

  // 2. Edit the bookmark url at edit page.

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_tap()];

  // Modify the url.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
             modifyTextField:@"URL Field_textField"
                          to:@"www.b.fr"
                 dismissWith:kBookmarkEditNavigationBarDoneButtonIdentifier];

  // Verify that the bookmark was updated.
  [BookmarkEarlGreyUtils assertExistenceOfBookmarkWithURL:@"http://www.b.fr/"
                                                     name:@"French URL"];

  // Press undo and verify the edit is undone.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];
  [BookmarkEarlGreyUtils assertAbsenceOfBookmarkWithURL:@"http://www.b.fr/"];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

// Verify Move functionality on single URL selection.
- (void)testMoveOnSingleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // 1. Move a single url at edit page.

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single url.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Move the "Second URL" to "Folder 1.1".
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
           setParentFolderTo:@"Folder 1.1"
                        from:@"Mobile Bookmarks"];

  // Verify edit mode remains.
  [BookmarkEarlGreyUtils verifyContextBarInEditMode];

  // Close edit mode.
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  // Navigate to "Folder 1.1" and verify "Second URL" is under it.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // 2. Test the cancel button at edit page.

  // Come back to the Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Mobile Bookmarks")]
      performAction:grey_tap()];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_tap()];

  // Tap cancel after modifying the url.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
             modifyTextField:@"URL Field_textField"
                          to:@"www.b.fr"
                 dismissWith:@"Cancel"];

  // Verify that the bookmark was not updated.
  [BookmarkEarlGreyUtils assertAbsenceOfBookmarkWithURL:@"http://www.b.fr/"];

  // Verify edit mode remains.
  [BookmarkEarlGreyUtils verifyContextBarInEditMode];
}

// Verify Copy URL functionality on single URL selection.
- (void)testCopyFunctionalityOnSingleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_tap()];

  // Invoke Copy through context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Select Copy URL.
  [[EarlGrey selectElementWithMatcher:ContextMenuCopyButton()]
      performAction:grey_tap()];

  // Verify general pasteboard has the URL copied.
  ConditionBlock condition = ^{
    return !![[UIPasteboard generalPasteboard].string
        containsString:@"www.a.fr"];
  };
  GREYAssert(base::test::ios::WaitUntilConditionOrTimeout(10, condition),
             @"Waiting for URL to be copied to pasteboard.");

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

- (void)testContextMenuForMultipleURLSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URLs.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Verify it shows the context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(@"bookmark_context_menu")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify options on context menu.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN)]
      assertWithMatcher:grey_sufficientlyVisible()];

  [[EarlGrey selectElementWithMatcher:
                 ButtonWithAccessibilityLabelId(
                     IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN_INCOGNITO)]
      assertWithMatcher:grey_sufficientlyVisible()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Verify the Open All functionality on multiple url selection.
// TODO(crbug.com/816699): Re-enable this test on simulators.
- (void)FLAKY_testContextMenuForMultipleURLOpenAll {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Open 3 normal tabs from a normal session.
  [BookmarkEarlGreyUtils selectUrlsAndTapOnContextBarButtonWithLabelId:
                             IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN];

  // Verify there are 3 normal tabs.
  [ChromeEarlGrey waitForMainTabCount:3];
  GREYAssertTrue([ChromeEarlGrey incognitoTabCount] == 0,
                 @"Incognito tab count should be 0");

  // Verify the order of open tabs.
  [BookmarkEarlGreyUtils verifyOrderOfTabsWithCurrentTabIndex:0];

  // Switch to Incognito mode by adding a new incognito tab.
  [ChromeEarlGrey openNewIncognitoTab];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open 3 normal tabs from a incognito session.
  [BookmarkEarlGreyUtils selectUrlsAndTapOnContextBarButtonWithLabelId:
                             IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN];

  // Verify there are 6 normal tabs and no new incognito tabs.
  [ChromeEarlGrey waitForMainTabCount:6];
  GREYAssertTrue([ChromeEarlGrey incognitoTabCount] == 1,
                 @"Incognito tab count should be 1");

  // Close the incognito tab to go back to normal mode.
  [ChromeEarlGrey closeAllIncognitoTabs];

  // The following verifies the selected bookmarks are open in the same order as
  // in folder.

  // Verify the order of open tabs.
  [BookmarkEarlGreyUtils verifyOrderOfTabsWithCurrentTabIndex:3];
}

// Verify the Open All in Incognito functionality on multiple url selection.
// TODO(crbug.com/816699): Re-enable this test on simulators.
- (void)FLAKY_testContextMenuForMultipleURLOpenAllInIncognito {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Open 3 incognito tabs from a normal session.
  [BookmarkEarlGreyUtils selectUrlsAndTapOnContextBarButtonWithLabelId:
                             IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN_INCOGNITO];

  // Verify there are 3 incognito tabs and no new normal tab.
  [ChromeEarlGrey waitForIncognitoTabCount:3];
  GREYAssertTrue([ChromeEarlGrey mainTabCount] == 1,
                 @"Main tab count should be 1");

  // Verify the current tab is an incognito tab.
  GREYAssertTrue([ChromeEarlGrey isIncognitoMode],
                 @"Failed to switch to incognito mode");

  // Verify the order of open tabs.
  [BookmarkEarlGreyUtils verifyOrderOfTabsWithCurrentTabIndex:0];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open 3 incognito tabs from a incognito session.
  [BookmarkEarlGreyUtils selectUrlsAndTapOnContextBarButtonWithLabelId:
                             IDS_IOS_BOOKMARK_CONTEXT_MENU_OPEN_INCOGNITO];

  // The 3rd tab will be re-used to open one of the selected bookmarks.  So
  // there will be 2 new tabs only.

  // Verify there are 5 incognito tabs and no new normal tab.
  [ChromeEarlGrey waitForIncognitoTabCount:5];
  GREYAssertTrue([ChromeEarlGrey mainTabCount] == 1,
                 @"Main tab count should be 1");

  // Verify the order of open tabs.
  [BookmarkEarlGreyUtils verifyOrderOfTabsWithCurrentTabIndex:2];
}

// Verify the Open and Open in Incognito functionality on single url.
- (void)testOpenSingleBookmarkInNormalAndIncognitoTab {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Open a bookmark in current tab in a normal session.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"First URL")]
      performAction:grey_tap()];

  // Verify "First URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetFirstUrl().GetContent())]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open a bookmark in new tab from a normal session (through a long press).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      performAction:grey_longPress()];
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_CONTENT_CONTEXT_OPENLINKNEWTAB)]
      performAction:grey_tap()];

  // Verify there is 1 new normal tab created and no new incognito tab created.
  [ChromeEarlGrey waitForMainTabCount:2];
  GREYAssertTrue([ChromeEarlGrey incognitoTabCount] == 0,
                 @"Incognito tab count should be 0");

  // Verify "Second URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetSecondUrl().GetContent())]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open a bookmark in an incognito tab from a normal session (through a long
  // press).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_longPress()];
  [[EarlGrey selectElementWithMatcher:
                 ButtonWithAccessibilityLabelId(
                     IDS_IOS_CONTENT_CONTEXT_OPENLINKNEWINCOGNITOTAB)]
      performAction:grey_tap()];

  // Verify there is 1 incognito tab created and no new normal tab created.
  [ChromeEarlGrey waitForIncognitoTabCount:1];
  GREYAssertTrue([ChromeEarlGrey mainTabCount] == 2,
                 @"Main tab count should be 2");

  // Verify the current tab is an incognito tab.
  GREYAssertTrue([ChromeEarlGrey isIncognitoMode],
                 @"Failed to switch to incognito mode");

  // Verify "French URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetFrenchUrl().GetContent())]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open a bookmark in current tab from a incognito session.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"First URL")]
      performAction:grey_tap()];

  // Verify "First URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetFirstUrl().GetContent())]
      assertWithMatcher:grey_notNil()];

  // Verify the current tab is an incognito tab.
  GREYAssertTrue([ChromeEarlGrey isIncognitoMode],
                 @"Failed to staying at incognito mode");

  // Verify no new tabs created.
  GREYAssertTrue([ChromeEarlGrey incognitoTabCount] == 1,
                 @"Incognito tab count should be 1");
  GREYAssertTrue([ChromeEarlGrey mainTabCount] == 2,
                 @"Main tab count should be 2");

  [BookmarkEarlGreyUtils openBookmarks];

  // Open a bookmark in new incognito tab from a incognito session (through a
  // long press).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      performAction:grey_longPress()];
  [[EarlGrey selectElementWithMatcher:
                 ButtonWithAccessibilityLabelId(
                     IDS_IOS_CONTENT_CONTEXT_OPENLINKNEWINCOGNITOTAB)]
      performAction:grey_tap()];

  // Verify a new incognito tab is created.
  [ChromeEarlGrey waitForIncognitoTabCount:2];
  GREYAssertTrue([ChromeEarlGrey mainTabCount] == 2,
                 @"Main tab count should be 2");

  // Verify the current tab is an incognito tab.
  GREYAssertTrue([ChromeEarlGrey isIncognitoMode],
                 @"Failed to staying at incognito mode");

  // Verify "Second URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetSecondUrl().GetContent())]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils openBookmarks];

  // Open a bookmark in a new normal tab from a incognito session (through a
  // long press).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_longPress()];
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_CONTENT_CONTEXT_OPENLINKNEWTAB)]
      performAction:grey_tap()];

  // Verify a new normal tab is created and no incognito tab is created.
  [ChromeEarlGrey waitForMainTabCount:3];
  GREYAssertTrue([ChromeEarlGrey incognitoTabCount] == 2,
                 @"Incognito tab count should be 2");

  // Verify the current tab is a normal tab.
  GREYAssertFalse([ChromeEarlGrey isIncognitoMode],
                  @"Failed to switch to normal mode");

  // Verify "French URL" appears in the omnibox.
  [[EarlGrey selectElementWithMatcher:OmniboxText(GetFrenchUrl().GetContent())]
      assertWithMatcher:grey_notNil()];
}

- (void)testContextMenuForMixedSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL and folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  [BookmarkEarlGreyUtils verifyContextMenuForMultiAndMixedSelection];
}

- (void)testLongPressOnSingleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_longPress()];

  // Verify context menu.
  [BookmarkEarlGreyUtils verifyContextMenuForSingleURL];
}

// Verify Move functionality on mixed folder / url selection.
- (void)testMoveFunctionalityOnMixedSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL and folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap on move, from context menu.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Verify folder picker appeared.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Delete the First URL programmatically in background.  Folder picker will
  // not close as the selected nodes "Second URL" and "Folder 1" still exist.
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"First URL"];

  // Choose to move into a new folder.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkCreateNewFolderCellIdentifier)]
      performAction:grey_tap()];

  // Enter custom new folder name.
  [BookmarkEarlGreyUtils
      renameBookmarkFolderWithFolderTitle:@"Title For New Folder"];

  // Verify current parent folder for "Title For New Folder" folder is "Mobile
  // Bookmarks" folder.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Mobile Bookmarks"),
                                   nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Tap Done to close bookmark move flow.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];

  // Verify new folder "Title For New Folder" has two bookmark nodes.
  [BookmarkEarlGreyUtils assertChildCount:2
                         ofFolderWithName:@"Title For New Folder"];

  // Drill down to where "Second URL" and "Folder 1" have been moved and assert
  // it's presence.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Title For New Folder")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Verify Move functionality on multiple url selection.
- (void)testMoveFunctionalityOnMultipleUrlSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL and folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap on move, from context menu.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Choose to move into Folder 1. Use grey_ancestor since
  // BookmarksHomeTableView might be visible on the background on non-compact
  // widthts, and there might be a "Folder1" node there as well.
  [[EarlGrey selectElementWithMatcher:
                 grey_allOf(TappableBookmarkNodeWithLabel(@"Folder 1"),
                            grey_ancestor(grey_accessibilityID(
                                kBookmarkFolderPickerViewContainerIdentifier)),
                            nil)] performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];

  // Verify Folder 1 has three bookmark nodes.
  [BookmarkEarlGreyUtils assertChildCount:3 ofFolderWithName:@"Folder 1"];

  // Drill down to where "Second URL" and "First URL" have been moved and assert
  // it's presence.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"First URL")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Verify Move is cancelled when all selected folder/url are deleted in
// background.
- (void)testMoveCancelledWhenAllSelectionDeleted {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL and folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap on move, from context menu.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Verify folder picker appeared.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Delete the selected URL and folder programmatically.
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 1"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Second URL"];

  // Verify folder picker is exited.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];
}

// Try deleting a bookmark from the edit screen, then undoing that delete.
- (void)testUndoDeleteBookmarkFromEditScreen {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select Folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap Edit Folder.
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Delete it.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditorDeleteButtonIdentifier)]
      performAction:grey_tap()];

  // Wait until it's gone.
  ConditionBlock condition = ^{
    NSError* error = nil;
    [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
        assertWithMatcher:grey_notVisible()
                    error:&error];
    return error == nil;
  };
  GREYAssert(base::test::ios::WaitUntilConditionOrTimeout(10, condition),
             @"Waiting for bookmark to go away");

  // Press undo
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];

  // Verify it's back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      assertWithMatcher:grey_notNil()];

  // Verify Delete is disabled (with visible Delete, it also means edit mode is
  // stayed).
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      assertWithMatcher:grey_allOf(grey_notNil(),
                                   grey_accessibilityTrait(
                                       UIAccessibilityTraitNotEnabled),
                                   nil)];
}

- (void)testDeleteSingleURLNode {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Delete it.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Wait until it's gone.
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Second URL"];

  // Press undo
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];

  // Verify it's back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_notNil()];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

- (void)testDeleteMultipleNodes {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select Folder and URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Delete it.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Wait until it's gone.
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Second URL"];
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Folder 1"];

  // Press undo
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];

  // Verify it's back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      assertWithMatcher:grey_notNil()];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

- (void)testSwipeDownToDismissFromPushedVC {
  if (!base::ios::IsRunningOnOrLater(13, 0, 0)) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iOS 12 and lower.");
  }
  if (!IsCollectionsCardPresentationStyleEnabled()) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on when feature flag is off.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Open Edit Bookmark through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      performAction:grey_tap()];

  // Tap on Folder to open folder picker.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Check that Change Folder is presented.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Swipe TableView down.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionDown)];

  // Check that the TableView has been dismissed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_nil()];

  // Open EditBookmark again to verify it was cleaned up successsfully.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      performAction:grey_tap()];

  // Swipe EditBookmark down.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionDown)];

  // Check that the EditBookmark has been dismissed.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_nil()];
}

@end

// Bookmark promo integration tests for Chrome.
@interface BookmarksPromoTestCase : ChromeTestCase
@end

@implementation BookmarksPromoTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];
  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksPromoTestCase Tests

// Tests that the promo view is only seen at root level and not in any of the
// child nodes.
- (void)testPromoViewIsSeenOnlyInRootNode {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // We are going to set the PromoAlreadySeen preference. Set a teardown handler
  // to reset it.
  [self setTearDownHandler:^{
    [BookmarkEarlGreyUtils setPromoAlreadySeen:NO];
  }];
  // Check that sign-in promo view is visible.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeColdState];

  // Go to child node.
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Wait until promo is gone.
  [SigninEarlGreyUI checkSigninPromoNotVisible];

  // Check that the promo already seen state is not updated.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];

  // Come back to root node, and the promo view should appear.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Bookmarks")]
      performAction:grey_tap()];

  // Check promo view is still visible.
  [[EarlGrey selectElementWithMatcher:PrimarySignInButton()]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Tests that tapping No thanks on the promo make it disappear.
- (void)testPromoNoThanksMakeItDisappear {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // We are going to set the PromoAlreadySeen preference. Set a teardown handler
  // to reset it.
  [self setTearDownHandler:^{
    [BookmarkEarlGreyUtils setPromoAlreadySeen:NO];
  }];
  // Check that sign-in promo view is visible.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeColdState];

  // Tap the dismiss button.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(kSigninPromoCloseButtonId)]
      performAction:grey_tap()];

  // Wait until promo is gone.
  [SigninEarlGreyUI checkSigninPromoNotVisible];

  // Check that the promo already seen state is updated.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:YES];
}

// Tests the tapping on the primary button of sign-in promo view in a cold
// state makes the sign-in sheet appear, and the promo still appears after
// dismissing the sheet.
- (void)testSignInPromoWithColdStateUsingPrimaryButton {
  [BookmarkEarlGreyUtils openBookmarks];

  // Check that sign-in promo view are visible.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeColdState];

  // Tap the primary button.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(PrimarySignInButton(),
                                          grey_sufficientlyVisible(), nil)]
      performAction:grey_tap()];
  // Cancel the sign-in operation.
  [[EarlGrey selectElementWithMatcher:
                 grey_buttonTitle([l10n_util::GetNSString(
                     IDS_IOS_ACCOUNT_CONSISTENCY_SETUP_SKIP_BUTTON)
                     uppercaseString])] performAction:grey_tap()];

  // Check that the bookmarks UI reappeared and the cell is still here.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeColdState];
}

// Tests the tapping on the primary button of sign-in promo view in a warm
// state makes the confirmaiton sheet appear, and the promo still appears after
// dismissing the sheet.
- (void)testSignInPromoWithWarmStateUsingPrimaryButton {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // Set up a fake identity.
  FakeChromeIdentity* identity = [SigninEarlGreyUtils fakeIdentity1];
  ios::FakeChromeIdentityService::GetInstanceFromChromeProvider()->AddIdentity(
      identity);

  // Check that promo is visible.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeWarmState];

  // Tap the primary button.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(PrimarySignInButton(),
                                          grey_sufficientlyVisible(), nil)]
      performAction:grey_tap()];

  // Cancel the sign-in operation.
  [[EarlGrey selectElementWithMatcher:
                 grey_buttonTitle([l10n_util::GetNSString(
                     IDS_IOS_ACCOUNT_CONSISTENCY_SETUP_SKIP_BUTTON)
                     uppercaseString])] performAction:grey_tap()];

  // Check that the bookmarks UI reappeared and the cell is still here.
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeWarmState];

  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
}

// Tests the tapping on the secondary button of sign-in promo view in a warm
// state makes the sign-in sheet appear, and the promo still appears after
// dismissing the sheet.
- (void)testSignInPromoWithWarmStateUsingSecondaryButton {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  // Set up a fake identity.
  FakeChromeIdentity* identity = [SigninEarlGreyUtils fakeIdentity1];
  ios::FakeChromeIdentityService::GetInstanceFromChromeProvider()->AddIdentity(
      identity);

  // Check that sign-in promo view are visible.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeWarmState];

  // Tap the secondary button.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(SecondarySignInButton(),
                                          grey_sufficientlyVisible(), nil)]
      performAction:grey_tap()];

  // Select the identity to dismiss the identity chooser.
  [SigninEarlGreyUI selectIdentityWithEmail:identity.userEmail];

  // Tap the CANCEL button.
  [[EarlGrey selectElementWithMatcher:
                 grey_buttonTitle([l10n_util::GetNSString(
                     IDS_IOS_ACCOUNT_CONSISTENCY_SETUP_SKIP_BUTTON)
                     uppercaseString])] performAction:grey_tap()];

  // Check that the bookmarks UI reappeared and the cell is still here.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeWarmState];
}

// Tests that the sign-in promo should not be shown after been shown 19 times.
- (void)testAutomaticSigninPromoDismiss {
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  PrefService* prefs = browser_state->GetPrefs();
  prefs->SetInteger(prefs::kIosBookmarkSigninPromoDisplayedCount, 19);
  [BookmarkEarlGreyUtils openBookmarks];
  // Check the sign-in promo view is visible.
  [SigninEarlGreyUI
      checkSigninPromoVisibleWithMode:SigninPromoViewModeColdState];
  // Check the sign-in promo already-seen state didn't change.
  [BookmarkEarlGreyUtils verifyPromoAlreadySeen:NO];
  GREYAssertEqual(
      20, prefs->GetInteger(prefs::kIosBookmarkSigninPromoDisplayedCount),
      @"Should have incremented the display count");
  // Close the bookmark view and open it again.
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];
  [BookmarkEarlGreyUtils openBookmarks];
  [[GREYUIThreadExecutor sharedInstance] drainUntilIdle];
  // Check that the sign-in promo is not visible anymore.
  [SigninEarlGreyUI checkSigninPromoNotVisible];
}

@end

// Bookmark accessibility tests for Chrome.
@interface BookmarksAccessibilityTestCase : ChromeTestCase
@end

@implementation BookmarksAccessibilityTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];

  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksAccessibilityTestCase Tests

// Tests that all elements on the bookmarks landing page are accessible.
- (void)testAccessibilityOnBookmarksLandingPage {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

// Tests that all elements on mobile bookmarks are accessible.
- (void)testAccessibilityOnMobileBookmarks {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

// Tests that all elements on the bookmarks folder Edit page are accessible.
- (void)testAccessibilityOnBookmarksFolderEditPage {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Edit through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_longPress()];

  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Verify that the editor is present.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];
  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

// Tests that all elements on the bookmarks Edit page are accessible.
- (void)testAccessibilityOnBookmarksEditPage {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Edit through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      performAction:grey_tap()];
  // TODO(crbug.com/976930): to support EG2 need to use [ChromeEarlGrey
  // verifyAccessibilityForCurrentScreen]
  chrome_test_util::VerifyAccessibilityForCurrentScreen();
}

// Tests that all elements on the bookmarks Move page are accessible.
- (void)testAccessibilityOnBookmarksMovePage {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Move through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // TODO(crbug.com/976930): to support EG2 need to use [ChromeEarlGrey
  // verifyAccessibilityForCurrentScreen]
  chrome_test_util::VerifyAccessibilityForCurrentScreen();
}

// Tests that all elements on the bookmarks Move to New Folder page are
// accessible.
- (void)testAccessibilityOnBookmarksMoveToNewFolderPage {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Move through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Tap on "Create New Folder."
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkCreateNewFolderCellIdentifier)]
      performAction:grey_tap()];

  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

// Tests that all elements on bookmarks Delete and Undo are accessible.
- (void)testAccessibilityOnBookmarksDeleteUndo {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];

  // Delete it.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Wait until it's gone.
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Second URL"];

  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

// Tests that all elements on the bookmarks Select page are accessible.
- (void)testAccessibilityOnBookmarksSelect {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  [ChromeEarlGrey verifyAccessibilityForCurrentScreen];
}

@end

// Bookmark folders integration tests for Chrome.
@interface BookmarksFoldersTestCase : ChromeTestCase
@end

@implementation BookmarksFoldersTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];
  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksTestFolders Tests

// Tests moving bookmarks into a new folder created in the moving process.
- (void)testCreateNewFolderWhileMovingBookmarks {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URLs.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap on Move.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Choose to move the bookmark into a new folder.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkCreateNewFolderCellIdentifier)]
      performAction:grey_tap()];

  // Enter custom new folder name.
  [BookmarkEarlGreyUtils
      renameBookmarkFolderWithFolderTitle:@"Title For New Folder"];

  // Verify current parent folder (Change Folder) is Bookmarks folder.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Mobile Bookmarks"),
                                   nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Choose new parent folder (Change Folder).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Verify folder picker UI is displayed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify Folder 2 only has one item.
  [BookmarkEarlGreyUtils assertChildCount:1 ofFolderWithName:@"Folder 2"];

  // Select Folder 2 as new Change Folder.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 2")]
      performAction:grey_tap()];

  // Verify folder picker is dismissed and folder creator is now visible.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderCreateViewContainerIdentifier)]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];

  // Verify picked parent folder (Change Folder) is Folder 2.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Folder 2"), nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Tap Done to close bookmark move flow.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderCreateViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];

  // Verify new folder has been created under Folder 2.
  [BookmarkEarlGreyUtils assertChildCount:2 ofFolderWithName:@"Folder 2"];

  // Verify new folder has two bookmarks.
  [BookmarkEarlGreyUtils assertChildCount:2
                         ofFolderWithName:@"Title For New Folder"];
}

- (void)testCantDeleteFolderBeingEdited {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Create a new folder and type "New Folder 1" without pressing return.
  NSString* newFolderTitle = @"New Folder";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Swipe action to try to delete the newly created folder while its name its
  // being edited.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"New Folder")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify the delete confirmation button doesn't show up.
  [[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      assertWithMatcher:grey_nil()];
}

- (void)testNavigateAwayFromFolderBeingEdited {
  [BookmarkEarlGreyUtils setupBookmarksWhichExceedsScreenHeight];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify bottom URL is not visible before scrolling to bottom (make sure
  // setupBookmarksWhichExceedsScreenHeight works as expected).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Bottom URL")]
      assertWithMatcher:grey_notVisible()];

  // Verify the top URL is visible (isn't covered by the navigation bar).
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Top URL")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Test new folder could be created.  This verifies bookmarks scrolled to
  // bottom successfully for folder name editng.
  NSString* newFolderTitle = @"New Folder";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Scroll to top to navigate away from the folder being created.
  [BookmarkEarlGreyUtils scrollToTop];

  // Scroll back to the Folder being created.
  [BookmarkEarlGreyUtils scrollToBottom];

  // Folder should still be in Edit mode, because of this match for Value.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityValue(@"New Folder")]
      assertWithMatcher:grey_notNil()];
}

- (void)testDeleteSingleFolderNode {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single URL.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Delete it.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Wait until it's gone.
  [BookmarkEarlGreyUtils waitForDeletionOfBookmarkWithTitle:@"Folder 1"];

  // Press undo
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Undo")]
      performAction:grey_tap()];

  // Verify it's back.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      assertWithMatcher:grey_notNil()];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

- (void)testSwipeDownToDismissFromEditFolder {
  if (!base::ios::IsRunningOnOrLater(13, 0, 0)) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iOS 12 and lower.");
  }
  if (!IsCollectionsCardPresentationStyleEnabled()) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on when feature flag is off.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Move through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Check that the TableView is presented.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Swipe TableView down.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionDown)];

  // Check that the TableView has been dismissed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_nil()];
}

// Test when current navigating folder is deleted in background, empty
// background should be shown with context bar buttons disabled.
- (void)testWhenCurrentFolderDeletedInBackground {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Enter Folder 1
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];

  // Enter Folder 2
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 2")]
      performAction:grey_tap()];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Delete the Folder 1 and Folder 2 programmatically in background.
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 2"];
  [BookmarkEarlGreyUtils removeBookmarkWithTitle:@"Folder 1"];

  // Verify edit mode is close automatically (context bar switched back to
  // default state) and both select and new folder button are disabled.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:NO
                                                        newFolderEnabled:NO];

  // Verify the empty background appears.
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];

  // Come back to Folder 1 (which is also deleted).
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Folder 1")]
      performAction:grey_tap()];

  // Verify both select and new folder button are disabled.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:NO
                                                        newFolderEnabled:NO];

  // Verify the empty background appears.
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];

  // Come back to Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Mobile Bookmarks")]
      performAction:grey_tap()];

  // Ensure Folder 1.1 is seen, that means it successfully comes back to Mobile
  // Bookmarks.
  [BookmarkEarlGreyUtils verifyBookmarkFolderIsSeen:@"Folder 1.1"];
}

- (void)testLongPressOnSingleFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_longPress()];

  // Verify it shows the context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(@"bookmark_context_menu")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify options on context menu.
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      assertWithMatcher:grey_sufficientlyVisible()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Dismiss the context menu. On non compact width tap the Bookmarks TableView
  // to dismiss, since there might not be a cancel button.
  if ([ChromeEarlGrey isCompactWidth]) {
    [[EarlGrey
        selectElementWithMatcher:ButtonWithAccessibilityLabelId(IDS_CANCEL)]
        performAction:grey_tap()];
  } else {
    [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                            kBookmarkHomeTableViewIdentifier)]
        performAction:grey_tap()];
  }

  // Come back to the root.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Bookmarks")]
      performAction:grey_tap()];

  // Long press on Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Mobile Bookmarks")]
      performAction:grey_longPress()];

  // Verify it doesn't show the context menu. (long press is disabled on
  // permanent node.)
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(@"bookmark_context_menu")]
      assertWithMatcher:grey_nil()];
}

// Verify Edit functionality for single folder selection.
- (void)testEditFunctionalityOnSingleFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // 1. Edit the folder title at edit page.

  // Invoke Edit through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_longPress()];

  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Verify that the editor is present.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];
  NSString* existingFolderTitle = @"Folder 1";
  NSString* newFolderTitle = @"New Folder Title";
  [BookmarkEarlGreyUtils renameBookmarkFolderWithFolderTitle:newFolderTitle];

  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify that the change has been made.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(existingFolderTitle)]
      assertWithMatcher:grey_nil()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      assertWithMatcher:grey_notNil()];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];

  // 2. Move a single folder at edit page.

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(newFolderTitle)]
      performAction:grey_tap()];

  // Move the "New Folder Title" to "Folder 1.1".
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER
                  openEditor:kBookmarkFolderEditViewContainerIdentifier
           setParentFolderTo:@"Folder 1.1"
                        from:@"Mobile Bookmarks"];

  // Verify edit mode remains.
  [BookmarkEarlGreyUtils verifyContextBarInEditMode];

  // Close edit mode.
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  // Navigate to "Folder 1.1" and verify "New Folder Title" is under it.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // 3. Test the cancel button at edit page.

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select single folder.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      performAction:grey_tap()];

  // Tap cancel after modifying the title.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER
                  openEditor:kBookmarkFolderEditViewContainerIdentifier
             modifyTextField:@"Title_textField"
                          to:@"Dummy"
                 dismissWith:@"Cancel"];

  // Verify that the bookmark was not updated.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify edit mode is stayed.
  [BookmarkEarlGreyUtils verifyContextBarInEditMode];

  // 4. Test the delete button at edit page.

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Verify that the editor is present.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditorDeleteButtonIdentifier)]
      performAction:grey_tap()];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify that the folder is deleted.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      assertWithMatcher:grey_notVisible()];

  // 5. Verify that when adding a new folder, edit mode will not mistakenly come
  // back (crbug.com/781783).

  // Create a new folder.
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:YES];

  // Tap on the new folder.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(newFolderTitle)]
      performAction:grey_tap()];

  // Verify we enter the new folder. (instead of selecting it in edit mode).
  [BookmarkEarlGreyUtils verifyEmptyBackgroundAppears];
}

// Verify Move functionality on single folder through long press.
- (void)testMoveFunctionalityOnSingleFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Move through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_longPress()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Choose to move the bookmark folder - "Folder 1" into a new folder.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkCreateNewFolderCellIdentifier)]
      performAction:grey_tap()];

  // Enter custom new folder name.
  [BookmarkEarlGreyUtils
      renameBookmarkFolderWithFolderTitle:@"Title For New Folder"];

  // Verify current parent folder for "Title For New Folder" folder is "Mobile
  // Bookmarks" folder.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Mobile Bookmarks"),
                                   nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Choose new parent folder for "Title For New Folder" folder.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Verify folder picker UI is displayed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify Folder 2 only has one item.
  [BookmarkEarlGreyUtils assertChildCount:1 ofFolderWithName:@"Folder 2"];

  // Select Folder 2 as new parent folder for "Title For New Folder".
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 2")]
      performAction:grey_tap()];

  // Verify folder picker is dismissed and folder creator is now visible.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderCreateViewContainerIdentifier)]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kBookmarkFolderPickerViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];

  // Verify picked parent folder is Folder 2.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Folder 2"), nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Tap Done to close bookmark move flow.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];

  // Verify new folder "Title For New Folder" has been created under Folder 2.
  [BookmarkEarlGreyUtils assertChildCount:2 ofFolderWithName:@"Folder 2"];

  // Verify new folder "Title For New Folder" has one bookmark folder.
  [BookmarkEarlGreyUtils assertChildCount:1
                         ofFolderWithName:@"Title For New Folder"];

  // Drill down to where "Folder 1.1" has been moved and assert it's presence.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 2")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Title For New Folder")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1.1")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Verify Move functionality on multiple folder selection.
- (void)testMoveFunctionalityOnMultipleFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select multiple folders.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Choose to move into a new folder. By tapping on the New Folder Cell.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkCreateNewFolderCellIdentifier)]
      performAction:grey_tap()];

  // Enter custom new folder name.
  [BookmarkEarlGreyUtils
      renameBookmarkFolderWithFolderTitle:@"Title For New Folder"];

  // Verify current parent folder for "Title For New Folder" folder is "Mobile
  // Bookmarks" folder.
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(
                                   grey_accessibilityID(@"Change Folder"),
                                   grey_accessibilityLabel(@"Mobile Bookmarks"),
                                   nil)]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Tap Done to close bookmark move flow.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];

  // Verify new folder "Title For New Folder" has two bookmark folder.
  [BookmarkEarlGreyUtils assertChildCount:2
                         ofFolderWithName:@"Title For New Folder"];

  // Drill down to where "Folder 1.1" and "Folder 1" have been moved and assert
  // it's presence.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Title For New Folder")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1.1")]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

- (void)testContextBarForSingleFolderSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select Folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap Edit Folder.
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Verify it shows edit view controller.  Uses notNil() instead of
  // sufficientlyVisible() because the large title in the navigation bar causes
  // less than 75% of the table view to be visible.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];
}

- (void)testContextMenuForMultipleFolderSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select Folders.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  [BookmarkEarlGreyUtils verifyContextMenuForMultiAndMixedSelection];
}

// Tests that the default folder bookmarks are saved in is updated to the last
// used folder.
- (void)testStickyDefaultFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Invoke Edit through long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_longPress()];
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      performAction:grey_tap()];

  // Tap the Folder button.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Create a new folder.
  [BookmarkEarlGreyUtils addFolderWithName:@"Sticky Folder"];

  // Verify that the editor is present.  Uses notNil() instead of
  // sufficientlyVisible() because the large title in the navigation bar causes
  // less than 75% of the table view to be visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Tap the Done button.
  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditDoneButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notVisible()];

  // Close bookmarks
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];

  // Second, bookmark a page.

  // Verify that the bookmark that is going to be added is not in the
  // BookmarkModel.
  const GURL bookmarkedURL = web::test::HttpServer::MakeUrl(
      "http://ios/testing/data/http_server_files/fullscreen.html");
  NSString* const bookmarkedURLString =
      base::SysUTF8ToNSString(bookmarkedURL.spec());
  [BookmarkEarlGreyUtils assertBookmarksWithTitle:bookmarkedURLString
                                    expectedCount:0];
  // Open the page.
  std::string expectedURLContent = bookmarkedURL.GetContent();
  [ChromeEarlGrey loadURL:bookmarkedURL];
  [[EarlGrey selectElementWithMatcher:OmniboxText(expectedURLContent)]
      assertWithMatcher:grey_notNil()];

  // Verify that the folder has only one element.
  [BookmarkEarlGreyUtils assertChildCount:1 ofFolderWithName:@"Sticky Folder"];

  // Bookmark the page.
  [BookmarkEarlGreyUtils starCurrentTab];

  // Verify the snackbar title.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityLabel(
                                          @"Bookmarked to Sticky Folder")]
      assertWithMatcher:grey_sufficientlyVisible()];

  // Verify that the newly-created bookmark is in the BookmarkModel.
  [BookmarkEarlGreyUtils assertBookmarksWithTitle:bookmarkedURLString
                                    expectedCount:1];

  // Verify that the folder has now two elements.
  [BookmarkEarlGreyUtils assertChildCount:2 ofFolderWithName:@"Sticky Folder"];
}

// Tests the new folder name is committed when name editing is interrupted by
// navigating away.
- (void)testNewFolderNameCommittedOnNavigatingAway {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Create a new folder and type "New Folder 1" without pressing return.
  NSString* newFolderTitle = @"New Folder 1";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Interrupt the folder name editing by tapping on back.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Bookmarks")]
      performAction:grey_tap()];

  // Come back to Mobile Bookmarks.
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify folder name "New Folder 1" was committed.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];

  // Create a new folder and type "New Folder 2" without pressing return.
  newFolderTitle = @"New Folder 2";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Interrupt the folder name editing by tapping on done.
  [[EarlGrey selectElementWithMatcher:BookmarkHomeDoneButton()]
      performAction:grey_tap()];
  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Verify folder name "New Folder 2" was committed.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];

  // Create a new folder and type "New Folder 3" without pressing return.
  newFolderTitle = @"New Folder 3";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Interrupt the folder name editing by entering Folder 1
  [BookmarkEarlGreyUtils scrollToTop];

  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Folder 1")]
      performAction:grey_tap()];
  // Come back to Mobile Bookmarks.
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Mobile Bookmarks")]
      performAction:grey_tap()];

  // Verify folder name "New Folder 3" was committed.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];

  // Create a new folder and type "New Folder 4" without pressing return.
  newFolderTitle = @"New Folder 4";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:NO];

  // Interrupt the folder name editing by tapping on First URL.
  [BookmarkEarlGreyUtils scrollToTop];

  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"French URL")]
      performAction:grey_tap()];
  // Reopen bookmarks.
  [BookmarkEarlGreyUtils openBookmarks];

  // Verify folder name "New Folder 4" was committed.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];
}

// Tests the creation of new folders by tapping on 'New Folder' button of the
// context bar.
- (void)testCreateNewFolderWithContextBar {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Create a new folder and name it "New Folder 1".
  NSString* newFolderTitle = @"New Folder 1";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:YES];

  // Verify "New Folder 1" is created.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];

  // Create a new folder and name it "New Folder 2".
  newFolderTitle = @"New Folder 2";
  [BookmarkEarlGreyUtils createNewBookmarkFolderWithFolderTitle:newFolderTitle
                                                    pressReturn:YES];

  // Verify "New Folder 2" is created.
  [BookmarkEarlGreyUtils verifyFolderCreatedWithTitle:newFolderTitle];

  // Verify context bar does not change after editing folder name.
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:YES];
}

// Test the creation of a bookmark and new folder (by tapping on the star).
- (void)testAddBookmarkInNewFolder {
  const GURL bookmarkedURL = web::test::HttpServer::MakeUrl(
      "http://ios/testing/data/http_server_files/pony.html");
  std::string expectedURLContent = bookmarkedURL.GetContent();

  [ChromeEarlGrey loadURL:bookmarkedURL];
  [[EarlGrey selectElementWithMatcher:OmniboxText(expectedURLContent)]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils starCurrentTab];

  // Verify the snackbar title.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityLabel(@"Bookmarked")]
      assertWithMatcher:grey_notNil()];

  // Tap on the snackbar.
  NSString* snackbarLabel =
      l10n_util::GetNSString(IDS_IOS_NAVIGATION_BAR_EDIT_BUTTON);
  [[EarlGrey selectElementWithMatcher:grey_accessibilityLabel(snackbarLabel)]
      performAction:grey_tap()];

  // Verify that the newly-created bookmark is in the BookmarkModel.
  [BookmarkEarlGreyUtils
      assertBookmarksWithTitle:base::SysUTF8ToNSString(expectedURLContent)
                 expectedCount:1];

  // Verify that the editor is present.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils assertFolderName:@"Mobile Bookmarks"];

  // Tap the Folder button.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Change Folder")]
      performAction:grey_tap()];

  // Create a new folder with default name.
  [BookmarkEarlGreyUtils addFolderWithName:nil];

  // Verify that the editor is present.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  [BookmarkEarlGreyUtils assertFolderExists:@"New Folder"];
}

@end

// Bookmark search integration tests for Chrome.
@interface BookmarksSearchTestCase : ChromeTestCase
@end

@implementation BookmarksSearchTestCase

- (void)setUp {
  [super setUp];

  [ChromeEarlGrey waitForBookmarksToFinishLoading];
  [ChromeEarlGrey clearBookmarks];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [ChromeEarlGrey clearBookmarks];
  // Clear position cache so that Bookmarks starts at the root folder in next
  // test.
  ios::ChromeBrowserState* browser_state =
      chrome_test_util::GetOriginalBrowserState();
  [BookmarkPathCache
      clearBookmarkTopMostRowCacheWithPrefService:browser_state->GetPrefs()];
}

#pragma mark - BookmarksSearchTestCase Tests

// Tests that the search bar is shown on root.
- (void)testSearchBarShownOnRoot {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // Verify the search bar is shown.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      assertWithMatcher:grey_allOf(grey_sufficientlyVisible(),
                                   grey_userInteractionEnabled(), nil)];
}

// Tests that the search bar is shown on mobile list.
- (void)testSearchBarShownOnMobileBookmarks {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify the search bar is shown.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      assertWithMatcher:grey_allOf(grey_sufficientlyVisible(),
                                   grey_userInteractionEnabled(), nil)];
}

// Tests the search.
- (void)testSearchResults {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify we have our 3 items.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_notNil()];

  // Search 'o'.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"o")];

  // Verify that folders are not filtered out.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      assertWithMatcher:grey_notNil()];

  // Search 'on'.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"n")];

  // Verify we are left only with the "First" and "Second" one.
  // 'on' matches 'pony.html' and 'Second'
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_nil()];
  // Verify that folders are not filtered out.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      assertWithMatcher:grey_nil()];

  // Search again for 'ony'.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"y")];

  // Verify we are left only with the "First" one for 'pony.html'.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_nil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_nil()];
}

// Tests that you get 'No Results' when no matching bookmarks are found.
- (void)testSearchWithNoResults {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search 'zz'.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"zz\n")];

  // Verify that we have a 'No Results' label somewhere.
  [[EarlGrey selectElementWithMatcher:grey_text(l10n_util::GetNSString(
                                          IDS_HISTORY_NO_SEARCH_RESULTS))]
      assertWithMatcher:grey_notNil()];

  // Verify that Edit button is disabled.
  [[EarlGrey selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarSelectString])]
      assertWithMatcher:grey_accessibilityTrait(
                            UIAccessibilityTraitNotEnabled)];
}

// Tests that scrim is shown while search box is enabled with no queries.
- (void)testSearchScrimShownWhenSearchBoxEnabled {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_tap()];

  // Verify that scrim is visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Searching.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"i")];

  // Verify that scrim is not visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_nil()];

  // Go back to original folder content.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_clearText()];

  // Verify that scrim is visible again.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Cancel.
  [[EarlGrey selectElementWithMatcher:CancelButton()] performAction:grey_tap()];

  // Verify that scrim is not visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_nil()];
}

// Tests that tapping scrim while search box is enabled dismisses the search
// controller.
- (void)testSearchTapOnScrimCancelsSearchController {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_tap()];

  // Tap on scrim.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      performAction:grey_tap()];

  // Verify that scrim is not visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_nil()];

  // Verifiy we went back to original folder content.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_notNil()];
}

// Tests that long press on scrim while search box is enabled dismisses the
// search controller.
- (void)testSearchLongPressOnScrimCancelsSearchController {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_tap()];

  // Try long press.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      performAction:grey_longPress()];

  // Verify context menu is not visible.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      assertWithMatcher:grey_nil()];

  // Verify that scrim is not visible.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeSearchScrimIdentifier)]
      assertWithMatcher:grey_nil()];

  // Verifiy we went back to original folder content.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_notNil()];
}

// Tests cancelling search restores the node's bookmarks.
- (void)testSearchCancelRestoresNodeBookmarks {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"X")];

  // Verify we have no items.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_nil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_nil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_nil()];

  // Cancel.
  [[EarlGrey selectElementWithMatcher:CancelButton()] performAction:grey_tap()];

  // Verify all items are back.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_notNil()];
}

// Tests that the navigation bar isn't shown when search is focused and empty.
- (void)testSearchHidesNavigationBar {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Focus Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_tap()];

  // Verify we have no navigation bar.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_nil()];

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First")];

  // Verify we now have a navigation bar.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_notNil()];
}

// Tests that you can long press and edit a bookmark and see edits when going
// back to search.
- (void)testSearchLongPressEditOnURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First")];

  // Invoke Edit through context menu.
  [BookmarkEarlGreyUtils
      tapOnLongPressContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                               onItem:TappableBookmarkNodeWithLabel(
                                          @"First URL")
                           openEditor:kBookmarkEditViewContainerIdentifier
                      modifyTextField:@"Title Field_textField"
                                   to:@"n6"
                          dismissWith:
                              kBookmarkEditNavigationBarDoneButtonIdentifier];

  // Should not find it anymore.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_nil()];

  // Search with new name.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_replaceText(@"n6")];

  // Should now find it again.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"n6")]
      assertWithMatcher:grey_notNil()];
}

// Tests that you can long press and edit a bookmark folder and see edits
// when going back to search.
- (void)testSearchLongPressEditOnFolder {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  NSString* existingFolderTitle = @"Folder 1.1";

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(existingFolderTitle)];

  // Invoke Edit through long press.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          existingFolderTitle)]
      performAction:grey_longPress()];

  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      performAction:grey_tap()];

  // Verify that the editor is present.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkFolderEditViewContainerIdentifier)]
      assertWithMatcher:grey_notNil()];

  NSString* newFolderTitle = @"n7";
  [BookmarkEarlGreyUtils renameBookmarkFolderWithFolderTitle:newFolderTitle];

  [[EarlGrey selectElementWithMatcher:BookmarksSaveEditFolderButton()]
      performAction:grey_tap()];

  // Verify that the change has been made.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          existingFolderTitle)]
      assertWithMatcher:grey_nil()];

  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_replaceText(newFolderTitle)];

  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(newFolderTitle)]
      assertWithMatcher:grey_notNil()];
}

// Tests that you can swipe URL items in search mode.
- (void)testSearchUrlCanBeSwipedToDelete {
  // TODO(crbug.com/851227): On non Compact Width, the bookmark cell is being
  // deleted by grey_swipeFastInDirection.
  // grey_swipeFastInDirectionWithStartPoint doesn't work either and it might
  // fail on devices. Disabling this test under these conditions on the
  // meantime.
  if (![ChromeEarlGrey isCompactWidth]) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iPad on iOS11.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First URL")];

  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify we have a delete button.
  [[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      assertWithMatcher:grey_notNil()];
}

// Tests that you can swipe folders in search mode.
- (void)testSearchFolderCanBeSwipedToDelete {
  // TODO(crbug.com/851227): On non Compact Width, the bookmark cell is being
  // deleted by grey_swipeFastInDirection.
  // grey_swipeFastInDirectionWithStartPoint doesn't work either and it might
  // fail on devices. Disabling this test under these conditions on the
  // meantime.
  if (![ChromeEarlGrey isCompactWidth]) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iPad on iOS11.");
  }

  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"Folder 1")];

  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];

  // Verify we have a delete button.
  [[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      assertWithMatcher:grey_notNil()];
}

// Tests that you can't search while in edit mode.
- (void)testDisablesSearchOnEditMode {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Verify search bar is enabled.
  [[EarlGrey selectElementWithMatcher:grey_kindOfClassName(@"UISearchBar")]
      assertWithMatcher:grey_userInteractionEnabled()];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Verify search bar is disabled.
  [[EarlGrey selectElementWithMatcher:grey_kindOfClassName(@"UISearchBar")]
      assertWithMatcher:grey_not(grey_userInteractionEnabled())];

  // Cancel edito mode.
  [BookmarkEarlGreyUtils closeContextBarEditMode];

  // Verify search bar is enabled.
  [[EarlGrey selectElementWithMatcher:grey_kindOfClassName(@"UISearchBar")]
      assertWithMatcher:grey_userInteractionEnabled()];
}

// Tests that new Folder is disabled when search results are shown.
- (void)testSearchDisablesNewFolderButtonOnNavigationBar {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search and hide keyboard.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First\n")];

  // Verify we now have a navigation bar.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_notNil()];

  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarNewFolderString])]
      assertWithMatcher:grey_accessibilityTrait(
                            UIAccessibilityTraitNotEnabled)];
}

// Tests that a single edit is possible when searching and selecting a single
// URL in edit mode.
- (void)testSearchEditModeEditOnSingleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search and hide keyboard.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First\n")];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityLabel(@"First URL, 127.0.0.1")]
      performAction:grey_tap()];

  // Invoke Edit through context menu.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
             modifyTextField:@"Title Field_textField"
                          to:@"n6"
                 dismissWith:kBookmarkEditNavigationBarDoneButtonIdentifier];

  // Should not find it anymore.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_nil()];

  // Search with new name.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_replaceText(@"n6")];

  // Should now find it again.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"n6")]
      assertWithMatcher:grey_notNil()];
}

// Tests that multiple deletes on search results works.
- (void)testSearchEditModeDeleteOnMultipleURL {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search and hide keyboard.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"URL\n")];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URLs.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityLabel(@"First URL, 127.0.0.1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityLabel(
                                          @"Second URL, 127.0.0.1")]
      performAction:grey_tap()];

  // Delete.
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarDeleteString])]
      performAction:grey_tap()];

  // Should not find them anymore.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_nil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      assertWithMatcher:grey_nil()];

  // Should find other two URLs.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Third URL")]
      assertWithMatcher:grey_notNil()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"French URL")]
      assertWithMatcher:grey_notNil()];
}

// Tests that multiple moves on search results works.
- (void)testMoveFunctionalityOnMultipleUrlSelection {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Search and hide keyboard.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"URL\n")];

  // Change to edit mode, using context menu.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL and folder.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Second URL")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      performAction:grey_tap()];

  // Tap context menu.
  [[EarlGrey selectElementWithMatcher:ContextBarCenterButtonWithLabel(
                                          [BookmarkEarlGreyUtils
                                              contextBarMoreString])]
      performAction:grey_tap()];

  // Tap on move, from context menu.
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_MOVE)]
      performAction:grey_tap()];

  // Choose to move into Folder 1. Use grey_ancestor since
  // BookmarksHomeTableView might be visible on the background on non-compact
  // widthts, and there might be a "Folder1" node there as well.
  [[EarlGrey selectElementWithMatcher:
                 grey_allOf(TappableBookmarkNodeWithLabel(@"Folder 1"),
                            grey_ancestor(grey_accessibilityID(
                                kBookmarkFolderPickerViewContainerIdentifier)),
                            nil)] performAction:grey_tap()];

  // Verify all folder flow UI is now closed.
  [BookmarkEarlGreyUtils verifyFolderFlowIsClosed];

  // Wait for Undo toast to go away from screen.
  [BookmarkEarlGreyUtils waitForUndoToastToGoAway];

  // Verify edit mode is closed (context bar back to default state).
  [BookmarkEarlGreyUtils verifyContextBarInDefaultStateWithSelectEnabled:YES
                                                        newFolderEnabled:NO];

  // Cancel search.
  [[EarlGrey selectElementWithMatcher:CancelButton()] performAction:grey_tap()];

  // Verify Folder 1 has three bookmark nodes.
  [BookmarkEarlGreyUtils assertChildCount:3 ofFolderWithName:@"Folder 1"];

  // Drill down to where "Second URL" and "First URL" have been moved and assert
  // it's presence.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"Second URL")]
      assertWithMatcher:grey_sufficientlyVisible()];
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(@"First URL")]
      assertWithMatcher:grey_sufficientlyVisible()];
}

// Tests that a search and single edit is possible when searching over root.
- (void)testSearchEditPossibleOnRoot {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];

  // Search and hide keyboard.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"First\n")];

  // Change to edit mode
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityID(
                                   kBookmarkHomeTrailingButtonIdentifier)]
      performAction:grey_tap()];

  // Select URL.
  [[EarlGrey
      selectElementWithMatcher:grey_accessibilityLabel(@"First URL, 127.0.0.1")]
      performAction:grey_tap()];

  // Invoke Edit through context menu.
  [BookmarkEarlGreyUtils
      tapOnContextMenuButton:IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT
                  openEditor:kBookmarkEditViewContainerIdentifier
             modifyTextField:@"Title Field_textField"
                          to:@"n6"
                 dismissWith:kBookmarkEditNavigationBarDoneButtonIdentifier];

  // Should not find it anymore.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"First URL")]
      assertWithMatcher:grey_nil()];

  // Search with new name.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_replaceText(@"n6")];

  // Should now find it again.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"n6")]
      assertWithMatcher:grey_notNil()];

  // Cancel search.
  [[EarlGrey selectElementWithMatcher:CancelButton()] performAction:grey_tap()];

  // Verify we have no navigation bar.
  [[EarlGrey selectElementWithMatcher:grey_accessibilityID(
                                          kBookmarkHomeUIToolbarIdentifier)]
      assertWithMatcher:grey_nil()];
}

// Tests that you can search folders.
- (void)testSearchFolders {
  [BookmarkEarlGreyUtils setupStandardBookmarks];
  [BookmarkEarlGreyUtils openBookmarks];
  [BookmarkEarlGreyUtils openMobileBookmarks];

  // Go down Folder 1 / Folder 2 / Folder 3.
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 2")]
      performAction:grey_tap()];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 3")]
      performAction:grey_tap()];

  // Search and go to folder 1.1.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"Folder 1.1")];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 1.1")]
      performAction:grey_tap()];

  // Go back and verify we are in MobileBooknarks. (i.e. not back to Folder 2)
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Mobile Bookmarks")]
      performAction:grey_tap()];

  // Search and go to Folder 2.
  [[EarlGrey selectElementWithMatcher:SearchIconButton()]
      performAction:grey_typeText(@"Folder 2")];
  [[EarlGrey
      selectElementWithMatcher:TappableBookmarkNodeWithLabel(@"Folder 2")]
      performAction:grey_tap()];

  // Go back and verify we are in Folder 1. (i.e. not back to Mobile Bookmarks)
  [[EarlGrey selectElementWithMatcher:NavigateBackButtonTo(@"Folder 1")]
      performAction:grey_tap()];
}

@end
