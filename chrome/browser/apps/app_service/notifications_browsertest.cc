// Copyright (c) 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <memory>

#include "ash/public/cpp/arc_notifications_host_initializer.h"
#include "ash/system/message_center/arc/arc_notification_manager.h"
#include "ash/system/message_center/arc/arc_notification_manager_delegate.h"
#include "base/run_loop.h"
#include "base/test/scoped_feature_list.h"
#include "chrome/browser/apps/app_service/app_service_proxy.h"
#include "chrome/browser/apps/app_service/app_service_proxy_factory.h"
#include "chrome/browser/apps/app_service/arc_apps.h"
#include "chrome/browser/apps/app_service/arc_apps_factory.h"
#include "chrome/browser/apps/platform_apps/app_browsertest_util.h"
#include "chrome/browser/chromeos/arc/arc_util.h"
#include "chrome/browser/extensions/api/notifications/extension_notification_display_helper.h"
#include "chrome/browser/extensions/api/notifications/extension_notification_display_helper_factory.h"
#include "chrome/browser/extensions/api/notifications/extension_notification_handler.h"
#include "chrome/browser/extensions/api/notifications/notifications_api.h"
#include "chrome/browser/extensions/extension_apitest.h"
#include "chrome/browser/extensions/extension_function_test_utils.h"
#include "chrome/browser/notifications/notification_display_service_tester.h"
#include "chrome/browser/notifications/profile_notification.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/ui/web_applications/test/web_app_browsertest_util.h"
#include "chrome/browser/web_applications/test/web_app_test.h"
#include "chrome/common/chrome_features.h"
#include "components/arc/arc_service_manager.h"
#include "components/arc/arc_util.h"
#include "components/arc/session/arc_bridge_service.h"
#include "components/arc/session/connection_holder.h"
#include "components/arc/test/connection_holder_util.h"
#include "components/arc/test/fake_app_instance.h"
#include "content/public/browser/notification_service.h"
#include "content/public/test/browser_test.h"
#include "content/public/test/test_navigation_observer.h"
#include "extensions/browser/api/test/test_api.h"
#include "extensions/browser/notification_types.h"
#include "extensions/common/extension.h"
#include "extensions/test/extension_test_message_listener.h"
#include "mojo/public/cpp/bindings/pending_remote.h"
#include "mojo/public/cpp/bindings/receiver.h"
#include "ui/display/display.h"
#include "ui/message_center/message_center.h"
#include "ui/message_center/public/cpp/notification.h"
#include "ui/message_center/public/cpp/notifier_id.h"

using extensions::Extension;
using extensions::ExtensionNotificationDisplayHelper;
using extensions::ExtensionNotificationDisplayHelperFactory;

namespace {

constexpr char kTestAppName1[] = "Test ARC App1";
constexpr char kTestAppName2[] = "Test ARC App2";
constexpr char kTestAppPackage1[] = "test.arc.app1.package";
constexpr char kTestAppPackage2[] = "test.arc.app2.package";
constexpr char kTestAppActivity1[] = "test.arc.app1.package.activity";
constexpr char kTestAppActivity2[] = "test.arc.app2.package.activity";

std::string GetTestAppId(const std::string& package_name,
                         const std::string& activity) {
  return ArcAppListPrefs::GetAppId(package_name, activity);
}

std::vector<arc::mojom::AppInfoPtr> GetTestAppsList() {
  std::vector<arc::mojom::AppInfoPtr> apps;

  arc::mojom::AppInfoPtr app(arc::mojom::AppInfo::New());
  app->name = kTestAppName1;
  app->package_name = kTestAppPackage1;
  app->activity = kTestAppActivity1;
  app->sticky = false;
  apps.push_back(std::move(app));

  app = arc::mojom::AppInfo::New();
  app->name = kTestAppName2;
  app->package_name = kTestAppPackage2;
  app->activity = kTestAppActivity2;
  app->sticky = false;
  apps.push_back(std::move(app));

  return apps;
}

apps::mojom::OptionalBool HasBadge(Profile* profile,
                                   const std::string& app_id) {
  auto has_badge = apps::mojom::OptionalBool::kUnknown;
  apps::AppServiceProxy* proxy =
      apps::AppServiceProxyFactory::GetForProfile(profile);
  proxy->FlushMojoCallsForTesting();
  proxy->AppRegistryCache().ForOneApp(
      app_id, [&has_badge](const apps::AppUpdate& update) {
        has_badge = update.HasBadge();
      });
  return has_badge;
}

void RemoveNotification(Profile* profile, const std::string& notification_id) {
  const std::string profile_notification_id =
      ProfileNotification::GetProfileNotificationId(
          notification_id, NotificationUIManager::GetProfileID(profile));
  message_center::MessageCenter::Get()->RemoveNotification(
      profile_notification_id, true);
}

}  // namespace

