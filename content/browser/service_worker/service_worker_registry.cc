// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "content/browser/service_worker/service_worker_registry.h"

#include "base/memory/ptr_util.h"
#include "content/browser/service_worker/service_worker_registration.h"
#include "content/browser/service_worker/service_worker_version.h"
#include "content/common/service_worker/service_worker_utils.h"

namespace content {

ServiceWorkerRegistry::ServiceWorkerRegistry(
    const base::FilePath& user_data_directory,
    ServiceWorkerContextCore* context,
    scoped_refptr<base::SequencedTaskRunner> database_task_runner,
    storage::QuotaManagerProxy* quota_manager_proxy,
    storage::SpecialStoragePolicy* special_storage_policy)
    : storage_(ServiceWorkerStorage::Create(user_data_directory,
                                            context,
                                            std::move(database_task_runner),
                                            quota_manager_proxy,
                                            special_storage_policy,
                                            this)) {}

ServiceWorkerRegistry::ServiceWorkerRegistry(
    ServiceWorkerContextCore* context,
    ServiceWorkerRegistry* old_registry)
    : storage_(ServiceWorkerStorage::Create(context,
                                            old_registry->storage(),
                                            this)) {}

ServiceWorkerRegistry::~ServiceWorkerRegistry() = default;

void ServiceWorkerRegistry::FindRegistrationForClientUrl(
    const GURL& client_url,
    FindRegistrationCallback callback) {
  storage()->FindRegistrationForClientUrl(client_url, std::move(callback));
}

void ServiceWorkerRegistry::FindRegistrationForScope(
    const GURL& scope,
    FindRegistrationCallback callback) {
  storage()->FindRegistrationForScope(scope, std::move(callback));
}

void ServiceWorkerRegistry::FindRegistrationForId(
    int64_t registration_id,
    const GURL& origin,
    FindRegistrationCallback callback) {
  storage()->FindRegistrationForId(registration_id, origin,
                                   std::move(callback));
}

void ServiceWorkerRegistry::FindRegistrationForIdOnly(
    int64_t registration_id,
    FindRegistrationCallback callback) {
  storage()->FindRegistrationForIdOnly(registration_id, std::move(callback));
}

ServiceWorkerRegistration* ServiceWorkerRegistry::GetUninstallingRegistration(
    const GURL& scope) {
  // TODO(bashi): Should we check state of ServiceWorkerStorage?
  for (const auto& registration : uninstalling_registrations_) {
    if (registration.second->scope() == scope) {
      DCHECK(registration.second->is_uninstalling());
      return registration.second.get();
    }
  }
  return nullptr;
}

void ServiceWorkerRegistry::NotifyInstallingRegistration(
    ServiceWorkerRegistration* registration) {
  DCHECK(installing_registrations_.find(registration->id()) ==
         installing_registrations_.end());
  installing_registrations_[registration->id()] = registration;
}

void ServiceWorkerRegistry::NotifyDoneInstallingRegistration(
    ServiceWorkerRegistration* registration,
    ServiceWorkerVersion* version,
    blink::ServiceWorkerStatusCode status) {
  installing_registrations_.erase(registration->id());
  if (status != blink::ServiceWorkerStatusCode::kOk && version) {
    ResourceList resources;
    version->script_cache_map()->GetResources(&resources);

    std::set<int64_t> resource_ids;
    for (const auto& resource : resources)
      resource_ids.insert(resource.resource_id);
    storage()->DoomUncommittedResources(resource_ids);
  }
}

void ServiceWorkerRegistry::NotifyDoneUninstallingRegistration(
    ServiceWorkerRegistration* registration,
    ServiceWorkerRegistration::Status new_status) {
  registration->SetStatus(new_status);
  uninstalling_registrations_.erase(registration->id());
}

ServiceWorkerRegistration*
ServiceWorkerRegistry::FindInstallingRegistrationForClientUrl(
    const GURL& client_url) {
  DCHECK(!client_url.has_ref());

  LongestScopeMatcher matcher(client_url);
  ServiceWorkerRegistration* match = nullptr;

  // TODO(nhiroki): This searches over installing registrations linearly and it
  // couldn't be scalable. Maybe the regs should be partitioned by origin.
  for (const auto& registration : installing_registrations_)
    if (matcher.MatchLongest(registration.second->scope()))
      match = registration.second.get();
  return match;
}

ServiceWorkerRegistration*
ServiceWorkerRegistry::FindInstallingRegistrationForScope(const GURL& scope) {
  for (const auto& registration : installing_registrations_)
    if (registration.second->scope() == scope)
      return registration.second.get();
  return nullptr;
}

ServiceWorkerRegistration*
ServiceWorkerRegistry::FindInstallingRegistrationForId(
    int64_t registration_id) {
  RegistrationRefsById::const_iterator found =
      installing_registrations_.find(registration_id);
  if (found == installing_registrations_.end())
    return nullptr;
  return found->second.get();
}

}  // namespace content
