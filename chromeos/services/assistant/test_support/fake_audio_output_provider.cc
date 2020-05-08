// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "libassistant/shared/public/platform_audio_output.h"

namespace assistant_client {

// TODO(b/156011466): temporary fix until the real fix lands in LibAssistant
void AudioOutputProvider::RegisterAudioEmittingStateCallback(
    AudioEmittingStateCallback callback) {}

}  // namespace assistant_client