class AppNotificationsExtensionApiTest : public extensions::ExtensionApiTest {
 public:
  const Extension* LoadExtensionAndWait(const std::string& test_name) {
    base::FilePath extdir = test_data_dir_.AppendASCII(test_name);
    content::WindowedNotificationObserver page_created(
        extensions::NOTIFICATION_EXTENSION_BACKGROUND_PAGE_READY,
        content::NotificationService::AllSources());
    const extensions::Extension* extension = LoadExtension(extdir);
    if (extension) {
      page_created.Wait();
    }
    return extension;
  }

  const Extension* LoadAppWithWindowState(const std::string& test_name) {
    const std::string& create_window_options =
        base::StringPrintf("{\"state\":\"normal\"}");
    base::FilePath extdir = test_data_dir_.AppendASCII(test_name);
    const extensions::Extension* extension = LoadExtension(extdir);
    EXPECT_TRUE(extension);

    ExtensionTestMessageListener launched_listener("launched", true);
    apps::AppServiceProxyFactory::GetForProfile(profile())->Launch(
        extension->id(), ui::EF_SHIFT_DOWN,
        apps::mojom::LaunchSource::kFromTest, display::kInvalidDisplayId);
    EXPECT_TRUE(launched_listener.WaitUntilSatisfied());
    launched_listener.Reply(create_window_options);

    return extension;
  }

  ExtensionNotificationDisplayHelper* GetDisplayHelper() {
    return ExtensionNotificationDisplayHelperFactory::GetForProfile(profile());
  }

 protected:
  // Returns the notification that's being displayed for |extension|, or nullptr
  // when the notification count is not equal to one. It's not safe to rely on
  // the Notification pointer after closing the notification, but a copy can be
  // made to continue to be able to access the underlying information.
  message_center::Notification* GetNotificationForExtension(
      const extensions::Extension* extension) {
    DCHECK(extension);

    std::set<std::string> notifications =
        GetDisplayHelper()->GetNotificationIdsForExtension(extension->url());
    if (notifications.size() != 1)
      return nullptr;

    return GetDisplayHelper()->GetByNotificationId(*notifications.begin());
  }
};

IN_PROC_BROWSER_TEST_F(AppNotificationsExtensionApiTest,
                       AddAndRemoveNotification) {
  // Load the permission app which should not generate notifications.
  const Extension* extension1 =
      LoadExtensionAndWait("notifications/api/permission");
  ASSERT_TRUE(extension1);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse,
            HasBadge(profile(), extension1->id()));

  // Load the basic app to generate a notification.
  ExtensionTestMessageListener notification_created_listener("created", false);
  const Extension* extension2 =
      LoadAppWithWindowState("notifications/api/basic_app");
  ASSERT_TRUE(extension2);
  ASSERT_TRUE(notification_created_listener.WaitUntilSatisfied());

  ASSERT_EQ(apps::mojom::OptionalBool::kFalse,
            HasBadge(profile(), extension1->id()));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue,
            HasBadge(profile(), extension2->id()));

  message_center::Notification* notification =
      GetNotificationForExtension(extension2);
  ASSERT_TRUE(notification);

  RemoveNotification(profile(), notification->id());
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse,
            HasBadge(profile(), extension1->id()));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse,
            HasBadge(profile(), extension2->id()));
}

