// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/common/credential_provider/archivable_credential_store.h"

#import "base/test/ios/wait_util.h"
#import "ios/chrome/common/credential_provider/archivable_credential.h"
#include "testing/gtest_mac.h"
#include "testing/platform_test.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

using ArchivableCredentialStoreTest = PlatformTest;

NSURL* testStorageFileURL() {
  return nil;
}

ArchivableCredential* TestCredential() {
  return [[ArchivableCredential alloc] initWithFavicon:@"favicon"
                                    keychainIdentifier:@"keychainIdentifier"
                                                  rank:5
                                      recordIdentifier:@"recordIdentifier"
                                     serviceIdentifier:@"serviceIdentifier"
                                           serviceName:@"serviceName"
                                                  user:@"user"
                                  validationIdentifier:@"validationIdentifier"];
}

// Tests that an ArchivableCredentialStore can be created.
TEST_F(ArchivableCredentialStoreTest, create) {
  ArchivableCredentialStore* credentialStore =
      [[ArchivableCredentialStore alloc] initWithFileURL:testStorageFileURL()];
  EXPECT_TRUE(credentialStore);
  EXPECT_TRUE(credentialStore.credentials);
}

// Tests that an ArchivableCredentialStore can add a credential.
TEST_F(ArchivableCredentialStoreTest, add) {
  ArchivableCredentialStore* credentialStore =
      [[ArchivableCredentialStore alloc] initWithFileURL:testStorageFileURL()];
  EXPECT_TRUE(credentialStore);
  [credentialStore addCredential:TestCredential()];
  EXPECT_EQ(1u, credentialStore.credentials.count);
}

// Tests that an ArchivableCredentialStore can update a credential.
TEST_F(ArchivableCredentialStoreTest, update) {
  ArchivableCredentialStore* credentialStore =
      [[ArchivableCredentialStore alloc] initWithFileURL:testStorageFileURL()];
  EXPECT_TRUE(credentialStore);
  ArchivableCredential* credential = TestCredential();
  [credentialStore addCredential:credential];
  EXPECT_EQ(1u, credentialStore.credentials.count);

  ArchivableCredential* updatedCredential = [[ArchivableCredential alloc]
           initWithFavicon:@"other_favicon"
        keychainIdentifier:@"other_keychainIdentifier"
                      rank:credential.rank + 10
          recordIdentifier:@"recordIdentifier"
         serviceIdentifier:@"other_serviceIdentifier"
               serviceName:@"other_serviceName"
                      user:@"other_user"
      validationIdentifier:@"other_validationIdentifier"];

  [credentialStore updateCredential:updatedCredential];
  EXPECT_EQ(1u, credentialStore.credentials.count);
  EXPECT_EQ(updatedCredential.rank,
            credentialStore.credentials.firstObject.rank);
}

// Tests that an ArchivableCredentialStore can remove a credential.
TEST_F(ArchivableCredentialStoreTest, remove) {
  ArchivableCredentialStore* credentialStore =
      [[ArchivableCredentialStore alloc] initWithFileURL:testStorageFileURL()];
  EXPECT_TRUE(credentialStore);
  ArchivableCredential* credential = TestCredential();
  [credentialStore addCredential:credential];
  EXPECT_EQ(1u, credentialStore.credentials.count);

  [credentialStore removeCredential:credential];
  EXPECT_EQ(0u, credentialStore.credentials.count);
}

}
