// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef UI_SHELL_DIALOGS_SELECT_FILE_DIALOG_LACROS_H_
#define UI_SHELL_DIALOGS_SELECT_FILE_DIALOG_LACROS_H_

#include <vector>

#include "chromeos/lacros/mojom/select_file.mojom.h"
#include "mojo/public/cpp/bindings/remote.h"
#include "ui/shell_dialogs/select_file_dialog.h"
#include "ui/shell_dialogs/select_file_dialog_factory.h"
#include "ui/shell_dialogs/shell_dialogs_export.h"

namespace ui {

// SelectFileDialogLacros implements file open and save dialogs for the
// lacros-chrome binary. The dialog itself is handled by the file manager in
// ash-chrome.
class SHELL_DIALOGS_EXPORT SelectFileDialogLacros : public SelectFileDialog {
 public:
  class SHELL_DIALOGS_EXPORT Factory : public SelectFileDialogFactory {
   public:
    Factory();
    Factory(const Factory&) = delete;
    Factory& operator=(const Factory&) = delete;
    ~Factory() override;

    // SelectFileDialogFactory:
    ui::SelectFileDialog* Create(
        ui::SelectFileDialog::Listener* listener,
        std::unique_ptr<ui::SelectFilePolicy> policy) override;
  };

  SelectFileDialogLacros(Listener* listener,
                         std::unique_ptr<SelectFilePolicy> policy);
  SelectFileDialogLacros(const SelectFileDialogLacros&) = delete;
  SelectFileDialogLacros& operator=(const SelectFileDialogLacros&) = delete;

  // SelectFileDialog:
  void SelectFileImpl(Type type,
                      const base::string16& title,
                      const base::FilePath& default_path,
                      const FileTypeInfo* file_types,
                      int file_type_index,
                      const base::FilePath::StringType& default_extension,
                      gfx::NativeWindow owning_window,
                      void* params) override;
  bool HasMultipleFileTypeChoicesImpl() override;
  bool IsRunning(gfx::NativeWindow owning_window) const override;
  void ListenerDestroyed() override {}

 private:
  // Private because SelectFileDialog is ref-counted.
  ~SelectFileDialogLacros() override;

  // Callback for file selection.
  void OnSelected(lacros::mojom::SelectFileResult result,
                  std::vector<lacros::mojom::SelectedFileInfoPtr> files);

  // Cached parameters from the call to SelectFileImpl.
  void* params_ = nullptr;

  // Remote in the ash-chrome process.
  mojo::Remote<lacros::mojom::SelectFile> select_file_remote_;
};

}  // namespace ui

#endif  // UI_SHELL_DIALOGS_SELECT_FILE_DIALOG_LACROS_H_
