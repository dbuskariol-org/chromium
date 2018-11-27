// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/compositor/tab_content_manager.h"

#include <android/bitmap.h>
#include <stddef.h>

#include <list>
#include <memory>
#include <string>
#include <utility>

#include "base/android/callback_android.h"
#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/android/scoped_java_ref.h"
#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/macros.h"
#include "base/strings/utf_string_conversions.h"
#include "base/task/cancelable_task_tracker.h"
#include "cc/layers/layer.h"
#include "cc/layers/picture_image_layer.h"
#include "cc/paint/paint_flags.h"
#include "cc/paint/paint_image_builder.h"
#include "cc/paint/skia_paint_canvas.h"
#include "chrome/browser/android/compositor/layer/tabgroup_layer.h"
#include "chrome/browser/android/compositor/layer/thumbnail_layer.h"
#include "chrome/browser/android/tab_android.h"
#include "chrome/browser/android/thumbnail/thumbnail.h"
#include "chrome/browser/favicon/favicon_service_factory.h"
#include "chrome/browser/profiles/profile_android.h"
#include "components/favicon/core/favicon_service.h"
#include "components/favicon_base/favicon_types.h"
#include "content/public/browser/interstitial_page.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/render_view_host.h"
#include "content/public/browser/render_widget_host.h"
#include "content/public/browser/render_widget_host_view.h"
#include "content/public/browser/web_contents.h"
#include "jni/TabContentManager_jni.h"
#include "ui/android/resources/ui_resource_provider.h"
#include "ui/android/view_android.h"
#include "ui/gfx/android/java_bitmap.h"
#include "ui/gfx/codec/png_codec.h"
#include "ui/gfx/geometry/dip_util.h"
#include "ui/gfx/geometry/rect.h"
#include "ui/gfx/image/image.h"
#include "ui/gfx/image/image_skia.h"
#include "url/gurl.h"

using base::android::JavaParamRef;
using base::android::JavaRef;
using base::android::ScopedJavaGlobalRef;
using cc::UIResourceLayer;

class Profile;

namespace {

const size_t kMaxReadbacks = 1;
typedef base::Callback<void(float, const SkBitmap&)> TabReadbackCallback;

}  // namespace

namespace android {

class TabContentManager::TabReadbackRequest {
 public:
  TabReadbackRequest(content::RenderWidgetHostView* rwhv,
                     float thumbnail_scale,
                     const TabReadbackCallback& end_callback)
      : thumbnail_scale_(thumbnail_scale),
        end_callback_(end_callback),
        drop_after_readback_(false),
        weak_factory_(this) {
    DCHECK(rwhv);
    auto result_callback =
        base::BindOnce(&TabReadbackRequest::OnFinishGetTabThumbnailBitmap,
                       weak_factory_.GetWeakPtr());

    gfx::Size view_size_in_pixels =
        rwhv->GetNativeView()->GetPhysicalBackingSize();
    if (view_size_in_pixels.IsEmpty()) {
      std::move(result_callback).Run(SkBitmap());
      return;
    }
    gfx::Size thumbnail_size(
        gfx::ScaleToCeiledSize(view_size_in_pixels, thumbnail_scale_));
    rwhv->CopyFromSurface(gfx::Rect(), thumbnail_size,
                          std::move(result_callback));
  }

  virtual ~TabReadbackRequest() {}

  void OnFinishGetTabThumbnailBitmap(const SkBitmap& bitmap) {
    if (bitmap.drawsNothing() || drop_after_readback_) {
      end_callback_.Run(0.f, SkBitmap());
      return;
    }

    SkBitmap result_bitmap = bitmap;
    result_bitmap.setImmutable();
    end_callback_.Run(thumbnail_scale_, bitmap);
  }

  void SetToDropAfterReadback() { drop_after_readback_ = true; }

 private:
  const float thumbnail_scale_;
  TabReadbackCallback end_callback_;
  bool drop_after_readback_;

  base::WeakPtrFactory<TabReadbackRequest> weak_factory_;

