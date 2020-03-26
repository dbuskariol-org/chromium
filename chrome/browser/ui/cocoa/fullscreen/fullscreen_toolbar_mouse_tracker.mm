// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "chrome/browser/ui/cocoa/fullscreen/fullscreen_toolbar_mouse_tracker.h"

#import "chrome/browser/ui/cocoa/fullscreen/fullscreen_toolbar_controller.h"
#include "chrome/browser/ui/cocoa/scoped_menu_bar_lock.h"
#import "ui/base/cocoa/tracking_area.h"

namespace {

// Additional height threshold added at the toolbar's bottom. This is to mimic
// threshold the mouse position needs to be at before the menubar automatically
// hides.
const CGFloat kTrackingAreaAdditionalThreshold = 50;

}  // namespace

@interface FullscreenToolbarMouseTracker () {
  // The frame for the tracking area. The value is the toolbar's frame with
  // additional height added at the bottom.
  NSRect _trackingAreaFrame;

  // The tracking area associated with the toolbar. This tracking area is used
  // to keep the toolbar active if the menubar had animated out but the mouse
  // is still on the toolbar.
  base::scoped_nsobject<CrTrackingArea> _trackingArea;

  // Keeps the menu bar from hiding until the mouse exits the tracking area.
  std::unique_ptr<ScopedMenuBarLock> _menuBarLock;

  // The content view for the window.
  NSView* _contentView;  // weak

  FullscreenToolbarController* _controller;  // weak
}

@end

@implementation FullscreenToolbarMouseTracker

- (instancetype)initWithFullscreenToolbarController:
    (FullscreenToolbarController*)controller {
  if ((self = [super init])) {
    _controller = controller;
  }

  return self;
}

- (void)dealloc {
  [self removeTrackingArea];
  [super dealloc];
}

- (void)updateTrackingArea {
  // Remove the tracking area if the toolbar and menu bar aren't both visible.
  if ([_controller toolbarFraction] == 0 || ![NSMenu menuBarVisible]) {
    [self removeTrackingArea];
    _menuBarLock.reset();
    return;
  }

  if (_trackingArea) {
    // If |trackingArea_|'s rect matches |trackingAreaFrame_|, quit early.
    if (NSEqualRects(_trackingAreaFrame, [_trackingArea rect]))
      return;

    [self removeTrackingArea];
  }

  _contentView = [[[_controller delegate] window] contentView];

  _trackingArea.reset([[CrTrackingArea alloc]
      initWithRect:_trackingAreaFrame
           options:NSTrackingMouseEnteredAndExited | NSTrackingActiveInKeyWindow
             owner:self
          userInfo:nil]);

  [_contentView addTrackingArea:_trackingArea];
}

- (void)updateToolbarFrame:(NSRect)frame {
  NSRect contentBounds = [[[[_controller delegate] window] contentView] bounds];
  _trackingAreaFrame = frame;
  _trackingAreaFrame.origin.y -= kTrackingAreaAdditionalThreshold;
  _trackingAreaFrame.size.height =
      NSMaxY(contentBounds) - _trackingAreaFrame.origin.y;

  [self updateTrackingArea];
}

- (void)removeTrackingArea {
  if (!_trackingArea)
    return;

  DCHECK(_contentView);
  [_contentView removeTrackingArea:_trackingArea];
  _trackingArea.reset();
  _contentView = nil;
}

- (void)mouseEntered:(NSEvent*)event {
  _menuBarLock = std::make_unique<ScopedMenuBarLock>();
}

- (void)mouseExited:(NSEvent*)event {
  _menuBarLock.reset();
}

@end
