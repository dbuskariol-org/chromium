// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/sharing/sharing_message_sender.h"

#include "base/guid.h"
#include "base/task/post_task.h"
#include "chrome/browser/sharing/sharing_constants.h"
#include "chrome/browser/sharing/sharing_metrics.h"
#include "chrome/browser/sharing/sharing_sync_preference.h"
#include "chrome/browser/sharing/sharing_utils.h"
#include "components/send_tab_to_self/target_device_info.h"
#include "components/sync_device_info/local_device_info_provider.h"
#include "content/public/browser/browser_task_traits.h"

SharingMessageSender::SharingMessageSender(
    SharingSyncPreference* sync_prefs,
    syncer::LocalDeviceInfoProvider* local_device_info_provider)
    : sync_prefs_(sync_prefs),
      local_device_info_provider_(local_device_info_provider) {}

SharingMessageSender::~SharingMessageSender() = default;

void SharingMessageSender::SendMessageToDevice(
    const syncer::DeviceInfo& device,
    base::TimeDelta response_timeout,
    chrome_browser_sharing::SharingMessage message,
    DelegateType delegate_type,
    ResponseCallback callback) {
  DCHECK_GE(response_timeout, kAckTimeToLive);
  DCHECK(message.payload_case() !=
         chrome_browser_sharing::SharingMessage::kAckMessage);

  std::string message_guid = base::GenerateGUID();
  send_message_callbacks_.emplace(message_guid, std::move(callback));
  chrome_browser_sharing::MessageType message_type =
      SharingPayloadCaseToMessageType(message.payload_case());
  SharingDevicePlatform receiver_device_platform =
      sync_prefs_->GetDevicePlatform(device.guid());
  base::TimeDelta last_updated_age =
      base::Time::Now() - device.last_updated_timestamp();

  auto delegate_iter = send_delegates_.find(delegate_type);
  if (delegate_iter == send_delegates_.end()) {
    InvokeSendMessageCallback(message_guid, message_type,
                              receiver_device_platform, last_updated_age,
                              SharingSendMessageResult::kInternalError,
                              /*response=*/nullptr);
    return;
  }
  SendMessageDelegate* delegate = delegate_iter->second.get();
  DCHECK(delegate);

  // TODO(crbug/1015411): Here we assume the caller gets the |device| from
  // GetDeviceCandidates, so LocalDeviceInfoProvider is ready. It's better to
  // queue up the message and wait until LocalDeviceInfoProvider is ready.
  const syncer::DeviceInfo* local_device_info =
      local_device_info_provider_->GetLocalDeviceInfo();
  if (!local_device_info) {
    InvokeSendMessageCallback(message_guid, message_type,
                              receiver_device_platform, last_updated_age,
                              SharingSendMessageResult::kInternalError,
                              /*response=*/nullptr);
    return;
  }

  base::PostDelayedTask(
      FROM_HERE, {base::TaskPriority::USER_VISIBLE, content::BrowserThread::UI},
      base::BindOnce(&SharingMessageSender::InvokeSendMessageCallback,
                     weak_ptr_factory_.GetWeakPtr(), message_guid, message_type,
                     receiver_device_platform, last_updated_age,
                     SharingSendMessageResult::kAckTimeout,
                     /*response=*/nullptr),
      response_timeout);

  LogSharingDeviceLastUpdatedAge(message_type, last_updated_age);
  LogSharingVersionComparison(message_type, device.chrome_version());

  message.set_sender_guid(local_device_info->guid());
  message.set_sender_device_name(
      send_tab_to_self::GetSharingDeviceNames(local_device_info).full_name);

  delegate->DoSendMessageToDevice(
      device, response_timeout - kAckTimeToLive, std::move(message),
      base::BindOnce(&SharingMessageSender::OnMessageSent,
                     weak_ptr_factory_.GetWeakPtr(), base::TimeTicks::Now(),
                     message_guid, message_type, receiver_device_platform,
                     last_updated_age));
}

void SharingMessageSender::OnMessageSent(
    base::TimeTicks start_time,
    const std::string& message_guid,
    chrome_browser_sharing::MessageType message_type,
    SharingDevicePlatform receiver_device_platform,
    base::TimeDelta last_updated_age,
    SharingSendMessageResult result,
    base::Optional<std::string> message_id) {
  if (result != SharingSendMessageResult::kSuccessful) {
    InvokeSendMessageCallback(message_guid, message_type,
                              receiver_device_platform, last_updated_age,
                              result,
                              /*response=*/nullptr);
    return;
  }

  send_message_times_.emplace(*message_id, start_time);
  message_guids_.emplace(*message_id, message_guid);
  receiver_device_platform_.emplace(*message_id, receiver_device_platform);
  receiver_last_updated_age_.emplace(*message_id, last_updated_age);
  message_types_.emplace(*message_id, message_type);
}

void SharingMessageSender::OnAckReceived(
    const std::string& message_id,
    std::unique_ptr<chrome_browser_sharing::ResponseMessage> response) {
  auto guid_iter = message_guids_.find(message_id);
  if (guid_iter == message_guids_.end())
    return;

  std::string message_guid = std::move(guid_iter->second);
  message_guids_.erase(guid_iter);

  auto message_types_iter = message_types_.find(message_id);
  DCHECK(message_types_iter != message_types_.end());

  chrome_browser_sharing::MessageType message_type = message_types_iter->second;
  message_types_.erase(message_types_iter);

  auto times_iter = send_message_times_.find(message_id);
  DCHECK(times_iter != send_message_times_.end());

  auto device_platform_iter = receiver_device_platform_.find(message_id);
  DCHECK(device_platform_iter != receiver_device_platform_.end());

  SharingDevicePlatform receiver_device_platform = device_platform_iter->second;
  receiver_device_platform_.erase(device_platform_iter);

  LogSharingMessageAckTime(message_type, receiver_device_platform,
                           base::TimeTicks::Now() - times_iter->second);
  send_message_times_.erase(times_iter);

  auto last_updated_age_iter = receiver_last_updated_age_.find(message_id);
  DCHECK(last_updated_age_iter != receiver_last_updated_age_.end());

  base::TimeDelta last_updated_age = last_updated_age_iter->second;
  receiver_last_updated_age_.erase(last_updated_age_iter);

  InvokeSendMessageCallback(
      message_guid, message_type, receiver_device_platform, last_updated_age,
      SharingSendMessageResult::kSuccessful, std::move(response));
}

void SharingMessageSender::RegisterSendDelegate(
    DelegateType type,
    std::unique_ptr<SendMessageDelegate> delegate) {
  auto result = send_delegates_.emplace(type, std::move(delegate));
  DCHECK(result.second) << "Delegate type already registered";
}

void SharingMessageSender::InvokeSendMessageCallback(
    const std::string& message_guid,
    chrome_browser_sharing::MessageType message_type,
    SharingDevicePlatform receiver_device_platform,
    base::TimeDelta last_updated_age,
    SharingSendMessageResult result,
    std::unique_ptr<chrome_browser_sharing::ResponseMessage> response) {
  auto iter = send_message_callbacks_.find(message_guid);
  if (iter == send_message_callbacks_.end())
    return;

  ResponseCallback callback = std::move(iter->second);
  send_message_callbacks_.erase(iter);
  std::move(callback).Run(result, std::move(response));

  LogSendSharingMessageResult(message_type, receiver_device_platform, result);
  LogSharingDeviceLastUpdatedAgeWithResult(result, last_updated_age);
}
