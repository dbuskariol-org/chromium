// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/upboarding/query_tiles/internal/tile_manager.h"

#include <utility>

#include "base/bind.h"
#include "base/run_loop.h"
#include "base/test/simple_test_clock.h"
#include "base/test/task_environment.h"
#include "chrome/browser/upboarding/query_tiles/internal/config.h"
#include "chrome/browser/upboarding/query_tiles/internal/query_tile_store.h"
#include "chrome/browser/upboarding/query_tiles/test/test_utils.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"

using testing::_;
using ::testing::Invoke;

namespace upboarding {
namespace {

class MockQueryTileStore : public Store<TileGroup> {
 public:
  MockQueryTileStore() = default;
  MOCK_METHOD1(InitAndLoad, void(QueryTileStore::LoadCallback));
  MOCK_METHOD3(Update,
               void(const std::string&,
                    const TileGroup&,
                    QueryTileStore::UpdateCallback));
  MOCK_METHOD2(Delete,
               void(const std::string&, QueryTileStore::DeleteCallback));
};

class TileManagerTest : public testing::Test {
 public:
  TileManagerTest() = default;
  ~TileManagerTest() override = default;

  TileManagerTest(const TileManagerTest& other) = delete;
  TileManagerTest& operator=(const TileManagerTest& other) = delete;

  void SetUp() override {
    auto tile_store = std::make_unique<MockQueryTileStore>();
    tile_store_ = tile_store.get();
    config_.locale = "en-US";
    config_.is_enabled = true;
    base::Time fake_now;
    EXPECT_TRUE(base::Time::FromString("03/18/20 01:00:00 AM", &fake_now));
    clock_.SetNow(fake_now);
    manager_ = TileManager::Create(std::move(tile_store), &clock_, &config_);
  }

  // Initial and load entries from store_, compare the |expected_status| to the
  // actual returned status.
  void Init(base::RepeatingClosure closure, TileGroupStatus expected_status) {
    manager()->Init(base::BindOnce(&TileManagerTest::OnInitCompleted,
                                   base::Unretained(this), std::move(closure),
                                   expected_status));
  }

  void OnInitCompleted(base::RepeatingClosure closure,
                       TileGroupStatus expected_status,
                       TileGroupStatus status) {
    EXPECT_EQ(status, expected_status);
    std::move(closure).Run();
  }

  // Run SaveTiles call from manager_, compare the |expected_status| to the
  // actual returned status.
  void SaveTiles(std::vector<std::unique_ptr<QueryTileEntry>> tiles,
                 base::RepeatingClosure closure,
                 TileGroupStatus expected_status) {
    manager()->SaveTiles(
        std::move(tiles),
        base::BindOnce(&TileManagerTest::OnTilesSaved, base::Unretained(this),
                       std::move(closure), expected_status));
  }

  void OnTilesSaved(base::RepeatingClosure closure,
                    TileGroupStatus expected_status,
                    TileGroupStatus status) {
    EXPECT_EQ(status, expected_status);
    std::move(closure).Run();
  }

  // Run GetTiles call from manager_, compare the |expected| to the actual
  // returned tiles.
  void GetTiles(std::vector<QueryTileEntry*> expected) {
    std::vector<QueryTileEntry*> actual;
    manager()->GetTiles(&actual);
    EXPECT_TRUE(test::AreTilesIdentical(expected, actual));
  }

 protected:
  TileManager* manager() { return manager_.get(); }
  MockQueryTileStore* tile_store() { return tile_store_; }
  const QueryTilesConfig& config() const { return config_; }
  const base::SimpleTestClock* clock() const { return &clock_; }

 private:
  base::test::TaskEnvironment task_environment_;
  std::unique_ptr<TileManager> manager_;
  MockQueryTileStore* tile_store_;
  QueryTilesConfig config_;
  base::SimpleTestClock clock_;
};

TEST_F(TileManagerTest, InitAndLoadWithDbOperationFailed) {
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [](base::OnceCallback<void(bool, MockQueryTileStore::KeysAndEntries)>
                 callback) {
            std::move(callback).Run(false,
                                    MockQueryTileStore::KeysAndEntries());
          }));

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kFailureDbOperation);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);
  loop.Run();
}

