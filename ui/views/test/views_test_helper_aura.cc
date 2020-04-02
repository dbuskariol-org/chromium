// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/views/test/views_test_helper_aura.h"

#include "ui/views/test/test_views_delegate.h"

namespace views {

namespace {

ViewsTestHelperAura::TestViewsDelegateFactory g_delegate_factory = nullptr;

}  // namespace

// static
std::unique_ptr<ViewsTestHelper> ViewsTestHelper::Create() {
  return std::make_unique<ViewsTestHelperAura>();
}

ViewsTestHelperAura::ViewsTestHelperAura() {
  aura_test_helper_ = std::make_unique<aura::test::AuraTestHelper>();
}

ViewsTestHelperAura::~ViewsTestHelperAura() {
  gfx::NativeWindow root_window = GetContext();
  if (root_window) {
    // Ensure all Widgets (and windows) are closed in unit tests. This is done
    // automatically when the RootWindow is torn down, but is an error on
    // platforms that must ensure no Compositors are alive when the
    // ContextFactory is torn down.
    // So, although it's optional, check the root window to detect failures
    // before they hit the CQ on other platforms.
    DCHECK(root_window->children().empty()) << "Not all windows were closed.";
  }
}

std::unique_ptr<TestViewsDelegate>
ViewsTestHelperAura::GetFallbackTestViewsDelegate() {
  // The factory delegate takes priority over the parent default.
  return g_delegate_factory ? (*g_delegate_factory)()
                            : ViewsTestHelper::GetFallbackTestViewsDelegate();
}

void ViewsTestHelperAura::SetUp() {
  aura_test_helper_->SetUp();
}

gfx::NativeWindow ViewsTestHelperAura::GetContext() {
  return aura_test_helper_->GetContext();
}

// static
void ViewsTestHelperAura::SetFallbackTestViewsDelegateFactory(
    TestViewsDelegateFactory factory) {
  DCHECK_NE(g_delegate_factory == nullptr, factory == nullptr);
  g_delegate_factory = factory;
}

}  // namespace views
