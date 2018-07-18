// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/compositor/layer/autotab_layer.h"

#include "base/strings/utf_string_conversions.h"
#include "cc/layers/ui_resource_layer.h"
#include "cc/paint/skia_paint_canvas.h"
#include "ui/gfx/canvas.h"
#include "ui/gfx/font.h"
#include "ui/gfx/geometry/size_conversions.h"
#include "ui/gfx/render_text.h"

namespace android {

namespace {
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
scoped_refptr<AutoTabLayer> AutoTabLayer::Create(float dp_to_px) {
  return base::WrapRefCounted(new AutoTabLayer(dp_to_px));
}

// static
scoped_refptr<AutoTabLayer> AutoTabLayer::CreateWith(
    float dp_to_px,
    bool image_is_favicon,
    const SkBitmap& image,
    const std::string& title,
    const std::string& domain) {
  scoped_refptr<AutoTabLayer> layer = AutoTabLayer::Create(dp_to_px);
  layer->SetThumbnailBitmap(image, image_is_favicon);

  base::string16 title_text = base::string16();
  base::UTF8ToUTF16(title.c_str(), title.length(), &title_text);
  base::string16 domain_text = base::string16();
  base::UTF8ToUTF16(domain.c_str(), domain.length(), &domain_text);

  layer->SetText(title_text, domain_text);
  return layer;
}

void AutoTabLayer::SetThumbnailBitmap(const SkBitmap& thumbnail,
                                      bool is_favicon) {
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
  thumbnail_layer_->SetBitmap(thumbnail_bitmap);
}

void AutoTabLayer::SetText(const base::string16& title,
                           const base::string16& domain) {
  // Initialize and draw title text.
  float title_text_height = dp_to_px_ * 14;
  SkBitmap title_bitmap;
  title_bitmap.allocN32Pixels(title_layer_->bounds().width(),
                              title_layer_->bounds().height());
  title_bitmap.eraseColor(SK_ColorTRANSPARENT);

  cc::SkiaPaintCanvas title_paint_canvas(title_bitmap);
  gfx::Canvas title_canvas(&title_paint_canvas, 1.0f);
  title_canvas.Save();
  title_canvas.ClipRect(gfx::Rect(title_bitmap.width(), title_bitmap.height()));

  auto title_render_text = CreateRenderText(
      title_text_height, gfx::Font::Weight::NORMAL, SkColorSetARGB(222, 0, 0, 0), true,
      gfx::Rect(title_bitmap.width(), title_bitmap.height()));
  title_render_text->SetText(title);
  title_render_text->Draw(&title_canvas);
  title_canvas.Restore();
  title_bitmap.setImmutable();

  title_layer_->SetBitmap(title_bitmap);

  // Initialize and draw domain text.
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
      domain_text_height, gfx::Font::Weight::NORMAL, SkColorSetARGB(138, 0, 0, 0), false,
      gfx::Rect(domain_bitmap.width(), domain_bitmap.height()));
  domain_render_text->SetText(domain);
  domain_render_text->Draw(&domain_canvas);
  domain_canvas.Restore();
  domain_bitmap.setImmutable();

  domain_layer_->SetBitmap(domain_bitmap);
}

void AutoTabLayer::AddBorderLayer() {
  scoped_refptr<cc::UIResourceLayer> layer = cc::UIResourceLayer::Create();
  layer->SetIsDrawable(true);
  float desired_width = 104 * dp_to_px_;
  float desired_height = 156 * dp_to_px_;
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
  canvas.drawLine(SkPoint::Make(0, 84 * dp_to_px_),
                  SkPoint::Make(104 * dp_to_px_, 84 * dp_to_px_), paint);

  layer->SetBitmap(border_bitmap);
  layer_->AddChild(layer);
  layer->SetPosition(gfx::PointF(0, 0));
}

gfx::PointF AutoTabLayer::GetPinPosition(float size) {
  return gfx::PointF(layer_->position().x() + thumbnail_layer_->bounds().width() - size + 11 * dp_to_px_, layer_->position().y() - 10 * dp_to_px_);
}

scoped_refptr<cc::Layer> AutoTabLayer::layer() {
  return layer_;
}

AutoTabLayer::AutoTabLayer(float dp_to_px)
    : layer_(cc::Layer::Create()),
      thumbnail_layer_(cc::UIResourceLayer::Create()),
      title_layer_(cc::UIResourceLayer::Create()),
      domain_layer_(cc::UIResourceLayer::Create()),
      dp_to_px_(dp_to_px) {
  layer_->SetIsDrawable(true);
  thumbnail_layer_->SetIsDrawable(true);
  thumbnail_layer_->SetBounds(gfx::Size(104 * dp_to_px_, 84 * dp_to_px_));
  layer_->AddChild(thumbnail_layer_);
  thumbnail_layer_->SetPosition(gfx::PointF(0, 0));

  title_layer_->SetIsDrawable(true);
  title_layer_->SetBounds(gfx::Size(88 * dp_to_px_, 42 * dp_to_px_));
  layer_->AddChild(title_layer_);
  title_layer_->SetPosition(gfx::PointF(
      8 * dp_to_px_, thumbnail_layer_->bounds().height() + 6 * dp_to_px_));

  domain_layer_->SetIsDrawable(true);
  domain_layer_->SetBounds(gfx::Size(88 * dp_to_px_, 24 * dp_to_px_));
  layer_->AddChild(domain_layer_);
  domain_layer_->SetPosition(gfx::PointF(
      8 * dp_to_px_,
      title_layer_->position().y() + title_layer_->bounds().height()));

  AddBorderLayer();
}

AutoTabLayer::~AutoTabLayer() {}

}  // namespace android
