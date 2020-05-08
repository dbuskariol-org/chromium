// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_EXTENSIONS_API_DEBUGGER_EXTENSION_DEV_TOOLS_INFOBAR_H_
#define CHROME_BROWSER_EXTENSIONS_API_DEBUGGER_EXTENSION_DEV_TOOLS_INFOBAR_H_

#include <map>
#include <string>

#include "base/callback_forward.h"

namespace extensions {
class ExtensionDevToolsClientHost;

// An infobar used to globally warn users that an extension is debugging the
// browser (which has security consequences).
class ExtensionDevToolsInfoBar {
 public:
  // Ensures a global infobar corresponding to the supplied extension is
  // showing and registers |destroyed_callback| with it to be called back on
  // destruction.
  static ExtensionDevToolsInfoBar* Create(
      const std::string& extension_id,
      const std::string& extension_name,
      ExtensionDevToolsClientHost* client_host,
      base::OnceClosure destroyed_callback);

  ExtensionDevToolsInfoBar(const ExtensionDevToolsInfoBar&) = delete;
  ExtensionDevToolsInfoBar& operator=(const ExtensionDevToolsInfoBar&) = delete;

  // Unregisters the callback associated with |client_host|, so it will not be
  // called on infobar destruction.
  void Unregister(ExtensionDevToolsClientHost* client_host);

 private:
  ExtensionDevToolsInfoBar(std::string extension_id,
                           const std::string& extension_name);
  ~ExtensionDevToolsInfoBar();

  void InfoBarDestroyed();

  const std::string extension_id_;
  std::map<ExtensionDevToolsClientHost*, base::OnceClosure> callbacks_;
};

}  // namespace extensions

#endif  // CHROME_BROWSER_EXTENSIONS_API_DEBUGGER_EXTENSION_DEV_TOOLS_INFOBAR_H_
