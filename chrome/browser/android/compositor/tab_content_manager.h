// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_COMPOSITOR_TAB_CONTENT_MANAGER_H_
#define CHROME_BROWSER_ANDROID_COMPOSITOR_TAB_CONTENT_MANAGER_H_

#include <jni.h>

#include <map>
#include <memory>
#include <string>

#include "base/android/jni_android.h"
#include "base/android/jni_weak_ref.h"
#include "base/android/scoped_java_ref.h"
#include "base/containers/flat_map.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/task/cancelable_task_tracker.h"
#include "cc/layers/ui_resource_layer.h"
#include "chrome/browser/android/thumbnail/thumbnail_cache.h"
#include "components/favicon_base/favicon_callback.h"
#include "components/favicon_base/favicon_types.h"

class Profile;

using base::android::ScopedJavaLocalRef;

namespace cc {
class Layer;
}

namespace gfx {
class Image;
}

namespace ui {
class UIResourceProvider;
}

namespace android {

class ThumbnailLayer;
class TabGroupLayer;

// A native component of the Java TabContentManager class.
class TabContentManager : public ThumbnailCacheObserver {
 public:
  static TabContentManager* FromJavaObject(
      const base::android::JavaRef<jobject>& jobj);

  TabContentManager(JNIEnv* env,
                    jobject obj,
                    jint default_cache_size,
                    jint approximation_cache_size,
                    jint compression_queue_max_size,
                    jint write_queue_max_size,
                    jboolean use_approximation_thumbnail,
                    jfloat dp_to_px);

  virtual ~TabContentManager();

  void Destroy(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);

  void SetUIResourceProvider(ui::UIResourceProvider* ui_resource_provider);

  // Get the live layer from the cache.
  scoped_refptr<cc::Layer> GetLiveLayer(int tab_id);

  scoped_refptr<ThumbnailLayer> GetStaticLayer(int tab_id);

  void SetTabInfoLayer(const scoped_refptr<cc::Layer>& layer);
  scoped_refptr<cc::Layer> GetTabInfoLayer();

  // Get the static thumbnail from the cache, or the NTP.
  scoped_refptr<ThumbnailLayer> GetOrCreateStaticLayer(int tab_id,
                                                       bool force_disk_read);

  // Should be called when a tab gets a new live layer that should be served
  // by the cache to the CompositorView.
  void AttachLiveLayer(int tab_id, scoped_refptr<cc::Layer> layer);

  // Should be called when a tab removes a live layer because it should no
  // longer be served by the CompositorView.  If |layer| is NULL, will
  // make sure all live layers are detached.
  void DetachLiveLayer(int tab_id, scoped_refptr<cc::Layer> layer);

  scoped_refptr<cc::UIResourceLayer> CreateTabGroupLabelLayer(float width);

  // JNI methods.
  jboolean HasFullCachedThumbnail(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& obj,
      jint tab_id);
  void CacheTab(JNIEnv* env,
                const base::android::JavaParamRef<jobject>& obj,
                const base::android::JavaParamRef<jobject>& tab,
                jfloat thumbnail_scale);
  void CacheTabWithBitmap(JNIEnv* env,
                          const base::android::JavaParamRef<jobject>& obj,
                          const base::android::JavaParamRef<jobject>& tab,
                          const base::android::JavaParamRef<jobject>& bitmap,
                          jfloat thumbnail_scale);
  void InvalidateIfChanged(JNIEnv* env,
                           const base::android::JavaParamRef<jobject>& obj,
                           jint tab_id,
                           const base::android::JavaParamRef<jstring>& jurl);
  void UpdateVisibleIds(JNIEnv* env,
                        const base::android::JavaParamRef<jobject>& obj,
                        const base::android::JavaParamRef<jintArray>& priority,
                        jint primary_tab_id);
  void NativeRemoveTabThumbnail(int tab_id);

  void RemoveTabThumbnail(JNIEnv* env,
                          const base::android::JavaParamRef<jobject>& obj,
                          jint tab_id);
  void OnUIResourcesWereEvicted();

