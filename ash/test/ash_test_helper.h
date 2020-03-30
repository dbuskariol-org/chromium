// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_TEST_ASH_TEST_HELPER_H_
#define ASH_TEST_ASH_TEST_HELPER_H_

#include <stdint.h>

#include <memory>
#include <utility>

#include "ash/assistant/test/test_assistant_service.h"
#include "ash/session/test_session_controller_client.h"
#include "ash/shell_delegate.h"
#include "base/macros.h"
#include "base/optional.h"
#include "base/test/scoped_command_line.h"

class PrefService;

namespace aura {
class Window;
}

namespace chromeos {
namespace system {
class ScopedFakeStatisticsProvider;
}  // namespace system
}  // namespace chromeos

namespace display {
class Display;
}

namespace ui {
class ContextFactory;
class ScopedAnimationDurationScaleMode;
class TestContextFactories;
}

namespace views {
class TestViewsDelegate;
}

namespace wm {
class WMState;
}

namespace ash {

class AppListTestHelper;
class TestKeyboardControllerObserver;
class TestNewWindowDelegate;
class TestNotifierSettingsController;
class TestPrefServiceProvider;
class TestSystemTrayClient;
class TestPhotoController;

// A helper class that does common initialization required for Ash. Creates a
// root window and an ash::Shell instance with a test delegate.
class AshTestHelper {
 public:
  enum ConfigType {
    // The configuration for shell executable.
    kShell,
    // The configuration for unit tests.
    kUnitTest,
    // The configuration for perf tests. Unlike kUnitTest, this
    // does not disable animations.
    kPerfTest,
  };

  struct InitParams {
    InitParams();
    InitParams(InitParams&&);
    InitParams& operator=(InitParams&&) = default;
    ~InitParams();

    // True if the user should log in.
    bool start_session = true;
    // If this is not set, a TestShellDelegate will be used automatically.
    std::unique_ptr<ShellDelegate> delegate;
    PrefService* local_state = nullptr;
  };

  // Instantiates/destroys an AshTestHelper. This can happen in a
  // single-threaded phase without a backing task environment or ViewsDelegate,
  // and must not create those lest the caller wish to do so.
  explicit AshTestHelper(ConfigType config_type = kUnitTest,
                         ui::ContextFactory* context_factory = nullptr);
  ~AshTestHelper();

  // Calls through to SetUp() below, see comments there.
  void SetUp();

  // Tears down everything but the Screen instance, which some tests access
  // after this point.
  void TearDown();

  aura::Window* GetContext();

  // Creates the ash::Shell and performs associated initialization according
  // to |init_params|.  When this function returns it guarantees a task
  // environment and ViewsDelegate will exist, the shell will be started, and a
  // window will be showing.
  void SetUp(InitParams init_params);

  PrefService* GetLocalStatePrefService();

  display::Display GetSecondaryDisplay() const;

  TestSessionControllerClient* test_session_controller_client() {
    return session_controller_client_.get();
  }
  void set_test_session_controller_client(
      std::unique_ptr<TestSessionControllerClient> session_controller_client) {
    session_controller_client_ = std::move(session_controller_client);
  }
  TestNotifierSettingsController* notifier_settings_controller() {
    return notifier_settings_controller_.get();
  }
  TestSystemTrayClient* system_tray_client() {
    return system_tray_client_.get();
  }
  TestPrefServiceProvider* prefs_provider() { return prefs_provider_.get(); }

  AppListTestHelper* app_list_test_helper() {
    return app_list_test_helper_.get();
  }

  TestKeyboardControllerObserver* test_keyboard_controller_observer() {
    return test_keyboard_controller_observer_.get();
  }

  TestAssistantService* test_assistant_service() {
    return assistant_service_.get();
  }

  void reset_commandline() { command_line_.reset(); }

 private:
  ConfigType config_type_;
  ui::ContextFactory* context_factory_;
  std::unique_ptr<::wm::WMState> wm_state_;
  std::unique_ptr<ui::ScopedAnimationDurationScaleMode> zero_duration_mode_;
  std::unique_ptr<ui::TestContextFactories> context_factories_;
  std::unique_ptr<base::test::ScopedCommandLine> command_line_;
  std::unique_ptr<chromeos::system::ScopedFakeStatisticsProvider>
      statistics_provider_;
  std::unique_ptr<TestPrefServiceProvider> prefs_provider_;
  std::unique_ptr<TestNotifierSettingsController> notifier_settings_controller_;
  std::unique_ptr<TestAssistantService> assistant_service_;
  std::unique_ptr<TestSystemTrayClient> system_tray_client_;
  std::unique_ptr<TestPhotoController> photo_controller_;
  std::unique_ptr<AppListTestHelper> app_list_test_helper_;
  bool bluez_dbus_manager_initialized_ = false;
  bool power_policy_controller_initialized_ = false;
  std::unique_ptr<TestNewWindowDelegate> new_window_delegate_;
  std::unique_ptr<views::TestViewsDelegate> test_views_delegate_;
  std::unique_ptr<TestSessionControllerClient> session_controller_client_;
  std::unique_ptr<TestKeyboardControllerObserver>
      test_keyboard_controller_observer_;

  DISALLOW_COPY_AND_ASSIGN(AshTestHelper);
};

}  // namespace ash

#endif  // ASH_TEST_ASH_TEST_HELPER_H_
