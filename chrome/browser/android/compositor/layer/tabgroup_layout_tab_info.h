// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#ifndef CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYOUT_TAB_INFO_H_
#define CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYOUT_TAB_INFO_H_

#include "base/android/scoped_java_ref.h"
#include "cc/layers/layer.h"

namespace android {

class TabGroupLayoutTabInfo {
 public:
  static TabGroupLayoutTabInfo* FromJavaObject(
      JNIEnv* env,
      const base::android::JavaRef<jobject>& jobj);

  TabGroupLayoutTabInfo(JNIEnv* env,
                        const base::android::JavaParamRef<jobject>& jobj);
  ~TabGroupLayoutTabInfo();

  void Destroy(JNIEnv* env, const base::android::JavaParamRef<jobject>& jobj);

  void OnComputeLayout(JNIEnv* env,
                       const base::android::JavaParamRef<jobject>& jobj);

  void UpdateLayer(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
      const base::android::JavaParamRef<jobject>& j_resource_manager);

  void PutTabGroupLayer(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
      const base::android::JavaParamRef<jobject>& j_resource_manager,
      jint close_resource_id,
      jint pinned_resource_id,
      jfloat j_pinned_size,
      jlong j_timestamp,
      jboolean is_pinned,
      jfloat x,
      jfloat y,
      jboolean is_focused_tab);

  void PutTabGroupLayerLabel(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
      jfloat width,
      jfloat y);

  void PutTabGroupCreationLayer(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
      jfloat width,
      jfloat y);

  void PutTabGroupAddTabLayer(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& j_tab_contents_manager,
      const base::android::JavaParamRef<jobject>& j_resource_manager,
      jint add_resource_id,
      jfloat x,
      jfloat y);

 private:
  scoped_refptr<cc::Layer> own_tree_ = cc::Layer::Create();
  bool recreate_layer_ = true;
};

}  // namespace android

#endif  // CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYOUT_TAB_INFO_H_
