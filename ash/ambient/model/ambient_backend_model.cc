// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ash/ambient/model/ambient_backend_model.h"

#include "ash/ambient/ambient_constants.h"
#include "ash/ambient/model/ambient_backend_model_observer.h"

namespace ash {

AmbientBackendModel::AmbientBackendModel() {
  SetPhotoRefreshInterval(kPhotoRefreshInterval);
}

AmbientBackendModel::~AmbientBackendModel() = default;

void AmbientBackendModel::AddObserver(AmbientBackendModelObserver* observer) {
  observers_.AddObserver(observer);
}

void AmbientBackendModel::RemoveObserver(
    AmbientBackendModelObserver* observer) {
  observers_.RemoveObserver(observer);
}

void AmbientBackendModel::SetTopics(
    const std::vector<AmbientModeTopic>& topics) {
  topics_ = topics;
  topic_index_ = 0;

  NotifyTopicsChanged();
}

const AmbientModeTopic& AmbientBackendModel::GetNextTopic() {
  DCHECK(!topics_.empty());

  const auto& topic = topics_[topic_index_];
  ++topic_index_;
  if (topic_index_ == topics_.size())
    topic_index_ = 0;

  return topic;
}

bool AmbientBackendModel::ShouldFetchImmediately() const {
  // Prefetch one image |next_image_| for photo transition animation.
  return current_image_.isNull() || next_image_.isNull();
}

void AmbientBackendModel::AddNextImage(const gfx::ImageSkia& image) {
  if (current_image_.isNull()) {
    current_image_ = image;
  } else if (next_image_.isNull()) {
    next_image_ = image;
  } else {
    current_image_ = next_image_;
    next_image_ = image;
  }

  NotifyImagesChanged();
}

base::TimeDelta AmbientBackendModel::GetPhotoRefreshInterval() {
  if (ShouldFetchImmediately())
    return base::TimeDelta();

  return photo_refresh_interval_;
}

void AmbientBackendModel::SetPhotoRefreshInterval(base::TimeDelta interval) {
  photo_refresh_interval_ = interval;
}

void AmbientBackendModel::Clear() {
  topics_.clear();
  topic_index_ = 0;
  current_image_ = gfx::ImageSkia();
  next_image_ = gfx::ImageSkia();
}

gfx::ImageSkia AmbientBackendModel::GetCurrentImage() const {
  return current_image_;
}

gfx::ImageSkia AmbientBackendModel::GetNextImage() const {
  return next_image_;
}

void AmbientBackendModel::UpdateWeatherInfo(
    const gfx::ImageSkia& weather_condition_icon,
    float temperature) {
  weather_condition_icon_ = weather_condition_icon;
  temperature_ = temperature;

  if (!weather_condition_icon.isNull())
    NotifyWeatherInfoUpdated();
}

void AmbientBackendModel::NotifyTopicsChanged() {
  for (auto& observer : observers_)
    observer.OnTopicsChanged();
}

void AmbientBackendModel::NotifyImagesChanged() {
  for (auto& observer : observers_)
    observer.OnImagesChanged();
}

void AmbientBackendModel::NotifyWeatherInfoUpdated() {
  for (auto& observer : observers_)
    observer.OnWeatherInfoUpdated();
}

}  // namespace ash