  DISALLOW_COPY_AND_ASSIGN(TabReadbackRequest);
};

// static
TabContentManager* TabContentManager::FromJavaObject(
    const JavaRef<jobject>& jobj) {
  if (jobj.is_null())
    return nullptr;
  return reinterpret_cast<TabContentManager*>(
      Java_TabContentManager_getNativePtr(base::android::AttachCurrentThread(),
                                          jobj));
}

TabContentManager::TabContentManager(JNIEnv* env,
                                     jobject obj,
                                     jint default_cache_size,
                                     jint approximation_cache_size,
                                     jint compression_queue_max_size,
                                     jint write_queue_max_size,
                                     jboolean use_approximation_thumbnail,
                                     jfloat dp_to_px)
    : dp_to_px_(dp_to_px),
      weak_java_tab_content_manager_(env, obj),
      weak_factory_(this) {
  thumbnail_cache_ = std::make_unique<ThumbnailCache>(
      static_cast<size_t>(default_cache_size),
      static_cast<size_t>(approximation_cache_size),
      static_cast<size_t>(compression_queue_max_size),
      static_cast<size_t>(write_queue_max_size), use_approximation_thumbnail);
  thumbnail_cache_->AddThumbnailCacheObserver(this);
}

TabContentManager::~TabContentManager() {
}

void TabContentManager::Destroy(JNIEnv* env, const JavaParamRef<jobject>& obj) {
  thumbnail_cache_->RemoveThumbnailCacheObserver(this);
  delete this;
}

void TabContentManager::SetUIResourceProvider(
    ui::UIResourceProvider* ui_resource_provider) {
  thumbnail_cache_->SetUIResourceProvider(ui_resource_provider);
}

scoped_refptr<cc::Layer> TabContentManager::GetLiveLayer(int tab_id) {
  return live_layer_list_[tab_id];
}

scoped_refptr<ThumbnailLayer> TabContentManager::GetStaticLayer(int tab_id) {
  return static_layer_cache_[tab_id];
}

void TabContentManager::SetTabInfoLayer(const scoped_refptr<cc::Layer>& layer) {
  tab_info_layer_ = layer;
}

scoped_refptr<cc::Layer> TabContentManager::GetTabInfoLayer() {
  return tab_info_layer_;
}

scoped_refptr<ThumbnailLayer> TabContentManager::GetOrCreateStaticLayer(
    int tab_id,
    bool force_disk_read) {
  Thumbnail* thumbnail = thumbnail_cache_->Get(tab_id, force_disk_read, true);
  scoped_refptr<ThumbnailLayer> static_layer = static_layer_cache_[tab_id];

  if (!thumbnail || !thumbnail->ui_resource_id()) {
    if (static_layer.get()) {
      static_layer->layer()->RemoveFromParent();
      static_layer_cache_.erase(tab_id);
    }
    return nullptr;
  }

  if (!static_layer.get()) {
    static_layer = ThumbnailLayer::Create();
    static_layer_cache_[tab_id] = static_layer;
  }

  static_layer->SetThumbnail(thumbnail);
  return static_layer;
}

void TabContentManager::AttachLiveLayer(int tab_id,
                                        scoped_refptr<cc::Layer> layer) {
  if (!layer.get())
    return;

  scoped_refptr<cc::Layer> cached_layer = live_layer_list_[tab_id];
  if (cached_layer != layer)
    live_layer_list_[tab_id] = layer;
}

void TabContentManager::DetachLiveLayer(int tab_id,
                                        scoped_refptr<cc::Layer> layer) {
  scoped_refptr<cc::Layer> current_layer = live_layer_list_[tab_id];
  if (!current_layer.get()) {
    // Empty cached layer should not exist but it is ok if it happens.
    return;
  }

  // We need to remove if we're getting a detach for our current layer or we're
  // getting a detach with NULL and we have a current layer, which means remove
  //  all layers.
  if (current_layer.get() &&
      (layer.get() == current_layer.get() || !layer.get())) {
    live_layer_list_.erase(tab_id);
  }
}

jboolean TabContentManager::HasFullCachedThumbnail(
    JNIEnv* env,
    const JavaParamRef<jobject>& obj,
    jint tab_id) {
  return thumbnail_cache_->Get(tab_id, false, false) != nullptr;
}

void TabContentManager::CacheTab(JNIEnv* env,
                                 const JavaParamRef<jobject>& obj,
                                 const JavaParamRef<jobject>& tab,
                                 jfloat thumbnail_scale) {
  TabAndroid* tab_android = TabAndroid::GetNativeTab(env, tab);
  DCHECK(tab_android);
  const int tab_id = tab_android->GetAndroidId();
  if (pending_tab_readbacks_.find(tab_id) != pending_tab_readbacks_.end() ||
      pending_tab_readbacks_.size() >= kMaxReadbacks) {
    return;
  }

  content::WebContents* web_contents = tab_android->web_contents();
  DCHECK(web_contents);

  content::RenderViewHost* rvh = web_contents->GetRenderViewHost();
  if (web_contents->ShowingInterstitialPage()) {
    if (!web_contents->GetInterstitialPage()->GetMainFrame())
      return;

    rvh = web_contents->GetInterstitialPage()->GetMainFrame()->
        GetRenderViewHost();
  }
  if (!rvh)
    return;

  content::RenderWidgetHost* rwh = rvh->GetWidget();
  content::RenderWidgetHostView* rwhv = rwh ? rwh->GetView() : nullptr;
  if (!rwhv || !rwhv->IsSurfaceAvailableForCopy())
    return;

  if (thumbnail_cache_->CheckAndUpdateThumbnailMetaData(
          tab_id, tab_android->GetURL())) {
    TabReadbackCallback readback_done_callback =
        base::Bind(&TabContentManager::PutThumbnailIntoCache,
                   weak_factory_.GetWeakPtr(), tab_id);
    pending_tab_readbacks_[tab_id] = std::make_unique<TabReadbackRequest>(
        rwhv, thumbnail_scale, readback_done_callback);
  }
}

scoped_refptr<cc::UIResourceLayer> TabContentManager::CreateTabGroupLabelLayer(
    float width) {
  return TabGroupLayer::CreateTabGroupLabelLayer(dp_to_px_, width);
}

void TabContentManager::CacheTabWithBitmap(JNIEnv* env,
                                           const JavaParamRef<jobject>& obj,
                                           const JavaParamRef<jobject>& tab,
                                           const JavaParamRef<jobject>& bitmap,
                                           jfloat thumbnail_scale) {
  TabAndroid* tab_android = TabAndroid::GetNativeTab(env, tab);
  DCHECK(tab_android);
  int tab_id = tab_android->GetAndroidId();
  GURL url = tab_android->GetURL();

  gfx::JavaBitmap java_bitmap_lock(bitmap);
  SkBitmap skbitmap = gfx::CreateSkBitmapFromJavaBitmap(java_bitmap_lock);
  skbitmap.setImmutable();

  if (thumbnail_cache_->CheckAndUpdateThumbnailMetaData(tab_id, url))
    PutThumbnailIntoCache(tab_id, thumbnail_scale, skbitmap);
}

void TabContentManager::InvalidateIfChanged(JNIEnv* env,
                                            const JavaParamRef<jobject>& obj,
                                            jint tab_id,
                                            const JavaParamRef<jstring>& jurl) {
  thumbnail_cache_->InvalidateThumbnailIfChanged(
      tab_id, GURL(base::android::ConvertJavaStringToUTF8(env, jurl)));
}

void TabContentManager::UpdateVisibleIds(
    JNIEnv* env,
    const JavaParamRef<jobject>& obj,
    const JavaParamRef<jintArray>& priority,
    jint primary_tab_id) {
  std::list<int> priority_ids;
  jsize length = env->GetArrayLength(priority);
  jint* ints = env->GetIntArrayElements(priority, nullptr);
  for (jsize i = 0; i < length; ++i)
    priority_ids.push_back(static_cast<int>(ints[i]));

  env->ReleaseIntArrayElements(priority, ints, JNI_ABORT);
  thumbnail_cache_->UpdateVisibleIds(priority_ids, primary_tab_id);
}

void TabContentManager::NativeRemoveTabThumbnail(int tab_id) {
  TabReadbackRequestMap::iterator readback_iter =
      pending_tab_readbacks_.find(tab_id);
  if (readback_iter != pending_tab_readbacks_.end())
    readback_iter->second->SetToDropAfterReadback();
  thumbnail_cache_->Remove(tab_id);
}

void TabContentManager::RemoveTabThumbnail(JNIEnv* env,
                                           const JavaParamRef<jobject>& obj,
                                           jint tab_id) {
  NativeRemoveTabThumbnail(tab_id);
}

void TabContentManager::OnUIResourcesWereEvicted() {
  thumbnail_cache_->OnUIResourcesWereEvicted();
}

void TabContentManager::OnFinishedThumbnailRead(int tab_id) {
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_TabContentManager_notifyListenersOfThumbnailChange(
      env, weak_java_tab_content_manager_.get(env), tab_id);
}

void TabContentManager::PutThumbnailIntoCache(int tab_id,
                                              float thumbnail_scale,
                                              const SkBitmap& bitmap) {
  TabReadbackRequestMap::iterator readback_iter =
      pending_tab_readbacks_.find(tab_id);

  if (readback_iter != pending_tab_readbacks_.end())
    pending_tab_readbacks_.erase(tab_id);

  if (thumbnail_scale > 0 && !bitmap.empty())
    thumbnail_cache_->Put(tab_id, bitmap, thumbnail_scale);
}

void TabContentManager::GetTabThumbnailFromCallback(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    jint tab_id,
    const base::android::JavaParamRef<jobject>& j_callback) {
  thumbnail_cache_->DecompressThumbnailFromFile(
      tab_id,
      base::BindRepeating(&TabContentManager::TabThumbnailAvailableFromDisk,
                          weak_factory_.GetWeakPtr(),
                          ScopedJavaGlobalRef<jobject>(j_callback)));
}

void TabContentManager::TabThumbnailAvailableFromDisk(
    ScopedJavaGlobalRef<jobject> j_callback,
    bool result,
    SkBitmap bitmap) {
  ScopedJavaLocalRef<jobject> j_bitmap;
  if (!bitmap.isNull() && result)
    j_bitmap = gfx::ConvertToJavaBitmap(&bitmap);

  RunObjectCallbackAndroid(j_callback, j_bitmap);
}

// ----------------------------------------------------------------------------
// Native JNI methods
// ----------------------------------------------------------------------------

jlong JNI_TabContentManager_Init(JNIEnv* env,
                                 const JavaParamRef<jobject>& obj,
                                 jint default_cache_size,
                                 jint approximation_cache_size,
                                 jint compression_queue_max_size,
                                 jint write_queue_max_size,
                                 jboolean use_approximation_thumbnail,
                                 jfloat dp_to_px) {
  TabContentManager* manager = new TabContentManager(
      env, obj, default_cache_size, approximation_cache_size,
      compression_queue_max_size, write_queue_max_size,
      use_approximation_thumbnail, dp_to_px);
  return reinterpret_cast<intptr_t>(manager);
}

// ----------------------------------------------------------------------------
// Tab Group methods
// ----------------------------------------------------------------------------
scoped_refptr<cc::UIResourceLayer>
TabContentManager::GetSelectedTabGroupTabLayer(float width, float height) {
  if (!selected_tabgroup_tab_layer_) {
    selected_tabgroup_tab_layer_ = cc::UIResourceLayer::Create();
    selected_tabgroup_tab_layer_->SetIsDrawable(true);
    selected_tabgroup_tab_layer_->SetBounds(gfx::Size(width, height));

    SkBitmap border_bitmap;
    border_bitmap.allocN32Pixels(width, height);
    border_bitmap.eraseColor(SK_ColorTRANSPARENT);

    SkCanvas canvas(border_bitmap);
    SkRect dest_rect = SkRect::MakeWH(width, height);

    SkPaint paint = SkPaint();
    // Matching the tab strip tab focused color
    SkColor modern_blue_600 = SkColorSetARGB(255, 26, 115, 232);
    paint.setStyle(SkPaint::Style::kStroke_Style);
    paint.setColor(modern_blue_600);
    paint.setStrokeWidth(SkFloatToScalar(5 * dp_to_px_));
    paint.setAntiAlias(true);

    int radius = 20;
    canvas.drawRRect(
        SkRRect::MakeRectXY(dest_rect, radius * dp_to_px_, radius * dp_to_px_),
        paint);
    border_bitmap.setImmutable();
    selected_tabgroup_tab_layer_->SetBitmap(border_bitmap);
  }

  return selected_tabgroup_tab_layer_;
}

scoped_refptr<cc::UIResourceLayer>
TabContentManager::CreateTabGroupCreationLayer(float width) {
  return TabGroupLayer::CreateTabGroupCreationLayer(dp_to_px_, width);
}

scoped_refptr<TabGroupLayer> TabContentManager::CreateTabGroupAddTabLayer(
    cc::UIResourceId add_resource_id) {
  if (tabgroup_layer_cache_.count(TabGroupLayer::ADD_TAB_IN_GROUP_TAB_ID) ==
      0) {
    tabgroup_layer_cache_[TabGroupLayer::ADD_TAB_IN_GROUP_TAB_ID] =
        TabGroupLayer::CreateTabGroupAddTabLayer(dp_to_px_, add_resource_id);
  }

  return tabgroup_layer_cache_[TabGroupLayer::ADD_TAB_IN_GROUP_TAB_ID];
}

scoped_refptr<TabGroupLayer> TabContentManager::GetTabGroupLayer(
    int64_t tab_id) {
  return tabgroup_layer_cache_[tab_id];
}

const SkBitmap TabContentManager::CreateDummyBitmapForTabGroupTab(int width,
                                                                  int height) {
  SkBitmap bitmap;
  bitmap.allocN32Pixels(width, height);
  bitmap.eraseARGB(255, 255, 255, 255);
  return bitmap;
}

void TabContentManager::CacheTabAsTabGroupTab(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    jint tab_id,
    jstring url,
    jstring title,
    const JavaParamRef<jobject>& j_profile) {
  std::string tab_url = base::android::ConvertJavaStringToUTF8(env, url);
  std::string tab_title = base::android::ConvertJavaStringToUTF8(env, title);
  gfx::Image dummy_image(gfx::ImageSkia::CreateFrom1xBitmap(
      CreateDummyBitmapForTabGroupTab(25, 10)));

  if (tab_title == "")
    tab_title = "Loading";

  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile);
  OnTabGroupResourceFetched(tab_id, tab_url, tab_title, SkBitmap(), dummy_image,
                            profile);
}

