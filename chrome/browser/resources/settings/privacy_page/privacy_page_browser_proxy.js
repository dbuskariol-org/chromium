// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/** @fileoverview Handles interprocess communication for the privacy page. */

cr.define('settings', function() {
  /** @typedef {{enabled: boolean, managed: boolean}} */
  let MetricsReporting;

  /** @typedef {{name: string, value: string, policy: string}} */
  let ResolverOption;

  /**
   * Contains the possible string values for the secure DNS mode. This should be
   * kept in sync with the modes in chrome/browser/net/dns_util.h.
   * @enum {string}
   */
  const SecureDnsMode = {
    OFF: 'off',
    AUTOMATIC: 'automatic',
    SECURE: 'secure',
  };

  /**
   * Contains the possible management modes. This should be kept in sync with
   * the management modes in chrome/browser/net/dns_util.h.
   * @enum {number}
   */
  const SecureDnsUiManagementMode = {
    NO_OVERRIDE: 0,
    DISABLED_MANAGED: 1,
    DISABLED_PARENTAL_CONTROLS: 2,
  };

  /**
   * @typedef {{
   *   mode: settings.SecureDnsMode,
   *   templates: !Array<string>,
   *   managementMode: settings.SecureDnsUiManagementMode
   * }}
   */
  let SecureDnsSetting;

  /** @interface */
  class PrivacyPageBrowserProxy {
    // <if expr="_google_chrome and not chromeos">
    /** @return {!Promise<!settings.MetricsReporting>} */
    getMetricsReporting() {}

    /** @param {boolean} enabled */
    setMetricsReportingEnabled(enabled) {}

    // </if>

    // <if expr="is_win or is_macosx">
    /** Invokes the native certificate manager (used by win and mac). */
    showManageSSLCertificates() {}

    // </if>

    /** @param {boolean} enabled */
    setBlockAutoplayEnabled(enabled) {}

    /** @return {!Promise<!Array<!settings.ResolverOption>>} */
    getSecureDnsResolverList() {}

    /** @return {!Promise<!settings.SecureDnsSetting>} */
    getSecureDnsSetting() {}

    /**
     * Returns the first valid URL template, if any.
     * @param {string} entry
     * @return {!Promise<string>}
     */
    validateCustomDnsEntry(entry) {}

    /**
     * Returns whether a test query to the secure DNS template succeeded.
     * @param {string} template
     * @return {!Promise<boolean>}
     */
    probeCustomDnsTemplate(template) {}

    /**
     * Records metrics on the user's interaction with the dropdown menu.
     * @param {string} oldSelection value of previously selected dropdown option
     * @param {string} newSelection value of newly selected dropdown option
     */
    recordUserDropdownInteraction(oldSelection, newSelection) {}
  }

  /**
   * @implements {settings.PrivacyPageBrowserProxy}
   */
  class PrivacyPageBrowserProxyImpl {
    // <if expr="_google_chrome and not chromeos">
    /** @override */
    getMetricsReporting() {
      return cr.sendWithPromise('getMetricsReporting');
    }

    /** @override */
    setMetricsReportingEnabled(enabled) {
      chrome.send('setMetricsReportingEnabled', [enabled]);
    }

    // </if>

    /** @override */
    setBlockAutoplayEnabled(enabled) {
      chrome.send('setBlockAutoplayEnabled', [enabled]);
    }

    // <if expr="is_win or is_macosx">
    /** @override */
    showManageSSLCertificates() {
      chrome.send('showManageSSLCertificates');
    }
    // </if>

    /** @override */
    getSecureDnsResolverList() {
      return cr.sendWithPromise('getSecureDnsResolverList');
    }

    /** @override */
    getSecureDnsSetting() {
      return cr.sendWithPromise('getSecureDnsSetting');
    }

    /** @override */
    validateCustomDnsEntry(entry) {
      return cr.sendWithPromise('validateCustomDnsEntry', entry);
    }

    /** @override */
    probeCustomDnsTemplate(template) {
      return cr.sendWithPromise('probeCustomDnsTemplate', template);
    }

    /** override */
    recordUserDropdownInteraction(oldSelection, newSelection) {
      chrome.send(
          'recordUserDropdownInteraction', [oldSelection, newSelection]);
    }
  }

  cr.addSingletonGetter(PrivacyPageBrowserProxyImpl);

  // #cr_define_end
  return {
    MetricsReporting,
    PrivacyPageBrowserProxy,
    PrivacyPageBrowserProxyImpl,
    ResolverOption,
    SecureDnsMode,
    SecureDnsUiManagementMode,
    SecureDnsSetting,
  };
});