TEST_F(TileManagerTest, InitWithEmptyDb) {
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [](base::OnceCallback<void(bool, MockQueryTileStore::KeysAndEntries)>
                 callback) {
            std::move(callback).Run(true, MockQueryTileStore::KeysAndEntries());
          }));

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kSuccess);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);
  loop.Run();
}

TEST_F(TileManagerTest, InitAndLoadWithLocaleNotMatch) {
  auto invalid_group = std::make_unique<TileGroup>();
  test::ResetTestGroup(invalid_group.get());
  invalid_group->locale = "jp";
  MockQueryTileStore::KeysAndEntries input;
  input[invalid_group->id] = std::move(invalid_group);
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [&input](base::OnceCallback<void(
                       bool, MockQueryTileStore::KeysAndEntries)> callback) {
            std::move(callback).Run(true, std::move(input));
          }));

  EXPECT_CALL(*tile_store(), Delete(_, _));

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kInvalidGroup);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);
  loop.Run();
}

TEST_F(TileManagerTest, InitAndLoadWithExpiredGroup) {
  auto invalid_group = std::make_unique<TileGroup>();
  test::ResetTestGroup(invalid_group.get());
  invalid_group->last_updated_ts =
      clock()->Now() - base::TimeDelta::FromDays(3);
  MockQueryTileStore::KeysAndEntries input;
  input[invalid_group->id] = std::move(invalid_group);
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [&input](base::OnceCallback<void(
                       bool, MockQueryTileStore::KeysAndEntries)> callback) {
            std::move(callback).Run(true, std::move(input));
          }));

  EXPECT_CALL(*tile_store(), Delete(_, _));

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kInvalidGroup);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);
  loop.Run();
}

TEST_F(TileManagerTest, InitAndLoadSuccess) {
  auto input_group = std::make_unique<TileGroup>();
  test::ResetTestGroup(input_group.get());
  std::vector<QueryTileEntry*> expected;
  input_group->last_updated_ts =
      clock()->Now() - base::TimeDelta::FromMinutes(5);
  for (const auto& tile : input_group->tiles)
    expected.emplace_back(tile.get());

  MockQueryTileStore::KeysAndEntries input;
  input[input_group->id] = std::move(input_group);
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [&input](base::OnceCallback<void(
                       bool, MockQueryTileStore::KeysAndEntries)> callback) {
            std::move(callback).Run(true, std::move(input));
          }));

  EXPECT_CALL(*tile_store(), Delete(_, _)).Times(0);

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kSuccess);
  GetTiles(expected);
  loop.Run();
}

// Failed to init an empty db, and save tiles call failed because of db is
// uninitialized. GetTiles should return empty result.
TEST_F(TileManagerTest, SaveTilesWhenUnintialized) {
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [](base::OnceCallback<void(bool, MockQueryTileStore::KeysAndEntries)>
                 callback) {
            std::move(callback).Run(false,
                                    MockQueryTileStore::KeysAndEntries());
          }));
  EXPECT_CALL(*tile_store(), Update(_, _, _)).Times(0);
  EXPECT_CALL(*tile_store(), Delete(_, _)).Times(0);

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kFailureDbOperation);

  auto tile_to_save = std::make_unique<QueryTileEntry>();
  test::ResetTestEntry(tile_to_save.get());
  std::vector<std::unique_ptr<QueryTileEntry>> tiles_to_save;
  tiles_to_save.emplace_back(std::move(tile_to_save));

  SaveTiles(std::move(tiles_to_save), loop.QuitClosure(),
            TileGroupStatus::kUninitialized);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);

  loop.Run();
}

