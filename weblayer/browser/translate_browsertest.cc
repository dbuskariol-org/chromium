// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "build/build_config.h"
#include "components/translate/content/browser/translate_waiter.h"
#include "components/translate/core/browser/language_state.h"
#include "components/translate/core/browser/translate_error_details.h"
#include "components/translate/core/browser/translate_manager.h"
#include "components/translate/core/common/translate_switches.h"
#include "content/public/browser/browser_context.h"
#include "net/base/mock_network_change_notifier.h"
#include "net/test/embedded_test_server/http_request.h"
#include "net/test/embedded_test_server/http_response.h"
#include "weblayer/browser/profile_impl.h"
#include "weblayer/browser/tab_impl.h"
#include "weblayer/browser/translate_client_impl.h"
#include "weblayer/public/tab.h"
#include "weblayer/shell/browser/shell.h"
#include "weblayer/test/weblayer_browser_test.h"
#include "weblayer/test/weblayer_browser_test_utils.h"

#if defined(OS_ANDROID)
#include "base/android/build_info.h"
#include "components/infobars/core/infobar_manager.h"  // nogncheck
#include "components/translate/core/browser/translate_download_manager.h"
#include "weblayer/browser/infobar_android.h"
#include "weblayer/browser/infobar_service.h"
#include "weblayer/browser/translate_compact_infobar.h"
#endif

namespace weblayer {

namespace {

static std::string kTestValidScript = R"(
    var google = {};
    google.translate = (function() {
      return {
        TranslateService: function() {
          return {
            isAvailable : function() {
              return true;
            },
            restore : function() {
              return;
            },
            getDetectedLanguage : function() {
              return "fr";
            },
            translatePage : function(originalLang, targetLang,
                                     onTranslateProgress) {
              var error = (originalLang == 'auto') ? true : false;
              onTranslateProgress(100, true, error);
            }
          };
        }
      };
    })();
    cr.googleTranslate.onTranslateElementLoad();)";

static std::string kTestScriptInitializationError = R"(
    var google = {};
    google.translate = (function() {
      return {
        TranslateService: function() {
          return error;
        }
      };
    })();
    cr.googleTranslate.onTranslateElementLoad();)";

static std::string kTestScriptTimeout = R"(
    var google = {};
    google.translate = (function() {
      return {
        TranslateService: function() {
          return {
            isAvailable : function() {
              return false;
            },
          };
        }
      };
    })();
    cr.googleTranslate.onTranslateElementLoad();)";

TranslateClientImpl* GetTranslateClient(Shell* shell) {
  return TranslateClientImpl::FromWebContents(
      static_cast<TabImpl*>(shell->tab())->web_contents());
}

std::unique_ptr<translate::TranslateWaiter> CreateTranslateWaiter(
    Shell* shell,
    translate::TranslateWaiter::WaitEvent wait_event) {
  return std::make_unique<translate::TranslateWaiter>(
      GetTranslateClient(shell)->translate_driver(), wait_event);
}

void WaitUntilLanguageDetermined(Shell* shell) {
  CreateTranslateWaiter(
      shell, translate::TranslateWaiter::WaitEvent::kLanguageDetermined)
      ->Wait();
}

void WaitUntilPageTranslated(Shell* shell) {
  CreateTranslateWaiter(shell,
                        translate::TranslateWaiter::WaitEvent::kPageTranslated)
      ->Wait();
}

}  // namespace

#if defined(OS_ANDROID)
class TestInfoBarManagerObserver : public infobars::InfoBarManager::Observer {
 public:
  TestInfoBarManagerObserver() = default;
  ~TestInfoBarManagerObserver() override = default;
  void OnInfoBarAdded(infobars::InfoBar* infobar) override {
    if (on_infobar_added_callback_)
      std::move(on_infobar_added_callback_).Run();
  }

  void OnInfoBarRemoved(infobars::InfoBar* infobar, bool animate) override {
    if (on_infobar_removed_callback_)
      std::move(on_infobar_removed_callback_).Run();
  }

  void set_on_infobar_added_callback(base::OnceClosure callback) {
    on_infobar_added_callback_ = std::move(callback);
  }

  void set_on_infobar_removed_callback(base::OnceClosure callback) {
    on_infobar_removed_callback_ = std::move(callback);
  }

 private:
  base::OnceClosure on_infobar_added_callback_;
  base::OnceClosure on_infobar_removed_callback_;
};
#endif  // if defined(OS_ANDROID)

