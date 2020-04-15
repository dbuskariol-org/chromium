// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#include <memory>

#include "chrome/services/app_service/public/cpp/preferred_apps_converter.h"
#include "chrome/services/app_service/public/mojom/types.mojom.h"

namespace {

base::Value ConvertConditionValueToValue(
    const apps::mojom::ConditionValuePtr& condition_value) {
  base::Value condition_value_dict(base::Value::Type::DICTIONARY);
  condition_value_dict.SetStringKey(apps::kValueKey, condition_value->value);
  condition_value_dict.SetIntKey(apps::kMatchTypeKey,
                                 static_cast<int>(condition_value->match_type));
  return condition_value_dict;
}

base::Value ConvertConditionToValue(
    const apps::mojom::ConditionPtr& condition) {
  base::Value condition_dict(base::Value::Type::DICTIONARY);
  condition_dict.SetIntKey(apps::kConditionTypeKey,
                           static_cast<int>(condition->condition_type));
  base::Value condition_values_list(base::Value::Type::LIST);
  for (auto& condition_value : condition->condition_values) {
    condition_values_list.Append(ConvertConditionValueToValue(condition_value));
  }
  condition_dict.SetKey(apps::kConditionValuesKey,
                        std::move(condition_values_list));
  return condition_dict;
}

base::Value ConvertIntentFilterToValue(
    const apps::mojom::IntentFilterPtr& intent_filter) {
  base::Value intent_filter_value(base::Value::Type::LIST);
  for (auto& condition : intent_filter->conditions) {
    intent_filter_value.Append(ConvertConditionToValue(condition));
  }
  return intent_filter_value;
}

}  // namespace

namespace apps {

const char kConditionTypeKey[] = "condition_type";
const char kConditionValuesKey[] = "condition_values";
const char kValueKey[] = "value";
const char kMatchTypeKey[] = "match_type";
const char kAppIdKey[] = "app_id";
const char kIntentFilterKey[] = "intent_filter";

base::Value ConvertPreferredAppsToValue(
    const PreferredAppsList::PreferredApps& preferred_apps) {
  base::Value preferred_apps_value(base::Value::Type::LIST);
  for (auto& preferred_app : preferred_apps) {
    base::Value preferred_app_dict(base::Value::Type::DICTIONARY);
    preferred_app_dict.SetKey(
        kIntentFilterKey,
        ConvertIntentFilterToValue(preferred_app->intent_filter));
    preferred_app_dict.SetStringKey(kAppIdKey, preferred_app->app_id);
    preferred_apps_value.Append(std::move(preferred_app_dict));
  }
  return preferred_apps_value;
}

}  // namespace apps
