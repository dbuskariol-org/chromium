// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "chrome/updater/server/mac/server.h"

#import <Foundation/Foundation.h>
#include <xpc/xpc.h>

#include "base/mac/scoped_nsobject.h"
#include "base/strings/sys_string_conversions.h"
#import "chrome/updater/server/mac/service_delegate.h"
#import "chrome/updater/update_service.h"
#include "chrome/updater/updater_version.h"

namespace updater {

int RunServer(std::unique_ptr<UpdateService> update_service) {
  @autoreleasepool {
    std::string service_name = MAC_BUNDLE_IDENTIFIER_STRING;
    service_name.append(".UpdaterXPCService");
    base::scoped_nsobject<NSString> updaterMachServiceName(
        base::SysUTF8ToNSString(service_name), base::scoped_policy::RETAIN);
    base::scoped_nsobject<UpdateCheckXPCServiceDelegate> delegate(
        [[UpdateCheckXPCServiceDelegate alloc]
            initWithUpdateService:std::move(update_service)]);

    NSXPCListener* listener =
        [[NSXPCListener alloc] initWithMachServiceName:updaterMachServiceName];
    listener.delegate = delegate.get();

    [listener resume];

    return 0;
  }
}

}  // namespace updater
