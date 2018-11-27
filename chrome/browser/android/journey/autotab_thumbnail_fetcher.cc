// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/android/journey/autotab_thumbnail_fetcher.h"

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/location.h"
#include "base/logging.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/search/suggestions/image_decoder_impl.h"
#include "components/image_fetcher/core/image_decoder.h"
#include "components/image_fetcher/core/image_fetcher_impl.h"
#include "components/image_fetcher/core/image_fetcher_types.h"
#include "components/ntp_snippets/remote/remote_suggestions_database.h"
#include "content/public/browser/storage_partition.h"
#include "net/traffic_annotation/network_traffic_annotation.h"
#include "ui/gfx/geometry/size.h"
#include "ui/gfx/image/image.h"

namespace journey {
namespace {
constexpr net::NetworkTrafficAnnotationTag kTrafficAnnotation =
    net::DefineNetworkTrafficAnnotation("remote_suggestions_provider", R"(
        semantics {
          sender: "Content Suggestion Thumbnail Fetch"
          description:
            "Retrieves thumbnails for content suggestions, for display on the "
            "New Tab page or Chrome Home."
          trigger:
            "Triggered when the user looks at a content suggestion (and its "
            "thumbnail isn't cached yet)."
          data: "None."
          destination: GOOGLE_OWNED_SERVICE
        }
        policy {
          cookies_allowed: NO
          setting: "Currently not available, but in progress: crbug.com/703684"
        chrome_policy {
          NTPContentSuggestionsEnabled {
            policy_options {mode: MANDATORY}
            NTPContentSuggestionsEnabled: false
          }
        }
      })");
}  // namespace

AutotabThumbnailFetcher::AutotabThumbnailFetcher(Profile* profile)
    : weak_ptr_factory_(this) {
  image_fetcher_ = std::make_unique<image_fetcher::ImageFetcherImpl>(
      std::make_unique<suggestions::ImageDecoderImpl>(),
      content::BrowserContext::GetDefaultStoragePartition(profile)
          ->GetURLLoaderFactoryForBrowserProcess());
}

AutotabThumbnailFetcher::~AutotabThumbnailFetcher() {}

void AutotabThumbnailFetcher::FetchSalientImage(
    int64_t entry_timestamp,
    const GURL& url,
    ImageDataFetchedCallback image_data_callback,
    ImageFetchedCallback image_callback) {
  OnImageFetchedFromDatabase(std::move(image_data_callback),
                             std::move(image_callback), entry_timestamp, url,
                             "");
}

void AutotabThumbnailFetcher::OnImageDecodingDone(
    ImageFetchedCallback callback,
    const std::string& entry_timestamp,
    const gfx::Image& image,
    const image_fetcher::RequestMetadata& metadata) {
  std::move(callback).Run(image);
}

void AutotabThumbnailFetcher::OnImageFetchedFromDatabase(
    ImageDataFetchedCallback image_data_callback,
    ImageFetchedCallback image_callback,
    int64_t entry_timestamp,
    const GURL& url,
    std::string data) {
  if (data.empty()) {
    // Fetching from the DB failed; start a network fetch.
    FetchImageFromNetwork(entry_timestamp, url, std::move(image_data_callback),
                          std::move(image_callback));
    return;
  }

  if (image_data_callback) {
    std::move(image_data_callback).Run(data);
  }

  if (image_callback) {
    image_fetcher_->GetImageDecoder()->DecodeImage(
        data,
        // We're not dealing with multi-frame images.
        /*desired_image_frame_size=*/gfx::Size(),
        base::Bind(&AutotabThumbnailFetcher::OnImageDecodedFromDatabase,
                   weak_ptr_factory_.GetWeakPtr(),
                   base::Passed(std::move(image_callback)), entry_timestamp,
                   url));
  }
}

void AutotabThumbnailFetcher::OnImageDecodedFromDatabase(
    ImageFetchedCallback callback,
    int64_t entry_timestamp,
    const GURL& url,
    const gfx::Image& image) {
  if (!image.IsEmpty()) {
    std::move(callback).Run(image);
    return;
  }

  FetchImageFromNetwork(entry_timestamp, url, ImageDataFetchedCallback(),
                        std::move(callback));
}

void AutotabThumbnailFetcher::FetchImageFromNetwork(
    int64_t entry_timestamp,
    const GURL& url,
    ImageDataFetchedCallback image_data_callback,
    ImageFetchedCallback image_callback) {
  if (url.is_empty()) {
    // Return an empty image. Directly, this is never synchronous with the
    // original FetchSuggestionImage() call - an asynchronous database query has
    // happened in the meantime.
    if (image_data_callback) {
      std::move(image_data_callback).Run(std::string());
    }
    if (image_callback) {
      std::move(image_callback).Run(gfx::Image());
    }
    return;
  }
  // Image decoding callback only set when requested.
  image_fetcher::ImageFetcherCallback decode_callback;
  if (image_callback) {
    decode_callback =
        base::BindOnce(&AutotabThumbnailFetcher::OnImageDecodingDone,
                       weak_ptr_factory_.GetWeakPtr(),
                       base::Passed(std::move(image_callback)));
  }

  image_fetcher_->FetchImageAndData(
      std::to_string(entry_timestamp), url,
      base::BindOnce(&AutotabThumbnailFetcher::SaveImageAndInvokeDataCallback,
                     weak_ptr_factory_.GetWeakPtr(), entry_timestamp,
                     std::move(image_data_callback)),
      std::move(decode_callback), kTrafficAnnotation);
}

void AutotabThumbnailFetcher::SaveImageAndInvokeDataCallback(
    int64_t entry_timestamp,
    ImageDataFetchedCallback callback,
    const std::string& image_data,
    const image_fetcher::RequestMetadata& request_metadata) {
  if (callback) {
    std::move(callback).Run(image_data);
  }
}

}  // namespace journey
