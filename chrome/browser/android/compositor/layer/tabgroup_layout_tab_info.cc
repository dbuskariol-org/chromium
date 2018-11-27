// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#include "chrome/browser/android/compositor/layer/tabgroup_layout_tab_info.h"

#include <utility>

#include "base/android/jni_array.h"
#include "chrome/browser/android/compositor/layer/tabgroup_layer.h"
#include "chrome/browser/android/compositor/tab_content_manager.h"
#include "jni/TabGroupLayoutTabInfo_jni.h"
#include "ui/android/resources/resource_manager.h"
#include "ui/android/resources/resource_manager_impl.h"

namespace android {

/* static  */
TabGroupLayoutTabInfo* TabGroupLayoutTabInfo::FromJavaObject(
    JNIEnv* env,
    const base::android::JavaRef<jobject>& jobj) {
  if (jobj.is_null())
    return nullptr;
  return reinterpret_cast<TabGroupLayoutTabInfo*>(
      Java_TabGroupLayoutTabInfo_getNativePtr(env, jobj));
}

TabGroupLayoutTabInfo::TabGroupLayoutTabInfo(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj) {
  LOG(ERROR) << __FUNCTION__ << " this " << this;
}

TabGroupLayoutTabInfo::~TabGroupLayoutTabInfo() {}

void TabGroupLayoutTabInfo::Destroy(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj) {
  // TODO(acolwell) : Delete layers stored in TabContentManager.
  // LOG(ERROR) << __FUNCTION__ << " this " << this;
  delete this;
}

void TabGroupLayoutTabInfo::OnComputeLayout(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj) {
  LOG(ERROR) << __FUNCTION__ << " this " << this;
  recreate_layer_ = true;
}

void TabGroupLayoutTabInfo::UpdateLayer(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj,
    const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
    const base::android::JavaParamRef<jobject>& j_resource_manager) {
  if (!recreate_layer_) {
    return;
  }

  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_contents_manager);

  tab_content_manager->SetTabInfoLayer(std::move(own_tree_));
  own_tree_ = cc::Layer::Create();
  recreate_layer_ = false;
}

void TabGroupLayoutTabInfo::PutTabGroupLayer(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj,
    const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
    const base::android::JavaParamRef<jobject>& j_resource_manager,
    jint close_resource_id,
    jint pinned_resource_id,
    jfloat pinned_size,
    jlong j_timestamp,
    jboolean is_pinned,
    jfloat x,
    jfloat y,
    jboolean is_focused_tab) {
  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_contents_manager);
  ui::ResourceManager* resource_manager =
      ui::ResourceManagerImpl::FromJavaObject(j_resource_manager);

  int64_t timestamp = j_timestamp;
  scoped_refptr<TabGroupLayer> tabgroup_layer =
      tab_content_manager->GetTabGroupLayer(timestamp);
  if (!tabgroup_layer) {
    return;
  }

  scoped_refptr<cc::Layer> layer = tabgroup_layer->layer();
  if (layer) {
    layer->SetPosition(gfx::PointF(x, y));
    own_tree_->AddChild(layer);

    if (is_focused_tab) {
      scoped_refptr<cc::UIResourceLayer> border_layer =
          tab_content_manager->GetSelectedTabGroupTabLayer(
              layer->bounds().width(), layer->bounds().height());
      border_layer->SetPosition(layer->position());
      own_tree_->AddChild(border_layer);
    }

    tabgroup_layer->SetCloseResourceId(resource_manager->GetUIResourceId(
        ui::ANDROID_RESOURCE_TYPE_STATIC, close_resource_id));

    if (is_pinned) {
      scoped_refptr<cc::UIResourceLayer> pin_layer =
          cc::UIResourceLayer::Create();
      pin_layer->SetIsDrawable(true);
      pin_layer->SetBounds(gfx::Size(pinned_size, pinned_size));
      pin_layer->SetUIResourceId(resource_manager->GetUIResourceId(
          ui::ANDROID_RESOURCE_TYPE_STATIC, pinned_resource_id));
      pin_layer->SetPosition(tabgroup_layer->GetPinPosition(pinned_size));
      own_tree_->AddChild(pin_layer);
    }
  } else {
    LOG(ERROR) << "\tno tabgroup_layer for id " << timestamp;
  }
}

void TabGroupLayoutTabInfo::PutTabGroupLayerLabel(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj,
    const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
    jfloat width,
    jfloat y) {
  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_contents_manager);

  scoped_refptr<cc::UIResourceLayer> label_layer =
      tab_content_manager->CreateTabGroupLabelLayer(width);
  label_layer->SetPosition(gfx::PointF(0, y));
  own_tree_->AddChild(label_layer);
}

void TabGroupLayoutTabInfo::PutTabGroupCreationLayer(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj,
    const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
    jfloat width,
    jfloat y) {
  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_contents_manager);

  scoped_refptr<cc::UIResourceLayer> creation_layer =
      tab_content_manager->CreateTabGroupCreationLayer(width);

  creation_layer->SetPosition(gfx::PointF(0, y));
  own_tree_->AddChild(creation_layer);
}

void TabGroupLayoutTabInfo::PutTabGroupAddTabLayer(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj,
    const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
    const base::android::JavaParamRef<jobject>& j_resource_manager,
    jint add_resource_id,
    jfloat x,
    jfloat y) {
  TabContentManager* tab_content_manager =
      TabContentManager::FromJavaObject(j_tab_contents_manager);
  ui::ResourceManager* resource_manager =
      ui::ResourceManagerImpl::FromJavaObject(j_resource_manager);

  scoped_refptr<TabGroupLayer> tabgroup_add_tab_layer =
      tab_content_manager->CreateTabGroupAddTabLayer(
          resource_manager->GetUIResourceId(ui::ANDROID_RESOURCE_TYPE_STATIC,
                                            add_resource_id));

  tabgroup_add_tab_layer->layer()->SetPosition(gfx::PointF(x, y));
  own_tree_->AddChild(tabgroup_add_tab_layer->layer());
}

static jlong JNI_TabGroupLayoutTabInfo_Init(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jobj) {
  // This will automatically bind to the Java object and pass ownership there.
  TabGroupLayoutTabInfo* tabgroup_layout_tab_info =
      new TabGroupLayoutTabInfo(env, jobj);
  return reinterpret_cast<intptr_t>(tabgroup_layout_tab_info);
}

}  // namespace android
