// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/lite_video/lite_video_decider.h"

#include <map>
#include <utility>

#include "base/optional.h"
#include "base/test/metrics/histogram_tester.h"
#include "base/test/scoped_feature_list.h"
#include "base/test/simple_test_clock.h"
#include "base/time/clock.h"
#include "chrome/browser/lite_video/lite_video_features.h"
#include "chrome/browser/lite_video/lite_video_hint.h"
#include "chrome/browser/lite_video/lite_video_user_blocklist.h"
#include "chrome/common/chrome_features.h"
#include "chrome/test/base/chrome_render_view_host_test_harness.h"
#include "components/blocklist/opt_out_blocklist/opt_out_blocklist.h"
#include "components/blocklist/opt_out_blocklist/opt_out_blocklist_delegate.h"
#include "components/blocklist/opt_out_blocklist/opt_out_store.h"
#include "content/public/test/mock_navigation_handle.h"
#include "content/public/test/test_renderer_host.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "url/gurl.h"

class TestLiteVideoUserBlocklist : public lite_video::LiteVideoUserBlocklist {
 public:
  TestLiteVideoUserBlocklist(
      std::unique_ptr<blocklist::OptOutStore> opt_out_store,
      base::Clock* clock,
      blocklist::OptOutBlocklistDelegate* blocklist_delegate)
      : lite_video::LiteVideoUserBlocklist(std::move(opt_out_store),
                                           clock,
                                           blocklist_delegate) {}

  ~TestLiteVideoUserBlocklist() override = default;

  lite_video::LiteVideoBlocklistReason IsLiteVideoAllowedOnNavigation(
      content::NavigationHandle* navigation_handle) const override {
    return blocklist_reason_;
  }

  void set_blocklist_reason(
      lite_video::LiteVideoBlocklistReason blocklist_reason) {
    blocklist_reason_ = blocklist_reason;
  }

 private:
  lite_video::LiteVideoBlocklistReason blocklist_reason_ =
      lite_video::LiteVideoBlocklistReason::kAllowed;
};

class TestLiteVideoHintCache : public lite_video::LiteVideoHintCache {
 public:
  TestLiteVideoHintCache() = default;
  ~TestLiteVideoHintCache() override = default;
  base::Optional<lite_video::LiteVideoHint> GetHintForNavigationURL(
      const GURL& url) const override {
    auto it = hint_cache_.find(url);
    if (it != hint_cache_.end())
      return it->second;
    return base::nullopt;
  }

  void AddHintForTesting(const GURL& url,
                         const lite_video::LiteVideoHint& hint) {
    hint_cache_.insert(std::make_pair(url, hint));
  }

 private:
  std::map<GURL, lite_video::LiteVideoHint> hint_cache_;
};

class LiteVideoDeciderTest : public ChromeRenderViewHostTestHarness {
 public:
  void SetUp() override {
    content::RenderViewHostTestHarness::SetUp();
    scoped_feature_list_.InitAndEnableFeature({::features::kLiteVideo});
    lite_video_decider_ =
        std::make_unique<lite_video::LiteVideoDecider>(nullptr, &test_clock_);
  }

  void DisableLiteVideo() {
    scoped_feature_list_.Reset();
    scoped_feature_list_.InitAndDisableFeature({::features::kLiteVideo});
  }

  base::Optional<lite_video::LiteVideoHint> CanApplyOnSubframeNavigation(
      const GURL& mainframe_url,
      const GURL& subframe_url) {
    // Needed so that a mainframe navigation exists.
    NavigateAndCommit(mainframe_url);
    content::RenderFrameHostTester* rfh_tester =
        content::RenderFrameHostTester::For(main_rfh());
    content::RenderFrameHost* subframe = rfh_tester->AppendChild("subframe");
    content::MockNavigationHandle navigation_handle(subframe_url, subframe);
    return lite_video_decider_->CanApplyLiteVideo(&navigation_handle);
  }

  void SeedLiteVideoHintCache(const GURL& gurl,
                              lite_video::LiteVideoHint hint) {
    std::unique_ptr<TestLiteVideoHintCache> hint_cache =
        std::make_unique<TestLiteVideoHintCache>();
    hint_cache->AddHintForTesting(gurl, hint);
    lite_video_decider_->SetHintCacheForTesting(std::move(hint_cache));
  }

  void SetBlocklistReason(lite_video::LiteVideoBlocklistReason reason) {
    std::unique_ptr<TestLiteVideoUserBlocklist> user_blocklist_ =
        std::make_unique<TestLiteVideoUserBlocklist>(nullptr, &test_clock_,
                                                     lite_video_decider_.get());
    user_blocklist_->set_blocklist_reason(reason);
    lite_video_decider_->SetUserBlocklistForTesting(std::move(user_blocklist_));
  }

  lite_video::LiteVideoDecider* lite_video_decider() {
    return lite_video_decider_.get();
  }

  void TearDown() override { content::RenderViewHostTestHarness::TearDown(); }

 private:
  base::test::ScopedFeatureList scoped_feature_list_;
  base::SimpleTestClock test_clock_;
  std::unique_ptr<lite_video::LiteVideoDecider> lite_video_decider_;
};

