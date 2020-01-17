// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/sessions/core/command_storage_manager.h"

#include <utility>

#include "base/bind.h"
#include "base/location.h"
#include "base/memory/scoped_refptr.h"
#include "base/sequenced_task_runner.h"
#include "base/task/post_task.h"
#include "base/threading/thread.h"
#include "base/threading/thread_task_runner_handle.h"
#include "components/sessions/core/command_storage_manager_delegate.h"
#include "components/sessions/core/session_backend.h"

namespace sessions {
namespace {

// Helper used by ScheduleGetLastSessionCommands. It runs callback on TaskRunner
// thread if it's not canceled.
void RunIfNotCanceled(
    const base::CancelableTaskTracker::IsCanceledCallback& is_canceled,
    CommandStorageManager::GetCommandsCallback callback,
    std::vector<std::unique_ptr<SessionCommand>> commands) {
  if (is_canceled.Run())
    return;
  std::move(callback).Run(std::move(commands));
}

void PostOrRunInternalGetCommandsCallback(
    base::SequencedTaskRunner* task_runner,
    CommandStorageManager::GetCommandsCallback callback,
    std::vector<std::unique_ptr<SessionCommand>> commands) {
  if (task_runner->RunsTasksInCurrentSequence()) {
    std::move(callback).Run(std::move(commands));
  } else {
    task_runner->PostTask(
        FROM_HERE, base::BindOnce(std::move(callback), std::move(commands)));
  }
}

}  // namespace

// Delay between when a command is received, and when we save it to the
// backend.
static const int kSaveDelayMS = 2500;

CommandStorageManager::CommandStorageManager(
    SessionType type,
    const base::FilePath& path,
    CommandStorageManagerDelegate* delegate)
    : pending_reset_(false),
      commands_since_reset_(0),
      delegate_(delegate),
      backend_task_runner_(base::CreateSequencedTaskRunner(
          {base::ThreadPool(), base::MayBlock(),
           base::TaskShutdownBehavior::BLOCK_SHUTDOWN})) {
  backend_ =
      base::MakeRefCounted<SessionBackend>(backend_task_runner_, type, path);
  DCHECK(backend_);
}

CommandStorageManager::~CommandStorageManager() {}

void CommandStorageManager::MoveCurrentSessionToLastSession() {
  Save();
  RunTaskOnBackendThread(
      FROM_HERE,
      base::BindOnce(&SessionBackend::MoveCurrentSessionToLastSession,
                     backend_));
}

void CommandStorageManager::DeleteLastSession() {
  RunTaskOnBackendThread(
      FROM_HERE, base::BindOnce(&SessionBackend::DeleteLastSession, backend_));
}

void CommandStorageManager::ScheduleCommand(
    std::unique_ptr<SessionCommand> command) {
  DCHECK(command);
  commands_since_reset_++;
  pending_commands_.push_back(std::move(command));
  StartSaveTimer();
}

void CommandStorageManager::AppendRebuildCommand(
    std::unique_ptr<SessionCommand> command) {
  DCHECK(command);
  pending_commands_.push_back(std::move(command));
}

void CommandStorageManager::EraseCommand(SessionCommand* old_command) {
  auto it = std::find_if(
      pending_commands_.begin(), pending_commands_.end(),
      [old_command](const std::unique_ptr<SessionCommand>& command_ptr) {
        return command_ptr.get() == old_command;
      });
  CHECK(it != pending_commands_.end());
  pending_commands_.erase(it);
}

void CommandStorageManager::SwapCommand(
    SessionCommand* old_command,
    std::unique_ptr<SessionCommand> new_command) {
  auto it = std::find_if(
      pending_commands_.begin(), pending_commands_.end(),
      [old_command](const std::unique_ptr<SessionCommand>& command_ptr) {
        return command_ptr.get() == old_command;
      });
  CHECK(it != pending_commands_.end());
  *it = std::move(new_command);
}

void CommandStorageManager::ClearPendingCommands() {
  pending_commands_.clear();
}

void CommandStorageManager::StartSaveTimer() {
  // Don't start a timer when testing.
  if (delegate_->ShouldUseDelayedSave() &&
      base::ThreadTaskRunnerHandle::IsSet() && !weak_factory_.HasWeakPtrs()) {
    base::ThreadTaskRunnerHandle::Get()->PostDelayedTask(
        FROM_HERE,
        base::BindOnce(&CommandStorageManager::Save,
                       weak_factory_.GetWeakPtr()),
        base::TimeDelta::FromMilliseconds(kSaveDelayMS));
  }
}

void CommandStorageManager::Save() {
  // Inform the delegate that we will save the commands now, giving it the
  // opportunity to append more commands.
  delegate_->OnWillSaveCommands();

  if (pending_commands_.empty())
    return;

  // We create a new vector which will receive all elements from the
  // current commands. This will also clear the current list.
  RunTaskOnBackendThread(
      FROM_HERE, base::BindOnce(&SessionBackend::AppendCommands, backend_,
                                std::move(pending_commands_), pending_reset_));

  if (pending_reset_) {
    commands_since_reset_ = 0;
    pending_reset_ = false;
  }
}

base::CancelableTaskTracker::TaskId
CommandStorageManager::ScheduleGetLastSessionCommands(
    GetCommandsCallback callback,
    base::CancelableTaskTracker* tracker) {
  base::CancelableTaskTracker::IsCanceledCallback is_canceled;
  base::CancelableTaskTracker::TaskId id =
      tracker->NewTrackedTaskId(&is_canceled);

  GetCommandsCallback run_if_not_canceled =
      base::BindOnce(&RunIfNotCanceled, is_canceled, std::move(callback));

  GetCommandsCallback callback_runner =
      base::BindOnce(&PostOrRunInternalGetCommandsCallback,
                     base::RetainedRef(base::ThreadTaskRunnerHandle::Get()),
                     std::move(run_if_not_canceled));

  RunTaskOnBackendThread(
      FROM_HERE,
      base::BindOnce(&SessionBackend::ReadLastSessionCommands, backend_,
                     is_canceled, std::move(callback_runner)));
  return id;
}

void CommandStorageManager::RunTaskOnBackendThread(
    const base::Location& from_here,
    base::OnceClosure task) {
  backend_task_runner_->PostNonNestableTask(from_here, std::move(task));
}

}  // namespace sessions
