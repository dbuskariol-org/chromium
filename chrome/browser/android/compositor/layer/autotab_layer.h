// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_AUTOTAB_LAYER_H_
#define CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_AUTOTAB_LAYER_H_

#include <stddef.h>

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

class AutoTabLayer : public Layer {
 public:
  // Creates a AutoTabLayer.
  static scoped_refptr<AutoTabLayer> Create(float dp_to_px);

  // Creates a AutoTabLayer and initializes with given content.
  static scoped_refptr<AutoTabLayer> CreateWith(float dp_to_px,
                                                bool image_is_favicon,
                                                const SkBitmap& image,
                                                const std::string& title,
                                                const std::string& domain);

  void SetThumbnailBitmap(const SkBitmap& thumbnail, bool is_favicon);
  void SetText(const base::string16& title, const base::string16& domain);
  gfx::PointF GetPinPosition(float size);

  // Implements Layer.
  scoped_refptr<cc::Layer> layer() override;

 protected:
  AutoTabLayer(float dp_to_px);
  ~AutoTabLayer() override;

 private:
  void AddBorderLayer();

  scoped_refptr<cc::Layer> layer_;
  scoped_refptr<cc::UIResourceLayer> thumbnail_layer_;
  scoped_refptr<cc::UIResourceLayer> title_layer_;
  scoped_refptr<cc::UIResourceLayer> domain_layer_;
  float dp_to_px_;

  DISALLOW_COPY_AND_ASSIGN(AutoTabLayer);
};

}  // namespace android

#endif  // CHROME_BROWSER_ANDROID_COMPOSITOR_LAYER_AUTOTAB_LAYER_H_
