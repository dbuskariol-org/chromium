// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/common/credential_provider/archivable_credential_store.h"

#include "base/logging.h"
#import "ios/chrome/common/credential_provider/archivable_credential.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

@interface ArchivableCredentialStore ()

// Working queue used to sync the mutable set operations.
@property(nonatomic) dispatch_queue_t workingQueue;

// The fileURL to the disk file, can be nil.
@property(nonatomic, strong) NSURL* fileURL;

// The in-memory storage.
@property(nonatomic, strong)
    NSMutableDictionary<NSString*, ArchivableCredential*>* memoryStorage;

@end

@implementation ArchivableCredentialStore

#pragma mark - Public

- (instancetype)initWithFileURL:(NSURL*)fileURL {
  self = [super init];
  if (self) {
    _fileURL = fileURL;
    _workingQueue = dispatch_queue_create(nullptr, DISPATCH_QUEUE_CONCURRENT);
  }
  return self;
}

- (void)addCredential:(ArchivableCredential*)credential {
  DCHECK(credential.recordIdentifier)
      << "credential must have a record identifier";
  dispatch_barrier_async(self.workingQueue, ^{
    DCHECK(!self.memoryStorage[credential.recordIdentifier])
        << "Credential already exists in the storage";
    self.memoryStorage[credential.recordIdentifier] = credential;
  });
}

- (void)updateCredential:(ArchivableCredential*)credential {
  [self removeCredential:credential];
  [self addCredential:credential];
}

- (void)removeCredential:(ArchivableCredential*)credential {
  DCHECK(credential.recordIdentifier)
      << "credential must have a record identifier";
  dispatch_barrier_async(self.workingQueue, ^{
    DCHECK(self.memoryStorage[credential.recordIdentifier])
        << "Credential doesn't exist in the storage";
    self.memoryStorage[credential.recordIdentifier] = nil;
  });
}

#pragma mark - CredentialStore

- (NSArray<id<Credential>>*)credentials {
  __block NSArray<id<Credential>>* credentials;
  dispatch_sync(self.workingQueue, ^{
    credentials = [self.memoryStorage allValues];
  });
  return credentials;
}

- (void)saveDataWithCompletion:(void (^)(NSError* error))completion {
  // TODO(crbug.com/1045456): Implement.
}

#pragma mark - Getters

- (NSMutableDictionary<NSString*, ArchivableCredential*>*)memoryStorage {
#if !defined(NDEBUG)
  dispatch_assert_queue(self.workingQueue);
#endif  // !defined(NDEBUG)
  if (!_memoryStorage) {
    _memoryStorage = [self loadStorage];
  }
  return _memoryStorage;
}

#pragma mark - Private

// Loads the store from disk.
- (NSMutableDictionary<NSString*, ArchivableCredential*>*)loadStorage {
  // TODO(crbug.com/1045456): Implement.
  return [[NSMutableDictionary alloc] init];
}

@end
