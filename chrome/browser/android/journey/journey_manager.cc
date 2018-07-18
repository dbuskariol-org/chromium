// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/journey/journey_manager.h"

#include "base/android/callback_android.h"
#include "base/android/jni_array.h"
#include "base/android/jni_string.h"
#include "base/callback.h"
#include "chrome/browser/android/compositor/layer/autotab_layer.h"
#include "chrome/browser/android/compositor/tab_content_manager.h"
#include "chrome/browser/android/journey/autotab_thumbnail_fetcher.h"
#include "chrome/browser/android/journey/journey_info_fetcher.h"
#include "chrome/browser/favicon/favicon_service_factory.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_android.h"
#include "components/favicon/core/favicon_service.h"
#include "components/favicon/core/favicon_util.h"
#include "jni/JourneyManager_jni.h"
#include "ui/gfx/codec/png_codec.h"
#include "ui/gfx/image/image.h"

using base::android::AttachCurrentThread;
using base::android::ConvertUTF16ToJavaString;
using base::android::ConvertUTF8ToJavaString;
using base::android::JavaParamRef;
using base::android::ScopedJavaGlobalRef;
using base::android::ScopedJavaLocalRef;
using android::TabContentManager;

namespace journey {

namespace {
  std::string GetHostFromGURL(GURL gurl) {
    std::string host = gurl.host();
    const std::string prefix("www.");
    const std::string mobile_prefix("mobile.");

    const std::string amp_prefix("https://www.google.com/amp/");
    const std::string amp_s_prefix("https://www.google.com/amp/s/");

    if (gurl.spec().compare(0, amp_s_prefix.length(), amp_s_prefix) == 0) {
      host = gurl.spec().substr(amp_s_prefix.length());
    } else if (gurl.spec().compare(0, amp_prefix.length(), amp_prefix) == 0) {
      host = gurl.spec().substr(amp_prefix.length());
    }

    if (host.compare(0, prefix.length(), prefix) == 0) {
      host = host.substr(prefix.length());
    } else if (host.compare(0, mobile_prefix.length(), mobile_prefix) == 0) {
      host = host.substr(mobile_prefix.length());
    }
    return host;
  }

  GURL GetAMPURLIfNeeded(GURL gurl) {
    std::string url = gurl.spec();
    const std::string amp_prefix("https://www.google.com/amp/");
    const std::string amp_s_prefix("https://www.google.com/amp/s/");

    if (gurl.spec().compare(0, amp_s_prefix.length(), amp_s_prefix) == 0) {
          url = "https://" + gurl.spec().substr(amp_s_prefix.length());
        } else if (gurl.spec().compare(0, amp_prefix.length(), amp_prefix) == 0) {
          url = "http://" + gurl.spec().substr(amp_prefix.length());
        }
    return GURL(url);
  }
}

static jlong JNI_JourneyManager_Init(
    JNIEnv* env,
    const JavaParamRef<jobject>& obj,
    const JavaParamRef<jobject>& j_profile,
    const JavaParamRef<jobject>& j_tab_content_manager,
    jint j_autotabs_selection_type) {
  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile);
  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_content_manager);
  JourneyManager* journey_manager =
      new JourneyManager(env, std::move(obj), profile, tab_content_manager, j_autotabs_selection_type);
  return reinterpret_cast<intptr_t>(journey_manager);
}

JourneyManager::JourneyManager(JNIEnv* env,
                               const JavaParamRef<jobject>& obj,
                               Profile* profile,
                               TabContentManager* tab_content_manager,
                               int selection_type)
    : profile_(profile),
      selection_type_(selection_type),
      weak_java_journey_manager_(env, std::move(obj)),
      tab_content_manager_(tab_content_manager),
      weak_ptr_factory_(this) {
  journey_fetcher_ = std::make_unique<journey::JourneyInfoFetcher>(profile, selection_type_);
  thumbnail_fetcher_ =
      std::make_unique<journey::AutotabThumbnailFetcher>(profile);
}

JourneyManager::~JourneyManager() {}

void JourneyManager::FetchJourneyInfo(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    const base::android::JavaParamRef<jlongArray>& j_timestamps) {
  std::vector<int64_t> timestamps;
  base::android::JavaLongArrayToInt64Vector(env, j_timestamps, &timestamps);
  journey_fetcher_.get()->FetchJourneyInfo(
      timestamps, GURL(),
      base::BindRepeating(&JourneyManager::OnJourneyInfoFetched,
                     weak_ptr_factory_.GetWeakPtr()));
}

