// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/policy/messaging_layer/storage/storage.h"

#include <utility>
#include <vector>

#include "base/callback.h"
#include "base/strings/strcat.h"
#include "base/strings/string_number_conversions.h"
#include "base/task/task_traits.h"
#include "base/task_runner.h"
#include "base/threading/thread.h"
#include "base/threading/thread_task_runner_handle.h"
#include "chrome/browser/policy/messaging_layer/storage/storage_queue.h"
#include "chrome/browser/policy/messaging_layer/util/status_macros.h"
#include "chrome/browser/policy/messaging_layer/util/task_runner_context.h"

namespace reporting {

namespace {

// Parameters of individual queues.
// TODO(b/159352842): Deliver space and upload parameters from outside.

constexpr base::FilePath::CharType immediate_queue_subdir[] =
    FILE_PATH_LITERAL("Immediate");
constexpr base::FilePath::CharType immediate_queue_prefix[] =
    FILE_PATH_LITERAL("P_Immediate");
const uint64_t immediate_queue_total = 4 * 1024LL;

constexpr base::FilePath::CharType fast_batch_queue_subdir[] =
    FILE_PATH_LITERAL("FastBatch");
constexpr base::FilePath::CharType fast_batch_queue_prefix[] =
    FILE_PATH_LITERAL("P_FastBatch");
const uint64_t fast_batch_queue_total = 64 * 1024LL;
constexpr base::TimeDelta fast_batch_upload_period =
    base::TimeDelta::FromSeconds(1);

constexpr base::FilePath::CharType slow_batch_queue_subdir[] =
    FILE_PATH_LITERAL("SlowBatch");
constexpr base::FilePath::CharType slow_batch_queue_prefix[] =
    FILE_PATH_LITERAL("P_SlowBatch");
const uint64_t slow_batch_queue_total = 16 * 1024LL * 1024LL;
constexpr base::TimeDelta slow_batch_upload_period =
    base::TimeDelta::FromSeconds(20);

constexpr base::FilePath::CharType background_queue_subdir[] =
    FILE_PATH_LITERAL("Background");
constexpr base::FilePath::CharType background_queue_prefix[] =
    FILE_PATH_LITERAL("P_Background");
const uint64_t background_queue_total = 64 * 1024LL * 1024LL;
constexpr base::TimeDelta background_upload_period =
    base::TimeDelta::FromMinutes(1);

// Returns vector of <priority, queue_options> for all expected queues in
// Storage. Queues are all located under the given root directory.
std::vector<std::pair<Priority, StorageQueue::Options>> ExpectedQueues(
    const base::FilePath& root_directory) {
  return {
      std::make_pair(IMMEDIATE, StorageQueue::Options()
                                    .set_directory(root_directory.Append(
                                        immediate_queue_subdir))
                                    .set_file_prefix(immediate_queue_prefix)
                                    .set_total_size(immediate_queue_total)),
      std::make_pair(
          FAST_BATCH,
          StorageQueue::Options()
              .set_directory(root_directory.Append(fast_batch_queue_subdir))
              .set_file_prefix(fast_batch_queue_prefix)
              .set_total_size(fast_batch_queue_total)
              .set_upload_period(fast_batch_upload_period)),
      std::make_pair(
          SLOW_BATCH,
          StorageQueue::Options()
              .set_directory(root_directory.Append(slow_batch_queue_subdir))
              .set_file_prefix(slow_batch_queue_prefix)
              .set_total_size(slow_batch_queue_total)
              .set_upload_period(slow_batch_upload_period)),
      std::make_pair(
          BACKGROUND_BATCH,
          StorageQueue::Options()
              .set_directory(root_directory.Append(background_queue_subdir))
              .set_file_prefix(background_queue_prefix)
              .set_total_size(background_queue_total)
              .set_upload_period(background_upload_period)),
  };
}

}  // namespace

// Uploader interface adaptor for individual queue.
class Storage::QueueUploaderInterface : public StorageQueue::UploaderInterface {
 public:
  QueueUploaderInterface(
      Priority priority,
      std::unique_ptr<Storage::UploaderInterface> storage_interface)
      : priority_(priority), storage_interface_(std::move(storage_interface)) {}

