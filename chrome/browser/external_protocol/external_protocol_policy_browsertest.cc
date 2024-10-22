// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/values.h"
#include "chrome/browser/external_protocol/auto_launch_protocols_policy_handler.h"
#include "chrome/browser/external_protocol/external_protocol_handler.h"
#include "chrome/browser/policy/policy_test_utils.h"
#include "chrome/browser/ui/browser.h"
#include "components/policy/core/common/policy_map.h"
#include "components/policy/policy_constants.h"
#include "content/public/test/browser_test.h"
#include "url/gurl.h"
#include "url/origin.h"

namespace policy {

class ExternalProtocolPolicyBrowserTest : public PolicyTest {};

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsMalformedPolicy) {
  const char kWildcardOrigin[] = "*";
  const char kExampleScheme[] = "custom";

  url::Origin test_origin = url::Origin::Create(GURL("https://example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);

  // Single dictionary for this test case, but erroneously not embedded
  // in a list.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol list with a matching protocol.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set origins list with a wildcard origin matching pattern.
  base::ListValue origins;
  origins.Append(kWildcardOrigin);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsEmptyOriginList) {
  const char kExampleScheme[] = "custom";

  url::Origin test_origin = url::Origin::Create(GURL("https://example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol list with a matching protocol.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an empty origins list.
  base::ListValue origins;
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsWildcardOriginList) {
  const char kWildcardOrigin[] = "*";
  const char kExampleScheme[] = "custom";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with the wildcard origin matching pattern.
  base::ListValue origins;
  origins.Append(kWildcardOrigin);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  url::Origin test_origin = url::Origin::Create(GURL("https://example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsInvalidProtocols) {
  const char kInvalidProtocol1[] = "custom:";
  const char kInvalidProtocol2[] = "custom://";
  const char kInvalidProtocol3[] = "custom//";
  const char kWildcardOrigin[] = "*";
  const char kExampleScheme[] = "custom";

  base::ListValue protocol_origins_map_list;

  // Three dictionaries in the list for this test case.
  base::DictionaryValue protocol_origins_map1;
  base::DictionaryValue protocol_origins_map2;
  base::DictionaryValue protocol_origins_map3;

  // Set invalid protocols, each with the wildcard origin matching pattern.
  protocol_origins_map1.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kInvalidProtocol1);
  base::ListValue origins1;
  origins1.Append(kWildcardOrigin);
  protocol_origins_map1.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                               std::move(origins1));
  protocol_origins_map_list.Append(std::move(protocol_origins_map1));

  protocol_origins_map2.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kInvalidProtocol2);
  base::ListValue origins2;
  origins2.Append(kWildcardOrigin);
  protocol_origins_map2.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                               std::move(origins2));
  protocol_origins_map_list.Append(std::move(protocol_origins_map2));

  protocol_origins_map3.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kInvalidProtocol3);
  base::ListValue origins3;
  origins3.Append(kWildcardOrigin);
  protocol_origins_map3.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                               std::move(origins3));
  protocol_origins_map_list.Append(std::move(protocol_origins_map3));

  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  url::Origin test_origin = url::Origin::Create(GURL("https://example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithMissingScheme) {
  const char kExampleScheme[] = "custom";
  const char kHost[] = "www.example.test";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that matches but is
  // only the host name.
  base::ListValue origins;
  origins.Append(kHost);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  // Test that secure origin matches.
  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that insecure origin matches.
  test_origin = url::Origin::Create(GURL("http://www.example.test"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that different origin does not match.
  test_origin = url::Origin::Create(GURL("http://www.other.test"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithExactHostname) {
  const char kExampleScheme[] = "custom";
  const char kExactHostName[] = ".www.example.test";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that matches exactly
  // but has no scheme.
  base::ListValue origins;
  origins.Append(kExactHostName);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  // Test that secure origin matches.
  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that insecure origin matches.
  test_origin = url::Origin::Create(GURL("http://www.example.test"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that different origin does not match.
  test_origin = url::Origin::Create(GURL("http://www.other.test"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithParentDomain) {
  const char kExampleScheme[] = "custom";
  const char kParentDomain[] = "example.test";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that is the parent
  // domain but should match subdomains.
  base::ListValue origins;
  origins.Append(kParentDomain);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  // Test that a subdomain matches.
  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithWildcardOrigin) {
  const char kExampleScheme[] = "custom";
  const char kProtocolWithWildcardHostname[] = "https://*";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that matches the scheme
  // and all hosts.
  base::ListValue origins;
  origins.Append(kProtocolWithWildcardHostname);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  // Test that secure origin matches.
  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that insecure origin does not match.
  test_origin = url::Origin::Create(GURL("http://www.example.test"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithFullOrigin) {
  const char kExampleScheme[] = "custom";
  const char kFullOrigin[] = "https://www.example.test:443";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that matches the full
  // origin exactly.
  base::ListValue origins;
  origins.Append(kFullOrigin);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  // Test that default HTTPS port 443 matches.
  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that explicit port 443 matches.
  test_origin = url::Origin::Create(GURL("https://www.example.test:443"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::DONT_BLOCK, block_state);

  // Test that explicit other port does not match.
  test_origin = url::Origin::Create(GURL("https://www.example.test:8080"));
  block_state = ExternalProtocolHandler::GetBlockState(
      kExampleScheme, &test_origin, browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithExactParentDomain) {
  const char kExampleScheme[] = "custom";
  const char kExactParentDomain[] = ".example.com";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that doesn't match
  // because it is a parent domain that does not match subdomains.
  base::ListValue origins;
  origins.Append(kExactParentDomain);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  url::Origin test_origin =
      url::Origin::Create(GURL("https://www.example.test"));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

IN_PROC_BROWSER_TEST_F(ExternalProtocolPolicyBrowserTest,
                       AutoLaunchProtocolsOriginPatternWithPath) {
  const char kExampleScheme[] = "custom";
  const char kFullUrlWithPath[] = "https://example.test/home.html";

  base::ListValue protocol_origins_map_list;
  // Single dictionary in the list for this test case.
  base::DictionaryValue protocol_origins_map;
  // Set a protocol to match the test.
  protocol_origins_map.SetStringKey(
      AutoLaunchProtocolsPolicyHandler::kProtocolNameKey, kExampleScheme);
  // Set an origins list with an origin matching pattern that doesn't match
  // because it contains a [/path] element.
  base::ListValue origins;
  origins.Append(kFullUrlWithPath);
  protocol_origins_map.SetKey(AutoLaunchProtocolsPolicyHandler::kOriginListKey,
                              std::move(origins));
  protocol_origins_map_list.Append(std::move(protocol_origins_map));
  PolicyMap policies;
  policies.Set(key::kAutoLaunchProtocolsFromOrigins, POLICY_LEVEL_MANDATORY,
               POLICY_SCOPE_USER, POLICY_SOURCE_CLOUD,
               protocol_origins_map_list.CreateDeepCopy(), nullptr);
  UpdateProviderPolicy(policies);

  url::Origin test_origin = url::Origin::Create(GURL(kFullUrlWithPath));
  ExternalProtocolHandler::BlockState block_state =
      ExternalProtocolHandler::GetBlockState(kExampleScheme, &test_origin,
                                             browser()->profile());
  EXPECT_EQ(ExternalProtocolHandler::UNKNOWN, block_state);
}

}  // namespace policy
