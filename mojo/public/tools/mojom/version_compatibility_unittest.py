# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from mojom_parser_test_case import MojomParserTestCase


class VersionCompatibilityTest(MojomParserTestCase):
  """Tests covering compatibility between two versions of the same mojom type
  definition. This coverage ensures that we can reliably detect unsafe changes
  to definitions that are expected to tolerate version skew in production
  environments."""

  def _GetTypeCompatibilityMap(self, old_mojom, new_mojom):
    """Helper to support the implementation of assertBackwardCompatible and
    assertNotBackwardCompatible."""

    old = self.ExtractTypes(old_mojom)
    new = self.ExtractTypes(new_mojom)
    self.assertEqual(set(old.keys()), set(new.keys()),
                     'Old and new test mojoms should use the same type names.')

    compatibility_map = {}
    for name in old.keys():
      compatibility_map[name] = new[name].IsBackwardCompatible(old[name])
    return compatibility_map

  def assertBackwardCompatible(self, old_mojom, new_mojom):
    compatibility_map = self._GetTypeCompatibilityMap(old_mojom, new_mojom)
    for name, compatible in compatibility_map.items():
      if not compatible:
        raise AssertionError(
            'Given the old mojom:\n\n    %s\n\nand the new mojom:\n\n    %s\n\n'
            'The new definition of %s should pass a backward-compatibiity '
            'check, but it does not.' % (old_mojom, new_mojom, name))

  def assertNotBackwardCompatible(self, old_mojom, new_mojom):
    compatibility_map = self._GetTypeCompatibilityMap(old_mojom, new_mojom)
    if all(compatibility_map.values()):
      raise AssertionError(
          'Given the old mojom:\n\n    %s\n\nand the new mojom:\n\n    %s\n\n'
          'The new mojom should fail a backward-compatibility check, but it '
          'does not.' % (old_mojom, new_mojom))

  def testNewNonExtensibleEnumValue(self):
    """Adding a value to a non-extensible enum breaks backward-compatibility."""
    self.assertNotBackwardCompatible('enum E { kFoo, kBar };',
                                     'enum E { kFoo, kBar, kBaz };')

  def testNewNonExtensibleEnumValueWithMinVersion(self):
    """Adding a value to a non-extensible enum breaks backward-compatibility,
    even with a new [MinVersion] specified for the value."""
    self.assertNotBackwardCompatible(
        'enum E { kFoo, kBar };', 'enum E { kFoo, kBar, [MinVersion=1] kBaz };')

  def testNewValueInExistingVersion(self):
    """Adding a value to an existing version is not allowed, even if the old
    enum was marked [Extensible]. Note that it is irrelevant whether or not the
    new enum is marked [Extensible]."""
    self.assertNotBackwardCompatible('[Extensible] enum E { kFoo, kBar };',
                                     'enum E { kFoo, kBar, kBaz };')
    self.assertNotBackwardCompatible(
        '[Extensible] enum E { kFoo, kBar };',
        '[Extensible] enum E { kFoo, kBar, kBaz };')
    self.assertNotBackwardCompatible(
        '[Extensible] enum E { kFoo, [MinVersion=1] kBar };',
        'enum E { kFoo, [MinVersion=1] kBar, [MinVersion=1] kBaz };')

  def testEnumValueRemoval(self):
    """Removal of an enum value is never valid even for [Extensible] enums."""
    self.assertNotBackwardCompatible('enum E { kFoo, kBar };',
                                     'enum E { kFoo };')
    self.assertNotBackwardCompatible('[Extensible] enum E { kFoo, kBar };',
                                     '[Extensible] enum E { kFoo };')
    self.assertNotBackwardCompatible(
        '[Extensible] enum E { kA, [MinVersion=1] kB };',
        '[Extensible] enum E { kA, };')
    self.assertNotBackwardCompatible(
        '[Extensible] enum E { kA, [MinVersion=1] kB, [MinVersion=1] kZ };',
        '[Extensible] enum E { kA, [MinVersion=1] kB };')

  def testNewExtensibleEnumValueWithMinVersion(self):
    """Adding a new and properly [MinVersion]'d value to an [Extensible] enum
    is a backward-compatible change. Note that it is irrelevant whether or not
    the new enum is marked [Extensible]."""
    self.assertBackwardCompatible('[Extensible] enum E { kA, kB };',
                                  'enum E { kA, kB, [MinVersion=1] kC };')
    self.assertBackwardCompatible(
        '[Extensible] enum E { kA, kB };',
        '[Extensible] enum E { kA, kB, [MinVersion=1] kC };')
    self.assertBackwardCompatible(
        '[Extensible] enum E { kA, [MinVersion=1] kB };',
        '[Extensible] enum E { kA, [MinVersion=1] kB, [MinVersion=2] kC };')

  def testRenameEnumValue(self):
    """Renaming an enum value does not affect backward-compatibility. Only
    numeric value is relevant."""
    self.assertBackwardCompatible('enum E { kA, kB };', 'enum E { kX, kY };')

  def testAddEnumValueAlias(self):
    """Adding new enum fields does not affect backward-compatibility if it does
    not introduce any new numeric values."""
    self.assertBackwardCompatible(
        'enum E { kA, kB };', 'enum E { kA, kB, kC = kA, kD = 1, kE = kD };')
