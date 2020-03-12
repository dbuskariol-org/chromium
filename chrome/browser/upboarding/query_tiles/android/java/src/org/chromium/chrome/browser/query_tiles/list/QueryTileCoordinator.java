// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.query_tiles.list;

import android.content.Context;
import android.view.View;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.query_tiles.TileProvider;

/**
 * The top level coordinator for the query tiles UI.
 */
public class QueryTileCoordinator {
    public QueryTileCoordinator(
            Context context, TileProvider tileProvider, Callback<Boolean> visibilityCallback) {}

    public View getView() {
        return null;
    }
}
