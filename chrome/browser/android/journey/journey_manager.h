// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_MANAGER_H_
#define CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_MANAGER_H_

#include <memory>

#include "base/android/jni_weak_ref.h"
#include "base/android/scoped_java_ref.h"
#include "base/memory/weak_ptr.h"
#include "base/task/cancelable_task_tracker.h"
#include "components/favicon_base/favicon_types.h"
#include "url/gurl.h"

class Profile;

namespace android {
class TabContentManager;
}

namespace journey {

class AutotabThumbnailFetcher;
class JourneyInfoFetcher;
struct PageLoadInfo;

class JourneyManager {
 public:
  JourneyManager(JNIEnv* env,
                 const base::android::JavaParamRef<jobject>& obj,
                 Profile* profile,
                 android::TabContentManager* tab_content_manager,
                 int selection_type);

  void FetchJourneyInfo(JNIEnv* env,
                        const base::android::JavaParamRef<jobject>& obj,
                        const base::android::JavaParamRef<jlongArray>& j_timestamps);

  void Destroy(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

 private:
  void OnFaviconImageFetched(
      int64_t source_timestamp,
      const std::string& journey_id,
      const PageLoadInfo& pageload,
      const favicon_base::FaviconRawBitmapResult& result);
  void OnImageFetchResult(
      int64_t source_timestamp,
      const std::string& journey_id,
      const PageLoadInfo& pageload,
      const gfx::Image& image);
  bool CheckForValidThumbnailUrl(PageLoadInfo* pageload);
  void OnJourneyInfoFetched(int64_t timestamp,
                            const std::vector<PageLoadInfo>& important_pages,
                            const std::string& journey_id);

  ~JourneyManager();

  Profile* profile_;

  int selection_type_;

  base::CancelableTaskTracker cancelable_task_tracker_for_favicon_;

  JavaObjectWeakGlobalRef weak_java_journey_manager_;

  std::unique_ptr<journey::JourneyInfoFetcher> journey_fetcher_;

  std::unique_ptr<journey::AutotabThumbnailFetcher> thumbnail_fetcher_;

  android::TabContentManager* tab_content_manager_;

  base::WeakPtrFactory<JourneyManager> weak_ptr_factory_;

  DISALLOW_COPY_AND_ASSIGN(JourneyManager);
};

}  // namespace journey

#endif  // CHROME_BROWSER_ANDROID_JOURNEY_JOURNEY_MANAGER_H_
