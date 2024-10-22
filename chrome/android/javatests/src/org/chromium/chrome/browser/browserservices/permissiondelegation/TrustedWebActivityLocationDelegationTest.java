// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browserservices.permissiondelegation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.chromium.chrome.browser.browserservices.TrustedWebActivityTestUtil.isTrustedWebActivity;

import android.net.Uri;
import android.os.RemoteException;
import android.support.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import org.chromium.base.CommandLine;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.test.BaseJUnit4ClassRunner;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.chrome.browser.browserservices.TrustedWebActivityTestUtil;
import org.chromium.chrome.browser.customtabs.CustomTabActivityTestRule;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.permissions.PermissionTestRule.PermissionUpdateWaiter;
import org.chromium.chrome.browser.settings.SettingsActivity;
import org.chromium.chrome.browser.site_settings.SiteSettingsTestUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.test.MockCertVerifierRuleAndroid;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.site_settings.SingleCategorySettings;
import org.chromium.components.browser_ui.site_settings.SiteSettingsCategory;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.content_public.common.ContentSwitches;
import org.chromium.device.geolocation.LocationProviderOverrider;
import org.chromium.device.geolocation.MockLocationProvider;

import java.util.concurrent.TimeoutException;

/**
 * Tests TrustedWebActivity location delegation.
 */
@RunWith(BaseJUnit4ClassRunner.class)
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE,
        "enable-features=" + ChromeFeatureList.TRUSTED_WEB_ACTIVITY_LOCATION_DELEGATION})
public class TrustedWebActivityLocationDelegationTest {
    public final CustomTabActivityTestRule mCustomTabActivityTestRule =
            new CustomTabActivityTestRule();

    public final MockCertVerifierRuleAndroid mCertVerifierRule =
            new MockCertVerifierRuleAndroid(0 /* net::OK */);

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.emptyRuleChain().around(mCustomTabActivityTestRule).around(mCertVerifierRule);

    private static final String TEST_FILE = "/chrome/test/data/android/google.html";
    private static final String TEST_SUPPORT_PACKAGE = "org.chromium.chrome.tests.support";

    private String mTestPage;

    @Before
    public void setUp() throws TimeoutException, RemoteException {
        // Initialize native.
        LibraryLoader.getInstance().ensureInitialized();

        mCustomTabActivityTestRule.getEmbeddedTestServerRule().setServerUsesHttps(true);
        Uri mapToUri = Uri.parse(mCustomTabActivityTestRule.getTestServer().getURL("/"));
        CommandLine.getInstance().appendSwitchWithValue(
                ContentSwitches.HOST_RESOLVER_RULES, "MAP * " + mapToUri.getAuthority());

        mTestPage = mCustomTabActivityTestRule.getTestServer().getURLWithHostName(
                "www.example.com", TEST_FILE);

        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                TrustedWebActivityTestUtil.createTrustedWebActivityIntentAndVerifiedSession(
                        mTestPage, TEST_SUPPORT_PACKAGE));
    }

    @Test
    @MediumTest
    public void getLocationFromTestTwaService() throws TimeoutException, Exception {
        assertTrue(ChromeFeatureList.isEnabled(
                ChromeFeatureList.TRUSTED_WEB_ACTIVITY_LOCATION_DELEGATION));
        Tab tab = mCustomTabActivityTestRule.getActivity().getActivityTab();
        PermissionUpdateWaiter updateWaiter =
                new PermissionUpdateWaiter("Update", mCustomTabActivityTestRule.getActivity());
        tab.addObserver(updateWaiter);
        getGeolocation();
        updateWaiter.waitForNumUpdates(0);
    }

    @Test
    @MediumTest
    @CommandLineFlags.
    Add("disable-features=" + ChromeFeatureList.TRUSTED_WEB_ACTIVITY_LOCATION_DELEGATION)
    public void getLocationFromChrome_delegationDisabled() throws TimeoutException, Exception {
        assertFalse(ChromeFeatureList.isEnabled(
                ChromeFeatureList.TRUSTED_WEB_ACTIVITY_LOCATION_DELEGATION));
        verifyLocationFromChrome();
    }

    @Test
    @MediumTest
    public void getLocationFromChrome_noTwaService() throws TimeoutException, Exception {
        String packageName = "other.package.name";
        String testPage = mCustomTabActivityTestRule.getTestServer().getURLWithHostName(
                "www.otherexample.com", TEST_FILE);

        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                TrustedWebActivityTestUtil.createTrustedWebActivityIntentAndVerifiedSession(
                        testPage, packageName));

        assertTrue(isTrustedWebActivity(mCustomTabActivityTestRule.getActivity()));

        verifyLocationFromChrome();
    }

    @Test
    @MediumTest
    public void getLocationFromChrome_afterNavigateAwayFromTrustedOrigin()
            throws TimeoutException, Exception {
        String other_page = mCustomTabActivityTestRule.getTestServer().getURLWithHostName(
                "www.otherexample.com", TEST_FILE);

        mCustomTabActivityTestRule.loadUrl(other_page);
        assertFalse(isTrustedWebActivity(mCustomTabActivityTestRule.getActivity()));

        verifyLocationFromChrome();
    }

    private String getGeolocation() throws TimeoutException {
        return mCustomTabActivityTestRule.runJavaScriptCodeInCurrentTab(
                "navigator.geolocation.getCurrentPosition("
                + "()=>{window.document.title = 'Update'},"
                + "()=>{window.document.title = 'Error'}, { })");
    }

    private void setAllowChromeSiteLocation(boolean enabled) {
        LocationProviderOverrider.setLocationProviderImpl(new MockLocationProvider());
        final SettingsActivity settingsActivity = SiteSettingsTestUtils.startSiteSettingsCategory(
                SiteSettingsCategory.Type.DEVICE_LOCATION);

        TestThreadUtils.runOnUiThreadBlocking(() -> {
            SingleCategorySettings websitePreferences =
                    (SingleCategorySettings) settingsActivity.getMainFragment();
            ChromeSwitchPreference location =
                    (ChromeSwitchPreference) websitePreferences.findPreference(
                            SingleCategorySettings.BINARY_TOGGLE_KEY);

            websitePreferences.onPreferenceChange(location, enabled);
            settingsActivity.finish();
        });
    }

    private void verifyLocationFromChrome() throws TimeoutException, Exception {
        Tab tab = mCustomTabActivityTestRule.getActivity().getActivityTab();

        setAllowChromeSiteLocation(false);
        PermissionUpdateWaiter errorWaiter =
                new PermissionUpdateWaiter("Error", mCustomTabActivityTestRule.getActivity());
        tab.addObserver(errorWaiter);
        getGeolocation();
        errorWaiter.waitForNumUpdates(0);
    }
}
