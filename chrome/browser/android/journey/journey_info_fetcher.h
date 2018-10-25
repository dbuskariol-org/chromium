// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_FETCHER_H_
#define CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_FETCHER_H_

#include <memory>
#include <string>
#include <vector>

#include "base/callback.h"
#include "base/containers/queue.h"
#include "base/optional.h"
#include "base/time/clock.h"
#include "chrome/browser/android/journey/journey_info_json_request.h"
#include "chrome/browser/profiles/profile.h"
#include "net/url_request/url_request_context_getter.h"
#include "services/identity/public/cpp/primary_account_access_token_fetcher.h"

class PrefService;

namespace identity {
class IdentityManager;
}

namespace journey {
struct PageLoadInfo {
  PageLoadInfo(int64_t timestamp,
               GURL url,
               GURL thumbnail_url,
               std::string title);
  PageLoadInfo(const PageLoadInfo& pageload);
  ~PageLoadInfo();

  int64_t timestamp_;
  GURL url_;
  GURL thumbnail_url_;
  std::string title_;
};

using ImportantPages = std::vector<PageLoadInfo>;

using JourneyInfoAvailableCallback = base::RepeatingCallback<
    void(int64_t, const ImportantPages&, const std::string&)>;

class JourneyInfoFetcher {
 public:
  JourneyInfoFetcher(Profile* profile, int selection_type);
  ~JourneyInfoFetcher();

  void FetchJourneyInfo(std::vector<int64_t> timestamps,
                        const GURL& url,
                        JourneyInfoAvailableCallback callback);

 private:
  void StartRequest(std::vector<int64_t> timestamps,
                    JourneyInfoAvailableCallback callback,
                    const std::string& oauth_access_token);

  void AccessTokenFetchFinished(GoogleServiceAuthError error,
                                identity::AccessTokenInfo access_token);

  void AccessTokenError(const GoogleServiceAuthError& error);

  void JsonRequestDone(std::unique_ptr<JourneyInfoJsonRequest> request,
                       JourneyInfoAvailableCallback callback,
                       std::unique_ptr<base::Value> result);

  bool JsonToImportantPageLoads(const base::ListValue* parsed,
                                ImportantPages* important_pages);

  identity::IdentityManager* identity_manager_;

  // Holds the URL request context.
  scoped_refptr<net::URLRequestContextGetter> url_request_context_getter_;

  GURL fetch_url_;

  int selection_type_;

  std::unique_ptr<identity::PrimaryAccountAccessTokenFetcher> token_fetcher_;

  // Stores requests that wait for an access token.
  base::queue<std::pair<std::vector<int64_t>, JourneyInfoAvailableCallback>>
      pending_requests_;

  DISALLOW_COPY_AND_ASSIGN(JourneyInfoFetcher);
};
}  // namespace journey

#endif  // CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_FETCHER_H_
