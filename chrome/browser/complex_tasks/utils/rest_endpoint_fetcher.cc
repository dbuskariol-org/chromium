// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/complex_tasks/utils/rest_endpoint_fetcher.h"

#include "base/android/callback_android.h"
#include "base/android/jni_array.h"
#include "base/android/jni_string.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_android.h"
#include "chrome/browser/signin/identity_manager_factory.h"
#include "content/public/browser/storage_partition.h"
#include "content/public/browser/web_contents.h"
#include "google_apis/gaia/gaia_urls.h"
#include "jni/RestEndpointFetcher_jni.h"
#include "services/network/public/cpp/simple_url_loader.h"

const char kContentTypeKey[] = "Content-Type";
const char kDeveloperKey[] = "X-Developer-Key";
const int kNumRetries = 3;

RestEndpointFetcher::RestEndpointFetcher(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    Profile* profile,
    std::string oath_consumer_name,
    std::string url,
    std::string method,
    std::string content_type,
    std::vector<std::string> scopes,
    std::string post_data) {
  oath_consumer_name_ = oath_consumer_name;
  profile_ = profile;
  url_ = url;
  method_ = method;
  content_type_ = content_type;
  scopes_ = scopes;
  post_data_ = post_data;
  url_loader_factory_ =
      content::BrowserContext::GetDefaultStoragePartition(profile)
          ->GetURLLoaderFactoryForBrowserProcess();
  Java_RestEndpointFetcher_setNativePtr(env, obj,
                                        reinterpret_cast<intptr_t>(this));
}

RestEndpointFetcher::~RestEndpointFetcher() = default;

void RestEndpointFetcher::Fetch(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    const base::android::JavaParamRef<jobject>& jcallback) {
  identity::IdentityManager* identity_manager =
      IdentityManagerFactory::GetForProfile(profile_);
  identity::ScopeSet oauth_scopes;
  for (auto scope : scopes_) {
    oauth_scopes.insert(scope);
  }
  access_token_fetcher_.reset();

  identity::AccessTokenFetcher::TokenCallback callback = base::BindOnce(
      &RestEndpointFetcher::OnAuthTokenFetched, base::Unretained(this),
      base::android::ScopedJavaGlobalRef<jobject>(env, jcallback));
  access_token_fetcher_ =
      std::make_unique<identity::PrimaryAccountAccessTokenFetcher>(
          oath_consumer_name_, identity_manager, oauth_scopes,
          std::move(callback),
          identity::PrimaryAccountAccessTokenFetcher::Mode::kImmediate);
}

void RestEndpointFetcher::OnAuthTokenFetched(
    const base::android::JavaRef<jobject>& jcaller,
    GoogleServiceAuthError error,
    identity::AccessTokenInfo access_token_info) {
  if (error.state() != GoogleServiceAuthError::NONE) {
    // TODO: (davidjm) come up with some better error reporting
    // https://crbug.com/968209
    base::android::RunStringCallbackAndroid(jcaller,
                                            std::move("There was an error"));
  }

  access_token_ = access_token_info.token;
  access_token_fetcher_.reset();
  net::NetworkTrafficAnnotationTag traffic_annotation =
      NO_TRAFFIC_ANNOTATION_YET;

  auto resource_request = std::make_unique<network::ResourceRequest>();
  resource_request->method = method_;
  resource_request->url = GURL(url_);
  resource_request->allow_credentials = false;
  resource_request->headers.SetHeader(
      net::HttpRequestHeaders::kAuthorization,
      base::StringPrintf("Bearer %s", access_token_.c_str()));
  resource_request->headers.SetHeader(kContentTypeKey, content_type_);
  resource_request->headers.SetHeader(
      kDeveloperKey, GaiaUrls::GetInstance()->oauth2_chrome_client_id());

  simple_url_loader_ = network::SimpleURLLoader::Create(
      std::move(resource_request), traffic_annotation);
  if (method_.compare("POST") == 0)
    simple_url_loader_->AttachStringForUpload(post_data_, content_type_);
  simple_url_loader_->SetRetryOptions(kNumRetries,
                                      network::SimpleURLLoader::RETRY_ON_5XX);

  network::SimpleURLLoader::BodyAsStringCallback body_as_string_callback =
      base::BindOnce(&RestEndpointFetcher::OnResponseFetched,
                     base::Unretained(this),
                     base::android::ScopedJavaGlobalRef<jobject>(jcaller));
  simple_url_loader_->DownloadToStringOfUnboundedSizeUntilCrashAndDie(
      url_loader_factory_.get(), std::move(body_as_string_callback));
}

void RestEndpointFetcher::OnResponseFetched(
    const base::android::JavaRef<jobject>& jcaller,
    std::unique_ptr<std::string> response_body) {
  response_body_ =
      response_body ? std::move(*response_body) : "No response was found";
  base::android::RunStringCallbackAndroid(jcaller, std::move(response_body_));
}

void RestEndpointFetcher::Destroy(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj) {
  delete this;
}

RestEndpointFetcher::Builder::Builder() {}

RestEndpointFetcher::Builder::~Builder() = default;

static void JNI_RestEndpointFetcher_Init(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    const base::android::JavaParamRef<jobject>& jcaller,
    const base::android::JavaParamRef<jobject>& jprofile,
    const base::android::JavaParamRef<jstring>& jurl,
    const base::android::JavaParamRef<jstring>& joath_consumer_name,
    const base::android::JavaParamRef<jstring>& jmethod,
    const base::android::JavaParamRef<jstring>& jcontent_type,
    const base::android::JavaParamRef<jobjectArray>& jscopes,
    const base::android::JavaParamRef<jstring>& jpost_data) {
  Profile* profile = ProfileAndroid::FromProfileAndroid(jprofile);

  RestEndpointFetcher::Builder builder;
  builder.set_env(env);
  builder.set_obj(&obj);
  builder.set_profile(profile);
  builder.set_oath_consumer_name(
      base::android::ConvertJavaStringToUTF8(env, joath_consumer_name));
  builder.set_method(base::android::ConvertJavaStringToUTF8(env, jmethod));
  builder.set_url(base::android::ConvertJavaStringToUTF8(env, jurl));
  builder.set_content_type(
      base::android::ConvertJavaStringToUTF8(env, jcontent_type));

  std::vector<std::string> scopes;
  base::android::AppendJavaStringArrayToStringVector(env, jscopes, &scopes);
  builder.set_scopes(scopes);
  builder.set_post_data(
      base::android::ConvertJavaStringToUTF8(env, jpost_data));
  builder.build();
}