class TranslateBrowserTest : public WebLayerBrowserTest {
 public:
  TranslateBrowserTest() {
    error_subscription_ =
        translate::TranslateManager::RegisterTranslateErrorCallback(
            base::BindRepeating(&TranslateBrowserTest::OnTranslateError,
                                base::Unretained(this)));
  }
  ~TranslateBrowserTest() override = default;

  void SetUpOnMainThread() override {
    embedded_test_server()->RegisterRequestHandler(base::BindRepeating(
        &TranslateBrowserTest::HandleRequest, base::Unretained(this)));
    embedded_test_server()->StartAcceptingConnections();

    // Translation will not be offered if NetworkChangeNotifier reports that the
    // app is offline, which can occur on bots. Prevent this.
    // NOTE: MockNetworkChangeNotifier cannot be instantiated earlier than this
    // due to its dependence on browser state having been created.
    mock_network_change_notifier_ =
        std::make_unique<net::test::ScopedMockNetworkChangeNotifier>();
    mock_network_change_notifier_->mock_network_change_notifier()
        ->SetConnectionType(net::NetworkChangeNotifier::CONNECTION_WIFI);

    // By default, translation is not offered if the Google API key is not set.
    GetTranslateClient(shell())
        ->GetTranslateManager()
        ->SetIgnoreMissingKeyForTesting(true);

    GetTranslateClient(shell())->GetTranslatePrefs()->ResetToDefaults();
  }

  void TearDownOnMainThread() override {
    mock_network_change_notifier_.reset();
  }

  void SetUpCommandLine(base::CommandLine* command_line) override {
    ASSERT_TRUE(embedded_test_server()->InitializeAndListen());

    command_line->AppendSwitchASCII(
        translate::switches::kTranslateScriptURL,
        embedded_test_server()->GetURL("/mock_translate_script.js").spec());
  }

 protected:
  translate::TranslateErrors::Type GetPageTranslatedResult() {
    return error_type_;
  }
  void SetTranslateScript(const std::string& script) { script_ = script; }

 private:
  std::unique_ptr<net::test_server::HttpResponse> HandleRequest(
      const net::test_server::HttpRequest& request) {
    if (request.GetURL().path() != "/mock_translate_script.js")
      return nullptr;

    auto http_response =
        std::make_unique<net::test_server::BasicHttpResponse>();
    http_response->set_code(net::HTTP_OK);
    http_response->set_content(script_);
    http_response->set_content_type("text/javascript");
    return std::move(http_response);
  }

  void OnTranslateError(const translate::TranslateErrorDetails& details) {
    error_type_ = details.error;
  }

  std::unique_ptr<net::test::ScopedMockNetworkChangeNotifier>
      mock_network_change_notifier_;

  translate::TranslateErrors::Type error_type_ =
      translate::TranslateErrors::NONE;
  std::unique_ptr<
      translate::TranslateManager::TranslateErrorCallbackList::Subscription>
      error_subscription_;
  std::string script_;
};

// Tests that the CLD (Compact Language Detection) works properly.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, PageLanguageDetection) {
  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Go to a page in English.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/english_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("en", translate_client->GetLanguageState().original_language());

  // Now navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());
}

// Test that the translation was successful.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, PageTranslationSuccess) {
  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // Translate the page through TranslateManager.
  translate::TranslateManager* manager =
      translate_client->GetTranslateManager();
  manager->TranslatePage(
      translate_client->GetLanguageState().original_language(), "en", true);

  WaitUntilPageTranslated(shell());

  EXPECT_FALSE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::NONE, GetPageTranslatedResult());
}

class IncognitoTranslateBrowserTest : public TranslateBrowserTest {
 public:
  IncognitoTranslateBrowserTest() { SetShellStartsInIncognitoMode(); }
};

// Test that the translation infrastructure is set up properly when the user is
// in incognito mode.
IN_PROC_BROWSER_TEST_F(IncognitoTranslateBrowserTest,
                       PageTranslationSuccess_IncognitoMode) {
  ASSERT_TRUE(GetProfile()->GetBrowserContext()->IsOffTheRecord());

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // Translate the page through TranslateManager.
  translate::TranslateManager* manager =
      translate_client->GetTranslateManager();
  manager->TranslatePage(
      translate_client->GetLanguageState().original_language(), "en", true);

  WaitUntilPageTranslated(shell());

  EXPECT_FALSE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::NONE, GetPageTranslatedResult());
}

