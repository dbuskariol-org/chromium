// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_JOURNEY_AUTOTAB_THUMNAIL_FETCHER_H_
#define CHROME_BROWSER_ANDROID_JOURNEY_AUTOTAB_THUMNAIL_FETCHER_H_

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

#include "base/callback_forward.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "url/gurl.h"

class PrefService;
class Profile;

namespace gfx {
class Image;
}  // namespace gfx

namespace image_fetcher {
class ImageFetcher;
struct RequestMetadata;
}  // namespace image_fetcher

namespace journey {

using ImageFetchedCallback = base::OnceCallback<void(const gfx::Image&)>;

using ImageDataFetchedCallback =
    base::OnceCallback<void(const std::string& image_data)>;

// AutotabThumbnailFetcher takes care of fetching images from the network and
// caching them in the database.
class AutotabThumbnailFetcher {
 public:
  AutotabThumbnailFetcher(Profile* profile);
  virtual ~AutotabThumbnailFetcher();

  virtual void FetchSalientImage(int64_t entry_timestamp,
                                 const GURL& url,
                                 ImageDataFetchedCallback image_data_callback,
                                 ImageFetchedCallback image_callback);

 private:
  void OnImageDataFetched();

  void OnImageDecodingDone(ImageFetchedCallback callback,
                           const std::string& entry_timestamp,
                           const gfx::Image& image,
                           const image_fetcher::RequestMetadata& metadata);

  void OnImageFetchedFromDatabase(ImageDataFetchedCallback image_data_callback,
                                  ImageFetchedCallback image_callback,
                                  int64_t entry_timestamp,
                                  const GURL& url,
                                  std::string data);

  void OnImageDecodedFromDatabase(ImageFetchedCallback callback,
                                  int64_t entry_timestamp,
                                  const GURL& url,
                                  const gfx::Image& image);

  void FetchImageFromNetwork(int64_t entry_timestamp,
                             const GURL& url,
                             ImageDataFetchedCallback image_data_callback,
                             ImageFetchedCallback image_callback);
  void SaveImageAndInvokeDataCallback(
      int64_t entry_timestamp,
      ImageDataFetchedCallback callback,
      const std::string& image_data,
      const image_fetcher::RequestMetadata& request_metadata);

  std::unique_ptr<image_fetcher::ImageFetcher> image_fetcher_;

  base::WeakPtrFactory<AutotabThumbnailFetcher> weak_ptr_factory_;
  DISALLOW_COPY_AND_ASSIGN(AutotabThumbnailFetcher);
};

}  // namespace journey

#endif  // CHROME_BROWSER_ANDROID_JOURNEY_AUTOTAB_THUMNAIL_FETCHER_H_
