// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.weblayer_private;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;
import org.chromium.base.annotations.NativeMethods;

/**
 * Minimal native interface to allow registering all natives if WebView compatibility is needed.
 */
@MainDex
@JNINamespace("weblayer")
public final class WebViewCompatibilityHelperImpl {
    public static void registerJni() {
        WebViewCompatibilityHelperImplJni.get().registerJni();
    }

    @NativeMethods
    interface Natives {
        void registerJni();
    }
}
