// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_LITE_VIDEO_LITE_VIDEO_FEATURES_H_
#define CHROME_BROWSER_LITE_VIDEO_LITE_VIDEO_FEATURES_H_

#include "base/feature_list.h"
#include "base/optional.h"

namespace base {
class Value;
}  // namespace base

namespace lite_video {
namespace features {

// Whether the LiteVideo feature that throttles media requests to reduce
// adaptive bitrates of media streams is enabled. Currently disabled by default.
bool IsLiteVideoEnabled();

// Return the origins that are whitelisted for using the LiteVideo optimization
// and the parameters needed to throttle media requests for that origin.
base::Optional<base::Value> GetLiteVideoOriginHintsFromFieldTrial();

// The target for of the round-trip time for media requests used when
// throttling media requests.
int LiteVideoTargetDownlinkRTTLatencyMs();

// The number of kilobytes to be buffered before starting to throttle media
// requests.
int LiteVideoKilobytesToBufferBeforeThrottle();

// The maximum number of hosts maintained for each blocklist for the LiteVideo
// optimization.
size_t MaxUserBlocklistHosts();

// The duration which a host will remain blocklisted from having media requests
// throttled based on user opt-outs.
base::TimeDelta UserBlocklistHostDuration();

// The number of opt-out events for a host to be considered to be blocklisted.
int UserBlocklistOptOutHistoryThreshold();

// The current version of the LiteVideo user blocklist.
int LiteVideoBlocklistVersion();

}  // namespace features
}  // namespace lite_video

#endif  // CHROME_BROWSER_LITE_VIDEO_LITE_VIDEO_FEATURES_H_
