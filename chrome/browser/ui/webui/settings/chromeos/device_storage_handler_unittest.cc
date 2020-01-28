// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/webui/settings/chromeos/device_storage_handler.h"
#include "base/strings/utf_string_conversions.h"
#include "base/system/sys_info.h"
#include "chrome/browser/chromeos/arc/session/arc_session_manager.h"
#include "chrome/browser/chromeos/file_manager/fake_disk_mount_manager.h"
#include "chrome/browser/chromeos/file_manager/path_util.h"
#include "chrome/common/webui_url_constants.h"
#include "chrome/test/base/testing_browser_process.h"
#include "chrome/test/base/testing_profile_manager.h"
#include "components/arc/arc_service_manager.h"
#include "components/arc/test/fake_arc_session.h"
#include "content/public/browser/web_ui_data_source.h"
#include "content/public/test/browser_task_environment.h"
#include "content/public/test/test_web_ui.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "ui/base/text/bytes_formatting.h"

namespace chromeos {
namespace settings {

namespace {

class TestStorageHandler : public StorageHandler {
 public:
  explicit TestStorageHandler(Profile* profile,
                              content::WebUIDataSource* html_source)
      : StorageHandler(profile, html_source) {}

  // Pull WebUIMessageHandler::set_web_ui() into public so tests can call it.
  using StorageHandler::set_web_ui;
};

class StorageHandlerTest : public testing::Test {
 public:
  StorageHandlerTest() = default;
  ~StorageHandlerTest() override = default;

  void SetUp() override {
    // The storage handler requires an instance of DiskMountManager,
    // ArcServiceManager and ArcSessionManager.
    chromeos::disks::DiskMountManager::InitializeForTesting(
        new file_manager::FakeDiskMountManager);
    arc_service_manager_ = std::make_unique<arc::ArcServiceManager>();
    arc_session_manager_ = std::make_unique<arc::ArcSessionManager>(
        std::make_unique<arc::ArcSessionRunner>(
            base::BindRepeating(arc::FakeArcSession::Create)));

    // Initialize profile.
    profile_manager_ = std::make_unique<TestingProfileManager>(
        TestingBrowserProcess::GetGlobal());
    ASSERT_TRUE(profile_manager_->SetUp());
    profile_ = profile_manager_->CreateTestingProfile("p1");

    // Initialize storage handler.
    content::WebUIDataSource* html_source =
        content::WebUIDataSource::Create(chrome::kChromeUIOSSettingsHost);
    handler_ = std::make_unique<TestStorageHandler>(profile_, html_source);
    handler_test_api_ =
        std::make_unique<StorageHandler::TestAPI>(handler_.get());
    handler_->set_web_ui(&web_ui_);
    handler_->AllowJavascriptForTesting();
  }

  void TearDown() override {
    handler_.reset();
    handler_test_api_.reset();
    chromeos::disks::DiskMountManager::Shutdown();
  }

 protected:
  // From a given amount of total size and available size as input, returns the
  // space state determined by the OnGetSizeState function.
  int GetSpaceState(int64_t* total_size, int64_t* available_size) {
    handler_test_api_->OnGetSizeStat(total_size, available_size);
    task_environment_.RunUntilIdle();
    const base::Value* dictionary =
        GetWebUICallbackMessage("storage-size-stat-changed");
    EXPECT_TRUE(dictionary) << "No 'storage-size-stat-changed' callback";
    int space_state = dictionary->FindKey("spaceState")->GetInt();
    return space_state;
  }

  // Expects a callback message with a given event_name. A non null base::Value
  // is returned if the callback message is found and has associated data.
  const base::Value* GetWebUICallbackMessage(const std::string& event_name) {
    for (auto it = web_ui_.call_data().rbegin();
         it != web_ui_.call_data().rend(); ++it) {
      const content::TestWebUI::CallData* data = it->get();
      std::string name;
      if (data->function_name() != "cr.webUIListenerCallback" ||
          !data->arg1()->GetAsString(&name)) {
        continue;
      }
      if (name == event_name)
        return data->arg2();
    }
    return nullptr;
  }

