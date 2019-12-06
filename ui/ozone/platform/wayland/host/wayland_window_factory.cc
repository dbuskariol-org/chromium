// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/ozone/platform/wayland/host/wayland_window.h"

#include <memory>

#include "ui/ozone/platform/wayland/host/wayland_window.h"

namespace ui {

// static
std::unique_ptr<WaylandWindow> WaylandWindow::Create(
    PlatformWindowDelegate* delegate,
    WaylandConnection* connection,
    PlatformWindowInitProperties properties) {
  // TODO(msisov): once WaylandWindow becomes a base class, add switch cases to
  // create different Wayland windows.
  std::unique_ptr<WaylandWindow> window(
      new WaylandWindow(delegate, connection));
  return window->Initialize(std::move(properties)) ? std::move(window)
                                                   : nullptr;
}

}  // namespace ui