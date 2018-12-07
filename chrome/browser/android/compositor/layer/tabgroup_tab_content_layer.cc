// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/compositor/layer/tabgroup_tab_content_layer.h"

#include "base/lazy_instance.h"
#include "cc/layers/layer.h"
#include "cc/layers/layer_collections.h"
#include "cc/layers/nine_patch_layer.h"
#include "cc/paint/filter_operations.h"
#include "chrome/browser/android/compositor/layer/thumbnail_layer.h"
#include "chrome/browser/android/compositor/tab_content_manager.h"
#include "content/public/browser/android/compositor.h"
#include "ui/android/resources/nine_patch_resource.h"
#include "ui/gfx/geometry/size.h"

namespace android {

// static
scoped_refptr<TabGroupTabContentLayer> TabGroupTabContentLayer::Create(
    TabContentManager* tab_content_manager) {
  return base::WrapRefCounted(new TabGroupTabContentLayer(tab_content_manager));
}

void TabGroupTabContentLayer::SetProperties(
    int id,
    bool can_use_live_layer,
    float static_to_view_blend,
    bool should_override_content_alpha,
    float content_alpha_override,
    float saturation,
    bool should_clip,
    const gfx::Rect& clip,
    ui::NinePatchResource* border_inner_shadow_resource,
    int group_size,
    float width,
    float height,
    std::vector<int>& tab_ids,
    float border_inner_shadow_alpha,
    int inset_diff) {
  DVLOG(0) << "Meil2 tab group content set SetProperties";

  content_->SetProperties(id, can_use_live_layer, static_to_view_blend,
                          should_override_content_alpha, content_alpha_override,
                          saturation, should_clip, clip);

  setBorderProperties(0, border_inner_shadow_resource, clip, width, height,
                      border_inner_shadow_alpha, inset_diff);

  layer_->SetBounds(front_border_inner_shadow_->bounds());
}

scoped_refptr<cc::Layer> TabGroupTabContentLayer::layer() {
  return layer_;
}

TabGroupTabContentLayer::TabGroupTabContentLayer(
    TabContentManager* tab_content_manager)
    : layer_(cc::Layer::Create()),
      content_(ContentLayer::Create(tab_content_manager)),
      front_border_inner_shadow_(cc::NinePatchLayer::Create()) {
  layer_->AddChild(content_->layer());
  layer_->AddChild(front_border_inner_shadow_);

  front_border_inner_shadow_->SetIsDrawable(true);
}

TabGroupTabContentLayer::~TabGroupTabContentLayer() {}

void TabGroupTabContentLayer::setBorderProperties(
    int i,
    ui::NinePatchResource* border_inner_shadow_resource,
    const gfx::Rect& clip,
    float width,
    float height,
    float border_inner_shadow_alpha,
    int inset_diff) {
  // precalculate helper values
  const gfx::RectF border_inner_shadow_padding(
      border_inner_shadow_resource->padding());

  const gfx::Size border_inner_shadow_padding_size(
      border_inner_shadow_resource->size().width() -
          border_inner_shadow_padding.width(),
      border_inner_shadow_resource->size().height() -
          border_inner_shadow_padding.height());

  gfx::Size border_inner_shadow_size(
      clip.width() + border_inner_shadow_padding_size.width(),
      clip.height() + border_inner_shadow_padding_size.height());

  front_border_inner_shadow_->SetUIResourceId(
      border_inner_shadow_resource->ui_resource()->id());
  front_border_inner_shadow_->SetAperture(
      border_inner_shadow_resource->aperture());
  front_border_inner_shadow_->SetBorder(
      border_inner_shadow_resource->Border(border_inner_shadow_size));

  gfx::PointF border_inner_shadow_position(-border_inner_shadow_padding.x(),
                                           -border_inner_shadow_padding.y());

  front_border_inner_shadow_->SetPosition(border_inner_shadow_position);
  front_border_inner_shadow_->SetBounds(border_inner_shadow_size);
  front_border_inner_shadow_->SetOpacity(border_inner_shadow_alpha);
}

}  //  namespace android
