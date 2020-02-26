// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <memory>

#include "base/files/file_path.h"
#include "base/path_service.h"
#include "base/values.h"
#include "content/public/test/browser_task_environment.h"
#include "content/public/test/test_utils.h"
#include "extensions/browser/content_verifier.h"
#include "extensions/browser/content_verifier/test_utils.h"
#include "extensions/browser/extension_registry.h"
#include "extensions/browser/extensions_test.h"
#include "extensions/common/extension.h"
#include "extensions/common/extension_builder.h"
#include "extensions/common/extension_paths.h"
#include "extensions/common/extensions_client.h"
#include "extensions/common/manifest_constants.h"
#include "extensions/common/manifest_handlers/background_info.h"
#include "extensions/common/manifest_handlers/content_scripts_handler.h"
#include "extensions/common/scoped_testing_manifest_handler_registry.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace extensions {

namespace {

enum class BackgroundManifestType {
  kNone,
  kBackgroundScript,
  kBackgroundPage,
};

base::FilePath kBackgroundScriptPath(FILE_PATH_LITERAL("foo/bg.txt"));
base::FilePath kContentScriptPath(FILE_PATH_LITERAL("foo/content.txt"));
base::FilePath kBackgroundPagePath(FILE_PATH_LITERAL("foo/page.txt"));
base::FilePath kScriptFilePath(FILE_PATH_LITERAL("bar/code.js"));
base::FilePath kUnknownTypeFilePath(FILE_PATH_LITERAL("bar/code.txt"));
base::FilePath kHTMLFilePath(FILE_PATH_LITERAL("bar/page.html"));
base::FilePath kHTMFilePath(FILE_PATH_LITERAL("bar/page.htm"));
base::FilePath kIconPath(FILE_PATH_LITERAL("bar/16.png"));

class TestContentVerifierDelegate : public MockContentVerifierDelegate {
 public:
  TestContentVerifierDelegate() = default;
  ~TestContentVerifierDelegate() override = default;

  std::set<base::FilePath> GetBrowserImagePaths(
      const extensions::Extension* extension) override;

  void SetBrowserImagePaths(std::set<base::FilePath> paths);

 private:
  std::set<base::FilePath> browser_images_paths_;

  DISALLOW_COPY_AND_ASSIGN(TestContentVerifierDelegate);
};

std::set<base::FilePath> TestContentVerifierDelegate::GetBrowserImagePaths(
    const extensions::Extension* extension) {
  return std::set<base::FilePath>(browser_images_paths_);
}

void TestContentVerifierDelegate::SetBrowserImagePaths(
    std::set<base::FilePath> paths) {
  browser_images_paths_ = paths;
}

}  // namespace

class ContentVerifierTest : public ExtensionsTest {
 public:
  ContentVerifierTest() = default;

  ContentVerifierTest(const ContentVerifierTest&) = delete;
  ContentVerifierTest& operator=(const ContentVerifierTest&) = delete;

  void SetUp() override {
    ExtensionsTest::SetUp();

    // Manually register handlers since the |ContentScriptsHandler| is not
    // usually registered in extensions_unittests.
    ScopedTestingManifestHandlerRegistry registry;
    {
      ManifestHandlerRegistry* registry = ManifestHandlerRegistry::Get();
      registry->RegisterHandler(std::make_unique<BackgroundManifestHandler>());
      registry->RegisterHandler(std::make_unique<ContentScriptsHandler>());
      ManifestHandler::FinalizeRegistration();
    }

    extension_ = CreateTestExtension();
    ExtensionRegistry::Get(browser_context())->AddEnabled(extension_);

    auto content_verifier_delegate =
        std::make_unique<TestContentVerifierDelegate>();
    content_verifier_delegate_raw_ = content_verifier_delegate.get();

    content_verifier_ = new ContentVerifier(
        browser_context(), std::move(content_verifier_delegate));
    // |ContentVerifier::ShouldVerifyAnyPaths| always returns false if the
    // Content Verifier does not have |ContentVerifierIOData::ExtensionData|
    // for the extension.
    content_verifier_->ResetIODataForTesting(extension_.get());
  }

  void TearDown() override {
    content_verifier_->Shutdown();
    ExtensionsTest::TearDown();
  }

  void UpdateBrowserImagePaths(const std::set<base::FilePath>& paths) {
    content_verifier_delegate_raw_->SetBrowserImagePaths(paths);
    content_verifier_->ResetIODataForTesting(extension_.get());
  }

  bool ShouldVerifySinglePath(const base::FilePath& path) {
    return content_verifier_->ShouldVerifyAnyPathsForTesting(
        extension_->id(), extension_->path(), {path});
  }

  BackgroundManifestType GetBackgroundManifestType() {
    return background_manifest_type_;
  }

 protected:
  BackgroundManifestType background_manifest_type_ =
      BackgroundManifestType::kNone;