void TabContentManager::RemoveTabGroupTabFromCache(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj,
    jint tab_id) {
  tabgroup_layer_cache_.erase(tab_id);
}

void TabContentManager::OnTabGroupResourceFetched(
    int64_t tab_id,
    const std::string& url,
    const std::string& title,
    const SkBitmap& favicon_bitmap,
    const gfx::Image& image,
    Profile* profile) {
  scoped_refptr<TabGroupLayer> layer = nullptr;
  if (favicon_bitmap.isNull()) {
    layer = TabGroupLayer::CreateWith(dp_to_px_, false, *(image.ToSkBitmap()),
                                      title, url);
  } else {
    layer =
        TabGroupLayer::CreateWith(dp_to_px_, true, favicon_bitmap, title, url);
  }
  tabgroup_layer_cache_[tab_id] = layer;
  UpdateTabGroupTabFavicon(tab_id, url, profile);
}

void TabContentManager::UpdateTabGroupTabFavicon(int tab_id,
                                                 std::string url,
                                                 Profile* profile) {
  favicon_base::FaviconRawBitmapCallback callback =
      base::BindRepeating(&TabContentManager::OnFaviconImageFetched,
                          weak_factory_.GetWeakPtr(), tab_id);

  favicon::FaviconService* favicon_service =
      FaviconServiceFactory::GetForProfile(profile,
                                           ServiceAccessType::EXPLICIT_ACCESS);

  favicon_service->GetRawFaviconForPageURL(
      GURL(url),
      {favicon_base::IconType::kFavicon, favicon_base::IconType::kTouchIcon,
       favicon_base::IconType::kTouchPrecomposedIcon,
       favicon_base::IconType::kWebManifestIcon},
      0, true, callback, &cancelable_task_tracker_for_favicon_);
}

