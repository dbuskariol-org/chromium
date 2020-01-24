// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_PUBLIC_CPP_ASSISTANT_CONVERSATION_STARTERS_CLIENT_H_
#define ASH_PUBLIC_CPP_ASSISTANT_CONVERSATION_STARTERS_CLIENT_H_

#include "ash/public/cpp/ash_public_export.h"

namespace ash {

// The interface for the conversation starters feature browser client.
class ASH_PUBLIC_EXPORT ConversationStartersClient {
 public:
  // Returns the singleton instance.
  static ConversationStartersClient* Get();

 protected:
  ConversationStartersClient();
  virtual ~ConversationStartersClient();
};

}  // namespace ash

#endif  // ASH_PUBLIC_CPP_ASSISTANT_CONVERSATION_STARTERS_CLIENT_H_
