// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/services/app_service/public/cpp/preferred_apps_converter.h"

#include "base/json/json_reader.h"
#include "chrome/services/app_service/public/cpp/intent_filter_util.h"
#include "chrome/services/app_service/public/cpp/intent_test_util.h"
#include "chrome/services/app_service/public/cpp/intent_util.h"
#include "chrome/services/app_service/public/cpp/preferred_apps_list.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace {

const char kAppId1[] = "abcdefg";

}  // namespace

class PreferredAppsConverterTest : public testing::Test {
 protected:
  apps::PreferredAppsList preferred_apps_;
};

// Test one simple entry with simple filter.
TEST_F(PreferredAppsConverterTest, ConvertSimpleEntry) {
  GURL filter_url = GURL("https://www.google.com/abc");
  auto intent_filter = apps_util::CreateIntentFilterForUrlScope(filter_url);
  preferred_apps_.AddPreferredApp(kAppId1, intent_filter);

  auto converted_value =
      apps::ConvertPreferredAppsToValue(preferred_apps_.GetReference());

  // Check that each entry is correct.
  ASSERT_EQ(1u, converted_value.GetList().size());
  auto& entry = converted_value.GetList()[0];
  EXPECT_EQ(kAppId1, *entry.FindStringKey(apps::kAppIdKey));

  auto* converted_intent_filter = entry.FindKey(apps::kIntentFilterKey);
  ASSERT_EQ(intent_filter->conditions.size(),
            converted_intent_filter->GetList().size());

  for (size_t i = 0; i < intent_filter->conditions.size(); i++) {
    auto& condition = intent_filter->conditions[i];
    auto& converted_condition = converted_intent_filter->GetList()[i];
    auto& condition_values = condition->condition_values;
    auto converted_condition_values =
        converted_condition.FindKey(apps::kConditionValuesKey)->GetList();

    EXPECT_EQ(static_cast<int>(condition->condition_type),
              converted_condition.FindIntKey(apps::kConditionTypeKey));
    ASSERT_EQ(1u, converted_condition_values.size());
    EXPECT_EQ(condition_values[0]->value,
              *converted_condition_values[0].FindStringKey(apps::kValueKey));
    EXPECT_EQ(static_cast<int>(condition_values[0]->match_type),
              converted_condition_values[0].FindIntKey(apps::kMatchTypeKey));
  }
}

// Test one simple entry with json string.
TEST_F(PreferredAppsConverterTest, ConvertSimpleEntryJson) {
  GURL filter_url = GURL("https://www.google.com/abc");
  auto intent_filter = apps_util::CreateIntentFilterForUrlScope(filter_url);
  preferred_apps_.AddPreferredApp(kAppId1, intent_filter);

  auto converted_value =
      apps::ConvertPreferredAppsToValue(preferred_apps_.GetReference());

  const char expected_output_string[] =
      "[ {\"app_id\": \"abcdefg\","
      "   \"intent_filter\": [ {"
      "      \"condition_type\": 0,"
      "      \"condition_values\": [ {"
      "         \"match_type\": 0,"
      "         \"value\": \"https\""
      "      } ]"
      "   }, {"
      "      \"condition_type\": 1,"
      "      \"condition_values\": [ {"
      "         \"match_type\": 0,"
      "         \"value\": \"www.google.com\""
      "      } ]"
      "   }, {"
      "      \"condition_type\": 2,"
      "      \"condition_values\": [ {"
      "         \"match_type\": 2,"
      "         \"value\": \"/abc\""
      "      } ]"
      "   } ]"
      "} ]";
  base::Optional<base::Value> expected_output =
      base::JSONReader::Read(expected_output_string);
  ASSERT_TRUE(expected_output);
  EXPECT_EQ(expected_output.value(), converted_value);
}
