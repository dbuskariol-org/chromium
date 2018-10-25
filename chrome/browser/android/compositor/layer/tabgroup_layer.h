// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYER_H_
#define CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYER_H_

#include <stddef.h>
#include <string>

#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/strings/string16.h"
#include "cc/layers/ui_resource_layer.h"
#include "cc/resources/ui_resource_client.h"
#include "chrome/browser/android/compositor/layer/layer.h"
#include "ui/gfx/geometry/rect.h"
#include "ui/gfx/geometry/size_f.h"

namespace cc {
class Layer;
}

namespace android {

class TabGroupLayer : public Layer {
 public:
  // Creates a TabGroupLayer.
  static scoped_refptr<TabGroupLayer> Create(float dp_to_px);

  // Creates a TabGroupLayer and initializes with given content.
  static scoped_refptr<TabGroupLayer> CreateWith(float dp_to_px,
                                                 bool image_is_favicon,
                                                 const SkBitmap& image,
                                                 const std::string& title,
                                                 const std::string& domain);

  static scoped_refptr<cc::UIResourceLayer> CreateTabGroupLabelLayer(
      float dp_to_px,
      float width);

  static scoped_refptr<cc::UIResourceLayer> CreateTabGroupCreationLayer(
      float dp_to_px,
      float width);

  static scoped_refptr<TabGroupLayer> CreateTabGroupAddTabLayer(
      float dp_to_px,
      cc::UIResourceId resource_id);

  static const int ADD_TAB_IN_GROUP_TAB_ID = -2;

  void SetThumbnailBitmap(const SkBitmap& thumbnail, bool is_favicon);
  void SetCloseResourceId(cc::UIResourceId resource_id);
  void SetText(const base::string16& title, const base::string16& domain);
  gfx::PointF GetPinPosition(float size);
  void SetTitle(const base::string16& title);
  void SetDomain(const base::string16& domain);

  // Implements Layer.
  scoped_refptr<cc::Layer> layer() override;

 protected:
  explicit TabGroupLayer(float dp_to_px);
  TabGroupLayer(float dp_to_px, cc::UIResourceId add_resource_id);
  ~TabGroupLayer() override;

 private:
  void AddBorderLayer();

  scoped_refptr<cc::Layer> layer_;
  scoped_refptr<cc::UIResourceLayer> thumbnail_layer_;
  scoped_refptr<cc::UIResourceLayer> title_layer_;
  scoped_refptr<cc::UIResourceLayer> domain_layer_;
  scoped_refptr<cc::UIResourceLayer> close_layer_;
  float dp_to_px_;

  DISALLOW_COPY_AND_ASSIGN(TabGroupLayer);
};

}  // namespace android

#endif  // CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_TABGROUP_LAYER_H_