// Test if there was an error during translation.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, PageTranslationError) {
#if defined(OS_ANDROID)
  // TODO(crbug.com/1094903): Determine why this test times out on the M
  // trybot.
  if (base::android::BuildInfo::GetInstance()->sdk_int() <=
      base::android::SDK_VERSION_MARSHMALLOW) {
    return;
  }
#endif

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Translate the page through TranslateManager.
  translate::TranslateManager* manager =
      translate_client->GetTranslateManager();
  manager->TranslatePage(
      translate_client->GetLanguageState().original_language(), "en", true);

  WaitUntilPageTranslated(shell());

  EXPECT_TRUE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::TRANSLATION_ERROR,
            GetPageTranslatedResult());
}

// Test if there was an error during translate library initialization.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest,
                       PageTranslationInitializationError) {
  SetTranslateScript(kTestScriptInitializationError);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // Translate the page through TranslateManager.
  translate::TranslateManager* manager =
      translate_client->GetTranslateManager();
  manager->TranslatePage(
      translate_client->GetLanguageState().original_language(), "en", true);

  WaitUntilPageTranslated(shell());

  EXPECT_TRUE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::INITIALIZATION_ERROR,
            GetPageTranslatedResult());
}

// Test the checks translate lib never gets ready and throws timeout.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, PageTranslationTimeoutError) {
  SetTranslateScript(kTestScriptTimeout);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // Translate the page through TranslateManager.
  translate::TranslateManager* manager =
      translate_client->GetTranslateManager();
  manager->TranslatePage(
      translate_client->GetLanguageState().original_language(), "en", true);

  WaitUntilPageTranslated(shell());

  EXPECT_TRUE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::TRANSLATION_TIMEOUT,
            GetPageTranslatedResult());
}

// Test that autotranslation kicks in if configured via prefs.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, Autotranslation) {
#if defined(OS_ANDROID)
  // TODO(crbug.com/1094903): Determine why this test times out on the M
  // trybot.
  if (base::android::BuildInfo::GetInstance()->sdk_int() <=
      base::android::SDK_VERSION_MARSHMALLOW) {
    return;
  }
#endif

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  // Before browsing, set autotranslate from French to Chinese.
  translate_client->GetTranslatePrefs()->WhitelistLanguagePair("fr", "zh-CN");

  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // Autotranslation should kick in.
  WaitUntilPageTranslated(shell());

  EXPECT_FALSE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::NONE, GetPageTranslatedResult());
  EXPECT_EQ("zh-CN", translate_client->GetLanguageState().current_language());
}

#if defined(OS_ANDROID)
// Test that the translation infobar is presented when visiting a page with a
// translation opportunity and removed when navigating away.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, TranslateInfoBarPresentation) {
  auto* web_contents = static_cast<TabImpl*>(shell()->tab())->web_contents();
  auto* infobar_service = InfoBarService::FromWebContents(web_contents);

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  TestInfoBarManagerObserver infobar_observer;
  infobar_service->AddObserver(&infobar_observer);

  base::RunLoop run_loop;
  infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

  EXPECT_EQ(0u, infobar_service->infobar_count());
  // Navigate to a page in French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // The translate infobar should be added.
  run_loop.Run();

  EXPECT_EQ(1u, infobar_service->infobar_count());
  auto* infobar = static_cast<InfoBarAndroid*>(infobar_service->infobar_at(0));
  EXPECT_TRUE(infobar->HasSetJavaInfoBar());

  base::RunLoop run_loop2;
  infobar_observer.set_on_infobar_removed_callback(run_loop2.QuitClosure());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());

  // The translate infobar should be removed.
  run_loop2.Run();

  EXPECT_EQ(0u, infobar_service->infobar_count());
  infobar_service->RemoveObserver(&infobar_observer);
}
#endif