// Init with empty db successfully, and save tiles failed because of db
// operation failed. GetTiles should return empty result.
TEST_F(TileManagerTest, SaveTilesFailed) {
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [](base::OnceCallback<void(bool, MockQueryTileStore::KeysAndEntries)>
                 callback) {
            std::move(callback).Run(true, MockQueryTileStore::KeysAndEntries());
          }));
  EXPECT_CALL(*tile_store(), Update(_, _, _))
      .WillOnce(Invoke([](const std::string& id, const TileGroup& group,
                          MockQueryTileStore::UpdateCallback callback) {
        std::move(callback).Run(false);
      }));
  EXPECT_CALL(*tile_store(), Delete(_, _)).Times(0);

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kSuccess);

  auto tile_to_save = std::make_unique<QueryTileEntry>();
  test::ResetTestEntry(tile_to_save.get());
  std::vector<std::unique_ptr<QueryTileEntry>> tiles_to_save;
  tiles_to_save.emplace_back(std::move(tile_to_save));

  SaveTiles(std::move(tiles_to_save), loop.QuitClosure(),
            TileGroupStatus::kFailureDbOperation);
  GetTiles(std::vector<QueryTileEntry*>() /*expect an empty result*/);

  loop.Run();
}

// Init with empty db successfully, and save tiles successfully. GetTiles should
// return the recent saved tiles, and no Delete call is executed.
TEST_F(TileManagerTest, SaveTilesSuccess) {
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [](base::OnceCallback<void(bool, MockQueryTileStore::KeysAndEntries)>
                 callback) {
            std::move(callback).Run(true, MockQueryTileStore::KeysAndEntries());
          }));
  EXPECT_CALL(*tile_store(), Update(_, _, _))
      .WillOnce(Invoke([](const std::string& id, const TileGroup& group,
                          MockQueryTileStore::UpdateCallback callback) {
        std::move(callback).Run(true);
      }));
  EXPECT_CALL(*tile_store(), Delete(_, _)).Times(0);

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kSuccess);

  auto tile_to_save = std::make_unique<QueryTileEntry>();
  auto expected_tile = std::make_unique<QueryTileEntry>();
  test::ResetTestEntry(tile_to_save.get());
  test::ResetTestEntry(expected_tile.get());
  std::vector<std::unique_ptr<QueryTileEntry>> tiles_to_save;
  tiles_to_save.emplace_back(std::move(tile_to_save));
  std::vector<QueryTileEntry*> expected;
  expected.emplace_back(expected_tile.get());

  SaveTiles(std::move(tiles_to_save), loop.QuitClosure(),
            TileGroupStatus::kSuccess);
  GetTiles(std::move(expected));
  loop.Run();
}

// Init with store successfully. The store originally has entries loaded into
// memory. Save new tiles successfully. GetTiles should return the recent saved
// tiles, and Delete() call is executed in store, also replace the old group in
// memory.
TEST_F(TileManagerTest, SaveTilesAndReplaceOldGroupSuccess) {
  auto input_group = std::make_unique<TileGroup>();
  test::ResetTestGroup(input_group.get());
  input_group->last_updated_ts =
      clock()->Now() - base::TimeDelta::FromMinutes(5);

  MockQueryTileStore::KeysAndEntries input;
  input[input_group->id] = std::move(input_group);
  EXPECT_CALL(*tile_store(), InitAndLoad(_))
      .WillOnce(Invoke(
          [&input](base::OnceCallback<void(
                       bool, MockQueryTileStore::KeysAndEntries)> callback) {
            std::move(callback).Run(true, std::move(input));
          }));

  EXPECT_CALL(*tile_store(), Update(_, _, _))
      .WillOnce(Invoke([](const std::string& id, const TileGroup& group,
                          MockQueryTileStore::UpdateCallback callback) {
        std::move(callback).Run(true);
      }));

  EXPECT_CALL(*tile_store(), Delete("group_guid", _));

  base::RunLoop loop;
  Init(loop.QuitClosure(), TileGroupStatus::kSuccess);

  auto tile_to_save = std::make_unique<QueryTileEntry>();
  test::ResetTestEntry(tile_to_save.get());
  std::vector<std::unique_ptr<QueryTileEntry>> tiles_to_save;
  tiles_to_save.emplace_back(std::move(tile_to_save));

  auto expected_tile = std::make_unique<QueryTileEntry>();
  test::ResetTestEntry(expected_tile.get());
  std::vector<QueryTileEntry*> expected;
  expected.emplace_back(std::move(expected_tile.get()));

  SaveTiles(std::move(tiles_to_save), loop.QuitClosure(),
            TileGroupStatus::kSuccess);
  GetTiles(std::move(expected));
  loop.Run();
}

}  // namespace

}  // namespace upboarding
