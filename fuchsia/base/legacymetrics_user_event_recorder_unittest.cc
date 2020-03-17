// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "fuchsia/base/legacymetrics_user_event_recorder.h"

#include "base/test/task_environment.h"
#include "base/threading/thread_task_runner_handle.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace cr_fuchsia {
namespace {

TEST(LegacyMetricsUserActionRecorderTest, ProduceAndConsume) {
  constexpr char kExpectedUserAction1[] = "Hello";
  constexpr char kExpectedUserAction2[] = "There";

  base::test::SingleThreadTaskEnvironment task_environment;
  base::SetRecordActionTaskRunner(base::ThreadTaskRunnerHandle::Get());

  zx_time_t time_start = base::TimeTicks::Now().ToZxTime();
  auto buffer = std::make_unique<LegacyMetricsUserActionRecorder>();
  base::RecordComputedAction(kExpectedUserAction1);
  EXPECT_TRUE(buffer->HasEvents());
  base::RecordComputedAction(kExpectedUserAction2);

  auto events = buffer->TakeEvents();
  EXPECT_FALSE(buffer->HasEvents());
  EXPECT_EQ(2u, events.size());

  EXPECT_EQ(kExpectedUserAction1, events[0].name());
  EXPECT_GE(events[0].time(), time_start);

  EXPECT_EQ(kExpectedUserAction2, events[1].name());
  EXPECT_GE(events[1].time(), time_start);

  EXPECT_GE(events[1].time(), events[0].time());

  EXPECT_TRUE(buffer->TakeEvents().empty());

  base::RecordComputedAction(kExpectedUserAction2);
  EXPECT_TRUE(buffer->HasEvents());
  events = buffer->TakeEvents();
  EXPECT_FALSE(buffer->HasEvents());
  EXPECT_EQ(1u, events.size());
  EXPECT_EQ(kExpectedUserAction2, events[0].name());
}

TEST(LegacyMetricsUserActionRecorderTest, RecorderDeleted) {
  base::test::SingleThreadTaskEnvironment task_environment;
  base::SetRecordActionTaskRunner(base::ThreadTaskRunnerHandle::Get());

  auto buffer = std::make_unique<LegacyMetricsUserActionRecorder>();
  buffer.reset();

  // |buffer| was destroyed, so check that recording actions doesn't cause a
  // use-after-free error.
  base::RecordComputedAction("NoCrashingPlz");
}

TEST(LegacyMetricsUserActionRecorderTest, EmptyBuffer) {
  base::test::SingleThreadTaskEnvironment task_environment;
  base::SetRecordActionTaskRunner(base::ThreadTaskRunnerHandle::Get());
  auto buffer = std::make_unique<LegacyMetricsUserActionRecorder>();
  EXPECT_FALSE(buffer->HasEvents());
}

}  // namespace
}  // namespace cr_fuchsia
