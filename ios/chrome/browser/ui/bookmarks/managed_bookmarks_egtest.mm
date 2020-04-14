// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/sys_string_conversions.h"
#import "components/policy/core/common/policy_loader_ios_constants.h"
#include "components/policy/policy_constants.h"
#include "ios/chrome/browser/chrome_switches.h"
#import "ios/chrome/browser/policy/policy_app_interface.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_earl_grey.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_earl_grey_ui.h"
#import "ios/chrome/browser/ui/bookmarks/bookmark_ui_constants.h"
#include "ios/chrome/grit/ios_strings.h"
#import "ios/chrome/test/earl_grey/chrome_earl_grey.h"
#import "ios/chrome/test/earl_grey/chrome_matchers.h"
#import "ios/chrome/test/earl_grey/chrome_test_case.h"
#include "ios/testing/earl_grey/app_launch_configuration.h"
#import "ios/testing/earl_grey/earl_grey_test.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

using chrome_test_util::BookmarksDeleteSwipeButton;
using chrome_test_util::ButtonWithAccessibilityLabelId;
using chrome_test_util::ContextBarLeadingButtonWithLabel;
using chrome_test_util::ContextBarTrailingButtonWithLabel;
using chrome_test_util::TappableBookmarkNodeWithLabel;

namespace {

// Returns an AppLaunchConfiguration containing the given policy data.
// |policyData| must be in XML format.
AppLaunchConfiguration GenerateAppLaunchConfiguration(std::string policy_data) {
  AppLaunchConfiguration config;
  config.additional_args.push_back(std::string("--") +
                                   switches::kEnableEnterprisePolicy);
  config.additional_args.push_back(std::string("--") +
                                   switches::kInstallManagedBookmarksHandler);
  config.additional_args.push_back(
      std::string("--enable-features=ManagedBookmarksIOS"));

  // Remove whitespace from the policy data, because the XML parser does not
  // tolerate newlines.
  base::RemoveChars(policy_data, base::kWhitespaceASCII, &policy_data);

  // Commandline flags that start with a single "-" are automatically added to
  // the NSArgumentDomain in NSUserDefaults. Set fake policy data that can be
  // read by the production platform policy provider.
  config.additional_args.push_back(
      "-" + base::SysNSStringToUTF8(kPolicyLoaderIOSConfigurationKey));
  config.additional_args.push_back(policy_data);

  config.relaunch_policy = NoForceRelaunchAndResetState;
  return config;
}

void VerifyBookmarkContextBarNewFolderButtonDisabled() {
  [[EarlGrey selectElementWithMatcher:ContextBarLeadingButtonWithLabel(
                                          [BookmarkEarlGreyUI
                                              contextBarNewFolderString])]
      assertWithMatcher:grey_accessibilityTrait(
                            UIAccessibilityTraitNotEnabled)];
}

void VerifyBookmarkContextBarEditButtonDisabled() {
  [[EarlGrey
      selectElementWithMatcher:ContextBarTrailingButtonWithLabel(
                                   [BookmarkEarlGreyUI contextBarSelectString])]
      assertWithMatcher:grey_accessibilityTrait(
                            UIAccessibilityTraitNotEnabled)];
}

void LongPressBookmarkNodeWithLabel(NSString* bookmark_node_label) {
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          bookmark_node_label)]
      performAction:grey_longPress()];
}

void VerifyEditBookmarkContextMenuButtonNil() {
  [[EarlGrey selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                          IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT)]
      assertWithMatcher:grey_nil()];
}

void VerifyEditBookmarkFolderContextMenuButtonNil() {
  [[EarlGrey
      selectElementWithMatcher:ButtonWithAccessibilityLabelId(
                                   IDS_IOS_BOOKMARK_CONTEXT_MENU_EDIT_FOLDER)]
      assertWithMatcher:grey_nil()];
}

void VerifyBookmarkNodeWithLabelNotNil(NSString* bookmark_node_label) {
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          bookmark_node_label)]
      assertWithMatcher:grey_notNil()];
}

void SwipeBookmarkNodeWithLabel(NSString* bookmark_node_label) {
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          bookmark_node_label)]
      performAction:grey_swipeFastInDirection(kGREYDirectionLeft)];
}