void JourneyManager::OnJourneyInfoFetched(
    int64_t source_timestamp,
    const std::vector<PageLoadInfo>& important_pages,
    const std::string& journey_id) {
  if (journey_id.empty()) {
    JNIEnv* env = base::android::AttachCurrentThread();
    Java_JourneyManager_emptyJourneyFetched(
            env, weak_java_journey_manager_.get(env), source_timestamp);
    return;
  }
  VLOG(0) << "Yusuf Journey RECEIVED for timestamp " << source_timestamp
          << " with " << important_pages.size() << " important pages";
  std::map<std::string, PageLoadInfo*> map;

  // Temporary optimizations to make the initial flow smoother.
  // 1) Dont show any autotabs with the same url
  // 2) Dont recreate layers of autotabs we have created
  // 3) Don't let any autotabs to be created without valid thumbnail url.

  for (PageLoadInfo pageload : important_pages) {
    if (map[pageload.url_.spec()]) {
      continue;
    } else {
      map[pageload.url_.spec()] = &pageload;
    }

    if (CheckForValidThumbnailUrl(&pageload)) {
      if (!tab_content_manager_->GetAutoTabLayer(pageload.timestamp_)) {
        VLOG(0) << "Yusuf Fetching " << pageload.timestamp_
                << " and adding to journey list for " << source_timestamp;
        thumbnail_fetcher_.get()->FetchSalientImage(
            pageload.timestamp_, pageload.thumbnail_url_,
            base::BindOnce([](const std::string& image_data) {}),
            base::BindOnce(&JourneyManager::OnImageFetchResult,
                           weak_ptr_factory_.GetWeakPtr(),
                           source_timestamp, journey_id, pageload));
      }

    } else {
      favicon_base::FaviconRawBitmapCallback callback =
          base::BindRepeating(&JourneyManager::OnFaviconImageFetched,
                              weak_ptr_factory_.GetWeakPtr(), source_timestamp,
                              std::move(journey_id), std::move(pageload));
      FaviconServiceFactory::GetForProfile(profile_,
                                           ServiceAccessType::EXPLICIT_ACCESS)
          ->GetRawFaviconForPageURL(
              GetAMPURLIfNeeded(pageload.url_),
              {favicon_base::IconType::kFavicon,
               favicon_base::IconType::kTouchIcon,
               favicon_base::IconType::kTouchPrecomposedIcon,
               favicon_base::IconType::kWebManifestIcon},
              0, true, callback, &cancelable_task_tracker_for_favicon_);
    }
  }
}

void JourneyManager::OnImageFetchResult(
    int64_t source_timestamp,
    const std::string& journey_id,
    const PageLoadInfo& pageload,
    const gfx::Image& image) {
  if (image.IsEmpty() || image.Size().width() <= 1) {
    favicon_base::FaviconRawBitmapCallback callback =
        base::BindRepeating(&JourneyManager::OnFaviconImageFetched,
                            weak_ptr_factory_.GetWeakPtr(), source_timestamp,
                            std::move(journey_id), std::move(pageload));
    FaviconServiceFactory::GetForProfile(profile_,
                                         ServiceAccessType::EXPLICIT_ACCESS)
        ->GetRawFaviconForPageURL(
            GetAMPURLIfNeeded(pageload.url_),
            {favicon_base::IconType::kFavicon,
             favicon_base::IconType::kTouchIcon,
             favicon_base::IconType::kTouchPrecomposedIcon,
             favicon_base::IconType::kWebManifestIcon},
            0, true, callback, &cancelable_task_tracker_for_favicon_);
    return;
  }

  JNIEnv* env = base::android::AttachCurrentThread();
  tab_content_manager_->OnAutoTabResourceFetched(
      pageload.timestamp_, GetHostFromGURL(pageload.url_), pageload.title_, SkBitmap(), image);

  ScopedJavaLocalRef<jstring> j_url =
      ConvertUTF8ToJavaString(env, pageload.url_.spec());
  ScopedJavaLocalRef<jstring> j_id =
      ConvertUTF8ToJavaString(env, journey_id);
  Java_JourneyManager_addAutoTabForTimestamp(
      env, weak_java_journey_manager_.get(env), source_timestamp,
      pageload.timestamp_, j_url, j_id);
}

void JourneyManager::OnFaviconImageFetched(
    int64_t source_timestamp,
    const std::string& journey_id,
    const PageLoadInfo& pageload,
    const favicon_base::FaviconRawBitmapResult& result) {
  if (result.is_valid()) {
    SkBitmap favicon_bitmap;
    gfx::PNGCodec::Decode(result.bitmap_data->front(),
                          result.bitmap_data->size(), &favicon_bitmap);

    tab_content_manager_->OnAutoTabResourceFetched(
        pageload.timestamp_, GetHostFromGURL(pageload.url_), pageload.title_, favicon_bitmap,
        gfx::Image());

    JNIEnv* env = base::android::AttachCurrentThread();
    ScopedJavaLocalRef<jstring> j_url =
        ConvertUTF8ToJavaString(env, pageload.url_.spec());
    ScopedJavaLocalRef<jstring> j_id = ConvertUTF8ToJavaString(env, journey_id);
    Java_JourneyManager_addAutoTabForTimestamp(
        env, weak_java_journey_manager_.get(env), source_timestamp,
        pageload.timestamp_, j_url, j_id);
  }
}

bool JourneyManager::CheckForValidThumbnailUrl(PageLoadInfo* pageload) {
  if (pageload->thumbnail_url_.is_valid())
    return true;

  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_url =
      ConvertUTF8ToJavaString(env, pageload->url_.spec());

  std::string obtained_thumbnail_url =
      ConvertJavaStringToUTF8(Java_JourneyManager_getThumbnailUrlForUrl(
          env, weak_java_journey_manager_.get(env), j_url));
  GURL obtained_gurl = GURL(obtained_thumbnail_url);
  if (obtained_gurl.is_valid()) {
    pageload->thumbnail_url_ = obtained_gurl;
    return true;
  }

  return false;
}

void JourneyManager::Destroy(JNIEnv* env, const JavaParamRef<jobject>& obj) {
  delete this;
}

}  // namespace journey
