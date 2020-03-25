// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_ASSISTANT_TEST_ASSISTANT_ASH_TEST_BASE_H_
#define ASH_ASSISTANT_TEST_ASSISTANT_ASH_TEST_BASE_H_

#include <memory>
#include <string>
#include <vector>

#include "ash/assistant/model/assistant_ui_model.h"
#include "ash/test/ash_test_base.h"
#include "base/macros.h"
#include "chromeos/services/assistant/public/cpp/assistant_prefs.h"

namespace aura {
class Window;
}  // namespace aura

namespace views {
class Textfield;
class View;
class Widget;
}  // namespace views

namespace ash {

class AssistantController;
class AssistantInteractionController;
class AssistantInteractionModel;
class AssistantTestApi;
class TestAssistantClient;
class TestAssistantService;
class TestAssistantSetup;
class TestAssistantWebViewFactory;

// Helper class to make testing the Assistant Ash UI easier.
class AssistantAshTestBase : public AshTestBase {
 public:
  using AssistantEntryPoint = chromeos::assistant::mojom::AssistantEntryPoint;
  using AssistantExitPoint = chromeos::assistant::mojom::AssistantExitPoint;

  AssistantAshTestBase();
  AssistantAshTestBase(base::test::TaskEnvironment::TimeSource time);
  ~AssistantAshTestBase() override;

  void SetUp() override;
  void TearDown() override;

  // Show the Assistant UI. The optional |entry_point| can be used to emulate
  // the different ways of launching the Assistant.
  void ShowAssistantUi(
      AssistantEntryPoint entry_point = AssistantEntryPoint::kUnspecified);
  // Close the Assistant UI. The optional |exit_point| can be used to emulate
  // the different ways of closing the Assistant, such as without closing the
  // launcher.
  void CloseAssistantUi(
      AssistantExitPoint exit_point = AssistantExitPoint::kUnspecified);

  // Open the launcher (but do not open the Assistant UI).
  void OpenLauncher();
  // Close the Assistant UI by closing the launcher.
  void CloseLauncher();

  void SetTabletMode(bool enable);

  // Changes the user preference controlling the status of user consent.
  void SetConsentStatus(chromeos::assistant::prefs::ConsentStatus);

  // Change the user setting controlling whether the user prefers voice or
  // keyboard.
  void SetPreferVoice(bool value);

  void StartOverview();

  // Return true if the Assistant UI is visible.
  bool IsVisible();

  // Return the actual displayed Assistant main view.
  // Can only be used after |ShowAssistantUi| has been called.
  views::View* main_view();

  // This is the top-level Assistant specific view.
  // Can only be used after |ShowAssistantUi| has been called.
  views::View* page_view();

  // Return the app list view hosting the Assistant page view.
  // Can only be used after |ShowAssistantUi| has been called.
  views::View* app_list_view();

  // Return the root view hosting the Assistant page view.
  // Can only be used after |ShowAssistantUi| has been called.
  views::View* root_view();

  // Spoof sending a request to the Assistant service,
  // and receiving |response_text| as a response to display.
  void MockAssistantInteractionWithResponse(const std::string& response_text);

  void MockAssistantInteractionWithQueryAndResponse(
      const std::string& query,
      const std::string& response_text);

  // Simulate the user entering a query followed by <return>.
  void SendQueryThroughTextField(const std::string& query);

  // Simulate the user tapping on the given view.
  // Waits for the event to be processed.
  void TapOnAndWait(views::View* view);

  // Simulate the user tapping at the given position.
  // Waits for the event to be processed.
  void TapAndWait(gfx::Point position);

  // Simulate a mouse click on the given view.
  // Waits for the event to be processed.
  void ClickOnAndWait(views::View* view);

  // Returns the current interaction. Returns |base::nullopt| if no interaction
  // is in progress.
  base::Optional<chromeos::assistant::mojom::AssistantInteractionMetadata>
  current_interaction();

  // Creates a new App window, and activate it.
  // Returns a pointer to the newly created window.
  // The window will be destroyed when the test is finished.
  aura::Window* SwitchToNewAppWindow();

  // Creates a new Widget, and activate it.
  // Returns a pointer to the newly created widget.
  // The widget will be destroyed when the test is finished.
  views::Widget* SwitchToNewWidget();

  // Return the window containing the Assistant UI.
  // Note that this window is shared for all components of the |AppList|.
  aura::Window* window();

  // Return the text field used for inputting new queries.
  views::Textfield* input_text_field();

  // Return the mic field used for dictating new queries.
  views::View* mic_view();

  // Return the greeting label shown when you first open the Assistant.
  views::View* greeting_label();

  // Return the button to enable voice mode.
  views::View* voice_input_toggle();

  // Return the button to enable text mode.
  views::View* keyboard_input_toggle();

  // Returns the button to launch Assistant onboarding.
  views::View* opt_in_view();

  // Show/Dismiss the on-screen keyboard.
  void ShowKeyboard();
  void DismissKeyboard();

  // Returns if the on-screen keyboard is being displayed.
  bool IsKeyboardShowing() const;

  // Enable/Disable the on-screen keyboard.
  void EnableKeyboard() { SetVirtualKeyboardEnabled(true); }
  void DisableKeyboard() { SetVirtualKeyboardEnabled(false); }

  AssistantInteractionController* interaction_controller();
  const AssistantInteractionModel* interaction_model();

 private:
  TestAssistantService* assistant_service();

  std::unique_ptr<AssistantTestApi> test_api_;
  std::unique_ptr<TestAssistantSetup> test_setup_;
  std::unique_ptr<TestAssistantWebViewFactory> test_web_view_factory_;
  AssistantController* controller_ = nullptr;

  std::vector<std::unique_ptr<aura::Window>> windows_;
  std::vector<std::unique_ptr<views::Widget>> widgets_;

  std::unique_ptr<TestAssistantClient> assistant_client_;

  DISALLOW_COPY_AND_ASSIGN(AssistantAshTestBase);
};

}  // namespace ash

#endif  // ASH_ASSISTANT_TEST_ASSISTANT_ASH_TEST_BASE_H_