void VerifyDeleteSwipeButtonNil() {
  [[EarlGrey selectElementWithMatcher:BookmarksDeleteSwipeButton()]
      assertWithMatcher:grey_nil()];
}

}  // namespace

// ManagedBookmarks test case with empty managed bookmarks. This can be
// sub-classed to provide non-empty managed bookmarks policy data.
@interface ManagedBookmarksTestCase : ChromeTestCase
@end

@implementation ManagedBookmarksTestCase

- (AppLaunchConfiguration)appConfigurationForTestCase {
  const std::string loadPolicyKey =
      base::SysNSStringToUTF8(kPolicyLoaderIOSLoadPolicyKey);
  const std::string managedBookmarksData = [self managedBookmarksPolicyData];
  std::string policyData = "<dict>"
                           "<key>" +
                           loadPolicyKey +
                           "</key>"
                           "<true/>"
                           "<key>" +
                           std::string(policy::key::kManagedBookmarks) +
                           "</key>" + managedBookmarksData + "</dict>";
  return GenerateAppLaunchConfiguration(policyData);
}

// Overridable by subclasses for custom managed bookmarks policy data.
- (std::string)managedBookmarksPolicyData {
  return "<array></array>";
}

- (void)setUp {
  [super setUp];
  [ChromeEarlGrey waitForBookmarksToFinishLoading];
}

// Tear down called once per test.
- (void)tearDown {
  [super tearDown];
  [BookmarkEarlGrey clearBookmarksPositionCache];
}

@end

// Tests ManagedBookmarks when the policy data is empty.
@interface ManagedBookmarksEmptyPolicyDataTestCase : ManagedBookmarksTestCase
@end

@implementation ManagedBookmarksEmptyPolicyDataTestCase

- (std::string)managedBookmarksPolicyData {
  return "<array></array>";
}

// Tests that the managed bookmarks folder does not exist when the policy data
// is empty.
- (void)testEmptyManagedBookmarks {
  [BookmarkEarlGreyUI openBookmarks];

  // Mobile bookmarks exists.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Mobile Bookmarks")]
      assertWithMatcher:grey_notNil()];

  // Managed bookmarks folder does not exist.
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Managed Bookmarks")]
      assertWithMatcher:grey_nil()];
}

@end

// Tests ManagedBookmarks when the policy is set with no top-level folder name.
@interface ManagedBookmarksDefaultFolderTestCase : ManagedBookmarksTestCase
@end

@implementation ManagedBookmarksDefaultFolderTestCase

- (std::string)managedBookmarksPolicyData {
  // Note that this test removes all whitespace when setting the policy.
  return R"(
    <array>
      <dict>
        <key>url</key><string>firstURL.com</string>
        <key>name</key><string>First_Managed_URL</string>
      </dict>
    </array>
  )";
}

// Tests that the managed bookmarks folder exists with default name.
- (void)testDefaultFolderName {
  [BookmarkEarlGreyUI openBookmarks];

  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Managed Bookmarks")]
      performAction:grey_tap()];

  VerifyBookmarkNodeWithLabelNotNil(@"First_Managed_URL");
}

@end

// Tests ManagedBookmarks when the policy is set with an item in the top-level
// folder as well as an item in a sub-folder.
@interface ManagedBookmarksPolicyDataWithSubFolderTestCase
    : ManagedBookmarksTestCase
@end

@implementation ManagedBookmarksPolicyDataWithSubFolderTestCase

#pragma mark - Overrides

- (std::string)managedBookmarksPolicyData {
  // Note that this test removes all whitespace when setting the policy.
  return R"(
    <array>
      <dict>
        <key>toplevel_name</key><string>Custom_Folder_Name</string>
      </dict>
      <dict>
        <key>url</key><string>firstURL.com</string>
        <key>name</key><string>First_Managed_URL</string>
      </dict>
      <dict>
        <key>name</key><string>Managed_Sub_Folder</string>
        <key>children</key>
        <array>
          <dict>
            <key>url</key><string>subFolderFirstURL.org</string>
            <key>name</key><string>Sub_Folder_First_URL</string>
          </dict>
        </array>
      </dict>
    </array>
  )";
}

