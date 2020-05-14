// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/extensions/settings_overridden_dialog_view.h"

#include "base/path_service.h"
#include "base/strings/utf_string_conversions.h"
#include "chrome/browser/extensions/chrome_test_extension_loader.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_window.h"
#include "chrome/browser/ui/extensions/extension_settings_overridden_dialog.h"
#include "chrome/browser/ui/extensions/settings_overridden_dialog_controller.h"
#include "chrome/browser/ui/extensions/settings_overridden_params_providers.h"
#include "chrome/browser/ui/test/test_browser_dialog.h"
#include "chrome/common/chrome_paths.h"
#include "content/public/test/browser_test.h"

namespace {

// A stub dialog controller that displays the dialog with the supplied params.
class TestDialogController : public SettingsOverriddenDialogController {
 public:
  explicit TestDialogController(ShowParams show_params)
      : show_params_(std::move(show_params)) {}
  TestDialogController(const TestDialogController&) = delete;
  TestDialogController& operator=(const TestDialogController&) = delete;
  ~TestDialogController() override = default;

 private:
  bool ShouldShow() override { return true; }
  ShowParams GetShowParams() override { return show_params_; }
  void OnDialogShown() override {}
  void HandleDialogResult(DialogResult result) override {}

  const ShowParams show_params_;
};

}  // namespace

class SettingsOverriddenDialogViewBrowserTest : public DialogBrowserTest {
  void ShowUi(const std::string& name) override {
    if (name == "SimpleDialog")
      ShowSimpleDialog();
    else if (name == "NtpOverriddenDialog")
      ShowNtpOverriddenDialog();
    else
      NOTREACHED() << name;
  }

  void ShowSimpleDialog() {
    SettingsOverriddenDialogController::ShowParams params{
        base::ASCIIToUTF16("Settings overridden dialog title"),
        base::ASCIIToUTF16(
            "Settings overriden dialog body, which is quite a bit "
            "longer than the title alone")};
    auto* dialog = new SettingsOverriddenDialogView(
        std::make_unique<TestDialogController>(std::move(params)));
    dialog->Show(browser()->window()->GetNativeWindow());
  }

  void ShowNtpOverriddenDialog() {
    base::FilePath test_root_path;
    ASSERT_TRUE(base::PathService::Get(chrome::DIR_TEST_DATA, &test_root_path));

    Profile* const profile = browser()->profile();
    scoped_refptr<const extensions::Extension> extension =
        extensions::ChromeTestExtensionLoader(profile).LoadExtension(
            test_root_path.AppendASCII("extensions/api_test/override/newtab"));
    ASSERT_TRUE(extension);

    base::Optional<ExtensionSettingsOverriddenDialog::Params> params =
        settings_overridden_params::GetNtpOverriddenParams(profile);
    ASSERT_TRUE(params);
    auto controller = std::make_unique<ExtensionSettingsOverriddenDialog>(
        std::move(*params), profile);

    auto* dialog = new SettingsOverriddenDialogView(std::move(controller));
    dialog->Show(browser()->window()->GetNativeWindow());
  }
};

IN_PROC_BROWSER_TEST_F(SettingsOverriddenDialogViewBrowserTest,
                       InvokeUi_SimpleDialog) {
  ShowAndVerifyUi();
}

IN_PROC_BROWSER_TEST_F(SettingsOverriddenDialogViewBrowserTest,
                       InvokeUi_NtpOverriddenDialog) {
  ShowAndVerifyUi();
}
