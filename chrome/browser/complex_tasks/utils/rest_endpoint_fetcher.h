// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_COMPLEX_TASKS_UTILS_REST_ENDPOINT_FETCHER_H_
#define CHROME_BROWSER_COMPLEX_TASKS_UTILS_REST_ENDPOINT_FETCHER_H_

#include <string>
#include <vector>

#include "base/memory/scoped_refptr.h"
#include "services/identity/public/cpp/identity_manager.h"
#include "services/identity/public/cpp/primary_account_access_token_fetcher.h"
#include "services/network/public/cpp/shared_url_loader_factory.h"
#include "services/network/public/cpp/simple_url_loader.h"

namespace base {
class Time;
}

class GoogleServiceAuthError;
class Profile;

using FetchFinishedCallback = base::OnceCallback<void(std::string)>;

class RestEndpointFetcher {
 public:
  RestEndpointFetcher(JNIEnv* env,
                      const base::android::JavaParamRef<jobject>& obj,
                      Profile* profile,
                      std::string oath_consumer_name,
                      std::string url,
                      std::string method,
                      std::string content_type,
                      std::vector<std::string> scopes,
                      int64_t timeout,
                      std::string post_data = "");
  ~RestEndpointFetcher();

  void Fetch(JNIEnv* env,
             const base::android::JavaParamRef<jobject>& obj,
             const base::android::JavaParamRef<jobject>& jcallback);

  void Destroy(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

  class Builder {
   public:
    Builder();
    ~Builder();

    RestEndpointFetcher* build() {
      return new RestEndpointFetcher(env_, *obj_, profile_, oath_consumer_name_,
                                     url_, method_, content_type_, scopes_,
                                     timeout_, post_data_);
    }

    void set_oath_consumer_name(const std::string& oath_consumer_name) {
      oath_consumer_name_ = oath_consumer_name;
    }

    void set_url(const std::string& url) { url_ = url; }

    void set_method(const std::string& method) { method_ = method; }

    void set_content_type(const std::string& content_type) {
      content_type_ = content_type;
    }

    void set_scopes(const std::vector<std::string>& scopes) {
      scopes_ = scopes;
    }

    void set_timeout(int64_t timeout) { timeout_ = timeout; }

    void set_post_data(const std::string& post_data) { post_data_ = post_data; }

    void set_env(JNIEnv* env) { env_ = env; }

    void set_obj(const base::android::JavaParamRef<jobject>* obj) {
      obj_ = obj;
    }

    void set_profile(Profile* profile) { profile_ = profile; }

   private:
    std::string oath_consumer_name_;
    std::string url_;
    std::string method_;
    std::string content_type_;
    std::vector<std::string> scopes_;
    std::string post_data_;
    JNIEnv* env_;
    const base::android::JavaParamRef<jobject>* obj_;
    Profile* profile_;
    int64_t timeout_;

    DISALLOW_COPY_AND_ASSIGN(Builder);
  };

 private:
  std::string access_token_;
  std::string content_type_;
  std::string method_;
  std::string oath_consumer_name_;
  std::string post_data_;
  std::string response_body_;
  std::string url_;
  std::vector<std::string> scopes_;
  Profile* profile_;
  std::unique_ptr<identity::PrimaryAccountAccessTokenFetcher>
      access_token_fetcher_;
  std::unique_ptr<network::SimpleURLLoader> simple_url_loader_;
  scoped_refptr<network::SharedURLLoaderFactory> url_loader_factory_;
  base::Time request_start_time_;
  int64_t timeout_;

  void OnAuthTokenFetched(const base::android::JavaRef<jobject>& jcaller,
                          GoogleServiceAuthError error,
                          identity::AccessTokenInfo access_token_info);
  void OnResponseFetched(const base::android::JavaRef<jobject>& jcaller,
                         std::unique_ptr<std::string> response_body);
};

#endif  // CHROME_BROWSER_COMPLEX_TASKS_UTILS_REST_ENDPOINT_FETCHER_H_
