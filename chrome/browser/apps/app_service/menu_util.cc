// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/apps/app_service/menu_util.h"

namespace {
int kInvalidRadioGroupId = -1;
}

namespace apps {

void AddCommandItem(uint32_t command_id,
                    uint32_t string_id,
                    apps::mojom::MenuItemsPtr* menu_items) {
  apps::mojom::MenuItemPtr menu_item = apps::mojom::MenuItem::New();
  menu_item->type = apps::mojom::MenuItemType::kCommand;
  menu_item->command_id = command_id;
  menu_item->string_id = string_id;
  menu_item->radio_group_id = kInvalidRadioGroupId;
  (*menu_items)->items.push_back(menu_item.Clone());
}

}  // namespace apps
