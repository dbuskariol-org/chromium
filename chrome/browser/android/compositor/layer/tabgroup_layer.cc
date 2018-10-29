// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/compositor/layer/tabgroup_layer.h"

#include <memory>
#include <string>

#include "base/strings/utf_string_conversions.h"
#include "cc/layers/ui_resource_layer.h"
#include "cc/paint/skia_paint_canvas.h"
#include "ui/gfx/canvas.h"
#include "ui/gfx/font.h"
#include "ui/gfx/geometry/size_conversions.h"
#include "ui/gfx/render_text.h"

namespace android {

namespace {

// TODO: dedup with Java
const int TILE_WIDTH_DP = 400;
const int TILE_HEIGHT_DP = 84;
const int CLOSE_BTN_SIZE_DP = 24;
const int CLOSE_BTN_PADDING_DP = 16;
const int THUMBNAIL_WIDTH_DP = TILE_HEIGHT_DP;

std::unique_ptr<gfx::RenderText> CreateRenderText(int font_size,
                                                  gfx::Font::Weight weight,
                                                  SkColor color,
                                                  bool multiline,
                                                  gfx::Rect display_rect) {
  auto render_text = gfx::RenderText::CreateHarfBuzzInstance();
  render_text->SetDirectionalityMode(gfx::DIRECTIONALITY_FROM_TEXT);
  render_text->SetFontList(gfx::FontList(gfx::Font("sans-serif", font_size)));
  render_text->SetColor(color);
  if (multiline) {
    render_text->SetMultiline(true);
    render_text->SetMaxLines(2);
    render_text->SetWordWrapBehavior(gfx::WRAP_LONG_WORDS);
  }
  render_text->SetElideBehavior(gfx::ElideBehavior::ELIDE_TAIL);
  render_text->SetDisplayRect(display_rect);
  render_text->SetWeight(weight);
  return render_text;
}
}  // namespace

// static
scoped_refptr<TabGroupLayer> TabGroupLayer::Create(float dp_to_px) {
  return base::WrapRefCounted(new TabGroupLayer(dp_to_px));
}

// static
scoped_refptr<TabGroupLayer> TabGroupLayer::CreateWith(
    float dp_to_px,
    bool image_is_favicon,
    const SkBitmap& image,
    const std::string& title,
    const std::string& domain) {
  scoped_refptr<TabGroupLayer> layer = TabGroupLayer::Create(dp_to_px);
  layer->SetThumbnailBitmap(image, image_is_favicon);

  base::string16 title_text = base::string16();
  base::UTF8ToUTF16(title.c_str(), title.length(), &title_text);
  base::string16 domain_text = base::string16();
  base::UTF8ToUTF16(domain.c_str(), domain.length(), &domain_text);

  layer->SetText(title_text, domain_text);
  return layer;
}

// static
scoped_refptr<TabGroupLayer> TabGroupLayer::CreateTabGroupAddTabLayer(
    float dp_to_px,
    cc::UIResourceId add_resource_id) {
  static scoped_refptr<TabGroupLayer> kCache;
  if (kCache)
    return kCache;
  scoped_refptr<TabGroupLayer> layer =
      base::WrapRefCounted(new TabGroupLayer(dp_to_px, add_resource_id));

  std::string title = "New tab in group";
  std::string domain = "";
  base::string16 title_text = base::string16();
  base::UTF8ToUTF16(title.c_str(), title.length(), &title_text);
  base::string16 domain_text = base::string16();
  base::UTF8ToUTF16(domain.c_str(), domain.length(), &domain_text);

  layer->SetText(title_text, domain_text);
  kCache = layer;
  return layer;
}

// This is to construct a layer for New tab in group
TabGroupLayer::TabGroupLayer(float dp_to_px, cc::UIResourceId add_resource_id)
    : layer_(cc::Layer::Create()),
      thumbnail_layer_(cc::UIResourceLayer::Create()),
      title_layer_(cc::UIResourceLayer::Create()),
      dp_to_px_(dp_to_px) {
  /*
  |                      TILE_WIDTH_DP                          |
  | THUMBNAIL_WIDTH_DP |8|  text_width                       |16|

  |-------------------------------------------------------------|  ------------
  |                    | |                                   |  |
  |     thumbnail      | |  New tab in group                 |  | TILE_HEIGHT_DP
  |                    | |                                   |  |
  |-------------------------------------------------------------|  ------------

  |     84            |
  | THUMBNAIL_WIDTH_DP|
    18   24   24   18
  |----|----|----|----|  ------------
  |                   |
  |     thumbnail     | TILE_HEIGHT_DP
  |                   |
  |                   |
  |                   |
  |                   |
  |                   |
  |-------------------|  ------------
  */

  int text_width = TILE_WIDTH_DP - THUMBNAIL_WIDTH_DP - 8 - 16;
  layer_->SetIsDrawable(true);
  thumbnail_layer_->SetIsDrawable(true);
  thumbnail_layer_->SetBounds(gfx::Size(48 * dp_to_px_, 48 * dp_to_px_));
  layer_->AddChild(thumbnail_layer_);
  thumbnail_layer_->SetPosition(gfx::PointF(18 * dp_to_px_, 18 * dp_to_px_));
  thumbnail_layer_->SetUIResourceId(add_resource_id);

  title_layer_->SetIsDrawable(true);
  title_layer_->SetBounds(gfx::Size(text_width * dp_to_px_, 48 * dp_to_px_));
  layer_->AddChild(title_layer_);
  title_layer_->SetPosition(gfx::PointF(
      18 * 2 * dp_to_px_ + thumbnail_layer_->bounds().width() + 8 * dp_to_px_,
      18 * dp_to_px_));
}

// static
scoped_refptr<cc::UIResourceLayer> TabGroupLayer::CreateTabGroupLabelLayer(
    float dp_to_px,
    float width) {
  static scoped_refptr<cc::UIResourceLayer> kCache;
  if (kCache)
    return kCache;
  scoped_refptr<cc::UIResourceLayer> label_layer =
      cc::UIResourceLayer::Create();

  label_layer->SetIsDrawable(true);
  // Width is in pixels!
  label_layer->SetBounds(gfx::Size(width, 12 * dp_to_px));

  // Initialize and draw title text.
  float label_text_height = dp_to_px * 12;
  SkBitmap label_bitmap;
  label_bitmap.allocN32Pixels(label_layer->bounds().width(),
                              label_layer->bounds().height());
  label_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas label_paint_canvas(label_bitmap);
  gfx::Canvas label_canvas(&label_paint_canvas, 1.0f);
  label_canvas.Save();
  label_canvas.ClipRect(gfx::Rect(label_bitmap.width(), label_bitmap.height()));

  auto label_render_text =
      CreateRenderText(label_text_height, gfx::Font::Weight::SEMIBOLD,
                       SkColorSetARGB(138, 0, 0, 0), false,
                       gfx::Rect(label_bitmap.width(), label_bitmap.height()));

  label_render_text->SetHorizontalAlignment(gfx::ALIGN_CENTER);
  base::string16 label_text = base::string16();
  std::string label_string = "TAB GROUP";
  base::UTF8ToUTF16(label_string.c_str(), label_string.length(), &label_text);
  label_render_text->SetText(label_text);
  label_render_text->Draw(&label_canvas);
  label_canvas.Restore();
  label_bitmap.setImmutable();

  label_layer->SetBitmap(label_bitmap);
  kCache = label_layer;
  return label_layer;
}

// static
scoped_refptr<cc::UIResourceLayer> TabGroupLayer::CreateTabGroupCreationLayer(
    float dp_to_px,
    float width) {
  static scoped_refptr<cc::UIResourceLayer> kCache;
  if (kCache)
    return kCache;
  scoped_refptr<cc::UIResourceLayer> tab_group_creation_layer =
      cc::UIResourceLayer::Create();

  tab_group_creation_layer->SetIsDrawable(true);

  float label_text_height = dp_to_px * 12;

  scoped_refptr<cc::UIResourceLayer> label_layer =
      cc::UIResourceLayer::Create();

  label_layer->SetIsDrawable(true);
  // Width is in pixels!
  label_layer->SetBounds(gfx::Size(width, 12 * dp_to_px));

  SkBitmap label_bitmap;
  label_bitmap.allocN32Pixels(label_layer->bounds().width(),
                              label_layer->bounds().height());
  label_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas label_paint_canvas(label_bitmap);
  gfx::Canvas label_canvas(&label_paint_canvas, 1.0f);
  label_canvas.Save();
  label_canvas.ClipRect(gfx::Rect(label_bitmap.width(), label_bitmap.height()));

  auto label_render_text =
      CreateRenderText(label_text_height, gfx::Font::Weight::NORMAL,
                       SkColorSetARGB(138, 0, 0, 0), false,
                       gfx::Rect(label_bitmap.width(), label_bitmap.height()));

  label_render_text->SetHorizontalAlignment(gfx::ALIGN_CENTER);
  base::string16 label_text = base::string16();
  std::string label_string = "Quickly switch between related tabs";
  base::UTF8ToUTF16(label_string.c_str(), label_string.length(), &label_text);
  label_render_text->SetText(label_text);
  label_render_text->Draw(&label_canvas);
  label_canvas.Restore();
  label_bitmap.setImmutable();
  label_layer->SetBitmap(label_bitmap);
  tab_group_creation_layer->AddChild(label_layer);

  // button layer

  scoped_refptr<cc::UIResourceLayer> button_layer =
      cc::UIResourceLayer::Create();

  button_layer->SetIsDrawable(true);
  // Width is in pixels!
  button_layer->SetBounds(gfx::Size(width, 12 * dp_to_px));

  SkBitmap button_bitmap;
  button_bitmap.allocN32Pixels(button_layer->bounds().width(),
                               button_layer->bounds().height());
  button_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas button_paint_canvas(button_bitmap);
  gfx::Canvas button_canvas(&button_paint_canvas, 1.0f);
  button_canvas.Save();
  button_canvas.ClipRect(
      gfx::Rect(button_bitmap.width(), button_bitmap.height()));

  SkColor modern_blue_600 = SkColorSetARGB(255, 26, 115, 232);
  auto button_render_text = CreateRenderText(
      label_text_height, gfx::Font::Weight::SEMIBOLD, modern_blue_600, false,
      gfx::Rect(button_bitmap.width(), button_bitmap.height()));

  button_render_text->SetHorizontalAlignment(gfx::ALIGN_CENTER);
  base::string16 button_text = base::string16();
  std::string button_string = "CREATE TAB GROUP";
  base::UTF8ToUTF16(button_string.c_str(), button_string.length(),
                    &button_text);
  button_render_text->SetText(button_text);
  button_render_text->Draw(&button_canvas);
  button_canvas.Restore();
  button_bitmap.setImmutable();
  button_layer->SetBitmap(button_bitmap);
  tab_group_creation_layer->AddChild(button_layer);
  button_layer->SetPosition(gfx::PointF(
      label_layer->position().x(),
      label_layer->position().y() + label_layer->bounds().height() * 2));

  kCache = tab_group_creation_layer;
  return tab_group_creation_layer;
}

void TabGroupLayer::SetThumbnailBitmap(const SkBitmap& thumbnail,
                                       bool is_favicon) {
  /*
  |     84
  | THUMBNAIL_WIDTH_DP|
    18   24   24   18
  |----|----|----|----|  ------------
  |                   |
  |     thumbnail     | TILE_HEIGHT_DP
  |                   |
  |                   |
  |                   |
  |                   |
  |                   |
  |-------------------|  ------------
  */

  float desired_width = thumbnail_layer_->bounds().width();
  float desired_height = thumbnail_layer_->bounds().height();
  SkBitmap thumbnail_bitmap;
  thumbnail_bitmap.allocN32Pixels(desired_width, desired_height);
  thumbnail_bitmap.eraseColor(SK_ColorTRANSPARENT);
  SkCanvas canvas(thumbnail_bitmap);
  SkRect dest_rect = SkRect::MakeWH(desired_width, desired_height);
  SkRRect clipRect = SkRRect::MakeEmpty();
  SkScalar radius = SkIntToScalar(dp_to_px_ * 10);
  SkVector fRadii[4] = {{radius, radius}, {radius, radius}, {0, 0}, {0, 0}};
  clipRect.setRectRadii(dest_rect, fRadii);
  canvas.clipRRect(clipRect, SkClipOp::kIntersect, true);

  if (is_favicon) {
    SkPaint paint = SkPaint();
    paint.setStyle(SkPaint::Style::kStrokeAndFill_Style);
    paint.setColor(SkColorSetRGB(241, 243, 244));
    paint.setAntiAlias(true);
    float circle_radius = 24 * dp_to_px_;
    canvas.drawCircle(SkPoint::Make(desired_width / 2, desired_height / 2),
                      SkFloatToScalar(circle_radius), paint);
    float favicon_length = 24 * dp_to_px_;
    dest_rect.setLTRB((desired_width - favicon_length) / 2,
                      (desired_height - favicon_length) / 2,
                      (desired_width + favicon_length) / 2,
                      (desired_height + favicon_length) / 2);
    canvas.drawBitmapRect(thumbnail, dest_rect, nullptr);
  } else {
    float thumbnail_width = thumbnail.width();
    float thumbnail_height = thumbnail.height();
    float adjusted_width = thumbnail_width;
    float adjusted_height = thumbnail_height;
    SkRect src_rect;
    if (thumbnail_width < thumbnail_height) {
      adjusted_height = thumbnail_width * desired_height / desired_width;
      if (adjusted_height < 0.7 * thumbnail_height) {
        adjusted_height = 0.7 * thumbnail_height;
        adjusted_width = adjusted_height * desired_width / desired_height;
      }
    } else {
      adjusted_width = thumbnail_height * desired_width / desired_height;
      if (adjusted_width < 0.7 * thumbnail_width) {
        adjusted_width = 0.7 * thumbnail_width;
        adjusted_height = adjusted_width * desired_height / desired_width;
      }
    }
    src_rect = SkRect::MakeXYWH((thumbnail_width - adjusted_width) / 2,
                                (thumbnail_height - adjusted_height) / 2,
                                adjusted_width, adjusted_height);
    canvas.drawBitmapRect(thumbnail, src_rect, dest_rect, nullptr);
  }
  thumbnail_bitmap.setImmutable();
  thumbnail_layer_->SetBitmap(thumbnail_bitmap);
}

void TabGroupLayer::SetCloseResourceId(cc::UIResourceId resource_id) {
  close_layer_->SetUIResourceId(resource_id);
}

void TabGroupLayer::SetText(const base::string16& title,
                            const base::string16& domain) {
  // Initialize and draw title text.
  SetTitle(title);

  // Initialize and draw domain text.
  if (domain_layer_)
    SetDomain(domain);
}

void TabGroupLayer::SetTitle(const base::string16& title) {
  float title_text_height = dp_to_px_ * 14;
  SkBitmap title_bitmap;
  title_bitmap.allocN32Pixels(title_layer_->bounds().width(),
                              title_layer_->bounds().height());
  title_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas title_paint_canvas(title_bitmap);
  gfx::Canvas title_canvas(&title_paint_canvas, 1.0f);
  title_canvas.Save();
  title_canvas.ClipRect(gfx::Rect(title_bitmap.width(), title_bitmap.height()));

  auto title_render_text =
      CreateRenderText(title_text_height, gfx::Font::Weight::SEMIBOLD,
                       SkColorSetARGB(222, 0, 0, 0), false,
                       gfx::Rect(title_bitmap.width(), title_bitmap.height()));

  title_render_text->SetText(title);
  title_render_text->Draw(&title_canvas);
  title_canvas.Restore();
  title_bitmap.setImmutable();

  title_layer_->SetBitmap(title_bitmap);
}

void TabGroupLayer::SetDomain(const base::string16& domain) {
  float domain_text_height = dp_to_px_ * 12;
  SkBitmap domain_bitmap;
  domain_bitmap.allocN32Pixels(domain_layer_->bounds().width(),
                               domain_layer_->bounds().height());
  domain_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas domain_paint_canvas(domain_bitmap);
  gfx::Canvas domain_canvas(&domain_paint_canvas, 1.0f);
  domain_canvas.Save();
  domain_canvas.ClipRect(
      gfx::Rect(domain_bitmap.width(), domain_bitmap.height()));

  auto domain_render_text = CreateRenderText(
      domain_text_height, gfx::Font::Weight::NORMAL,
      SkColorSetARGB(138, 0, 0, 0), false,
      gfx::Rect(domain_bitmap.width(), domain_bitmap.height()));

  domain_render_text->SetText(domain);
  domain_render_text->Draw(&domain_canvas);
  domain_canvas.Restore();
  domain_bitmap.setImmutable();

  domain_layer_->SetBitmap(domain_bitmap);
}

void TabGroupLayer::AddBorderLayer() {
  scoped_refptr<cc::UIResourceLayer> layer = cc::UIResourceLayer::Create();
  layer->SetIsDrawable(true);
  float desired_width = TILE_WIDTH_DP * dp_to_px_;
  float desired_height = TILE_HEIGHT_DP * dp_to_px_;
  layer->SetBounds(gfx::Size(desired_width, desired_height));

  SkBitmap border_bitmap;
  border_bitmap.allocN32Pixels(desired_width, desired_height);
  border_bitmap.eraseColor(SK_ColorTRANSPARENT);
  SkCanvas canvas(border_bitmap);
  SkRect dest_rect = SkRect::MakeWH(desired_width, desired_height);
  SkPaint paint = SkPaint();
  paint.setStyle(SkPaint::Style::kStroke_Style);
  paint.setColor(SkColorSetRGB(241, 243, 244));
  paint.setStrokeWidth(SkFloatToScalar(2 * dp_to_px_));
  paint.setAntiAlias(true);
  canvas.drawRRect(SkRRect::MakeRectXY(dest_rect, SkIntToScalar(dp_to_px_ * 10),
                                       SkIntToScalar(dp_to_px_ * 10)),
                   paint);
  canvas.drawLine(
      SkPoint::Make(THUMBNAIL_WIDTH_DP * dp_to_px_, 0),
      SkPoint::Make(THUMBNAIL_WIDTH_DP * dp_to_px_, TILE_HEIGHT_DP * dp_to_px_),
      paint);

  border_bitmap.setImmutable();
  layer->SetBitmap(border_bitmap);
  layer_->AddChild(layer);
  layer->SetPosition(gfx::PointF(0, 0));
}

gfx::PointF TabGroupLayer::GetPinPosition(float size) {
  return gfx::PointF(layer_->position().x() +
                         thumbnail_layer_->bounds().width() - size +
                         11 * dp_to_px_,
                     layer_->position().y() - 10 * dp_to_px_);
}

scoped_refptr<cc::Layer> TabGroupLayer::layer() {
  return layer_;
}

TabGroupLayer::TabGroupLayer(float dp_to_px)
    : layer_(cc::Layer::Create()),
      thumbnail_layer_(cc::UIResourceLayer::Create()),
      title_layer_(cc::UIResourceLayer::Create()),
      domain_layer_(cc::UIResourceLayer::Create()),
      close_layer_(cc::UIResourceLayer::Create()),
      dp_to_px_(dp_to_px) {
  /*
  |                      TILE_WIDTH_DP                          |
  | THUMBNAIL_WIDTH_DP |8|  text_width  |16|CLOSE_BTN_SIZE_DP|16|

  |-------------------------------------------------------------|  ------------
  |                    | | title        |                       |
  |     thumbnail      | |--------------|  |  close button   |  | TILE_HEIGHT_DP
  |                    | | domain       |                       |
  |-------------------------------------------------------------|  ------------
  */

  int text_width = TILE_WIDTH_DP - THUMBNAIL_WIDTH_DP - 8 -
                   CLOSE_BTN_PADDING_DP - CLOSE_BTN_SIZE_DP -
                   CLOSE_BTN_PADDING_DP;
  layer_->SetIsDrawable(true);
  float desired_width = TILE_WIDTH_DP * dp_to_px_;
  float desired_height = TILE_HEIGHT_DP * dp_to_px_;
  layer_->SetBounds(gfx::Size(desired_width, desired_height));

  thumbnail_layer_->SetIsDrawable(true);
  thumbnail_layer_->SetBounds(
      gfx::Size(THUMBNAIL_WIDTH_DP * dp_to_px_, TILE_HEIGHT_DP * dp_to_px_));
  layer_->AddChild(thumbnail_layer_);
  thumbnail_layer_->SetPosition(gfx::PointF(0, 0));

  title_layer_->SetIsDrawable(true);
  title_layer_->SetBounds(gfx::Size(text_width * dp_to_px_, 24 * dp_to_px_));
  layer_->AddChild(title_layer_);
  title_layer_->SetPosition(gfx::PointF(
      thumbnail_layer_->bounds().width() + 8 * dp_to_px_, 20 * dp_to_px_));

  domain_layer_->SetIsDrawable(true);
  domain_layer_->SetBounds(gfx::Size(text_width * dp_to_px_, 24 * dp_to_px_));
  layer_->AddChild(domain_layer_);
  domain_layer_->SetPosition(gfx::PointF(
      thumbnail_layer_->bounds().width() + 8 * dp_to_px_,
      title_layer_->position().y() + title_layer_->bounds().height()));

  close_layer_->SetIsDrawable(true);
  close_layer_->SetBounds(
      gfx::Size(CLOSE_BTN_SIZE_DP * dp_to_px_, CLOSE_BTN_SIZE_DP * dp_to_px_));
  close_layer_->SetOpacity(0.5);
  layer_->AddChild(close_layer_);
  close_layer_->SetPosition(gfx::PointF(
      thumbnail_layer_->bounds().width() + 8 * dp_to_px_ +
          title_layer_->bounds().width() + CLOSE_BTN_PADDING_DP * dp_to_px_,
      (TILE_HEIGHT_DP - CLOSE_BTN_SIZE_DP) / 2 * dp_to_px_));

  // AddBorderLayer();
}

TabGroupLayer::~TabGroupLayer() {}

}  // namespace android
