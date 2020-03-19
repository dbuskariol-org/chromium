// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.settings.homepage;

import android.support.test.filters.SmallTest;

import androidx.preference.Preference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.test.BaseJUnit4ClassRunner;
import org.chromium.base.test.util.Feature;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.homepage.HomepageTestRule;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.settings.ChromeSwitchPreference;
import org.chromium.chrome.browser.settings.SettingsActivity;
import org.chromium.chrome.test.ChromeActivityTestRule;
import org.chromium.chrome.test.util.browser.Features;
import org.chromium.components.embedder_support.util.UrlConstants;
import org.chromium.content_public.browser.test.util.TestThreadUtils;

/**
 * Test for {@link HomepageSettings} when {@link ChromeFeatureList#HOMEPAGE_SETTINGS_UI_CONVERSION}
 * is turned off. This test focusing verifying the {@link HomepageEditor} component. This test could
 * be cleaned up after {@link ChromeFeatureList#HOMEPAGE_SETTINGS_UI_CONVERSION} is fully rolled
 * out.
 *
 * @see HomepageSettingsFragmentTest Tests when ChromeFeatureList#HOMEPAGE_SETTINGS_UI_CONVERSION is
 *         enabled.
 */
@RunWith(BaseJUnit4ClassRunner.class)
// clang-format off
@Features.DisableFeatures({
    ChromeFeatureList.HOMEPAGE_SETTINGS_UI_CONVERSION, ChromeFeatureList.CHROME_DUET})
public class HomepageSettingsFragmentWithEditorTest {
    // clang-format on
    private static final String ASSERT_HOMEPAGE_MISMATCH =
            "Summary in homepage editor does not match test setting.";
    private static final String ASSERT_HOMEPAGE_ENABLED = "Homepage should be enabled.";

    private static final String ASSERT_HOMEPAGE_EDIT_ENABLE =
            "HomepageEditor should be enabled when homepage is enabled.";
    private static final String ASSERT_SWITCH_CHECKED =
            "Switch checked state when homepage is enabled.";
    private static final String ASSERT_SWITCH_VISIBLE_WITHOUT_DUET =
            "Switch should be visible without bottom toolbar.";

    public static final String TEST_URL = "http://127.0.0.1:8000/foo.html";
    public static final String CHROME_NTP = UrlConstants.NTP_NON_NATIVE_URL;

    @Rule
    public ChromeActivityTestRule<ChromeActivity> mTestRule =
            new ChromeActivityTestRule<>(ChromeActivity.class);

    @Rule
    public HomepageTestRule mHomepageTestRule = new HomepageTestRule();

    @Rule
    public TestRule mFeatureProcessor = new Features.InstrumentationProcessor();

    @Before
    public void setup() {
        RecordHistogram.setDisabledForTests(true);
    }

    @After
    public void tearDown() {
        RecordHistogram.setDisabledForTests(false);
    }

    private ChromeSwitchPreference mSwitch;
    private Preference mHomepageEditor;

    private void launchSettingsActivity() {
        SettingsActivity homepagePreferenceActivity =
                mTestRule.startSettingsActivity(HomepageSettings.class.getName());
        HomepageSettings fragment = (HomepageSettings) homepagePreferenceActivity.getMainFragment();
        Assert.assertNotNull(fragment);

        mSwitch = (ChromeSwitchPreference) fragment.findPreference(
                HomepageSettings.PREF_HOMEPAGE_SWITCH);
        mHomepageEditor = fragment.findPreference(HomepageSettings.PREF_HOMEPAGE_EDIT);

        Assert.assertNotNull("Switch preference is Null", mSwitch);
        Assert.assertNotNull("Homepage Edit is Null", mHomepageEditor);
    }

    @Test
    @SmallTest
    @Feature({"Homepage"})
    public void testStartUp_Custom() {
        mHomepageTestRule.useCustomizedHomepageForTest(TEST_URL);

        launchSettingsActivity();

        Assert.assertTrue(ASSERT_SWITCH_VISIBLE_WITHOUT_DUET, mSwitch.isVisible());
        Assert.assertEquals(ASSERT_HOMEPAGE_MISMATCH, TEST_URL, mHomepageEditor.getSummary());

        Assert.assertTrue(ASSERT_SWITCH_CHECKED, mSwitch.isChecked());
        Assert.assertTrue(ASSERT_HOMEPAGE_ENABLED, HomepageManager.isHomepageEnabled());
    }

    @Test
    @SmallTest
    @Feature({"Homepage"})
    public void testStartUp_NTP() {
        mHomepageTestRule.useCustomizedHomepageForTest(CHROME_NTP);

        launchSettingsActivity();

        Assert.assertTrue(ASSERT_SWITCH_VISIBLE_WITHOUT_DUET, mSwitch.isVisible());
        Assert.assertEquals(ASSERT_HOMEPAGE_MISMATCH, CHROME_NTP, mHomepageEditor.getSummary());

        Assert.assertTrue(ASSERT_SWITCH_CHECKED, mSwitch.isChecked());
        Assert.assertTrue(ASSERT_HOMEPAGE_ENABLED, HomepageManager.isHomepageEnabled());
    }

    @Test
    @SmallTest
    @Feature({"Homepage"})
    public void testStartUp_Disabled() {
        mHomepageTestRule.useCustomizedHomepageForTest(TEST_URL);
        mHomepageTestRule.disableHomepageForTest();

        launchSettingsActivity();

        Assert.assertTrue(ASSERT_SWITCH_VISIBLE_WITHOUT_DUET, mSwitch.isVisible());
        Assert.assertEquals(ASSERT_HOMEPAGE_MISMATCH, TEST_URL, mHomepageEditor.getSummary());

        Assert.assertFalse("Homepage should be disabled", HomepageManager.isHomepageEnabled());
        Assert.assertFalse(
                "Switch should not be checked when homepage is disabled.", mSwitch.isChecked());
    }

    @Test
    @SmallTest
    @Feature({"Homepage"})
    public void testToggleSwitch() {
        mHomepageTestRule.useCustomizedHomepageForTest(TEST_URL);

        launchSettingsActivity();

        Assert.assertTrue(ASSERT_SWITCH_VISIBLE_WITHOUT_DUET, mSwitch.isVisible());
        Assert.assertEquals(ASSERT_HOMEPAGE_MISMATCH, TEST_URL, mHomepageEditor.getSummary());

        // Homepage should be enabled when start up.
        Assert.assertTrue(ASSERT_HOMEPAGE_ENABLED, HomepageManager.isHomepageEnabled());
        Assert.assertTrue(ASSERT_SWITCH_CHECKED, mSwitch.isChecked());

        // Click the switch
        TestThreadUtils.runOnUiThreadBlocking(() -> mSwitch.performClick());

        // Homepage should be disabled.
        Assert.assertFalse("Homepage should be disabled", HomepageManager.isHomepageEnabled());
        Assert.assertFalse(
                "Switch should not be checked when homepage is disabled.", mSwitch.isChecked());
        Assert.assertEquals("HomepageEditor test should stay constant.", TEST_URL,
                mHomepageEditor.getSummary());

        // Click the switch one more time, enable the homepage
        TestThreadUtils.runOnUiThreadBlocking(() -> mSwitch.performClick());

        // Homepage should be disabled.
        Assert.assertTrue(ASSERT_HOMEPAGE_ENABLED, HomepageManager.isHomepageEnabled());
        Assert.assertTrue(ASSERT_SWITCH_CHECKED, mSwitch.isChecked());
        Assert.assertEquals("HomepageEditor test should stay constant.", TEST_URL,
                mHomepageEditor.getSummary());
    }
}