class AppNotificationsWebNotificationTest
    : public extensions::PlatformAppBrowserTest,
      public ::testing::WithParamInterface<web_app::ProviderType> {
 protected:
  AppNotificationsWebNotificationTest() {
    if (GetParam() == web_app::ProviderType::kWebApps) {
      scoped_feature_list_.InitAndEnableFeature(
          features::kDesktopPWAsWithoutExtensions);
    } else {
      scoped_feature_list_.InitAndDisableFeature(
          features::kDesktopPWAsWithoutExtensions);
    }
  }

  ~AppNotificationsWebNotificationTest() override = default;

  // extensions::PlatformAppBrowserTest:
  void SetUpOnMainThread() override {
    extensions::PlatformAppBrowserTest::SetUpOnMainThread();

    https_server_.AddDefaultHandlers(GetChromeTestDataDir());
    ASSERT_TRUE(https_server_.Start());
  }

  std::string CreateWebApp(const GURL& url, const GURL& scope) const {
    auto web_app_info = std::make_unique<WebApplicationInfo>();
    web_app_info->app_url = url;
    web_app_info->scope = scope;
    std::string app_id =
        web_app::InstallWebApp(browser()->profile(), std::move(web_app_info));
    content::TestNavigationObserver navigation_observer(url);
    navigation_observer.StartWatchingNewWebContents();
    web_app::LaunchWebAppBrowser(browser()->profile(), app_id);
    navigation_observer.WaitForNavigationFinished();
    return app_id;
  }

  std::unique_ptr<message_center::Notification> CreateNotification(
      const std::string& notification_id,
      const GURL& origin) {
    return std::make_unique<message_center::Notification>(
        message_center::NOTIFICATION_TYPE_SIMPLE, notification_id,
        base::string16(), base::string16(), gfx::Image(),
        base::UTF8ToUTF16(origin.host()), origin,
        message_center::NotifierId(origin),
        message_center::RichNotificationData(), nullptr);
  }

  GURL GetOrigin() const { return https_server_.GetURL("app.com", "/"); }

  GURL GetUrl1() const {
    return https_server_.GetURL("app.com", "/ssl/google.html");
  }

  GURL GetScope1() const { return https_server_.GetURL("app.com", "/ssl/"); }

  GURL GetUrl2() const {
    return https_server_.GetURL("app.com", "/google/google.html");
  }

  GURL GetScope2() const { return https_server_.GetURL("app.com", "/google/"); }

  GURL GetUrl3() const {
    return https_server_.GetURL("app1.com", "/google/google.html");
  }

  GURL GetScope3() const {
    return https_server_.GetURL("app1.com", "/google/");
  }

 private:
  // For mocking a secure site.
  net::EmbeddedTestServer https_server_;

  base::test::ScopedFeatureList scoped_feature_list_;
};

// Test that we have the correct instance for Web apps.
IN_PROC_BROWSER_TEST_P(AppNotificationsWebNotificationTest,
                       AddAndRemovePersistentNotification) {
  std::string app_id1 = CreateWebApp(GetUrl1(), GetScope1());
  std::string app_id2 = CreateWebApp(GetUrl2(), GetScope2());
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));

  const GURL origin = GetOrigin();
  std::string notification_id = "notification-id1";
  auto notification = CreateNotification(notification_id, origin);

  auto metadata = std::make_unique<PersistentNotificationMetadata>();
  metadata->service_worker_scope = GetScope1();

  NotificationDisplayService::GetForProfile(profile())->Display(
      NotificationHandler::Type::WEB_PERSISTENT, *notification,
      std::move(metadata));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));

  NotificationDisplayService::GetForProfile(profile())->Close(
      NotificationHandler::Type::WEB_PERSISTENT, notification_id);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));

  notification_id = "notification-id2";
  notification = CreateNotification(notification_id, origin);
  metadata = std::make_unique<PersistentNotificationMetadata>();
  metadata->service_worker_scope = GetScope2();

  NotificationDisplayService::GetForProfile(profile())->Display(
      NotificationHandler::Type::WEB_PERSISTENT, *notification,
      std::move(metadata));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id2));

  NotificationDisplayService::GetForProfile(profile())->Close(
      NotificationHandler::Type::WEB_PERSISTENT, notification_id);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));
}

IN_PROC_BROWSER_TEST_P(AppNotificationsWebNotificationTest,
                       AddAndRemoveNonPersistentNotification) {
  const GURL origin = GetOrigin();
  std::string app_id1 = CreateWebApp(GetUrl1(), GetScope1());
  std::string app_id2 = CreateWebApp(GetUrl2(), GetScope2());
  std::string app_id3 = CreateWebApp(GetUrl3(), GetScope3());

  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id3));

  const std::string notification_id = "notification-id";
  auto notification = CreateNotification(notification_id, origin);

  NotificationDisplayService::GetForProfile(profile())->Display(
      NotificationHandler::Type::WEB_NON_PERSISTENT, *notification,
      /*metadata=*/nullptr);
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id2));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id3));

  RemoveNotification(profile(), notification_id);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id3));
}

class FakeArcNotificationManagerDelegate
    : public ash::ArcNotificationManagerDelegate {
 public:
  FakeArcNotificationManagerDelegate() = default;
  ~FakeArcNotificationManagerDelegate() override = default;

  // ArcNotificationManagerDelegate:
  bool IsPublicSessionOrKiosk() const override { return false; }
  void ShowMessageCenter() override {}
  void HideMessageCenter() override {}
};

