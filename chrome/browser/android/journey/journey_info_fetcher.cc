// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/journey/journey_info_fetcher.h"

#include "base/json/json_writer.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "base/task/post_task.h"
#include "base/time/default_clock.h"
#include "base/time/time.h"
#include "base/values.h"
#include "chrome/browser/signin/identity_manager_factory.h"
#include "components/ntp_snippets/category.h"
#include "components/strings/grit/components_strings.h"
#include "content/public/common/service_manager_connection.h"
#include "net/url_request/url_fetcher.h"
#include "net/url_request/url_request_status.h"
#include "services/data_decoder/public/cpp/safe_json_parser.h"
#include "services/identity/public/cpp/identity_manager.h"
#include "services/identity/public/cpp/primary_account_access_token_fetcher.h"
#include "ui/base/l10n/l10n_util.h"

using net::HttpRequestHeaders;
using net::URLFetcher;
using net::URLRequestContextGetter;

namespace journey {

namespace {
const char kChromeMemexScope[] = "https://www.googleapis.com/auth/chromememex";
}  // namespace

PageLoadInfo::PageLoadInfo(int64_t timestamp,
                           GURL url,
                           GURL thumbnail_url,
                           std::string title)
    : timestamp_(timestamp),
      url_(url),
      thumbnail_url_(thumbnail_url),
      title_(title) {}

PageLoadInfo::PageLoadInfo(const PageLoadInfo& pageload)
    : timestamp_(pageload.timestamp_),
      url_(pageload.url_),
      thumbnail_url_(pageload.thumbnail_url_),
      title_(pageload.title_) {}

PageLoadInfo::~PageLoadInfo() = default;

JourneyInfoFetcher::JourneyInfoFetcher(Profile* profile, int selection_type) {
  selection_type_ = selection_type;
  url_request_context_getter_ = profile->GetRequestContext();
  identity_manager_ = IdentityManagerFactory::GetForProfile(profile);
}

JourneyInfoFetcher::~JourneyInfoFetcher() = default;

void JourneyInfoFetcher::FetchJourneyInfo(
    std::vector<int64_t> timestamps,
    const GURL& url,
    JourneyInfoAvailableCallback callback) {
  if (token_fetcher_) {
    return;
  }

  OAuth2TokenService::ScopeSet scopes{kChromeMemexScope};
  token_fetcher_ = std::make_unique<identity::PrimaryAccountAccessTokenFetcher>(
      "journey_info", identity_manager_, scopes,
      base::BindOnce(&JourneyInfoFetcher::AccessTokenFetchFinished,
                     base::Unretained(this)),
      identity::PrimaryAccountAccessTokenFetcher::Mode::kWaitUntilAvailable);
  pending_requests_.emplace(timestamps, std::move(callback));
}

void JourneyInfoFetcher::AccessTokenFetchFinished(
    GoogleServiceAuthError error,
    identity::AccessTokenInfo access_token_info) {
  DCHECK(token_fetcher_);
  std::unique_ptr<identity::PrimaryAccountAccessTokenFetcher>
      token_fetcher_deleter(std::move(token_fetcher_));

  if (error.state() != GoogleServiceAuthError::NONE) {
    AccessTokenError(error);
    return;
  }

  DCHECK(!access_token_info.token.empty());

  while (!pending_requests_.empty()) {
    std::pair<std::vector<int64_t>, JourneyInfoAvailableCallback>
        timestamp_and_callback = std::move(pending_requests_.front());
    pending_requests_.pop();
    StartRequest(timestamp_and_callback.first,
                 std::move(timestamp_and_callback.second),
                 access_token_info.token);
  }
}

void JourneyInfoFetcher::AccessTokenError(const GoogleServiceAuthError& error) {
  DCHECK_NE(error.state(), GoogleServiceAuthError::NONE);
  LOG(WARNING) << "JourneyInfoFetcher::AccessTokenError "
                  "Unable to get token: "
               << error.ToString();

  while (!pending_requests_.empty()) {
    pending_requests_.pop();
  }
}

void JourneyInfoFetcher::StartRequest(std::vector<int64_t> timestamps,
                                      JourneyInfoAvailableCallback callback,
                                      const std::string& oauth_access_token) {
  JourneyInfoJsonRequest::Builder builder;
  builder
      .SetParseJsonCallback(
          base::Bind(&data_decoder::SafeJsonParser::Parse, nullptr))
      .SetTimestamps(timestamps)
      .SetUrlRequestContextGetter(url_request_context_getter_)
      .SetAuthentication(
          base::StringPrintf("bearer %s", oauth_access_token.c_str()));
  auto json_request = builder.Build();
  json_request.get()->Start(base::BindOnce(
      &JourneyInfoFetcher::JsonRequestDone, base::Unretained(this),
      std::move(json_request), std::move(callback)));
}

void JourneyInfoFetcher::JsonRequestDone(
    std::unique_ptr<JourneyInfoJsonRequest> request,
    JourneyInfoAvailableCallback callback,
    std::unique_ptr<base::Value> result) {
  if (!result->is_list() || result->GetList().size() == 0) {
    VLOG(0) << "Yusuf Empty journey info returned ";
    return;
  }
  VLOG(0) << "Selection type is " << selection_type_;
  const base::ListValue* journeys;
  result->GetAsList(&journeys);
  for (const auto& journey : *journeys) {
    const base::DictionaryValue* dict = nullptr;
    journey.GetAsDictionary(&dict);

    const base::ListValue* source_task_id;
    dict->FindKey("source_task_id")->GetAsList(&source_task_id);

    std::string journey_id = "";
    std::vector<PageLoadInfo> pageloads;

    if (dict->FindKey("status")->GetString().compare(
            "STATUS_PAGELOAD_NOT_FOUND") == -1) {
      const base::ListValue* autotabs_list;
      if (selection_type_ != -1 &&
          dict->FindKey("experimental_autotabs")->GetList().size() >
              (unsigned int)selection_type_ &&
          dict->FindKey("experimental_autotabs")
              ->GetList()[selection_type_]
              .FindKey("pageloads")) {
        dict->FindKey("experimental_autotabs")
            ->GetList()[selection_type_]
            .FindKey("pageloads")
            ->GetAsList(&autotabs_list);
        VLOG(0) << "Selecting experimental autotabs " << selection_type_;
      } else {
        if (!dict->FindKey("default_autotabs")->FindKey("pageloads")) {
          continue;
        }
        dict->FindKey("default_autotabs")
            ->FindKey("pageloads")
            ->GetAsList(&autotabs_list);
      }

      journey_id = dict->FindKey("journey_id")->GetString();
      VLOG(0) << "Yusuf Journey ID is found and is " << journey_id;

      JsonToImportantPageLoads(autotabs_list, &pageloads);
    }

    for (const auto& source : *source_task_id) {
      int64_t source_timestamp = std::stoll(source.GetString());
      VLOG(0) << "Yusuf Source timestamp is found and is " << source_timestamp
              << " also pageloads size is " << pageloads.size();
      callback.Run(source_timestamp, pageloads, journey_id);
    }
  }
}

bool JourneyInfoFetcher::JsonToImportantPageLoads(
    const base::ListValue* important_pageloads,
    ImportantPages* important_pages) {
  for (const auto& page : *important_pageloads) {
    const base::DictionaryValue* pageload = nullptr;
    page.GetAsDictionary(&pageload);
    if (pageload->HasKey("timestamp_us") && pageload->HasKey("url") &&
        pageload->HasKey("title")) {
      int64_t timestamp =
          std::stoll(pageload->FindKey("timestamp_us")->GetString());
      VLOG(0) << "Yusuf Adding page info for " << timestamp;
      GURL thumbnail_url = GURL();
      if (pageload->HasKey("image")) {
        thumbnail_url = GURL(
            pageload->FindKey("image")->FindKey("thumbnail_url")->GetString());
      } else {
        VLOG(0) << "Yusuf Image can't be retrieved";
      }

      std::string url = pageload->FindKey("url")->GetString();
      std::string title =
          pageload->FindKey("title")->FindKey("title")->GetString();
      important_pages->push_back(
          PageLoadInfo(timestamp, GURL(url), thumbnail_url, title));
    } else {
      VLOG(0) << "Yusuf Rejecting page info";
      if (!pageload->HasKey("timestamp_us"))
        VLOG(0) << "Yusuf Timestamp can't be retrieved";
      if (!pageload->HasKey("url"))
        VLOG(0) << "Yusuf Url can't be retrieved";
      if (!pageload->HasKey("title"))
        VLOG(0) << "Yusuf Title can't be retrieved";
    }
  }
  return true;
}

}  // namespace journey