  std::unique_ptr<TestStorageHandler> handler_;
  std::unique_ptr<TestStorageHandler::TestAPI> handler_test_api_;
  content::TestWebUI web_ui_;
  content::BrowserTaskEnvironment task_environment_;
  std::unique_ptr<TestingProfileManager> profile_manager_;
  Profile* profile_;

 private:
  std::unique_ptr<arc::ArcServiceManager> arc_service_manager_;
  std::unique_ptr<arc::ArcSessionManager> arc_session_manager_;
  DISALLOW_COPY_AND_ASSIGN(StorageHandlerTest);
};

TEST_F(StorageHandlerTest, GlobalSizeStat) {
  // Get local filesystem storage statistics.
  const base::FilePath mount_path =
      file_manager::util::GetMyFilesFolderForProfile(profile_);
  int64_t total_size = base::SysInfo::AmountOfTotalDiskSpace(mount_path);
  int64_t available_size = base::SysInfo::AmountOfFreeDiskSpace(mount_path);
  int64_t used_size = total_size - available_size;
  double used_ratio = static_cast<double>(used_size) / total_size;

  // Get statistics from storage handler's UpdateSizeStat.
  handler_test_api_->UpdateSizeStat();
  task_environment_.RunUntilIdle();

  const base::Value* dictionary =
      GetWebUICallbackMessage("storage-size-stat-changed");
  ASSERT_TRUE(dictionary) << "No 'storage-size-stat-changed' callback";

  const std::string& storage_handler_available_size =
      dictionary->FindKey("availableSize")->GetString();
  const std::string& storage_handler_used_size =
      dictionary->FindKey("usedSize")->GetString();
  double storage_handler_used_ratio =
      dictionary->FindKey("usedRatio")->GetDouble();

  EXPECT_EQ(ui::FormatBytes(available_size),
            base::ASCIIToUTF16(storage_handler_available_size));
  EXPECT_EQ(ui::FormatBytes(used_size),
            base::ASCIIToUTF16(storage_handler_used_size));
  double diff = used_ratio > storage_handler_used_ratio
                    ? used_ratio - storage_handler_used_ratio
                    : storage_handler_used_ratio - used_ratio;
  // Running the test while writing data on disk (~400MB/s), the difference
  // between the values returned by the two AmountOfFreeDiskSpace calls is never
  // more than 100KB. By expecting diff to be less than 100KB / total_size, the
  // test is very unlikely to be flaky.
  EXPECT_LE(diff, static_cast<double>(100 * 1024) / total_size);
}

TEST_F(StorageHandlerTest, StorageSpaceState) {
  // Less than 512 MB available, space state is critically low.
  int64_t total_size = 1024 * 1024 * 1024;
  int64_t available_size = 512 * 1024 * 1024 - 1;
  int space_state = GetSpaceState(&total_size, &available_size);
  EXPECT_EQ(handler_->STORAGE_SPACE_CRITICALLY_LOW, space_state);

  // Less than 1GB available, space state is low.
  available_size = 512 * 1024 * 1024;
  space_state = GetSpaceState(&total_size, &available_size);
  EXPECT_EQ(handler_->STORAGE_SPACE_LOW, space_state);
  available_size = 1024 * 1024 * 1024 - 1;
  space_state = GetSpaceState(&total_size, &available_size);
  EXPECT_EQ(handler_->STORAGE_SPACE_LOW, space_state);

  // From 1GB, normal space state.
  available_size = 1024 * 1024 * 1024;
  space_state = GetSpaceState(&total_size, &available_size);
  EXPECT_EQ(handler_->STORAGE_SPACE_NORMAL, space_state);
}

}  // namespace

}  // namespace settings
}  // namespace chromeos
