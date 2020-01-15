// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_SHARING_SHARING_MESSAGE_SENDER_H_
#define CHROME_BROWSER_SHARING_SHARING_MESSAGE_SENDER_H_

#include <map>
#include <memory>
#include <string>

#include "base/callback.h"
#include "base/memory/weak_ptr.h"
#include "base/optional.h"
#include "base/time/time.h"

namespace chrome_browser_sharing {
enum MessageType : int;
class ResponseMessage;
class SharingMessage;
}  // namespace chrome_browser_sharing

namespace syncer {
class DeviceInfo;
class LocalDeviceInfoProvider;
}  // namespace syncer

class SharingSyncPreference;
enum class SharingDevicePlatform;
enum class SharingSendMessageResult;

class SharingMessageSender {
 public:
  using ResponseCallback = base::OnceCallback<void(
      SharingSendMessageResult,
      std::unique_ptr<chrome_browser_sharing::ResponseMessage>)>;

  // Delegate class used to swap the actual message sending implementation.
  class SendMessageDelegate {
   public:
    using SendMessageCallback =
        base::OnceCallback<void(SharingSendMessageResult,
                                base::Optional<std::string>)>;
    virtual ~SendMessageDelegate() = default;

    virtual void DoSendMessageToDevice(
        const syncer::DeviceInfo& device,
        base::TimeDelta time_to_live,
        chrome_browser_sharing::SharingMessage message,
        SendMessageCallback callback) = 0;
  };

  // Delegate type used to send a message.
  enum class DelegateType {
    kFCM,
  };

  SharingMessageSender(
      SharingSyncPreference* sync_prefs,
      syncer::LocalDeviceInfoProvider* local_device_info_provider);
  SharingMessageSender(const SharingMessageSender&) = delete;
  SharingMessageSender& operator=(const SharingMessageSender&) = delete;
  virtual ~SharingMessageSender();

  virtual void SendMessageToDevice(
      const syncer::DeviceInfo& device,
      base::TimeDelta response_timeout,
      chrome_browser_sharing::SharingMessage message,
      DelegateType delegate_type,
      ResponseCallback callback);

  virtual void OnAckReceived(
      chrome_browser_sharing::MessageType message_type,
      const std::string& message_id,
      std::unique_ptr<chrome_browser_sharing::ResponseMessage> response);

  // Registers the given |delegate| to send messages when SendMessageToDevice is
  // called with |type|.
  void RegisterSendDelegate(DelegateType type,
                            std::unique_ptr<SendMessageDelegate> delegate);

 private:
  void OnMessageSent(base::TimeTicks start_time,
                     const std::string& message_guid,
                     chrome_browser_sharing::MessageType message_type,
                     SharingDevicePlatform receiver_device_platform,
                     base::TimeDelta last_updated_age,
                     SharingSendMessageResult result,
                     base::Optional<std::string> message_id);

  void InvokeSendMessageCallback(
      const std::string& message_guid,
      chrome_browser_sharing::MessageType message_type,
      SharingDevicePlatform receiver_device_platform,
      base::TimeDelta last_updated_age,
      SharingSendMessageResult result,
      std::unique_ptr<chrome_browser_sharing::ResponseMessage> response);

  SharingSyncPreference* sync_prefs_;
  syncer::LocalDeviceInfoProvider* local_device_info_provider_;

  // Map of random GUID to SendMessageCallback.
  std::map<std::string, ResponseCallback> send_message_callbacks_;
  // Map of FCM message_id to time at start of send message request to FCM.
  std::map<std::string, base::TimeTicks> send_message_times_;
  // Map of FCM message_id to random GUID.
  std::map<std::string, std::string> message_guids_;
  // Map of FCM message_id to platform of receiver device for metrics.
  std::map<std::string, SharingDevicePlatform> receiver_device_platform_;
  // Map of FCM message_id to age of last updated timestamp of receiver device.
  std::map<std::string, base::TimeDelta> receiver_last_updated_age_;

  // Registered delegates to send messages.
  std::map<DelegateType, std::unique_ptr<SendMessageDelegate>> send_delegates_;

  base::WeakPtrFactory<SharingMessageSender> weak_ptr_factory_{this};
};

#endif  // CHROME_BROWSER_SHARING_SHARING_MESSAGE_SENDER_H_