class AppNotificationsArcNotificationTest
    : public extensions::PlatformAppBrowserTest {
 protected:
  // extensions::PlatformAppBrowserTest:
  void SetUpCommandLine(base::CommandLine* command_line) override {
    extensions::PlatformAppBrowserTest::SetUpCommandLine(command_line);
    arc::SetArcAvailableCommandLineForTesting(command_line);
  }

  void SetUpInProcessBrowserTestFixture() override {
    extensions::PlatformAppBrowserTest::SetUpInProcessBrowserTestFixture();
    arc::ArcSessionManager::SetUiEnabledForTesting(false);
  }

  void SetUpOnMainThread() override {
    extensions::PlatformAppBrowserTest::SetUpOnMainThread();
    arc::SetArcPlayStoreEnabledForProfile(profile(), true);

    // This ensures app_prefs()->GetApp() below never returns nullptr.
    base::RunLoop run_loop;
    app_prefs()->SetDefaultAppsReadyCallback(run_loop.QuitClosure());
    run_loop.Run();

    StartInstance();

    arc_notification_manager_ = std::make_unique<ash::ArcNotificationManager>(
        std::make_unique<FakeArcNotificationManagerDelegate>(),
        EmptyAccountId(), message_center::MessageCenter::Get());

    ash::ArcNotificationsHostInitializer::Observer* observer =
        apps::ArcAppsFactory::GetInstance()->GetForProfile(profile());
    observer->OnSetArcNotificationsInstance(arc_notification_manager_.get());
  }

  void TearDownOnMainThread() override {
    arc_notification_manager_.reset();
    StopInstance();
    base::RunLoop().RunUntilIdle();

    extensions::PlatformAppBrowserTest::TearDownOnMainThread();
  }

  void InstallTestApps() {
    app_host()->OnAppListRefreshed(GetTestAppsList());

    SendPackageAdded(kTestAppPackage1, false);
    SendPackageAdded(kTestAppPackage2, false);
  }

  void SendPackageAdded(const std::string& package_name, bool package_synced) {
    auto package_info = arc::mojom::ArcPackageInfo::New();
    package_info->package_name = package_name;
    package_info->package_version = 1;
    package_info->last_backup_android_id = 1;
    package_info->last_backup_time = 1;
    package_info->sync = package_synced;
    package_info->system = false;
    app_instance_->SendPackageAdded(std::move(package_info));
    base::RunLoop().RunUntilIdle();
  }

  void StartInstance() {
    app_instance_ = std::make_unique<arc::FakeAppInstance>(app_host());
    arc_bridge_service()->app()->SetInstance(app_instance_.get());
  }

  void StopInstance() {
    if (app_instance_)
      arc_bridge_service()->app()->CloseInstance(app_instance_.get());
    arc_session_manager()->Shutdown();
  }

  void CreateNotificationWithKey(const std::string& key,
                                 const std::string& package_name) {
    auto data = arc::mojom::ArcNotificationData::New();
    data->key = key;
    data->title = "TITLE";
    data->message = "MESSAGE";
    data->package_name = package_name;
    arc_notification_manager_->OnNotificationPosted(std::move(data));
  }

  void RemoveNotificationWithKey(const std::string& key) {
    arc_notification_manager_->OnNotificationRemoved(key);
  }

  ArcAppListPrefs* app_prefs() { return ArcAppListPrefs::Get(profile()); }

  // Returns as AppHost interface in order to access to private implementation
  // of the interface.
  arc::mojom::AppHost* app_host() { return app_prefs(); }

 private:
  arc::ArcSessionManager* arc_session_manager() {
    return arc::ArcSessionManager::Get();
  }

  arc::ArcBridgeService* arc_bridge_service() {
    return arc::ArcServiceManager::Get()->arc_bridge_service();
  }

  std::unique_ptr<ash::ArcNotificationManager> arc_notification_manager_;
  std::unique_ptr<arc::FakeAppInstance> app_instance_;
};

IN_PROC_BROWSER_TEST_F(AppNotificationsArcNotificationTest,
                       AddAndRemoveNotification) {
  // Install app to remember existing apps.
  InstallTestApps();
  const std::string app_id1 = GetTestAppId(kTestAppPackage1, kTestAppActivity1);
  const std::string app_id2 = GetTestAppId(kTestAppPackage2, kTestAppActivity2);

  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));

  const std::string notification_key1 = "notification_key1";
  CreateNotificationWithKey(notification_key1, kTestAppPackage1);
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));

  const std::string notification_key2 = "notification_key2";
  CreateNotificationWithKey(notification_key2, kTestAppPackage2);
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id2));

  RemoveNotificationWithKey(notification_key1);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kTrue, HasBadge(profile(), app_id2));

  RemoveNotificationWithKey(notification_key2);
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id1));
  ASSERT_EQ(apps::mojom::OptionalBool::kFalse, HasBadge(profile(), app_id2));
}

INSTANTIATE_TEST_SUITE_P(All,
                         AppNotificationsWebNotificationTest,
                         ::testing::Values(web_app::ProviderType::kBookmarkApps,
                                           web_app::ProviderType::kWebApps),
                         web_app::ProviderTypeParamToString);
