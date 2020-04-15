// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/settings/privacy/cookies_view_controller.h"

#import "ios/chrome/browser/ui/settings/cells/settings_multiline_detail_item.h"
#import "ios/chrome/browser/ui/settings/privacy/cookies_commands.h"
#import "ios/chrome/browser/ui/settings/settings_navigation_controller.h"
#include "ios/chrome/grit/ios_strings.h"
#include "ui/base/l10n/l10n_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

typedef NS_ENUM(NSInteger, ItemType) {
  ItemTypeAllowCookies = kItemTypeEnumZero,
  ItemTypeBlockThirdPartyCookiesIncognito,
  ItemTypeBlockThirdPartyCookies,
  ItemTypeBlockAllCookies,
  ItemTypeCookiesDescription,
};

typedef NS_ENUM(NSInteger, SectionIdentifier) {
  SectionIdentifierContent = kSectionIdentifierEnumZero,
};

}  // namespace

@interface PrivacyCookiesViewController ()

@property(nonatomic, strong) TableViewItem* selectedCookiesItem;
@property(nonatomic, assign) ItemType selectedSetting;

@end

@implementation PrivacyCookiesViewController

#pragma mark - UIViewController

- (void)viewDidLoad {
  [super viewDidLoad];

  self.title = l10n_util::GetNSString(IDS_IOS_OPTIONS_PRIVACY_COOKIES);

  [self loadModel];
  NSIndexPath* indexPath =
      [self.tableViewModel indexPathForItemType:self.selectedSetting];
  [self updateSelectedCookiesItemWithIndexPath:indexPath];
}

- (void)didMoveToParentViewController:(UIViewController*)parent {
  [super didMoveToParentViewController:parent];
  if (!parent) {
    [self.presentationDelegate privacyCookiesViewControllerWasRemoved:self];
  }
}

#pragma mark - ChromeTableViewController

- (void)loadModel {
  [super loadModel];
  [self.tableViewModel addSectionWithIdentifier:SectionIdentifierContent];

  SettingsMultilineDetailItem* allowCookies =
      [[SettingsMultilineDetailItem alloc] initWithType:ItemTypeAllowCookies];
  allowCookies.text = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_ALLOW_COOKIES_TITLE);
  allowCookies.detailText = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_ALLOW_COOKIES_DETAIL);
  [self.tableViewModel addItem:allowCookies
       toSectionWithIdentifier:SectionIdentifierContent];

  SettingsMultilineDetailItem* blockThirdPartyCookiesIncognito =
      [[SettingsMultilineDetailItem alloc]
          initWithType:ItemTypeBlockThirdPartyCookiesIncognito];
  blockThirdPartyCookiesIncognito.text = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_THIRD_PARTY_COOKIES_INCOGNITO_TITLE);
  blockThirdPartyCookiesIncognito.detailText = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_THIRD_PARTY_COOKIES_DETAIL);
  [self.tableViewModel addItem:blockThirdPartyCookiesIncognito
       toSectionWithIdentifier:SectionIdentifierContent];

  SettingsMultilineDetailItem* blockThirdPartyCookies =
      [[SettingsMultilineDetailItem alloc]
          initWithType:ItemTypeBlockThirdPartyCookies];
  blockThirdPartyCookies.text = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_THIRD_PARTY_COOKIES_TITLE);
  blockThirdPartyCookies.detailText = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_THIRD_PARTY_COOKIES_DETAIL);
  [self.tableViewModel addItem:blockThirdPartyCookies
       toSectionWithIdentifier:SectionIdentifierContent];

  SettingsMultilineDetailItem* blockAllCookies =
      [[SettingsMultilineDetailItem alloc]
          initWithType:ItemTypeBlockAllCookies];
  blockAllCookies.text = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_ALL_COOKIES_TITLE);
  blockAllCookies.detailText = l10n_util::GetNSString(
      IDS_IOS_OPTIONS_PRIVACY_COOKIES_BLOCK_ALL_COOKIES_DETAIL);
  [self.tableViewModel addItem:blockAllCookies
       toSectionWithIdentifier:SectionIdentifierContent];
  // TODO(crbug.com/1064961): Implement this.
}

#pragma mark - Private

// Adds accessoryType to the selected item & cell.
// Removes the accessoryType of the prevous selected option item & cell.
- (void)updateSelectedCookiesItemWithIndexPath:(NSIndexPath*)indexPath {
  UITableViewCell* selectedCookiesItemCell;
  if (self.selectedCookiesItem) {
    self.selectedCookiesItem.accessoryType = UITableViewCellAccessoryNone;
    selectedCookiesItemCell = [self.tableView
        cellForRowAtIndexPath:[self.tableViewModel
                                  indexPathForItem:self.selectedCookiesItem]];
    selectedCookiesItemCell.accessoryType = UITableViewCellAccessoryNone;
  }
  self.selectedCookiesItem = [self.tableViewModel itemAtIndexPath:indexPath];
  self.selectedCookiesItem.accessoryType = UITableViewCellAccessoryCheckmark;
  selectedCookiesItemCell = [self.tableView
      cellForRowAtIndexPath:[self.tableViewModel
                                indexPathForItem:self.selectedCookiesItem]];
  selectedCookiesItemCell.accessoryType = UITableViewCellAccessoryCheckmark;
}

// Returns the ItemType associated with the CookiesSettingType.
- (ItemType)itemTypeForCookiesSettingType:(CookiesSettingType)settingType {
  switch (settingType) {
    case SettingTypeBlockThirdPartyCookiesIncognito:
      return ItemTypeBlockThirdPartyCookiesIncognito;
    case SettingTypeBlockThirdPartyCookies:
      return ItemTypeBlockThirdPartyCookies;
    case SettingTypeBlockAllCookies:
      return ItemTypeBlockAllCookies;
    case SettingTypeAllowCookies:
      return ItemTypeAllowCookies;
  }
}

// Returns the CookiesSettingType associated with the ItemType.
- (CookiesSettingType)SettingTypeForCookiesItemType:(ItemType)itemType {
  switch (itemType) {
    case ItemTypeBlockThirdPartyCookiesIncognito:
      return SettingTypeBlockThirdPartyCookiesIncognito;
    case ItemTypeBlockThirdPartyCookies:
      return SettingTypeBlockThirdPartyCookies;
    case ItemTypeBlockAllCookies:
      return SettingTypeBlockAllCookies;
    case ItemTypeAllowCookies:
      return SettingTypeAllowCookies;
    case ItemTypeCookiesDescription:
      NOTREACHED();
      return SettingTypeAllowCookies;
  }
}

#pragma mark - UITableViewDelegate

- (void)tableView:(UITableView*)tableView
    didSelectRowAtIndexPath:(NSIndexPath*)indexPath {
  // TODO(crbug.com/1064961): Implement this after adding new table view item.
  [self.tableView deselectRowAtIndexPath:indexPath animated:YES];
  [self updateSelectedCookiesItemWithIndexPath:indexPath];
  ItemType itemType =
      (ItemType)[self.tableViewModel itemTypeForIndexPath:indexPath];
  CookiesSettingType settingType =
      [self SettingTypeForCookiesItemType:itemType];
  [self.handler selectedCookiesSettingType:settingType];
}

#pragma mark - PrivacyCookiesConsumer

- (void)cookiesSettingsOptionSelected:(CookiesSettingType)settingType {
  self.selectedSetting = [self itemTypeForCookiesSettingType:settingType];
}

@end
