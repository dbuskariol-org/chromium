// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/journey/journey_info_json_request.h"

#include <algorithm>
#include <utility>
#include <vector>

#include "base/base64.h"
#include "base/base64url.h"
#include "base/json/json_writer.h"
#include "base/strings/stringprintf.h"
#include "base/values.h"
#include "chrome/browser/android/journey/journey_info_fetcher.h"
#include "chrome/browser/android/proto/batch_get_switcher_journey_from_pageload_request.pb.h"
#include "components/data_use_measurement/core/data_use_user_data.h"
#include "components/strings/grit/components_strings.h"
#include "components/variations/net/variations_http_headers.h"
#include "components/variations/variations_associated_data.h"
#include "net/base/load_flags.h"
#include "net/http/http_response_headers.h"
#include "net/http/http_status_code.h"
#include "net/traffic_annotation/network_traffic_annotation.h"
#include "net/url_request/url_fetcher.h"
#include "net/url_request/url_request_context_getter.h"
#include "third_party/icu/source/common/unicode/uloc.h"
#include "third_party/icu/source/common/unicode/utypes.h"
#include "third_party/protobuf/src/google/protobuf/io/coded_stream.h"

using net::URLFetcher;
using net::URLRequestContextGetter;
using net::HttpRequestHeaders;
using net::URLRequestStatus;

