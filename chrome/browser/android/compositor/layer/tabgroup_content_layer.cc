// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/compositor/layer/tabgroup_content_layer.h"

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
scoped_refptr<TabGroupContentLayer> TabGroupContentLayer::Create(
    TabContentManager* tab_content_manager) {
  return base::WrapRefCounted(new TabGroupContentLayer(tab_content_manager));
}

static void SetOpacityOnLeaf(scoped_refptr<cc::Layer> layer, float alpha) {
  const cc::LayerList& children = layer->children();
  if (children.size() > 0) {
    layer->SetOpacity(1.0f);
    for (uint i = 0; i < children.size(); ++i)
      SetOpacityOnLeaf(children[i], alpha);
  } else {
    layer->SetOpacity(alpha);
  }
}

static cc::Layer* GetDrawsContentLeaf(scoped_refptr<cc::Layer> layer) {
  if (!layer.get())
    return nullptr;

  // If the subtree is hidden, then any layers in this tree will not be drawn.
  if (layer->hide_layer_and_subtree())
    return nullptr;

  if (layer->opacity() == 0.0f)
    return nullptr;

  if (layer->DrawsContent())
    return layer.get();

  const cc::LayerList& children = layer->children();
  for (unsigned i = 0; i < children.size(); i++) {
    cc::Layer* leaf = GetDrawsContentLeaf(children[i]);
    if (leaf)
      return leaf;
  }
  return nullptr;
}

void TabGroupContentLayer::SetProperties(
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
  if (group_tab_content_layers_.size() == 0) {
    for (int i = 0; i < 4; i++) {
      group_tab_content_layers_.emplace_back(
          TabGroupTabContentLayer::Create(tab_content_manager_));
      layer_->AddChild(group_tab_content_layers_.back()->layer());
    }
  }

  const gfx::RectF border_inner_shadow_padding(
      border_inner_shadow_resource->padding());

  const gfx::Size border_inner_shadow_padding_size(
      border_inner_shadow_resource->size().width() -
          border_inner_shadow_padding.width(),
      border_inner_shadow_resource->size().height() -
          border_inner_shadow_padding.height());

  int size = tab_ids.size();
  for (int i = 0; i < size; i++) {
    group_tab_content_layers_[i]->SetProperties(
        tab_ids[i], can_use_live_layer, static_to_view_blend, true,
        content_alpha_override, saturation, true, clip,
        border_inner_shadow_resource, 1, width, height, tab_ids,
        border_inner_shadow_alpha, inset_diff);

    gfx::Transform transform;
    transform.Scale(0.475, 0.475);
    group_tab_content_layers_[i]->layer()->SetTransform(transform);

    int height_offset_factor = i / 2;
    int width_offset_factor = i % 2;

    gfx::PointF position(
        clip.x() + width_offset_factor * clip.width() * 0.475 +
            (i % 2) * ((clip.width()) * 0.05 -
                       border_inner_shadow_padding_size.width()),
        clip.y() + height_offset_factor * clip.height() * 0.475 +
            (i / 2) * ((clip.height()) * 0.05 -
                       border_inner_shadow_padding_size.height()));
    group_tab_content_layers_[i]->layer()->SetPosition(position);
  }
}

gfx::Size TabGroupContentLayer::ComputeSize(int id) const {
  gfx::Size size;
  scoped_refptr<cc::Layer> live_layer = tab_content_manager_->GetLiveLayer(id);
  cc::Layer* leaf_that_draws = GetDrawsContentLeaf(live_layer);
  if (leaf_that_draws)
    size.SetToMax(leaf_that_draws->bounds());

  scoped_refptr<ThumbnailLayer> static_layer =
      tab_content_manager_->GetStaticLayer(id);
  if (static_layer.get() && GetDrawsContentLeaf(static_layer->layer()))
    size.SetToMax(static_layer->layer()->bounds());

  return size;
}

scoped_refptr<cc::Layer> TabGroupContentLayer::layer() {
  return layer_;
}

TabGroupContentLayer::TabGroupContentLayer(
    TabContentManager* tab_content_manager)
    : ContentLayer(tab_content_manager) {}

TabGroupContentLayer::~TabGroupContentLayer() {}

}  //  namespace android
