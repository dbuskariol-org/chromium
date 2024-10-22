// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chromeos/lacros/browser/lacros_chrome_service_impl.h"

#include <utility>

#include "base/logging.h"

namespace chromeos {
namespace {

LacrosChromeServiceImpl* g_instance = nullptr;

}  // namespace

// static
LacrosChromeServiceImpl* LacrosChromeServiceImpl::Get() {
  return g_instance;
}

LacrosChromeServiceImpl::LacrosChromeServiceImpl()
    : pending_ash_chrome_service_receiver_(
          ash_chrome_service_.BindNewPipeAndPassReceiver()) {
  DCHECK(!g_instance);
  g_instance = this;
}

LacrosChromeServiceImpl::~LacrosChromeServiceImpl() {
  DCHECK_EQ(this, g_instance);
  g_instance = nullptr;
}

void LacrosChromeServiceImpl::RequestAshChromeServiceReceiver(
    RequestAshChromeServiceReceiverCallback callback) {
  // TODO(hidehiko): Remove non-error logging from here.
  LOG(WARNING) << "AshChromeServiceReceiver requested.";
  std::move(callback).Run(std::move(pending_ash_chrome_service_receiver_));
}

}  // namespace chromeos