void TabContentManager::OnFaviconImageFetched(
    int tab_id,
    const favicon_base::FaviconRawBitmapResult& result) {
  if (result.is_valid()) {
    SkBitmap favicon_bitmap;
    gfx::PNGCodec::Decode(result.bitmap_data->front(),
                          result.bitmap_data->size(), &favicon_bitmap);
    scoped_refptr<TabGroupLayer> tabgroup_layer = GetTabGroupLayer(tab_id);
    if (tabgroup_layer.get())
      tabgroup_layer->SetThumbnailBitmap(favicon_bitmap, true);
  }
}

void TabContentManager::UpdateTabGroupTabTitle(JNIEnv* env,
                                               const JavaParamRef<jobject>& obj,
                                               jint tab_id,
                                               jstring title) {
  std::string tab_title = base::android::ConvertJavaStringToUTF8(env, title);
  base::string16 title_text = base::string16();
  base::UTF8ToUTF16(tab_title.c_str(), tab_title.length(), &title_text);
  scoped_refptr<TabGroupLayer> tabgroup_layer = GetTabGroupLayer(tab_id);
  if (tabgroup_layer.get())
    tabgroup_layer->SetTitle(title_text);
}

void TabContentManager::UpdateTabGroupTabFavicon(
    JNIEnv* env,
    const JavaParamRef<jobject>& obj,
    jint tab_id,
    jstring url,
    const JavaParamRef<jobject>& j_profile) {
  std::string tab_url = base::android::ConvertJavaStringToUTF8(env, url);
  base::string16 url_text = base::string16();

  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile);
  UpdateTabGroupTabFavicon(tab_id, tab_url, profile);
}

void TabContentManager::UpdateTabGroupTabUrl(JNIEnv* env,
                                             const JavaParamRef<jobject>& obj,
                                             jint tab_id,
                                             jstring url) {
  std::string tab_url = base::android::ConvertJavaStringToUTF8(env, url);
  base::string16 url_text = base::string16();
  base::UTF8ToUTF16(tab_url.c_str(), tab_url.length(), &url_text);
  scoped_refptr<TabGroupLayer> tabgroup_layer = GetTabGroupLayer(tab_id);
  if (tabgroup_layer.get())
    tabgroup_layer->SetDomain(url_text);
}

void TabContentManager::ClearTabInfoLayer(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj) {
  SetTabInfoLayer(nullptr);
}

}  // namespace android
