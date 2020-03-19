// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/** @fileoverview Runs the Polymer Check Password tests. */

// clang-format off
// #import {PasswordManagerImpl} from 'chrome://settings/settings.js';
// #import {makeCompromisedCredential,  makePasswordCheckStatus} from 'chrome://test/settings/passwords_and_autofill_fake_data.m.js';
// #import {getSyncAllPrefs,simulateSyncStatus} from 'chrome://test/settings/sync_test_util.m.js';
// #import {flush} from 'chrome://resources/polymer/v3_0/polymer/polymer_bundled.min.js';
// #import {loadTimeData} from 'chrome://resources/js/load_time_data.m.js';
// #import {TestPasswordManagerProxy} from 'chrome://test/settings/test_password_manager_proxy.m.js';
// clang-format-on

cr.define('settings_passwords_check', function() {
  const PasswordCheckState = chrome.passwordsPrivate.PasswordCheckState;

  function createCheckPasswordSection() {
    // Create a passwords-section to use for testing.
    const passwordsSection =
        document.createElement('settings-password-check');
    document.body.appendChild(passwordsSection);
    Polymer.dom.flush();
    return passwordsSection;
  }

  function createEditDialog(leakedCredential) {
    const editDialog =
        document.createElement('settings-password-check-edit-dialog');
    editDialog.item = leakedCredential;
    document.body.appendChild(editDialog);
    Polymer.dom.flush();
    return editDialog;
  }

  /**
   * Helper method used to create a compromised list item.
   * @param {!chrome.passwordsPrivate.CompromisedCredential} entry
   * @return {!PasswordCheckListItemElement}
   */
  function createLeakedPasswordItem(entry) {
    const leakedPasswordItem =
        document.createElement('password-check-list-item');
    leakedPasswordItem.item = entry;
    document.body.appendChild(leakedPasswordItem);
    Polymer.dom.flush();
    return leakedPasswordItem;
  }

  function isElementVisible(element) {
    return !!element && !element.hidden && element.style.display != 'none' &&
        element.offsetParent !== null;  // Considers parents hiding |element|.
  }

  /**
   * Helper method used to randomize array.
   * @param {!Array<!chrome.passwordsPrivate.CompromisedCredential>} array
   * @return {!Array<!chrome.passwordsPrivate.CompromisedCredential>}
   */
  function shuffleArray(array) {
    const copy = array.slice();
    for (let i = copy.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = copy[i];
      copy[i] = copy[j];
      copy[j] = temp;
    }
    return copy;
  }

  /**
   * Helper method that validates a that elements in the compromised credentials
   * list match the expected data.
   * @param {!Element} checkPasswordSection The section element that will be
   *     checked.
   * @param {!Array<!chrome.passwordsPrivate.CompromisedCredential>}
   * passwordList The expected data.
   * @private
   */
  function validateLeakedPasswordsList(checkPasswordSection,
      compromisedCredentials) {
    const listElements = checkPasswordSection.$.leakedPasswordList;
    assertEquals(listElements.items.length, compromisedCredentials.length);
    const nodes = checkPasswordSection.shadowRoot.querySelectorAll(
        'password-check-list-item');
    for (let index = 0; index < compromisedCredentials.length; ++index) {
      const node = nodes[index];
      assertTrue(!!node);
      assertEquals(
          node.$.elapsedTime.textContent.trim(),
          compromisedCredentials[index].elapsedTimeSinceCompromise);
      assertEquals(
          node.$.leakUsername.textContent.trim(),
          compromisedCredentials[index].username);
      assertEquals(
          node.$.leakOrigin.textContent.trim(),
          compromisedCredentials[index].formattedOrigin);
    }
  }

  suite('PasswordsCheckSection', function() {
    /** @type {TestPasswordManagerProxy} */
    let passwordManager = null;

    suiteSetup(function() {
      loadTimeData.overrideValues({enablePasswordCheck: true});
    });

    setup(function() {
      PolymerTest.clearBody();
      // Override the PasswordManagerImpl for testing.
      passwordManager = new TestPasswordManagerProxy();
      PasswordManagerImpl.instance_ = passwordManager;
    });

    // Test verifies that clicking 'Check again' make proper function call to
    // password manager
    test('testCheckAgainButtonWhenIdle', function() {
      assertEquals(
          PasswordCheckState.IDLE, passwordManager.data.checkStatus.state);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
            expectEquals(
                section.i18n('checkPasswordsAgain'),
                section.$.controlPasswordCheckButton.innerText);
            section.$.controlPasswordCheckButton.click();
          })
          .then(() => passwordManager.whenCalled('startBulkPasswordCheck'));
    });

    // Test verifies that clicking 'Check again' make proper function call to
    // password manager
    test('testStopButtonWhenRunning', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.RUNNING,
              /*checked=*/ 0,
              /*remaining=*/ 2);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
            expectEquals(
                section.i18n('checkPasswordsStop'),
                section.$.controlPasswordCheckButton.innerText);
            section.$.controlPasswordCheckButton.click();
          })
          .then(() => passwordManager.whenCalled('stopBulkPasswordCheck'));
    });

    // Test verifies that 'Try again' is not visible if users are out of quota.
    test('testNoRetryAfterHittingQuota', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.QUOTA_LIMIT);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
      });
    });

    // Test verifies that sync users see only the link to account checkup and no
    // button to start the local leak check.
    test('testOnlyCheckupLinkForTooManyPasswordsWhenSyncing', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.TOO_MANY_PASSWORDS);

      const section = createCheckPasswordSection();
      cr.webUIListenerCallback(
          'sync-prefs-changed', sync_test_util.getSyncAllPrefs());
      sync_test_util.simulateSyncStatus({signedIn: true});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        expectTrue(isElementVisible(section.$.linkToGoogleAccount));
        expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
      });
    });

    // Test verifies that too many passwords don't prevent the user from
    // starting the check. The link to the password checkup is not displayed if
    // the user has too many passwords but is not signed in.
    test('testNoCheckupLinkForTooManyPasswordsWhenSignedOut', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS);

      const section = createCheckPasswordSection();
      cr.webUIListenerCallback(
          'sync-prefs-changed', sync_test_util.getSyncAllPrefs());
      sync_test_util.simulateSyncStatus({signedIn: false});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(section.$.linkToGoogleAccount));
        assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
        expectEquals(
            section.i18n('checkPasswordsAgain'),
            section.$.controlPasswordCheckButton.innerText);
      });
    });

    // Test verifies that too many passwords don't prevent the user from
    // starting the check. The link to the password checkup is not displayed if
    // a sync-user encrypted their passwords with a custom passphrase.
    test('testNoCheckupLinkForTooManyPasswordsForEncryption', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS);

      const section = createCheckPasswordSection();
      const syncPrefs = sync_test_util.getSyncAllPrefs();
      syncPrefs.encryptAllData = true;
      cr.webUIListenerCallback('sync-prefs-changed', syncPrefs);
      sync_test_util.simulateSyncStatus({signedIn: true});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(section.$.linkToGoogleAccount));
        assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
        expectEquals(
            section.i18n('checkPasswordsAgain'),
            section.$.controlPasswordCheckButton.innerText);
      });
    });

    // Test verifies that 'Try again' is not visible if users have too many
    // passwords and are out of quota. Instead, there should be a link to their
    // the checkup in their Google Account.
    test('testCheckupForTooManyPasswordsQuotaLimitWithSync', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS_AND_QUOTA_LIMIT);

      const section = createCheckPasswordSection();
      cr.webUIListenerCallback(
          'sync-prefs-changed', sync_test_util.getSyncAllPrefs());
      sync_test_util.simulateSyncStatus({signedIn: true});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
        expectTrue(isElementVisible(section.$.linkToGoogleAccount));
      });
    });

    // There should be neither a Retry button nor a link to the password checkup
    // if the user has too many passwords, is out of quota, and is signed out.
    test('testNoCheckupForTooManyPasswordsQuotaLimitWhenSignedOut', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS_AND_QUOTA_LIMIT);

      const section = createCheckPasswordSection();
      cr.webUIListenerCallback(
          'sync-prefs-changed', sync_test_util.getSyncAllPrefs());
      sync_test_util.simulateSyncStatus({signedIn: false});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
        expectFalse(isElementVisible(section.$.linkToGoogleAccount));
      });
    });

    // There should be neither a Retry button nor a link to the password checkup
    // if a sync-user encrypted their passwords with a custom passphrase.
    test('testNoCheckupForTooManyPasswordsQuotaLimitForEncryption', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS_AND_QUOTA_LIMIT);

      const section = createCheckPasswordSection();
      const syncPrefs = sync_test_util.getSyncAllPrefs();
      syncPrefs.encryptAllData = true;
      cr.webUIListenerCallback('sync-prefs-changed', syncPrefs);
      sync_test_util.simulateSyncStatus({signedIn: true});

      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
        expectFalse(isElementVisible(section.$.linkToGoogleAccount));
      });
    });

    // Test verifies that 'Try again' visible and working when users encounter a
    // generic error.
    test('testShowRetryAfterGenericError', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.OTHER_ERROR);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
            expectEquals(
                section.i18n('checkPasswordsAgainAfterError'),
                section.$.controlPasswordCheckButton.innerText);
            section.$.controlPasswordCheckButton.click();
          })
          .then(() => passwordManager.whenCalled('startBulkPasswordCheck'));
    });

    // Test verifies that 'Try again' visible and working when users encounter a
    // not-signed-in error.
    test('testShowRetryAfterSignOutError', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.SIGNED_OUT);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
            expectEquals(
                section.i18n('checkPasswordsAgainAfterError'),
                section.$.controlPasswordCheckButton.innerText);
            section.$.controlPasswordCheckButton.click();
          })
          .then(() => passwordManager.whenCalled('startBulkPasswordCheck'));
    });

    // Test verifies that 'Try again' is hidden when users encounter a
    // no-saved-passwords error.
    test('testHideRetryAfterNoPasswordsError', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.NO_PASSWORDS);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            expectFalse(isElementVisible(section.$.controlPasswordCheckButton));
          });
    });

    // Test verifies that 'Try again' visible and working when users encounter a
    // connection error.
    test('testShowRetryAfterNoConnectionError', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.OFFLINE);
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus')
          .then(() => {
            assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
            expectEquals(
                section.i18n('checkPasswordsAgainAfterError'),
                section.$.controlPasswordCheckButton.innerText);
            section.$.controlPasswordCheckButton.click();
          })
          .then(() => passwordManager.whenCalled('startBulkPasswordCheck'));
    });

    // Test verifies that if no compromised credentials found than list is not
    // shown TODO(https://crbug.com/1047726): add additional checks after
    // UI is implemented
    test('testNoCompromisedCredentials', function() {
      const checkPasswordSection = createCheckPasswordSection();
      assertTrue(checkPasswordSection.$.passwordCheckBody.hidden);
      assertFalse(checkPasswordSection.$.noCompromisedCredentials.hidden);
      validateLeakedPasswordsList(checkPasswordSection, []);
    });

    // Test verifies that compromised credentials are displayed in a proper way
    test('testSomeCompromisedCredentials', function() {
      const leakedPasswords = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'PHISHED', 1, 1),
        autofill_test_util.makeCompromisedCredential(
            'two.com', 'test3', 'LEAKED', 2, 2),
      ];
      passwordManager.data.leakedCredentials = leakedPasswords;
      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getCompromisedCredentials')
          .then(() => {
            Polymer.dom.flush();
            assertFalse(checkPasswordSection.$.passwordCheckBody.hidden);
            assertTrue(checkPasswordSection.$.noCompromisedCredentials.hidden);
            validateLeakedPasswordsList(checkPasswordSection, leakedPasswords);
          });
    });

    // Test verifies that credentials from mobile app shown correctly
    test('testSomeCompromisedCredentials', function() {
      const password = autofill_test_util.makeCompromisedCredential(
          'one.com', 'test4', 'LEAKED');
      password.changePasswordUrl = null;

      const checkPasswordSection = createLeakedPasswordItem(password);
      assertEquals(checkPasswordSection.$$('changePasswordUrl'), null);
      assertTrue(!!checkPasswordSection.$$('#changePasswordInApp'));
    });

    // Verify that the More Actions menu opens when the button is clicked.
    test('testMoreActionsMenu', function() {
      const leakedPasswords = [
        autofill_test_util.makeCompromisedCredential(
            'google.com', 'jdoerrie', 'LEAKED'),
      ];
      passwordManager.data.leakedCredentials = leakedPasswords;
      const checkPasswordSection = createCheckPasswordSection();

      return passwordManager.whenCalled('getCompromisedCredentials')
          .then(() => {
            Polymer.dom.flush();
            assertFalse(checkPasswordSection.$.passwordCheckBody.hidden);
            const listElement =
                checkPasswordSection.$$('password-check-list-item');
            const menu = checkPasswordSection.$.moreActionsMenu;

            assertFalse(menu.open);
            listElement.$.more.click();
            assertTrue(menu.open);
          });
    });

    // A changing status is immediately reflected in title, icon and banner.
    test('testUpdatesNumberOfCheckedPasswordsWhileRunning', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.RUNNING,
              /*checked=*/ 1,
              /*remaining=*/ 1);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        assertTrue(isElementVisible(section.$.title));
        expectEquals(
            section.i18n('checkPasswordsProgress', 1, 2),
            section.$.title.innerText);

        // Change status from running to IDLE.
        assertTrue(
            !!passwordManager.lastCallback.addPasswordCheckStatusListener);
        passwordManager.lastCallback.addPasswordCheckStatusListener(
            autofill_test_util.makePasswordCheckStatus(
                /*state=*/ PasswordCheckState.RUNNING,
                /*checked=*/ 2,
                /*remaining=*/ 0));

        Polymer.dom.flush();
        assertTrue(isElementVisible(section.$.title));
        expectEquals(
            section.i18n('checkPasswordsProgress', 2, 2),
            section.$.title.innerText);
      });
    });

    // Tests that the status is queried right when the page loads.
    test('testQueriesCheckedStatusImmediately', function() {
      const data = passwordManager.data;
      assertEquals(PasswordCheckState.IDLE, data.checkStatus.state);
      assertEquals(0, data.leakedCredentials.length);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectEquals(
            PasswordCheckState.IDLE, checkPasswordSection.status_.state);
      }, () => assertTrue(false));
    });

    // Tests that the spinner is replaced with a checkmark on successful runs.
    test('testShowsCheckmarkIconWhenFinishedWithoutLeaks', function() {
      const data = passwordManager.data;
      assertEquals(PasswordCheckState.IDLE, data.checkStatus.state);
      assertEquals(0, data.leakedCredentials.length);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const icon = checkPasswordSection.$$('iron-icon');
        const spinner = checkPasswordSection.$$('paper-spinner-lite');
        expectFalse(isElementVisible(spinner));
        assertTrue(isElementVisible(icon));
        expectFalse(icon.classList.contains('has-leaks'));
        expectTrue(icon.classList.contains('no-leaks'));
      });
    });

    // Tests that the spinner is replaced with a triangle if leaks were found.
    test('testShowsTriangleIconWhenFinishedWithLeaks', function() {
      const data = passwordManager.data;
      assertEquals(PasswordCheckState.IDLE, data.checkStatus.state);
      data.leakedCredentials = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'LEAKED'),
      ];

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const icon = checkPasswordSection.$$('iron-icon');
        const spinner = checkPasswordSection.$$('paper-spinner-lite');
        expectFalse(isElementVisible(spinner));
        assertTrue(isElementVisible(icon));
        expectTrue(icon.classList.contains('has-leaks'));
        expectFalse(icon.classList.contains('no-leaks'));
      });
    });

    // Tests that the spinner is replaced with a warning on errors.
    test('testShowsInfoIconWhenFinishedWithErrors', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.OFFLINE,
              /*checked=*/ undefined,
              /*remaining=*/ undefined);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const icon = checkPasswordSection.$$('iron-icon');
        const spinner = checkPasswordSection.$$('paper-spinner-lite');
        expectFalse(isElementVisible(spinner));
        assertTrue(isElementVisible(icon));
        expectFalse(icon.classList.contains('has-leaks'));
        expectFalse(icon.classList.contains('no-leaks'));
      });
    });

    // Tests that the spinner replaces any icon while the check is running.
    test('testShowsSpinnerWhileRunning', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.RUNNING,
              /*checked=*/ 1,
              /*remaining=*/ 3);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const icon = checkPasswordSection.$$('iron-icon');
        const spinner = checkPasswordSection.$$('paper-spinner-lite');
        expectTrue(isElementVisible(spinner));
        expectFalse(isElementVisible(icon));
      });
    });

    // While running, the check should show the processed and total passwords.
    test('testShowOnlyProgressWhileRunningWithoutLeaks', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              /*state=*/ PasswordCheckState.RUNNING,
              /*checked=*/ 1,
              /*remaining=*/ 3);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsProgress', 1, 4), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // While running, show progress and already found leak count.
    test('testShowProgressAndLeaksWhileRunning', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.RUNNING,
          /*checked=*/ 2,
          /*remaining=*/ 3);
      data.leakedCredentials = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'LEAKED'),
      ];

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        const subtitle = section.$.subtitle;
        assertTrue(isElementVisible(title));
        assertTrue(isElementVisible(subtitle));
        expectEquals(
            section.i18n('checkPasswordsProgress', 2, 5), title.innerText);
      });
    });

    // When canceled, show string explaining that and already found leak
    // count.
    test('testShowProgressAndLeaksAfterCanceled', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.CANCELED,
          /*checked=*/ 2,
          /*remaining=*/ 3);
      data.leakedCredentials = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'LEAKED'),
      ];

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        const subtitle = section.$.subtitle;
        assertTrue(isElementVisible(title));
        assertTrue(isElementVisible(subtitle));
        expectEquals(section.i18n('checkPasswordsCanceled'), title.innerText);
      });
    });

    // After running, show confirmation, timestamp and number of leaks.
    test('testDontShowTimeStampIfNotRun', function() {
      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        const subtitle = section.$.subtitle;
        assertTrue(isElementVisible(title));
        assertTrue(isElementVisible(subtitle));
        expectEquals(section.i18n('checkPasswords'), title.innerText);
      });
    });

    // After running, show confirmation, timestamp and number of leaks.
    test('testShowLeakCountAndTimeStampWhenIdle', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.IDLE,
          /*checked=*/ 4,
          /*remaining=*/ 0,
          /*lastCheck=*/ 'Just now');
      data.leakedCredentials = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'LEAKED'),
      ];

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        const subtitle = section.$.subtitle;
        assertTrue(isElementVisible(title));
        assertTrue(isElementVisible(subtitle));
        expectEquals(
            section.i18n('checkPasswords') + ' • Just now', title.innerText);
      });
    });

    // When offline, only show an error.
    test('testShowOnlyErrorWhenOffline', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.OFFLINE);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsErrorOffline'), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // When signed out, only show an error.
    test('testShowOnlyErrorWhenSignedOut', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.SIGNED_OUT);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsErrorSignedOut'), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // When no passwords are saved, only show an error.
    test('testShowOnlyErrorWithoutPasswords', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.NO_PASSWORDS);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsErrorNoPasswords'), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // When too many passwords were saved to check them, only show an error.
    test('testShowOnlyErrorWhenTooManyPasswords', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(section.i18n('checkPasswordsErrorTooManyPasswords'),
                     title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // When too many passwords were saved to check them, only show an error.
    test('testShowyErrorAndSubtitleWhenTooManyPasswordsAndOutOfQuota', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.TOO_MANY_PASSWORDS_AND_QUOTA_LIMIT);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsErrorInterruptedTooManyPasswords'),
            title.innerText);
        expectTrue(isElementVisible(section.$.subtitle));
      });
    });

    // When users run out of quota, only show an error.
    test('testShowOnlyErrorWhenQuotaIsHit', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.QUOTA_LIMIT);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(section.i18n('checkPasswordsErrorQuota'), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // When a general error occurs, only show the message.
    test('testShowOnlyGenericError', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(
              PasswordCheckState.OTHER_ERROR);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        const title = section.$.title;
        assertTrue(isElementVisible(title));
        expectEquals(
            section.i18n('checkPasswordsErrorGeneric'), title.innerText);
        expectFalse(isElementVisible(section.$.subtitle));
      });
    });

    // Transform check-button to stop-button if a check is running.
    test('testButtonChangesTextAccordingToStatus', function() {
      passwordManager.data.checkStatus =
          autofill_test_util.makePasswordCheckStatus(PasswordCheckState.IDLE);

      const section = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
        expectEquals(
            section.i18n('checkPasswordsAgain'),
            section.$.controlPasswordCheckButton.innerText);

        // Change status from running to IDLE.
        assertTrue(
            !!passwordManager.lastCallback.addPasswordCheckStatusListener);
        passwordManager.lastCallback.addPasswordCheckStatusListener(
            autofill_test_util.makePasswordCheckStatus(
                /*state=*/ PasswordCheckState.RUNNING,
                /*checked=*/ 0,
                /*remaining=*/ 2));
        assertTrue(isElementVisible(section.$.controlPasswordCheckButton));
        expectEquals(
            section.i18n('checkPasswordsStop'),
            section.$.controlPasswordCheckButton.innerText);
      });
    });

    // Test that the banner is in a state that shows the positive confirmation
    // after a leak check finished.
    test('testShowsPositiveBannerWhenIdle', function() {
      const data = passwordManager.data;
      assertEquals(PasswordCheckState.IDLE, data.checkStatus.state);
      assertEquals(0, data.leakedCredentials.length);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        assertTrue(isElementVisible(checkPasswordSection.$$('#bannerImage')));
        expectEquals(
            'chrome://settings/images/password_check_positive.svg',
            checkPasswordSection.$$('#bannerImage').src);
      });
    });

    // Test that the banner is in a state that shows that the leak check is
    // in progress but hasn't found anything yet.
    test('testShowsNeutralBannerWhenRunning', function() {
      const data = passwordManager.data;
      assertEquals(0, data.leakedCredentials.length);
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.RUNNING, /*checked=*/ 1,
          /*remaining=*/ 5);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        assertTrue(isElementVisible(checkPasswordSection.$$('#bannerImage')));
        expectEquals(
            'chrome://settings/images/password_check_neutral.svg',
            checkPasswordSection.$$('#bannerImage').src);
      });
    });

    // Test that the banner is in a state that shows that the leak check is
    // in progress but hasn't found anything yet.
    test('testShowsNeutralBannerWhenCanceled', function() {
      const data = passwordManager.data;
      assertEquals(0, data.leakedCredentials.length);
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.CANCELED);

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        assertTrue(isElementVisible(checkPasswordSection.$$('#bannerImage')));
        expectEquals(
            'chrome://settings/images/password_check_neutral.svg',
            checkPasswordSection.$$('#bannerImage').src);
      });
    });

    // Test that the banner isn't visible as soon as the first leak is detected.
    test('testLeaksHideBannerWhenRunning', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.RUNNING, /*checked=*/ 1,
          /*remaining=*/ 5);
      data.leakedCredentials = [
          autofill_test_util.makeCompromisedCredential(
              'one.com', 'test4', 'LEAKED'),
      ];

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(checkPasswordSection.$$('#bannerImage')));
      });
    });

    // Test that the banner isn't visible if a leak is detected after a check.
    test('testLeaksHideBannerWhenIdle', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.IDLE);
      data.leakedCredentials = [
          autofill_test_util.makeCompromisedCredential(
              'one.com', 'test4', 'LEAKED'),
      ];

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(checkPasswordSection.$$('#bannerImage')));
      });
    });

    // Test that the banner isn't visible if a leak is detected after canceling.
    test('testLeaksHideBannerWhenCanceled', function() {
      const data = passwordManager.data;
      data.checkStatus = autofill_test_util.makePasswordCheckStatus(
          /*state=*/ PasswordCheckState.CANCELED);
      data.leakedCredentials = [
          autofill_test_util.makeCompromisedCredential(
              'one.com', 'test4', 'LEAKED'),
      ];

      const checkPasswordSection = createCheckPasswordSection();
      return passwordManager.whenCalled('getPasswordCheckStatus').then(() => {
        Polymer.dom.flush();
        expectFalse(isElementVisible(checkPasswordSection.$$('#bannerImage')));
      });
    });

    // Test verifies that new credentials are added to the bottom
    test('testAppendCompromisedCredentials', function() {
      const leakedPasswords = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'LEAKED', 1, 0),
        autofill_test_util.makeCompromisedCredential(
            'two.com', 'test3', 'LEAKED', 2, 0),
      ];
      const checkPasswordSection = createCheckPasswordSection();
      checkPasswordSection.updateList(leakedPasswords);
      Polymer.dom.flush();

      validateLeakedPasswordsList(checkPasswordSection, leakedPasswords);

      leakedPasswords.push(autofill_test_util.makeCompromisedCredential(
          'three.com', 'test2', 'PHISHED', 3, 3));
      leakedPasswords.push(autofill_test_util.makeCompromisedCredential(
          'four.com', 'test1', 'LEAKED', 4, 5));
      leakedPasswords.push(autofill_test_util.makeCompromisedCredential(
          'five.com', 'test0', 'LEAKED', 5, 4));
      checkPasswordSection.updateList(shuffleArray(leakedPasswords));
      Polymer.dom.flush();
      validateLeakedPasswordsList(checkPasswordSection, leakedPasswords);
    });

    // Test verifies that deleting and adding works as it should
    test('testDeleteComrpomisedCredemtials', function() {
      const leakedPasswords = [
        autofill_test_util.makeCompromisedCredential(
            'one.com', 'test4', 'PHISHED', 0, 0),
        autofill_test_util.makeCompromisedCredential(
            'two.com', 'test3', 'LEAKED', 1, 2),
        autofill_test_util.makeCompromisedCredential(
            'three.com', 'test2', 'LEAKED', 2, 2),
        autofill_test_util.makeCompromisedCredential(
            'four.com', 'test2', 'LEAKED', 3, 2),
      ];
      const checkPasswordSection = createCheckPasswordSection();
      checkPasswordSection.updateList(leakedPasswords);
      Polymer.dom.flush();
      validateLeakedPasswordsList(checkPasswordSection, leakedPasswords);

      // remove 2nd and 3rd elements
      leakedPasswords.splice(1, 2);
      leakedPasswords.push(autofill_test_util.makeCompromisedCredential(
          'five.com', 'test2', 'LEAKED', 4, 3));

      checkPasswordSection.updateList(shuffleArray(leakedPasswords));
      Polymer.dom.flush();
      validateLeakedPasswordsList(checkPasswordSection, leakedPasswords);
    });

    // Verify edit a password on the edit dialog.
    test('testEditDialog', function() {
      passwordManager.data.leakedCredentials = [
        autofill_test_util.makeCompromisedCredential(
            'google.com', 'jdoerrie', 'LEAKED'),
      ];
      const checkPasswordSection = createCheckPasswordSection();

      return passwordManager.whenCalled('getCompromisedCredentials')
          .then(() => {
            Polymer.dom.flush();
            const listElements = checkPasswordSection.$.leakedPasswordList;
            const node = listElements.children[1];

            // Open the more actions menu and click 'Edit Password'.
            node.$.more.click();
            checkPasswordSection.$.menuEditPassword.click();

            return passwordManager.whenCalled(
                'getPlainttextCompromisedPassword');
          })
          .then(() => {
            // Verify that the edit dialog has become visible.
            Polymer.dom.flush();
            assertTrue(!!checkPasswordSection.$$(
              'settings-password-check-edit-dialog'));
          });
    });

    test('testEditDialogChangePassword', function() {
      const leakedPassword = autofill_test_util.makeCompromisedCredential(
          'google.com', 'jdoerrie', 'LEAKED');
      leakedPassword.password = 'mybirthday';
      const editDialog = createEditDialog(leakedPassword);

      assertEquals(leakedPassword.password, editDialog.$.passwordInput.value);
      editDialog.$.passwordInput.value = 'yadhtribym';
      editDialog.$.save.click();

      return passwordManager.whenCalled('changeCompromisedCredential')
          .then(({newPassword}) => {
            assertEquals('yadhtribym', newPassword);
          });
    });

    test('testEditDialogCancel', function() {
      const leakedPassword = autofill_test_util.makeCompromisedCredential(
          'google.com', 'jdoerrie', 'LEAKED');
      leakedPassword.password = 'mybirthday';
      const editDialog = createEditDialog(leakedPassword);

      assertEquals(leakedPassword.password, editDialog.$.passwordInput.value);
      editDialog.$.passwordInput.value = 'yadhtribym';
      editDialog.$.cancel.click();

      assertEquals(0,
                   passwordManager.getCallCount('changeCompromisedCredential'));
    });
  });
  // #cr_define_end
});
