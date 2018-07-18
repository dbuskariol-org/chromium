// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_JSON_REQUEST_H_
#define CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_JSON_REQUEST_H_

#include <memory>
#include <string>
#include <utility>

#include "base/callback.h"
#include "base/memory/weak_ptr.h"
#include "net/http/http_request_headers.h"
#include "net/url_request/url_fetcher_delegate.h"
#include "url/gurl.h"

namespace base {
class Value;
}  // namespace base

namespace net {
class URLRequestContextGetter;
}  // namespace net

namespace journey {

// A request to query journey info.
class JourneyInfoJsonRequest : public net::URLFetcherDelegate {
  // Callbacks for JSON parsing to allow injecting platform-dependent code.
  using SuccessCallback =
      base::Callback<void(std::unique_ptr<base::Value> result)>;
  using ErrorCallback = base::Callback<void(const std::string& error)>;
  using ParseJSONCallback =
      base::Callback<void(const std::string& raw_json_string,
                          const SuccessCallback& success_callback,
                          const ErrorCallback& error_callback)>;
  using CompletedCallback =
      base::OnceCallback<void(std::unique_ptr<base::Value> result)>;

 public:
  // Builds authenticated and non-authenticated TaskInfoJsonRequests.
  class Builder {
   public:
    Builder();
    Builder(Builder&&);
    ~Builder();

    // Builds a Request object that contains all data to fetch new snippets.
    std::unique_ptr<JourneyInfoJsonRequest> Build() const;

    Builder& SetAuthentication(const std::string& auth_header);
    Builder& SetParseJsonCallback(ParseJSONCallback callback);
    Builder& SetUrlRequestContextGetter(
        const scoped_refptr<net::URLRequestContextGetter>& context_getter);
    Builder& SetContentUrl(const GURL& url);
    Builder& SetTimestamps(std::vector<int64_t> timestamps);

   private:
    std::string BuildHeaders() const;
    std::unique_ptr<net::URLFetcher> BuildURLFetcher(
        net::URLFetcherDelegate* request,
        const std::string& headers) const;

    std::string auth_header_;
    std::string body_;
    ParseJSONCallback parse_json_callback_;
    GURL url_;
    scoped_refptr<net::URLRequestContextGetter> url_request_context_getter_;

    DISALLOW_COPY_AND_ASSIGN(Builder);
  };

  JourneyInfoJsonRequest(const ParseJSONCallback& callback);
  JourneyInfoJsonRequest(JourneyInfoJsonRequest&&);
  ~JourneyInfoJsonRequest() override;

  void Start(CompletedCallback callback);

  std::string GetResponseString() const;

 private:
  // URLFetcherDelegate implementation.
  void OnURLFetchComplete(const net::URLFetcher* source) override;
  void OnJsonParsed(std::unique_ptr<base::Value> result);
  void OnJsonError(const std::string& error);

  // The fetcher for downloading the snippets. Only non-null if a fetch is
  // currently ongoing.
  std::unique_ptr<net::URLFetcher> url_fetcher_;

  // This callback is called to parse a json string. It contains callbacks for
  // error and success cases.
  ParseJSONCallback parse_json_callback_;

  CompletedCallback completed_callback_;

  base::WeakPtrFactory<JourneyInfoJsonRequest> weak_ptr_factory_;

  DISALLOW_COPY_AND_ASSIGN(JourneyInfoJsonRequest);
};

}  // namespace journey

#endif  // CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_INFO_JSON_REQUEST_H_
