// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "media/gpu/windows/d3d11_texture_wrapper.h"

#include <utility>

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/single_thread_task_runner.h"
#include "base/test/task_environment.h"
#include "media/gpu/test/fake_command_buffer_helper.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "ui/gl/gl_egl_api_implementation.h"
#include "ui/gl/gl_image.h"
#include "ui/gl/gl_surface.h"
#include "ui/gl/init/gl_factory.h"
#include "ui/gl/test/gl_image_test_support.h"

using ::testing::_;
using ::testing::Bool;
using ::testing::Combine;
using ::testing::Return;
using ::testing::Values;

namespace media {

class D3D11TextureWrapperUnittest : public ::testing::Test {
 public:
  void SetUp() override {
    task_runner_ = task_environment_.GetMainThreadTaskRunner();

    gl::GLImageTestSupport::InitializeGL(gl::kGLImplementationEGLANGLE);
    surface_ = gl::init::CreateOffscreenGLSurface(gfx::Size());
    context_ = gl::init::CreateGLContext(nullptr, surface_.get(),
                                         gl::GLContextAttribs());
    context_->MakeCurrent(surface_.get());

    // Create some objects that most tests want.
    fake_command_buffer_helper_ =
        base::MakeRefCounted<FakeCommandBufferHelper>(task_runner_);
    get_helper_cb_ = base::BindRepeating(
        [](scoped_refptr<CommandBufferHelper> helper) { return helper; },
        fake_command_buffer_helper_);
  }

  void TearDown() override {
    context_->ReleaseCurrent(surface_.get());
    context_ = nullptr;
    surface_ = nullptr;
    gl::GLImageTestSupport::CleanupGL();
  }

  base::test::TaskEnvironment task_environment_;
  scoped_refptr<base::SingleThreadTaskRunner> task_runner_;

  scoped_refptr<gl::GLSurface> surface_;
  scoped_refptr<gl::GLContext> context_;

  // Made-up size for the images.
  const gfx::Size size_{100, 200};

  // CommandBufferHelper, and a callback that returns it.  Useful to initialize
  // a wrapper.
  scoped_refptr<FakeCommandBufferHelper> fake_command_buffer_helper_;
  GetCommandBufferHelperCB get_helper_cb_;
};

TEST_F(D3D11TextureWrapperUnittest, NV12InitSuceeds) {
  const DXGI_FORMAT dxgi_format = DXGI_FORMAT_NV12;

  auto wrapper = std::make_unique<DefaultTexture2DWrapper>(size_, dxgi_format);
  const bool init_result = wrapper->Init(get_helper_cb_);
  EXPECT_TRUE(init_result);

  // TODO: verify that ProcessTexture processes both textures.
}

TEST_F(D3D11TextureWrapperUnittest, BGRA8InitSuceeds) {
  const DXGI_FORMAT dxgi_format = DXGI_FORMAT_B8G8R8A8_UNORM;

  auto wrapper = std::make_unique<DefaultTexture2DWrapper>(size_, dxgi_format);
  const bool init_result = wrapper->Init(get_helper_cb_);
  EXPECT_TRUE(init_result);
}

TEST_F(D3D11TextureWrapperUnittest, FP16InitSuceeds) {
  const DXGI_FORMAT dxgi_format = DXGI_FORMAT_R16G16B16A16_FLOAT;

  auto wrapper = std::make_unique<DefaultTexture2DWrapper>(size_, dxgi_format);
  const bool init_result = wrapper->Init(get_helper_cb_);
  EXPECT_TRUE(init_result);
}

TEST_F(D3D11TextureWrapperUnittest, P010InitSuceeds) {
  const DXGI_FORMAT dxgi_format = DXGI_FORMAT_P010;

  auto wrapper = std::make_unique<DefaultTexture2DWrapper>(size_, dxgi_format);
  const bool init_result = wrapper->Init(get_helper_cb_);
  EXPECT_TRUE(init_result);
}

}  // namespace media