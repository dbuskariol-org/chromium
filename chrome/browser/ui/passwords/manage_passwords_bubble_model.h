// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_UI_PASSWORDS_MANAGE_PASSWORDS_BUBBLE_MODEL_H_
#define CHROME_BROWSER_UI_PASSWORDS_MANAGE_PASSWORDS_BUBBLE_MODEL_H_

#include <utility>
#include <vector>

#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/time/clock.h"
#include "components/autofill/core/common/password_form.h"
#include "components/password_manager/core/browser/manage_passwords_referrer.h"
#include "components/password_manager/core/browser/password_manager_metrics_util.h"
#include "components/password_manager/core/browser/statistics_table.h"
#include "components/password_manager/core/common/password_manager_ui.h"

namespace content {
class WebContents;
}

namespace password_manager {
class PasswordFormMetricsRecorder;
}

class PasswordsModelDelegate;
class Profile;

// This model provides data for the ManagePasswordsBubble and controls the
// password management actions.
class ManagePasswordsBubbleModel {
 public:
  enum PasswordAction { REMOVE_PASSWORD, ADD_PASSWORD };
  enum DisplayReason { AUTOMATIC, USER_ACTION };

  // Creates a ManagePasswordsBubbleModel, which holds a weak pointer to the
  // delegate. Construction implies that the bubble is shown. The bubble's state
  // is updated from the ManagePasswordsUIController associated with |delegate|.
  ManagePasswordsBubbleModel(base::WeakPtr<PasswordsModelDelegate> delegate,
                             DisplayReason reason);
  ~ManagePasswordsBubbleModel();

  // The method MAY BE called to record the statistics while the bubble is being
  // closed. Otherwise, it is called later on when the model is destroyed.
  void OnBubbleClosing();

  password_manager::ui::State state() const { return state_; }

  const base::string16& title() const { return title_; }
  const autofill::PasswordForm& pending_password() const {
    return pending_password_;
  }

  bool are_passwords_revealed_when_bubble_is_opened() const {
    return are_passwords_revealed_when_bubble_is_opened_;
  }

  bool enable_editing() const { return enable_editing_; }

  Profile* GetProfile() const;
  content::WebContents* GetWebContents() const;

  void SetClockForTesting(base::Clock* clock);

 private:
  class InteractionKeeper;

  // URL of the page from where this bubble was triggered.
  GURL origin_;
  password_manager::ui::State state_;
  base::string16 title_;
  autofill::PasswordForm pending_password_;
  std::vector<autofill::PasswordForm> local_credentials_;

  // A bridge to ManagePasswordsUIController instance.
  base::WeakPtr<PasswordsModelDelegate> delegate_;

  // True if the model has already recorded all the necessary statistics when
  // the bubble is closing.
  bool interaction_reported_;

  // True iff bubble should pop up with revealed password value.
  bool are_passwords_revealed_when_bubble_is_opened_;

  // True iff username/password editing should be enabled.
  bool enable_editing_;

  // Reference to metrics recorder of the PasswordForm presented to the user by
  // |this|. We hold on to this because |delegate_| may not be able to provide
  // the reference anymore when we need it.
  scoped_refptr<password_manager::PasswordFormMetricsRecorder>
      metrics_recorder_;

  DISALLOW_COPY_AND_ASSIGN(ManagePasswordsBubbleModel);
};

#endif  // CHROME_BROWSER_UI_PASSWORDS_MANAGE_PASSWORDS_BUBBLE_MODEL_H_
