// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/page_load_metrics/integration_tests/metric_integration_test.h"

#include "base/test/trace_event_analyzer.h"
#include "chrome/test/base/ui_test_utils.h"
#include "services/metrics/public/cpp/ukm_builders.h"

using base::Bucket;
using base::Value;
using trace_analyzer::Query;
using trace_analyzer::TraceAnalyzer;
using trace_analyzer::TraceEventVector;
using ukm::builders::PageLoad;

IN_PROC_BROWSER_TEST_F(MetricIntegrationTest, FirstInputDelay) {
  LoadHTML(R"HTML(
    <script>
    runtest = async () => {
      const observePromise = new Promise(resolve => {
        new PerformanceObserver(e => {
          e.getEntries().forEach(entry => {
            const fid = entry.processingStart - entry.startTime;
            resolve(fid);
          })
        }).observe({type: 'first-input', buffered: true});
      });
      return await observePromise;
    };
    </script>
  )HTML");

  StartTracing({"loading"});

  content::SimulateMouseClickAt(web_contents(), 0,
                                blink::WebMouseEvent::Button::kLeft,
                                gfx::Point(10, 10));

  // Check web perf API.
  double expected_fid = EvalJs(web_contents(), "runtest()").ExtractDouble();
  EXPECT_GT(expected_fid, 0.0);

  ui_test_utils::NavigateToURL(browser(), GURL("about:blank"));

  // Check UKM.
  ExpectUKMPageLoadMetric(PageLoad::kInteractiveTiming_FirstInputDelay4Name,
                          expected_fid);

  // Check UMA.
  auto samples = histogram_tester().GetAllSamples(
      "PageLoad.InteractiveTiming.FirstInputDelay4");
  EXPECT_EQ(1ul, samples.size());
  EXPECT_EQ(samples[0], Bucket(expected_fid, 1));
}
