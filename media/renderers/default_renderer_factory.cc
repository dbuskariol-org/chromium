// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "media/renderers/default_renderer_factory.h"

#include <memory>
#include <utility>

#include "base/bind.h"
#include "build/build_config.h"
#include "media/base/audio_buffer.h"
#include "media/base/bind_to_current_loop.h"
#include "media/base/decoder_factory.h"
#include "media/renderers/audio_renderer_impl.h"
#include "media/renderers/renderer_impl.h"
#include "media/renderers/video_renderer_impl.h"
#include "media/video/gpu_memory_buffer_video_frame_pool.h"
#include "media/video/gpu_video_accelerator_factories.h"

namespace media {

#if defined(OS_ANDROID)
DefaultRendererFactory::DefaultRendererFactory(
    MediaLog* media_log,
    DecoderFactory* decoder_factory,
    const GetGpuFactoriesCB& get_gpu_factories_cb)
    : media_log_(media_log),
      decoder_factory_(decoder_factory),
      get_gpu_factories_cb_(get_gpu_factories_cb) {
  DCHECK(decoder_factory_);
}
#else
DefaultRendererFactory::DefaultRendererFactory(
    MediaLog* media_log,
    DecoderFactory* decoder_factory,
    const GetGpuFactoriesCB& get_gpu_factories_cb,
    std::unique_ptr<SpeechRecognitionClient> speech_recognition_client)
    : media_log_(media_log),
      decoder_factory_(decoder_factory),
      get_gpu_factories_cb_(get_gpu_factories_cb),
      speech_recognition_client_(std::move(speech_recognition_client)) {
  DCHECK(decoder_factory_);
  if (speech_recognition_client_) {
    // Unretained is safe because |this| owns the speech recognition client.
    speech_recognition_client_->SetOnReadyCallback(media::BindToCurrentLoop(
        base::BindOnce(&DefaultRendererFactory::EnableSpeechRecognition,
                       base::Unretained(this))));
  }
}
#endif

DefaultRendererFactory::~DefaultRendererFactory() = default;

std::vector<std::unique_ptr<AudioDecoder>>
DefaultRendererFactory::CreateAudioDecoders(
    const scoped_refptr<base::SingleThreadTaskRunner>& media_task_runner) {
  // Create our audio decoders and renderer.
  std::vector<std::unique_ptr<AudioDecoder>> audio_decoders;

  decoder_factory_->CreateAudioDecoders(media_task_runner, media_log_,
                                        &audio_decoders);
  return audio_decoders;
}

std::vector<std::unique_ptr<VideoDecoder>>
DefaultRendererFactory::CreateVideoDecoders(
    const scoped_refptr<base::SingleThreadTaskRunner>& media_task_runner,
    RequestOverlayInfoCB request_overlay_info_cb,
    const gfx::ColorSpace& target_color_space,
    GpuVideoAcceleratorFactories* gpu_factories) {
  // Create our video decoders and renderer.
  std::vector<std::unique_ptr<VideoDecoder>> video_decoders;

  decoder_factory_->CreateVideoDecoders(
      media_task_runner, gpu_factories, media_log_,
      std::move(request_overlay_info_cb), target_color_space, &video_decoders);

  return video_decoders;
}

std::unique_ptr<Renderer> DefaultRendererFactory::CreateRenderer(
    const scoped_refptr<base::SingleThreadTaskRunner>& media_task_runner,
    const scoped_refptr<base::TaskRunner>& worker_task_runner,
    AudioRendererSink* audio_renderer_sink,
    VideoRendererSink* video_renderer_sink,
    RequestOverlayInfoCB request_overlay_info_cb,
    const gfx::ColorSpace& target_color_space) {
  DCHECK(audio_renderer_sink);

  std::unique_ptr<AudioRenderer> audio_renderer(new AudioRendererImpl(
      media_task_runner, audio_renderer_sink,
      // Unretained is safe here, because the RendererFactory is guaranteed to
      // outlive the RendererImpl. The RendererImpl is destroyed when WMPI
      // destructor calls pipeline_controller_.Stop() -> PipelineImpl::Stop() ->
      // RendererWrapper::Stop -> RendererWrapper::DestroyRenderer(). And the
      // RendererFactory is owned by WMPI and gets called after WMPI destructor
      // finishes.
      base::BindRepeating(&DefaultRendererFactory::CreateAudioDecoders,
                          base::Unretained(this), media_task_runner),
      media_log_,
      BindToCurrentLoop(base::BindRepeating(
          &DefaultRendererFactory::TranscribeAudio, base::Unretained(this)))));

  GpuVideoAcceleratorFactories* gpu_factories = nullptr;
  if (get_gpu_factories_cb_)
    gpu_factories = get_gpu_factories_cb_.Run();

  std::unique_ptr<GpuMemoryBufferVideoFramePool> gmb_pool;
  if (gpu_factories && gpu_factories->ShouldUseGpuMemoryBuffersForVideoFrames(
                           false /* for_media_stream */)) {
    gmb_pool = std::make_unique<GpuMemoryBufferVideoFramePool>(
        std::move(media_task_runner), std::move(worker_task_runner),
        gpu_factories);
  }

  std::unique_ptr<VideoRenderer> video_renderer(new VideoRendererImpl(
      media_task_runner, video_renderer_sink,
      // Unretained is safe here, because the RendererFactory is guaranteed to
      // outlive the RendererImpl. The RendererImpl is destroyed when WMPI
      // destructor calls pipeline_controller_.Stop() -> PipelineImpl::Stop() ->
      // RendererWrapper::Stop -> RendererWrapper::DestroyRenderer(). And the
      // RendererFactory is owned by WMPI and gets called after WMPI destructor
      // finishes.
      base::BindRepeating(&DefaultRendererFactory::CreateVideoDecoders,
                          base::Unretained(this), media_task_runner,
                          std::move(request_overlay_info_cb),
                          target_color_space, gpu_factories),
      true, media_log_, std::move(gmb_pool)));

  return std::make_unique<RendererImpl>(
      media_task_runner, std::move(audio_renderer), std::move(video_renderer));
}

void DefaultRendererFactory::TranscribeAudio(
    scoped_refptr<media::AudioBuffer> buffer) {
#if !defined(OS_ANDROID)
  if (is_speech_recognition_available_ && speech_recognition_client_)
    speech_recognition_client_->AddAudio(std::move(buffer));
#endif
}

void DefaultRendererFactory::EnableSpeechRecognition() {
#if !defined(OS_ANDROID)
  if (speech_recognition_client_) {
    is_speech_recognition_available_ =
        speech_recognition_client_->IsSpeechRecognitionAvailable();
  }
#endif
}

}  // namespace media