#pragma mark - Test Helpers

- (void)openCustomManagedBookmarksFolder {
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Custom_Folder_Name")]
      performAction:grey_tap()];
}

- (void)openCustomManagedSubFolder {
  [[EarlGrey selectElementWithMatcher:TappableBookmarkNodeWithLabel(
                                          @"Managed_Sub_Folder")]
      performAction:grey_tap()];
}

#pragma mark - Tests

// Tests that the managed bookmarks folder exists with custom name and
// contents.
- (void)testManagedBookmarksFolderStructure {
  [BookmarkEarlGreyUI openBookmarks];
  [self openCustomManagedBookmarksFolder];
  VerifyBookmarkNodeWithLabelNotNil(@"First_Managed_URL");

  [self openCustomManagedSubFolder];
  VerifyBookmarkNodeWithLabelNotNil(@"Sub_Folder_First_URL");
}

// Tests that 'New Folder' and 'Edit' buttons are disabled inside top-level
// managed bookmarks folder and sub-folder.
- (void)testContextBarButtonsDisabled {
  [BookmarkEarlGreyUI openBookmarks];
  [self openCustomManagedBookmarksFolder];

  VerifyBookmarkContextBarNewFolderButtonDisabled();
  VerifyBookmarkContextBarEditButtonDisabled();

  [self openCustomManagedSubFolder];

  VerifyBookmarkContextBarNewFolderButtonDisabled();
  VerifyBookmarkContextBarEditButtonDisabled();
}

// Tests that long press is disabled while inside the top-level managed
// bookmarks folder and sub-folder.
- (void)testLongPressDisabled {
  [BookmarkEarlGreyUI openBookmarks];
  [self openCustomManagedBookmarksFolder];

  // Test long press on a bookmark URL.
  LongPressBookmarkNodeWithLabel(@"First_Managed_URL");
  VerifyEditBookmarkContextMenuButtonNil();

  // Test long press on a folder.
  LongPressBookmarkNodeWithLabel(@"Managed_Sub_Folder");
  VerifyEditBookmarkFolderContextMenuButtonNil();

  // Verify content is still the same.
  VerifyBookmarkNodeWithLabelNotNil(@"First_Managed_URL");
  VerifyBookmarkNodeWithLabelNotNil(@"Managed_Sub_Folder");

  [self openCustomManagedSubFolder];

  // Test long press inside sub-folder.
  LongPressBookmarkNodeWithLabel(@"Sub_Folder_First_URL");
  VerifyEditBookmarkContextMenuButtonNil();

  // Verify content is still the same.
  VerifyBookmarkNodeWithLabelNotNil(@"Sub_Folder_First_URL");
}

// Tests that swipe is disabled in managed bookmarks top-level folder and
// sub-folder.
- (void)testSwipeDisabled {
  // TODO(crbug.com/851227): On non Compact Width, the bookmark cell is being
  // deleted by grey_swipeFastInDirection.
  // grey_swipeFastInDirectionWithStartPoint doesn't work either and it might
  // fail on devices. Disabling this test under these conditions on the
  // meantime.
  if (![ChromeEarlGrey isCompactWidth]) {
    EARL_GREY_TEST_SKIPPED(@"Test disabled on iPad on iOS11.");
  }

  [BookmarkEarlGreyUI openBookmarks];
  [self openCustomManagedBookmarksFolder];

  SwipeBookmarkNodeWithLabel(@"First_Managed_URL");
  VerifyDeleteSwipeButtonNil();

  SwipeBookmarkNodeWithLabel(@"Managed_Sub_Folder");
  VerifyDeleteSwipeButtonNil();

  // Verify content is still the same.
  VerifyBookmarkNodeWithLabelNotNil(@"First_Managed_URL");
  VerifyBookmarkNodeWithLabelNotNil(@"Managed_Sub_Folder");

  [self openCustomManagedSubFolder];

  SwipeBookmarkNodeWithLabel(@"Sub_Folder_First_URL");
  VerifyDeleteSwipeButtonNil();

  // Verify content is still the same.
  VerifyBookmarkNodeWithLabelNotNil(@"Sub_Folder_First_URL");
}

@end