  // ThumbnailCacheObserver implementation;
  void OnFinishedThumbnailRead(TabId tab_id) override;

  // ---------------------------------------------------------------------------
  // Tab Group methods
  // ---------------------------------------------------------------------------
  scoped_refptr<cc::UIResourceLayer> GetSelectedTabGroupTabLayer(float width,
                                                                 float height);
  scoped_refptr<cc::UIResourceLayer> CreateTabGroupCreationLayer(float width);

  scoped_refptr<TabGroupLayer> CreateTabGroupAddTabLayer(
      cc::UIResourceId add_resource_id);

  void OnTabGroupResourceFetched(int64_t tab_id,
                                 const std::string& url,
                                 const std::string& title,
                                 const SkBitmap& favicon_bitmap,
                                 const gfx::Image& image,
                                 Profile* profile);
  void CacheTabAsTabGroupTab(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& obj,
      jint tab_id,
      jstring url,
      jstring title,
      const base::android::JavaParamRef<jobject>& j_profile);
  void RemoveTabGroupTabFromCache(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& obj,
      jint tab_id);
  scoped_refptr<TabGroupLayer> GetTabGroupLayer(int64_t tab_id);
  const SkBitmap CreateDummyBitmapForTabGroupTab(int width, int height);
  void UpdateTabGroupTabFavicon(int tab_id, std::string url, Profile* profile);
  void OnFaviconImageFetched(
      int tab_id,
      const favicon_base::FaviconRawBitmapResult& result);
  void UpdateTabGroupTabFavicon(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& obj,
      jint tab_id,
      jstring url,
      const base::android::JavaParamRef<jobject>& j_profile);
  void UpdateTabGroupTabTitle(JNIEnv* env,
                              const base::android::JavaParamRef<jobject>& obj,
                              jint tab_id,
                              jstring title);
  void UpdateTabGroupTabUrl(JNIEnv* env,
                            const base::android::JavaParamRef<jobject>& obj,
                            jint tab_id,
                            jstring url);

  void ClearTabInfoLayer(JNIEnv* env,
                         const base::android::JavaParamRef<jobject>& obj);

  void GetTabThumbnailFromCallback(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& obj,
      jint tab_id,
      const base::android::JavaParamRef<jobject>& j_callback);

 private:
  class TabReadbackRequest;
  // TODO(bug 714384) check sizes and consider using base::flat_map if these
  // layer maps are small.
  using TabGroupLayerMap = std::map<int64_t, scoped_refptr<TabGroupLayer>>;
  using LayerMap = std::map<int, scoped_refptr<cc::Layer>>;
  using ThumbnailLayerMap = std::map<int, scoped_refptr<ThumbnailLayer>>;
  using TabReadbackRequestMap =
      base::flat_map<int, std::unique_ptr<TabReadbackRequest>>;

  void PutThumbnailIntoCache(int tab_id,
                             float thumbnail_scale,
                             const SkBitmap& bitmap);

  void TabThumbnailAvailableFromDisk(
      base::android::ScopedJavaGlobalRef<jobject> j_callback,
      bool result,
      SkBitmap bitmap);

  std::unique_ptr<ThumbnailCache> thumbnail_cache_;
  ThumbnailLayerMap static_layer_cache_;
  LayerMap live_layer_list_;
  TabReadbackRequestMap pending_tab_readbacks_;
  TabGroupLayerMap tabgroup_layer_cache_;
  scoped_refptr<cc::Layer> tab_info_layer_;
  scoped_refptr<cc::UIResourceLayer> selected_tabgroup_tab_layer_;
  float dp_to_px_;
  base::CancelableTaskTracker cancelable_task_tracker_for_favicon_;
  JavaObjectWeakGlobalRef weak_java_tab_content_manager_;
  base::WeakPtrFactory<TabContentManager> weak_factory_;

  DISALLOW_COPY_AND_ASSIGN(TabContentManager);
};

}  // namespace android

#endif  // CHROME_BROWSER_ANDROID_COMPOSITOR_TAB_CONTENT_MANAGER_H_