#if defined(OS_ANDROID)
// Test that the translation can be successfully initiated via infobar.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest, TranslationViaInfoBar) {
  // TODO(crbug.com/1094903): Determine why this test times out on the M
  // trybot.
  if (base::android::BuildInfo::GetInstance()->sdk_int() <=
      base::android::SDK_VERSION_MARSHMALLOW) {
    return;
  }

  auto* web_contents = static_cast<TabImpl*>(shell()->tab())->web_contents();
  auto* infobar_service = InfoBarService::FromWebContents(web_contents);

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  TestInfoBarManagerObserver infobar_observer;
  infobar_service->AddObserver(&infobar_observer);

  base::RunLoop run_loop;
  infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

  // Navigate to a page in French and wait for the infobar to be added.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  run_loop.Run();

  // Select the target language via the Java infobar and ensure that translation
  // occurs.
  auto* infobar =
      static_cast<TranslateCompactInfoBar*>(infobar_service->infobar_at(0));
  infobar->SelectButtonForTesting(InfoBarAndroid::ActionType::ACTION_TRANSLATE);

  WaitUntilPageTranslated(shell());

  EXPECT_FALSE(translate_client->GetLanguageState().translation_error());
  EXPECT_EQ(translate::TranslateErrors::NONE, GetPageTranslatedResult());

  // The translate infobar should still be present.
  EXPECT_EQ(1u, infobar_service->infobar_count());

  // NOTE: The notification that the translate state of the page changed can
  // occur synchronously once reversion is initiated, so it's necessary to start
  // listening for that notification prior to initiating the reversion.
  auto translate_reversion_waiter = CreateTranslateWaiter(
      shell(), translate::TranslateWaiter::WaitEvent::kIsPageTranslatedChanged);

  // Revert to the source language via the Java infobar and ensure that the
  // translation is undone.
  infobar->SelectButtonForTesting(
      InfoBarAndroid::ActionType::ACTION_TRANSLATE_SHOW_ORIGINAL);

  translate_reversion_waiter->Wait();
  EXPECT_EQ("fr", translate_client->GetLanguageState().current_language());

  // The translate infobar should still be present.
  EXPECT_EQ(1u, infobar_service->infobar_count());

  infobar_service->RemoveObserver(&infobar_observer);
}
#endif

#if defined(OS_ANDROID)
// Test that the translation infobar stays present when the "never translate
// language" item is clicked. Note that this behavior is intentionally different
// from that of Chrome, where the infobar is removed in this case and a snackbar
// is shown. As WebLayer has no snackbars, the UX decision was to simply leave
// the infobar open to allow the user to revert the decision if desired.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest,
                       TranslateInfoBarNeverTranslateLanguage) {
  auto* web_contents = static_cast<TabImpl*>(shell()->tab())->web_contents();
  auto* infobar_service = InfoBarService::FromWebContents(web_contents);

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  TestInfoBarManagerObserver infobar_observer;
  infobar_service->AddObserver(&infobar_observer);

  base::RunLoop run_loop;
  infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

  // Navigate to a page in French and wait for the infobar to be added.
  EXPECT_EQ(0u, infobar_service->infobar_count());
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  run_loop.Run();

  auto* infobar =
      static_cast<TranslateCompactInfoBar*>(infobar_service->infobar_at(0));
  infobar->ClickOverflowMenuItemForTesting(
      TranslateCompactInfoBar::OverflowMenuItemId::NEVER_TRANSLATE_LANGUAGE);

  // The translate infobar should still be present.
  EXPECT_EQ(1u, infobar_service->infobar_count());

  // However, the infobar should not be shown on a new navigation to a page in
  // French.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page2.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // NOTE: There is no notification to wait for for the event of the infobar not
  // showing. However, in practice the infobar is added synchronously, so if it
  // were to be shown, this check would fail.
  EXPECT_EQ(0u, infobar_service->infobar_count());

  // The infobar *should* be shown on a navigation to this site if the page's
  // language is detected as something other than French.
  base::RunLoop run_loop2;
  infobar_observer.set_on_infobar_added_callback(run_loop2.QuitClosure());

  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/german_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("de", translate_client->GetLanguageState().original_language());

  run_loop2.Run();

  EXPECT_EQ(1u, infobar_service->infobar_count());

  infobar_service->RemoveObserver(&infobar_observer);
}

