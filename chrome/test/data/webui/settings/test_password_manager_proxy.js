// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/** @fileoverview Test implementation of PasswordManagerProxy. */

// clang-format off
// #import {TestBrowserProxy} from 'chrome://test/test_browser_proxy.m.js';
// #import {makePasswordCheckStatus, PasswordManagerExpectations} from 'chrome://test/settings/passwords_and_autofill_fake_data.m.js';
// clang-format on

/**
 * Test implementation
 * @implements {PasswordManagerProxy}
 * @constructor
 */
/* #export */ class TestPasswordManagerProxy extends TestBrowserProxy {
  constructor() {
    super([
      'requestPlaintextPassword',
      'startBulkPasswordCheck',
      'stopBulkPasswordCheck',
      'getCompromisedCredentials',
      'getPasswordCheckStatus',
      'getPlainttextCompromisedPassword',
      'changeCompromisedCredential',
    ]);

    this.actual_ = new autofill_test_util.PasswordManagerExpectations();

    // Set these to have non-empty data.
    this.data = {
      passwords: [],
      exceptions: [],
      leakedCredentials: [],
      checkStatus: autofill_test_util.makePasswordCheckStatus(),
    };

    // Holds the last callbacks so they can be called when needed/
    this.lastCallback = {
      addPasswordCheckStatusListener: null,
      addSavedPasswordListChangedListener: null,
      addExceptionListChangedListener: null,
      requestPlaintextPassword: null,
      addCompromisedCredentialsListener: null,
    };

    this.plaintextPassword_ = '';
  }

  /** @override */
  addSavedPasswordListChangedListener(listener) {
    this.actual_.listening.passwords++;
    this.lastCallback.addSavedPasswordListChangedListener = listener;
  }

  /** @override */
  removeSavedPasswordListChangedListener(listener) {
    this.actual_.listening.passwords--;
  }

  /** @override */
  getSavedPasswordList(callback) {
    this.actual_.requested.passwords++;
    callback(this.data.passwords);
  }

  /** @override */
  recordPasswordsPageAccessInSettings() {}

  /** @override */
  removeSavedPassword(id) {
    this.actual_.removed.passwords++;

    if (this.onRemoveSavedPassword) {
      this.onRemoveSavedPassword(id);
    }
  }

  /** @override */
  addExceptionListChangedListener(listener) {
    this.actual_.listening.exceptions++;
    this.lastCallback.addExceptionListChangedListener = listener;
  }

  /** @override */
  removeExceptionListChangedListener(listener) {
    this.actual_.listening.exceptions--;
  }

  /** @override */
  getExceptionList(callback) {
    this.actual_.requested.exceptions++;
    callback(this.data.exceptions);
  }

  /** @override */
  removeException(id) {
    this.actual_.removed.exceptions++;

    if (this.onRemoveException) {
      this.onRemoveException(id);
    }
  }

  /** @override */
  requestPlaintextPassword(id, reason) {
    this.methodCalled('requestPlaintextPassword', {id, reason});
    return Promise.resolve(this.plaintextPassword_);
  }

  setPlaintextPassword(plaintextPassword) {
    this.plaintextPassword_ = plaintextPassword;
  }

  /** @override */
  addAccountStorageOptInStateListener(listener) {
    this.actual_.listening.accountStorageOptInState++;
    this.lastCallback.addAccountStorageOptInStateListener = listener;
  }

  /** @override */
  removeAccountStorageOptInStateListener(listener) {
    this.actual_.listening.accountStorageOptInState--;
  }

  /** @override */
  isOptedInForAccountStorage() {
    this.actual_.requested.accountStorageOptInState++;
    return Promise.resolve(false);
  }

  /**
   * Verifies expectations.
   * @param {!PasswordManagerExpectations} expected
   */
  assertExpectations(expected) {
    const actual = this.actual_;

    assertEquals(expected.requested.passwords, actual.requested.passwords);
    assertEquals(expected.requested.exceptions, actual.requested.exceptions);
    assertEquals(
        expected.requested.plaintextPassword,
        actual.requested.plaintextPassword);
    assertEquals(
        expected.requested.accountStorageOptInState,
        actual.requested.accountStorageOptInState);

    assertEquals(expected.removed.passwords, actual.removed.passwords);
    assertEquals(expected.removed.exceptions, actual.removed.exceptions);

    assertEquals(expected.listening.passwords, actual.listening.passwords);
    assertEquals(expected.listening.exceptions, actual.listening.exceptions);
    assertEquals(
        expected.listening.accountStorageOptInState,
        actual.listening.accountStorageOptInState);
  }

  /** @override */
  startBulkPasswordCheck() {
    this.methodCalled('startBulkPasswordCheck');
  }

  /** @override */
  stopBulkPasswordCheck() {
    this.methodCalled('stopBulkPasswordCheck');
  }

  /** @override */
  getCompromisedCredentials() {
    this.methodCalled('getCompromisedCredentials');
    return Promise.resolve(this.data.leakedCredentials);
  }

  /** @override */
  getPasswordCheckStatus() {
    this.methodCalled('getPasswordCheckStatus');
    return Promise.resolve(this.data.checkStatus);
  }

  /** @override */
  addCompromisedCredentialsListener(listener) {
    this.lastCallback.addCompromisedCredentialsListener = listener;
  }

  /** @override */
  removeCompromisedCredentialsListener(listener) {}

  /** @override */
  addPasswordCheckStatusListener(listener) {
    this.lastCallback.addPasswordCheckStatusListener = listener;
  }

  /** @override */
  removePasswordCheckStatusListener(listener) {}

  /** @override */
  getPlaintextCompromisedPassword(credential, reason) {
    this.methodCalled('getPlainttextCompromisedPassword');
    const newCredential = Object.assign({}, credential);
    newCredential.password = 'this-is-the-password';
    return Promise.resolve(newCredential);
  }

  /** @override */
  changeCompromisedCredential(credential, newPassword) {
    this.methodCalled('changeCompromisedCredential', {credential, newPassword});
    return Promise.resolve();
  }
}