TEST_F(LiteVideoDeciderTest, CanApplyOnNonHTTPOrHTTPSURL) {
  base::HistogramTester histogram_tester;
  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(GURL("chrome:://about"));
  base::Optional<lite_video::LiteVideoHint> hint =
      lite_video_decider()->CanApplyLiteVideo(&navigation_handle);
  EXPECT_FALSE(hint);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame", 0);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.SubFrame", 0);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", 0);
}

TEST_F(LiteVideoDeciderTest, CanApplyNoHintAndHostBlocklisted) {
  base::HistogramTester histogram_tester;
  SetBlocklistReason(
      lite_video::LiteVideoBlocklistReason::kNavigationBlocklisted);
  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(GURL("https://NoVideo.com"));
  base::Optional<lite_video::LiteVideoHint> hint =
      lite_video_decider()->CanApplyLiteVideo(&navigation_handle);
  EXPECT_FALSE(hint);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame",
      lite_video::LiteVideoBlocklistReason::kNavigationBlocklisted, 1);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", false, 1);
}

TEST_F(LiteVideoDeciderTest, CanApplyAllowedButNoHint) {
  base::HistogramTester histogram_tester;
  SetBlocklistReason(lite_video::LiteVideoBlocklistReason::kAllowed);

  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(GURL("https://NoVideo.com"));
  base::Optional<lite_video::LiteVideoHint> hint =
      lite_video_decider()->CanApplyLiteVideo(&navigation_handle);

  EXPECT_FALSE(hint);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame",
      lite_video::LiteVideoBlocklistReason::kAllowed, 1);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", false, 1);
}

TEST_F(LiteVideoDeciderTest, CanApplyLiteVideo) {
  base::HistogramTester histogram_tester;

  SetBlocklistReason(lite_video::LiteVideoBlocklistReason::kAllowed);
  GURL url("https://LiteVideo.com");
  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(url);
  lite_video::LiteVideoHint seeded_hint(
      /*target_downlink_bandwidth_kbps=*/123,
      /*target_downlink_rtt_latency_ms=*/2500,
      /*kilobytes_to_buffer_before_throttle=*/500);
  SeedLiteVideoHintCache(url, seeded_hint);

  base::Optional<lite_video::LiteVideoHint> hint =
      lite_video_decider()->CanApplyLiteVideo(&navigation_handle);

  ASSERT_TRUE(hint);
  EXPECT_EQ(seeded_hint.target_downlink_bandwidth_kbps(),
            hint->target_downlink_bandwidth_kbps());
  EXPECT_EQ(seeded_hint.target_downlink_rtt_latency_ms(),
            hint->target_downlink_rtt_latency_ms());
  EXPECT_EQ(seeded_hint.kilobytes_to_buffer_before_throttle(),
            hint->kilobytes_to_buffer_before_throttle());
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame",
      lite_video::LiteVideoBlocklistReason::kAllowed, 1);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.SubFrame", 0);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", true, 1);
}

TEST_F(LiteVideoDeciderTest, LiteVideoDisabled) {
  DisableLiteVideo();
  base::HistogramTester histogram_tester;
  SetBlocklistReason(lite_video::LiteVideoBlocklistReason::kAllowed);
  GURL url("https://LiteVideo.com");
  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(url);
  lite_video::LiteVideoHint seeded_hint(
      /*target_downlink_bandwidth_kbps=*/123,
      /*target_downlink_rtt_latency_ms=*/2500,
      /*kilobytes_to_buffer_before_throttle=*/500);
  SeedLiteVideoHintCache(url, seeded_hint);

  base::Optional<lite_video::LiteVideoHint> hint =
      lite_video_decider()->CanApplyLiteVideo(&navigation_handle);
  EXPECT_FALSE(hint);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame", 0);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.SubFrame", 0);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", 0);
}

TEST_F(LiteVideoDeciderTest, LiteVideoCanApplyOnSubframeNavigation) {
  base::HistogramTester histogram_tester;

  SetBlocklistReason(lite_video::LiteVideoBlocklistReason::kAllowed);
  GURL url("https://LiteVideo.com");
  content::MockNavigationHandle navigation_handle;
  navigation_handle.set_url(url);
  lite_video::LiteVideoHint seeded_hint(
      /*target_downlink_bandwidth_kbps=*/123,
      /*target_downlink_rtt_latency_ms=*/2500,
      /*kilobytes_to_buffer_before_throttle=*/500);
  SeedLiteVideoHintCache(url, seeded_hint);

  base::Optional<lite_video::LiteVideoHint> hint =
      CanApplyOnSubframeNavigation(GURL("https://mainframe.com"), url);

  ASSERT_TRUE(hint);
  EXPECT_EQ(seeded_hint.target_downlink_bandwidth_kbps(),
            hint->target_downlink_bandwidth_kbps());
  EXPECT_EQ(seeded_hint.target_downlink_rtt_latency_ms(),
            hint->target_downlink_rtt_latency_ms());
  EXPECT_EQ(seeded_hint.kilobytes_to_buffer_before_throttle(),
            hint->kilobytes_to_buffer_before_throttle());
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.SubFrame",
      lite_video::LiteVideoBlocklistReason::kAllowed, 1);
  histogram_tester.ExpectTotalCount(
      "LiteVideo.CanApplyLiteVideo.UserBlocklist.MainFrame", 0);
  histogram_tester.ExpectUniqueSample(
      "LiteVideo.CanApplyLiteVideo.HintCache.HasHint", true, 1);
}