 private:
  // Create a test extension with a content script and possibly a background
  // page or background script.
  scoped_refptr<Extension> CreateTestExtension() {
    base::DictionaryValue manifest;
    manifest.SetString("name", "Dummy Extension");
    manifest.SetString("version", "1");
    manifest.SetInteger("manifest_version", 2);

    if (background_manifest_type_ ==
        BackgroundManifestType::kBackgroundScript) {
      auto background_scripts = std::make_unique<base::ListValue>();
      background_scripts->AppendString("foo/bg.txt");
      manifest.Set(manifest_keys::kBackgroundScripts,
                   std::move(background_scripts));
    } else if (background_manifest_type_ ==
               BackgroundManifestType::kBackgroundPage) {
      manifest.SetString(manifest_keys::kBackgroundPage, "foo/page.txt");
    }

    auto content_scripts = std::make_unique<base::ListValue>();
    auto content_script = std::make_unique<base::DictionaryValue>();
    auto js_files = std::make_unique<base::ListValue>();
    auto matches = std::make_unique<base::ListValue>();
    js_files->AppendString("foo/content.txt");
    content_script->Set("js", std::move(js_files));
    matches->AppendString("http://*/*");
    content_script->Set("matches", std::move(matches));
    content_scripts->Append(std::move(content_script));
    manifest.Set(manifest_keys::kContentScripts, std::move(content_scripts));

    base::FilePath path;
    EXPECT_TRUE(base::PathService::Get(DIR_TEST_DATA, &path));

    std::string error;
    scoped_refptr<Extension> extension(Extension::Create(
        path, Manifest::INTERNAL, manifest, Extension::NO_FLAGS, &error));
    EXPECT_TRUE(extension.get()) << error;
    return extension;
  }

  scoped_refptr<ContentVerifier> content_verifier_;
  scoped_refptr<Extension> extension_;
  TestContentVerifierDelegate* content_verifier_delegate_raw_;
};

class ContentVerifierTestWithBackgroundType
    : public ContentVerifierTest,
      public testing::WithParamInterface<BackgroundManifestType> {
 public:
  ContentVerifierTestWithBackgroundType() {
    background_manifest_type_ = GetParam();
  }

  ContentVerifierTestWithBackgroundType(
      const ContentVerifierTestWithBackgroundType&) = delete;
  ContentVerifierTestWithBackgroundType& operator=(
      const ContentVerifierTestWithBackgroundType&) = delete;
};

// Verifies that |ContentVerifier::ShouldVerifyAnyPaths| returns true for
// some file paths even if those paths are specified as browser images.
TEST_P(ContentVerifierTestWithBackgroundType, BrowserImagesShouldBeVerified) {
  std::set<base::FilePath> files_to_be_verified = {
      kContentScriptPath, kScriptFilePath, kHTMLFilePath, kHTMFilePath};
  std::set<base::FilePath> files_not_to_be_verified{kIconPath,
                                                    kUnknownTypeFilePath};

  if (GetBackgroundManifestType() ==
      BackgroundManifestType::kBackgroundScript) {
    files_to_be_verified.insert(kBackgroundScriptPath);
    files_not_to_be_verified.insert(kBackgroundPagePath);
  } else if (GetBackgroundManifestType() ==
             BackgroundManifestType::kBackgroundPage) {
    files_to_be_verified.insert(kBackgroundPagePath);
    files_not_to_be_verified.insert(kBackgroundScriptPath);
  } else {
    files_not_to_be_verified.insert(kBackgroundScriptPath);
    files_not_to_be_verified.insert(kBackgroundPagePath);
  }

  for (const base::FilePath& path : files_to_be_verified) {
    UpdateBrowserImagePaths({});
    EXPECT_TRUE(ShouldVerifySinglePath(path)) << "for path " << path;
    UpdateBrowserImagePaths(std::set<base::FilePath>{path});
    EXPECT_TRUE(ShouldVerifySinglePath(path)) << "for path " << path;
  }

  for (const base::FilePath& path : files_not_to_be_verified) {
    UpdateBrowserImagePaths({});
    EXPECT_TRUE(ShouldVerifySinglePath(path)) << "for path " << path;
    UpdateBrowserImagePaths(std::set<base::FilePath>{path});
    EXPECT_FALSE(ShouldVerifySinglePath(path)) << "for path " << path;
  }
}

INSTANTIATE_TEST_SUITE_P(
    All,
    ContentVerifierTestWithBackgroundType,
    testing::Values(BackgroundManifestType::kNone,
                    BackgroundManifestType::kBackgroundScript,
                    BackgroundManifestType::kBackgroundPage));

TEST_F(ContentVerifierTest, NormalizeRelativePath) {
// This macro helps avoid wrapped lines in the test structs.
#define FPL(x) FILE_PATH_LITERAL(x)
  struct TestData {
    base::FilePath::StringPieceType input;
    base::FilePath::StringPieceType expected;
  } test_cases[] = {{FPL("foo/bar"), FPL("foo/bar")},
                    {FPL("foo//bar"), FPL("foo/bar")},
                    {FPL("foo/bar/"), FPL("foo/bar/")},
                    {FPL("foo/bar//"), FPL("foo/bar/")},
                    {FPL("foo/options.html/"), FPL("foo/options.html/")}};
#undef FPL
  for (const auto& test_case : test_cases) {
    base::FilePath input(test_case.input);
    base::FilePath expected(test_case.expected);
    EXPECT_EQ(expected,
              ContentVerifier::NormalizeRelativePathForTesting(input));
  }
}

// Tests that JavaScript and html/htm files are always verified, even if their
// extension case isn't lower cased or even if they are specified as browser
// image paths.
TEST_F(ContentVerifierTest, JSAndHTMLAlwaysVerified) {
  std::vector<std::string> paths = {
      "a.js",  "b.html", "c.htm",  "a.JS",  "b.HTML",
      "c.HTM", "a.Js",   "b.Html", "c.Htm",
  };

  for (const auto& path_str : paths) {
    const base::FilePath path = base::FilePath().AppendASCII(path_str);
    UpdateBrowserImagePaths({});
    // |path| would be treated as unclassified resource, so it gets verified.
    EXPECT_TRUE(ShouldVerifySinglePath(path)) << "for path " << path;
    // Even if |path| was specified as browser image, as |path| is JS/html
    // (sensitive) resource, it would still get verified.
    UpdateBrowserImagePaths({path});
    EXPECT_TRUE(ShouldVerifySinglePath(path)) << "for path " << path;
  }
}

}  // namespace extensions
