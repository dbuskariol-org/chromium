// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "weblayer/browser/java/jni/WebViewCompatibilityHelperImpl_jni.h"

#if defined(WEBLAYER_MANUAL_JNI_REGISTRATION)
#include "base/android/library_loader/library_loader_hooks.h"  // nogncheck
#include "weblayer/browser/java/weblayer_jni_registration.h"   // nogncheck
#endif

namespace weblayer {
namespace {
#if defined(WEBLAYER_MANUAL_JNI_REGISTRATION)
void RegisterNonMainDexNativesHook() {
  RegisterNonMainDexNatives(base::android::AttachCurrentThread());
}
#endif
}  // namespace

void JNI_WebViewCompatibilityHelperImpl_RegisterJni(JNIEnv* env) {
#if defined(WEBLAYER_MANUAL_JNI_REGISTRATION)
  RegisterMainDexNatives(base::android::AttachCurrentThread());
  base::android::SetNonMainDexJniRegistrationHook(
      RegisterNonMainDexNativesHook);
#endif
}

}  // namespace weblayer
