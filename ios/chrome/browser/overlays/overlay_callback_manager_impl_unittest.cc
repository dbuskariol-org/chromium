// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ios/chrome/browser/overlays/overlay_callback_manager_impl.h"

#include "base/bind.h"
#include "ios/chrome/browser/overlays/public/overlay_response.h"
#include "ios/chrome/browser/overlays/test/fake_overlay_user_data.h"
#include "testing/platform_test.h"

using OverlayCallbackManagerImplTest = PlatformTest;

// Tests that OverlayCallbackManagerImp can add and execute completion
// callbacks.
TEST_F(OverlayCallbackManagerImplTest, CompletionCallbacks) {
  OverlayCallbackManagerImpl manager;
  void* kResponseData = &kResponseData;
  // Add two completion callbacks that increment |callback_execution_count|.
  __block size_t callback_execution_count = 0;
  void (^callback_block)(OverlayResponse* response) =
      ^(OverlayResponse* response) {
        if (!response)
          return;
        if (response->GetInfo<FakeOverlayUserData>()->value() != kResponseData)
          return;
        ++callback_execution_count;
      };
  manager.AddCompletionCallback(
      base::BindOnce(base::RetainBlock(callback_block)));
  manager.AddCompletionCallback(
      base::BindOnce(base::RetainBlock(callback_block)));

  // Add a response to the queue with a fake info using kResponseData.
  manager.SetCompletionResponse(
      OverlayResponse::CreateWithInfo<FakeOverlayUserData>(kResponseData));
  OverlayResponse* response = manager.GetCompletionResponse();
  ASSERT_TRUE(response);
  EXPECT_EQ(kResponseData, response->GetInfo<FakeOverlayUserData>()->value());

  // Execute the callbacks and verify that both are called once.
  ASSERT_EQ(0U, callback_execution_count);
  manager.ExecuteCompletionCallbacks();
  EXPECT_EQ(2U, callback_execution_count);
}