// Test that the translation infobar stays present when the "never translate
// site" item is clicked. Note that this behavior is intentionally different
// from that of Chrome, where the infobar is removed in this case and a snackbar
// is shown. As WebLayer has no snackbars, the UX decision was to simply leave
// the infobar open to allow the user to revert the decision if desired.
IN_PROC_BROWSER_TEST_F(TranslateBrowserTest,
                       TranslateInfoBarNeverTranslateSite) {
  auto* web_contents = static_cast<TabImpl*>(shell()->tab())->web_contents();
  auto* infobar_service = InfoBarService::FromWebContents(web_contents);

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  TestInfoBarManagerObserver infobar_observer;
  infobar_service->AddObserver(&infobar_observer);

  base::RunLoop run_loop;
  infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

  // Navigate to a page in French and wait for the infobar to be added.
  EXPECT_EQ(0u, infobar_service->infobar_count());
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  run_loop.Run();

  auto* infobar =
      static_cast<TranslateCompactInfoBar*>(infobar_service->infobar_at(0));
  infobar->ClickOverflowMenuItemForTesting(
      TranslateCompactInfoBar::OverflowMenuItemId::NEVER_TRANSLATE_SITE);

  // The translate infobar should still be present.
  EXPECT_EQ(1u, infobar_service->infobar_count());

  // However, the infobar should not be shown on a new navigation to this site,
  // independent of the detected language.
  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/french_page2.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

  // NOTE: There is no notification to wait for for the event of the infobar not
  // showing. However, in practice the infobar is added synchronously, so if it
  // were to be shown, this check would fail.
  EXPECT_EQ(0u, infobar_service->infobar_count());

  NavigateAndWaitForCompletion(
      GURL(embedded_test_server()->GetURL("/german_page.html")), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("de", translate_client->GetLanguageState().original_language());
  EXPECT_EQ(0u, infobar_service->infobar_count());

  infobar_service->RemoveObserver(&infobar_observer);
}

// Parameterized to run tests on the "never translate language" and "never
// translate site" menu items.
class NeverTranslateMenuItemTranslateBrowserTest
    : public TranslateBrowserTest,
      public testing::WithParamInterface<
          TranslateCompactInfoBar::OverflowMenuItemId> {};

// Test that clicking and unclicking a never translate item ends up being a
// no-op.
IN_PROC_BROWSER_TEST_P(NeverTranslateMenuItemTranslateBrowserTest,
                       TranslateInfoBarToggleAndToggleBackNeverTranslateItem) {
  auto* web_contents = static_cast<TabImpl*>(shell()->tab())->web_contents();
  auto* infobar_service = InfoBarService::FromWebContents(web_contents);

  SetTranslateScript(kTestValidScript);

  TranslateClientImpl* translate_client = GetTranslateClient(shell());

  NavigateAndWaitForCompletion(GURL("about:blank"), shell());
  WaitUntilLanguageDetermined(shell());
  EXPECT_EQ("und", translate_client->GetLanguageState().original_language());

  TestInfoBarManagerObserver infobar_observer;
  infobar_service->AddObserver(&infobar_observer);

  // Navigate to a page in French, wait for the infobar to be added, and click
  // twice on the given overflow menu item.
  {
    base::RunLoop run_loop;
    infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

    EXPECT_EQ(0u, infobar_service->infobar_count());
    NavigateAndWaitForCompletion(
        GURL(embedded_test_server()->GetURL("/french_page.html")), shell());
    WaitUntilLanguageDetermined(shell());
    EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

    run_loop.Run();

    auto* infobar =
        static_cast<TranslateCompactInfoBar*>(infobar_service->infobar_at(0));
    infobar->ClickOverflowMenuItemForTesting(GetParam());

    // The translate infobar should still be present.
    EXPECT_EQ(1u, infobar_service->infobar_count());

    infobar->ClickOverflowMenuItemForTesting(GetParam());
  }

  // The infobar should be shown on a new navigation to a page in the same
  // language.
  {
    base::RunLoop run_loop;
    infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

    NavigateAndWaitForCompletion(
        GURL(embedded_test_server()->GetURL("/french_page2.html")), shell());
    WaitUntilLanguageDetermined(shell());
    EXPECT_EQ("fr", translate_client->GetLanguageState().original_language());

    run_loop.Run();
  }

  // The infobar should be shown on a new navigation to a page in a different
  // language in the same site.
  {
    base::RunLoop run_loop;
    infobar_observer.set_on_infobar_added_callback(run_loop.QuitClosure());

    NavigateAndWaitForCompletion(
        GURL(embedded_test_server()->GetURL("/german_page.html")), shell());
    WaitUntilLanguageDetermined(shell());
    EXPECT_EQ("de", translate_client->GetLanguageState().original_language());

    run_loop.Run();
  }

  infobar_service->RemoveObserver(&infobar_observer);
}

INSTANTIATE_TEST_SUITE_P(
    All,
    NeverTranslateMenuItemTranslateBrowserTest,
    ::testing::Values(
        TranslateCompactInfoBar::OverflowMenuItemId::NEVER_TRANSLATE_LANGUAGE,
        TranslateCompactInfoBar::OverflowMenuItemId::NEVER_TRANSLATE_SITE));

#endif  // #if defined(OS_ANDROID)

}  // namespace weblayer
