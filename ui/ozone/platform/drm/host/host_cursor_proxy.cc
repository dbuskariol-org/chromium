// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/ozone/platform/drm/host/host_cursor_proxy.h"

#include <utility>

#include "services/service_manager/public/cpp/connector.h"
#include "ui/ozone/public/gpu_platform_support_host.h"

namespace ui {

// We assume that this is invoked only on the Mus/UI thread.
HostCursorProxy::HostCursorProxy(
    mojo::PendingAssociatedRemote<ui::ozone::mojom::DeviceCursor> main_cursor,
    mojo::PendingAssociatedRemote<ui::ozone::mojom::DeviceCursor> evdev_cursor)
    : main_cursor_(std::move(main_cursor)),
      evdev_cursor_pending_remote_(std::move(evdev_cursor)),
      ui_thread_ref_(base::PlatformThread::CurrentRef()) {}

HostCursorProxy::~HostCursorProxy() {}

void HostCursorProxy::CursorSet(gfx::AcceleratedWidget widget,
                                const std::vector<SkBitmap>& bitmaps,
                                const gfx::Point& location,
                                int frame_delay_ms) {
  if (ui_thread_ref_ == base::PlatformThread::CurrentRef()) {
    main_cursor_->SetCursor(widget, bitmaps, location, frame_delay_ms);
  } else {
    InitializeOnEvdevIfNecessary();
    evdev_cursor_->SetCursor(widget, bitmaps, location, frame_delay_ms);
  }
}

void HostCursorProxy::Move(gfx::AcceleratedWidget widget,
                           const gfx::Point& location) {
  if (ui_thread_ref_ == base::PlatformThread::CurrentRef()) {
    main_cursor_->MoveCursor(widget, location);
  } else {
    InitializeOnEvdevIfNecessary();
    evdev_cursor_->MoveCursor(widget, location);
  }
}

void HostCursorProxy::InitializeOnEvdevIfNecessary() {
  if (evdev_cursor_.is_bound())
    return;
  evdev_cursor_.Bind(std::move(evdev_cursor_pending_remote_));
}

}  // namespace ui
