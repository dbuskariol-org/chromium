// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "media/base/video_frame_metadata.h"

#include <stdint.h>
#include <utility>
#include <vector>

#include "base/check_op.h"
#include "base/strings/string_number_conversions.h"
#include "base/util/values/values_util.h"
#include "ui/gfx/geometry/rect.h"

namespace media {

VideoFrameMetadata::VideoFrameMetadata() = default;

VideoFrameMetadata::~VideoFrameMetadata() = default;

VideoFrameMetadata::VideoFrameMetadata(const VideoFrameMetadata& other) =
    default;

#define SET_FIELD(k, field) \
  case Key::k:              \
    field = value;          \
    break

void VideoFrameMetadata::SetBoolean(Key key, bool value) {
  switch (key) {
    SET_FIELD(ALLOW_OVERLAY, allow_overlay);
    SET_FIELD(COPY_REQUIRED, copy_required);
    SET_FIELD(END_OF_STREAM, end_of_stream);
    SET_FIELD(INTERACTIVE_CONTENT, interactive_content);
    SET_FIELD(READ_LOCK_FENCES_ENABLED, read_lock_fences_enabled);
    SET_FIELD(TEXTURE_OWNER, texture_owner);
    SET_FIELD(WANTS_PROMOTION_HINT, wants_promotion_hint);
    SET_FIELD(PROTECTED_VIDEO, protected_video);
    SET_FIELD(HW_PROTECTED, hw_protected);
    SET_FIELD(POWER_EFFICIENT, power_efficient);
    default:
      DVLOG(2) << __func__ << " Invalid boolean key";
  }
}

void VideoFrameMetadata::SetInteger(Key key, int value) {
  DCHECK_EQ(CAPTURE_COUNTER, key);
  capture_counter = value;
}

void VideoFrameMetadata::SetDouble(Key key, double value) {
  switch (key) {
    SET_FIELD(FRAME_RATE, frame_rate);
    SET_FIELD(RESOURCE_UTILIZATION, resource_utilization);
    SET_FIELD(DEVICE_SCALE_FACTOR, device_scale_factor);
    SET_FIELD(PAGE_SCALE_FACTOR, page_scale_factor);
    SET_FIELD(ROOT_SCROLL_OFFSET_X, root_scroll_offset_x);
    SET_FIELD(ROOT_SCROLL_OFFSET_Y, root_scroll_offset_y);
    SET_FIELD(TOP_CONTROLS_VISIBLE_HEIGHT, top_controls_visible_height);
    SET_FIELD(RTP_TIMESTAMP, rtp_timestamp);
    default:
      DVLOG(2) << __func__ << " Invalid double key";
  }
}

void VideoFrameMetadata::SetRotation(Key key, VideoRotation value) {
  DCHECK_EQ(ROTATION, key);
  rotation = value;
}

void VideoFrameMetadata::SetTimeDelta(Key key, const base::TimeDelta& value) {
  switch (key) {
    SET_FIELD(FRAME_DURATION, frame_duration);
    SET_FIELD(PROCESSING_TIME, processing_time);
    SET_FIELD(WALLCLOCK_FRAME_DURATION, wallclock_frame_duration);
    default:
      DVLOG(2) << __func__ << " Invalid base::TimeDelta key";
  }
}

void VideoFrameMetadata::SetTimeTicks(Key key, const base::TimeTicks& value) {
  switch (key) {
    SET_FIELD(CAPTURE_BEGIN_TIME, capture_begin_time);
    SET_FIELD(CAPTURE_END_TIME, capture_end_time);
    SET_FIELD(REFERENCE_TIME, reference_time);
    SET_FIELD(DECODE_BEGIN_TIME, decode_begin_time);
    SET_FIELD(DECODE_END_TIME, decode_end_time);
    SET_FIELD(RECEIVE_TIME, receive_time);
    default:
      DVLOG(2) << __func__ << " Invalid base::TimeDelta key";
  }
}

void VideoFrameMetadata::SetUnguessableToken(
    Key key,
    const base::UnguessableToken& value) {
  DCHECK_EQ(OVERLAY_PLANE_ID, key);
  overlay_plane_id = value;
}

void VideoFrameMetadata::SetRect(Key key, const gfx::Rect& value) {
  DCHECK_EQ(CAPTURE_UPDATE_RECT, key);
  capture_update_rect = value;
}

#define ASSIGN_BOOL(k, field) \
  case Key::k:                \
    *value = field;           \
    return true

bool VideoFrameMetadata::GetBoolean(Key key, bool* value) const {
  switch (key) {
    ASSIGN_BOOL(ALLOW_OVERLAY, allow_overlay);
    ASSIGN_BOOL(COPY_REQUIRED, copy_required);
    ASSIGN_BOOL(END_OF_STREAM, end_of_stream);
    ASSIGN_BOOL(INTERACTIVE_CONTENT, interactive_content);
    ASSIGN_BOOL(READ_LOCK_FENCES_ENABLED, read_lock_fences_enabled);
    ASSIGN_BOOL(TEXTURE_OWNER, texture_owner);
    ASSIGN_BOOL(WANTS_PROMOTION_HINT, wants_promotion_hint);
    ASSIGN_BOOL(PROTECTED_VIDEO, protected_video);
    ASSIGN_BOOL(HW_PROTECTED, hw_protected);
    ASSIGN_BOOL(POWER_EFFICIENT, power_efficient);
    default:
      return false;
  }
}

bool VideoFrameMetadata::GetInteger(Key key, int* value) const {
  DCHECK(value);
  DCHECK_EQ(CAPTURE_COUNTER, key);

  if (capture_counter)
    *value = capture_counter.value();

  return capture_counter.has_value();
}

#define MAYBE_ASSIGN_AND_RETURN(k, field) \
  case Key::k:                            \
    if (field.has_value()) {              \
      *value = field.value();             \
    }                                     \
    return field.has_value()

bool VideoFrameMetadata::GetDouble(Key key, double* value) const {
  DCHECK(value);

  switch (key) {
    MAYBE_ASSIGN_AND_RETURN(FRAME_RATE, frame_rate);
    MAYBE_ASSIGN_AND_RETURN(RESOURCE_UTILIZATION, resource_utilization);
    MAYBE_ASSIGN_AND_RETURN(DEVICE_SCALE_FACTOR, device_scale_factor);
    MAYBE_ASSIGN_AND_RETURN(PAGE_SCALE_FACTOR, page_scale_factor);
    MAYBE_ASSIGN_AND_RETURN(ROOT_SCROLL_OFFSET_X, root_scroll_offset_x);
    MAYBE_ASSIGN_AND_RETURN(ROOT_SCROLL_OFFSET_Y, root_scroll_offset_y);
    MAYBE_ASSIGN_AND_RETURN(TOP_CONTROLS_VISIBLE_HEIGHT,
                            top_controls_visible_height);
    MAYBE_ASSIGN_AND_RETURN(RTP_TIMESTAMP, rtp_timestamp);
    default:
      return false;
  }
}

bool VideoFrameMetadata::GetRotation(Key key, VideoRotation* value) const {
  DCHECK(value);
  DCHECK_EQ(ROTATION, key);

  if (rotation)
    *value = rotation.value();

  return rotation.has_value();
}

bool VideoFrameMetadata::GetTimeDelta(Key key, base::TimeDelta* value) const {
  DCHECK(value);

  switch (key) {
    MAYBE_ASSIGN_AND_RETURN(FRAME_DURATION, frame_duration);
    MAYBE_ASSIGN_AND_RETURN(PROCESSING_TIME, processing_time);
    MAYBE_ASSIGN_AND_RETURN(WALLCLOCK_FRAME_DURATION, wallclock_frame_duration);
    default:
      return false;
  }
}

bool VideoFrameMetadata::GetTimeTicks(Key key, base::TimeTicks* value) const {
  DCHECK(value);

  switch (key) {
    MAYBE_ASSIGN_AND_RETURN(CAPTURE_BEGIN_TIME, capture_begin_time);
    MAYBE_ASSIGN_AND_RETURN(CAPTURE_END_TIME, capture_end_time);
    MAYBE_ASSIGN_AND_RETURN(REFERENCE_TIME, reference_time);
    MAYBE_ASSIGN_AND_RETURN(DECODE_BEGIN_TIME, decode_begin_time);
    MAYBE_ASSIGN_AND_RETURN(DECODE_END_TIME, decode_end_time);
    MAYBE_ASSIGN_AND_RETURN(RECEIVE_TIME, receive_time);
    default:
      return false;
      ;
  }
}

bool VideoFrameMetadata::GetUnguessableToken(
    Key key,
    base::UnguessableToken* value) const {
  DCHECK(value);
  DCHECK_EQ(key, OVERLAY_PLANE_ID);

  if (overlay_plane_id)
    *value = overlay_plane_id.value();

  return overlay_plane_id.has_value();
}

bool VideoFrameMetadata::GetRect(Key key, gfx::Rect* value) const {
  DCHECK(value);
  DCHECK_EQ(key, CAPTURE_UPDATE_RECT);

  if (capture_update_rect)
    *value = capture_update_rect.value();

  return capture_update_rect.has_value();
}

bool VideoFrameMetadata::IsTrue(Key key) const {
  bool value = false;
  return GetBoolean(key, &value) && value;
}

#define MERGE_FIELD(a, source) \
  if (source->a)               \
    this->a = source->a

void VideoFrameMetadata::MergeMetadataFrom(
    const VideoFrameMetadata* metadata_source) {
  MERGE_FIELD(allow_overlay, metadata_source);
  MERGE_FIELD(capture_begin_time, metadata_source);
  MERGE_FIELD(capture_end_time, metadata_source);
  MERGE_FIELD(capture_counter, metadata_source);
  MERGE_FIELD(capture_update_rect, metadata_source);
  MERGE_FIELD(copy_required, metadata_source);
  MERGE_FIELD(end_of_stream, metadata_source);
  MERGE_FIELD(frame_duration, metadata_source);
  MERGE_FIELD(frame_rate, metadata_source);
  MERGE_FIELD(interactive_content, metadata_source);
  MERGE_FIELD(reference_time, metadata_source);
  MERGE_FIELD(resource_utilization, metadata_source);
  MERGE_FIELD(read_lock_fences_enabled, metadata_source);
  MERGE_FIELD(rotation, metadata_source);
  MERGE_FIELD(texture_owner, metadata_source);
  MERGE_FIELD(wants_promotion_hint, metadata_source);
  MERGE_FIELD(protected_video, metadata_source);
  MERGE_FIELD(hw_protected, metadata_source);
  MERGE_FIELD(overlay_plane_id, metadata_source);
  MERGE_FIELD(power_efficient, metadata_source);
  MERGE_FIELD(device_scale_factor, metadata_source);
  MERGE_FIELD(page_scale_factor, metadata_source);
  MERGE_FIELD(root_scroll_offset_x, metadata_source);
  MERGE_FIELD(root_scroll_offset_y, metadata_source);
  MERGE_FIELD(top_controls_visible_height, metadata_source);
  MERGE_FIELD(decode_begin_time, metadata_source);
  MERGE_FIELD(decode_end_time, metadata_source);
  MERGE_FIELD(processing_time, metadata_source);
  MERGE_FIELD(rtp_timestamp, metadata_source);
  MERGE_FIELD(receive_time, metadata_source);
  MERGE_FIELD(wallclock_frame_duration, metadata_source);
}

}  // namespace media