namespace journey {

namespace {

const int k5xxRetries = 2;

const std::string SerializedJourneyRequest(std::vector<int64_t> timestamps) {
  BatchGetSwitcherJourneyFromPageloadRequest request;

  for (const auto timestamp : timestamps) {
    request.add_task_id(timestamp);
  }
  return request.SerializeAsString();
}

}  // namespace

JourneyInfoJsonRequest::JourneyInfoJsonRequest(
    const ParseJSONCallback& callback)
    : parse_json_callback_(callback), weak_ptr_factory_(this) {}

JourneyInfoJsonRequest::~JourneyInfoJsonRequest() {}

void JourneyInfoJsonRequest::Start(CompletedCallback callback) {
  completed_callback_ = std::move(callback);
  url_fetcher_->Start();
}

std::string JourneyInfoJsonRequest::GetResponseString() const {
  std::string response;
  url_fetcher_->GetResponseAsString(&response);
  return response;
}

// URLFetcherDelegate overrides
void JourneyInfoJsonRequest::OnURLFetchComplete(const net::URLFetcher* source) {
  DCHECK_EQ(url_fetcher_.get(), source);
  const URLRequestStatus& status = url_fetcher_->GetStatus();
  int response = url_fetcher_->GetResponseCode();

  if (status.is_success() && response == net::HTTP_OK) {
    std::string json_string;
    bool stores_result_to_string =
        url_fetcher_->GetResponseAsString(&json_string);
    VLOG(0) << "Yusuf OnUrlFetchComplete with HTTP_OK ";
    DCHECK(stores_result_to_string);
    parse_json_callback_.Run(json_string,
                             base::Bind(&JourneyInfoJsonRequest::OnJsonParsed,
                                        weak_ptr_factory_.GetWeakPtr()),
                             base::Bind(&JourneyInfoJsonRequest::OnJsonError,
                                        weak_ptr_factory_.GetWeakPtr()));
  } else {
    if (!status.is_success()) {
      VLOG(0) << "Yusuf Status is not success "
              << base::StringPrintf(" %d", status.error());
    } else {
      VLOG(0) << "Yusuf " << base::StringPrintf(" %d", response);
    }
  }
}

void JourneyInfoJsonRequest::OnJsonParsed(std::unique_ptr<base::Value> result) {
  std::move(completed_callback_).Run(std::move(result));
}

void JourneyInfoJsonRequest::OnJsonError(const std::string& error) {
  VLOG(0) << "Yusuf onJSONError with " << error;
}

JourneyInfoJsonRequest::Builder::Builder()
    : url_(GURL(
          "https://chrome-memex-dev.appspot.com/api/journey_from_pageload")) {}

JourneyInfoJsonRequest::Builder::Builder(JourneyInfoJsonRequest::Builder&&) =
    default;
JourneyInfoJsonRequest::Builder::~Builder() = default;

std::unique_ptr<JourneyInfoJsonRequest> JourneyInfoJsonRequest::Builder::Build()
    const {
  DCHECK(url_request_context_getter_);
  auto request = std::make_unique<JourneyInfoJsonRequest>(parse_json_callback_);
  std::string headers = BuildHeaders();
  request->url_fetcher_ = BuildURLFetcher(request.get(), headers);

  return request;
}

JourneyInfoJsonRequest::Builder&
JourneyInfoJsonRequest::Builder::SetAuthentication(
    const std::string& auth_header) {
  auth_header_ = auth_header;
  return *this;
}

JourneyInfoJsonRequest::Builder& JourneyInfoJsonRequest::Builder::SetTimestamps(
    std::vector<int64_t> timestamps) {
  body_ = SerializedJourneyRequest(timestamps);
  VLOG(0) << "Yusuf url will be " << url_.spec();
  return *this;
}

JourneyInfoJsonRequest::Builder&
JourneyInfoJsonRequest::Builder::SetParseJsonCallback(
    ParseJSONCallback callback) {
  parse_json_callback_ = callback;
  return *this;
}

JourneyInfoJsonRequest::Builder&
JourneyInfoJsonRequest::Builder::SetUrlRequestContextGetter(
    const scoped_refptr<net::URLRequestContextGetter>& context_getter) {
  url_request_context_getter_ = context_getter;
  return *this;
}

std::string JourneyInfoJsonRequest::Builder::BuildHeaders() const {
  net::HttpRequestHeaders headers;
  headers.SetHeader("Content-Type", "application/json; charset=UTF-8");
  if (!auth_header_.empty()) {
    headers.SetHeader("Authorization", auth_header_);
  }
  // Add X-Client-Data header with experiment IDs from field trials.
  // Note: It's OK to pass SignedIn::kNo if it's unknown, as it does not affect
  // transmission of experiments coming from the variations server.
  variations::AppendVariationHeaders(url_, variations::InIncognito::kNo,
                                     variations::SignedIn::kNo, &headers);
  return headers.ToString();
}

std::unique_ptr<net::URLFetcher>
JourneyInfoJsonRequest::Builder::BuildURLFetcher(
    net::URLFetcherDelegate* delegate,
    const std::string& headers) const {
  net::NetworkTrafficAnnotationTag traffic_annotation =
      net::DefineNetworkTrafficAnnotation("ntp_contextual_suggestions_fetch",
                                          R"(
        semantics {
          sender: "New Tab Page Contextual Suggestions Fetch"
          description:
            "Chromium can show contextual suggestions that are related to the "
            "currently visited page on the New Tab page. "
          trigger:
            "Triggered when Home sheet is pulled up."
          data:
            "Only for a white-listed signed-in test user, the URL of the "
            "current tab."
          destination: GOOGLE_OWNED_SERVICE
        }
        policy {
          cookies_allowed: NO
          setting:
            "This feature can be disabled by the flag "
            "contextual-suggestions-carousel."
          chrome_policy {
            NTPContentSuggestionsEnabled {
              NTPContentSuggestionsEnabled: False
            }
          }
        })");
  std::unique_ptr<net::URLFetcher> url_fetcher = net::URLFetcher::Create(
      url_, net::URLFetcher::POST, delegate, traffic_annotation);
  url_fetcher->SetRequestContext(url_request_context_getter_.get());
  url_fetcher->SetLoadFlags(net::LOAD_DO_NOT_SEND_COOKIES |
                            net::LOAD_DO_NOT_SAVE_COOKIES);

  url_fetcher->SetExtraRequestHeaders(headers);
  url_fetcher->SetUploadData("application/x-protobuf", body_);

  // Fetchers are sometimes cancelled because a network change was detected.
  url_fetcher->SetAutomaticallyRetryOnNetworkChanges(3);
  url_fetcher->SetMaxRetriesOn5xx(k5xxRetries);
  return url_fetcher;
}

}  // namespace journey
