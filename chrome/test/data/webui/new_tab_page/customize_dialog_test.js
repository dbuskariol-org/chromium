// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'chrome://new-tab-page/customize_dialog.js';

suite('NewTabPageCustomizeDialogTest', () => {
  /** @type {!CustomizeDialogElement} */
  let customizeDialog;

  setup(() => {
    PolymerTest.clearBody();

    customizeDialog = document.createElement('ntp-customize-dialog');
    document.body.appendChild(customizeDialog);
  });

  test('create customize dialog opens cr dialog', async () => {
    // Assert.
    assertTrue(customizeDialog.$.dialog.open);
  });
});