  // Factory method.
  static StatusOr<std::unique_ptr<StorageQueue::UploaderInterface>>
  ProvideUploader(Priority priority, StartUploadCb start_upload_cb) {
    ASSIGN_OR_RETURN(std::unique_ptr<Storage::UploaderInterface> uploader,
                     start_upload_cb.Run(priority));
    return std::make_unique<QueueUploaderInterface>(priority,
                                                    std::move(uploader));
  }

  void ProcessBlob(StatusOr<base::span<const uint8_t>> data,
                   base::OnceCallback<void(bool)> processed_cb) override {
    storage_interface_->ProcessBlob(priority_, data, std::move(processed_cb));
  }
  void Completed(Status final_status) override {
    storage_interface_->Completed(priority_, final_status);
  }

 private:
  const Priority priority_;
  const std::unique_ptr<Storage::UploaderInterface> storage_interface_;
};

void Storage::Create(
    const Options& options,
    StartUploadCb start_upload_cb,
    base::OnceCallback<void(StatusOr<scoped_refptr<Storage>>)> completion_cb) {
  // Initialize Storage object, populating all the queues.
  class StorageInitContext
      : public TaskRunnerContext<StatusOr<scoped_refptr<Storage>>> {
   public:
    StorageInitContext(
        const std::vector<std::pair<Priority, StorageQueue::Options>>&
            queues_options,
        scoped_refptr<Storage> storage,
        base::OnceCallback<void(StatusOr<scoped_refptr<Storage>>)> callback)
        : TaskRunnerContext<StatusOr<scoped_refptr<Storage>>>(
              std::move(callback),
              base::ThreadPool::CreateSequencedTaskRunner(
                  {base::TaskPriority::BEST_EFFORT, base::MayBlock()})),
          queues_options_(queues_options),
          storage_(std::move(storage)),
          count_(queues_options_.size()) {}

   private:
    // Context can only be deleted by calling Response method.
    ~StorageInitContext() override { DCHECK_EQ(count_, 0); }

    void OnStart() override {
      CheckOnValidSequence();
      for (const auto& queue_options : queues_options_) {
        StorageQueue::Create(
            /*options=*/queue_options.second,
            base::BindRepeating(&QueueUploaderInterface::ProvideUploader,
                                /*priority=*/queue_options.first,
                                storage_->start_upload_cb_),
            base::BindOnce(&StorageInitContext::ScheduleAddQueue, this,
                           /*priority=*/queue_options.first));
      }
    }

    void ScheduleAddQueue(
        Priority priority,
        StatusOr<scoped_refptr<StorageQueue>> storage_queue_result) {
      Schedule(&StorageInitContext::AddQueue, this, priority,
               std::move(storage_queue_result));
    }

    void AddQueue(Priority priority,
                  StatusOr<scoped_refptr<StorageQueue>> storage_queue_result) {
      CheckOnValidSequence();
      if (storage_queue_result.ok()) {
        auto add_result = storage_->queues_.emplace(
            priority, storage_queue_result.ValueOrDie());
        DCHECK(add_result.second);
      } else {
        LOG(ERROR) << "Could not create queue, priority=" << priority
                   << ", status=" << storage_queue_result.status();
        if (final_status_.ok()) {
          final_status_ = storage_queue_result.status();
        }
      }
      DCHECK_GT(count_, 0);
      if (--count_ > 0) {
        return;
      }
      if (!final_status_.ok()) {
        Response(final_status_);
        return;
      }
      Response(std::move(storage_));
    }

    const std::vector<std::pair<Priority, StorageQueue::Options>>
        queues_options_;
    scoped_refptr<Storage> storage_;
    int32_t count_;
    Status final_status_;
  };

  // Create Storage object.
  // Cannot use base::MakeRefCounted<Storage>, because constructor is private.
  scoped_refptr<Storage> storage =
      base::WrapRefCounted(new Storage(options, std::move(start_upload_cb)));

  // Asynchronously run initialization.
  Start<StorageInitContext>(ExpectedQueues(storage->options_.directory()),
                            std::move(storage), std::move(completion_cb));
}

Storage::Storage(const Options& options, StartUploadCb start_upload_cb)
    : options_(options), start_upload_cb_(std::move(start_upload_cb)) {}

Storage::~Storage() {
  DCHECK(is_shutting_down_) << "Storage not shut down properly";
  for (const auto& q : queues_) {
    DCHECK_EQ(q.second.get(), nullptr)
        << "Queue has not been shutdown properly, priority=" << q.first;
  }
}

// static
void Storage::ShutDown(scoped_refptr<Storage>* storage,
                       base::OnceCallback<void(Status)> done_cb) {
  // Shuts down all queues of the Storage object.
  class StorageShutDownContext : public TaskRunnerContext<Status> {
   public:
    StorageShutDownContext(scoped_refptr<Storage>* storage,
                           base::OnceCallback<void(Status)> callback)
        : TaskRunnerContext<Status>(
              std::move(callback),
              base::ThreadPool::CreateSequencedTaskRunner(
                  {base::TaskPriority::BEST_EFFORT, base::MayBlock()})),
          storage_(storage),
          count_((*storage_)->queues_.size()) {}

   private:
    // Context can only be deleted by calling Response method.
    ~StorageShutDownContext() override { DCHECK_EQ(count_, 0); }

    void OnStart() override {
      CheckOnValidSequence();
      (*storage_)->is_shutting_down_ = true;
      for (auto& queue : (*storage_)->queues_) {
        StorageQueue::ShutDown(
            &queue.second,
            base::BindOnce(&StorageShutDownContext::ScheduleQueueClosed, this));
      }
    }

    void ScheduleQueueClosed() {
      Schedule(&StorageShutDownContext::QueueClosed, this);
    }

    void QueueClosed() {
      CheckOnValidSequence();
      DCHECK_GT(count_, 0);
      if (--count_ > 0) {
        return;
      }
      storage_->reset();
      Response(Status::StatusOK());
    }

    const std::vector<std::pair<Priority, StorageQueue::Options>>
        queues_options_;
    scoped_refptr<Storage>* const storage_;
    int32_t count_;
    Status final_status_;
  };

  // Asynchronously shut down.
  Start<StorageShutDownContext>(storage, std::move(done_cb));
}

void Storage::Write(Priority priority,
                    base::span<const uint8_t> data,
                    base::OnceCallback<void(Status)> completion_cb) {
  // Note: queues_ never change after initialization is finished, so there is no
  // need to protect or serialize access to it.
  auto it = queues_.find(priority);
  if (it == queues_.end()) {
    std::move(completion_cb)
        .Run(Status(error::NOT_FOUND,
                    base::StrCat({"Undefined priority=",
                                  base::NumberToString(priority)})));
    return;
  }
  it->second->Write(data, std::move(completion_cb));
}

void Storage::Confirm(Priority priority,
                      uint64_t seq_number,
                      base::OnceCallback<void(Status)> completion_cb) {
  // Note: queues_ never change after initialization is finished, so there is no
  // need to protect or serialize access to it.
  auto it = queues_.find(priority);
  if (it == queues_.end()) {
    std::move(completion_cb)
        .Run(Status(error::NOT_FOUND,
                    base::StrCat({"Undefined priority=",
                                  base::NumberToString(priority)})));
    return;
  }
  it->second->Confirm(seq_number, std::move(completion_cb));
}

}  // namespace reporting
