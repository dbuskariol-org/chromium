#!/bin/bash
#
# Copyright 2018 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#

# Download the CL and prepare a local commit that's ready to be pushed.
# The author of the CL remains in the git author field in the local commit,
# and the git committer field contains whoever ran this script. Both the
# author and committer time stamps are updated to now, to better match the
# behavior of Chromium CQ.
#
# The final push should be done manually after potential conflict
# resolution, and local verification.

set -e

readonly REF_PATH='refs/wip/yusufo/mtm-exp'
readonly EXP_REPO='https://chromium.googlesource.com/experimental/chromium/src'

cl_to_commit() {
  readonly CL_URL="${1}" && shift

  # Make a throw-away random branch name.
  readonly BRANCH="mtm-exp-tmp-branch-for-landing-cl-$$"

  if [[ "${CL_URL}" == "" ]]; then
    echo "Usage: ${0} <Gerrit-URL-of-CL>"
    exit 1
  fi

  if ! git diff-files --quiet --ignore-submodules -- ; then
    echo "[ERROR] Please commit or stash local changes first."
    exit 1
  fi

  if [[ "$(git remote get-url exp)" != "${EXP_REPO}" ]]; then
    echo "[ERROR] You need to run \"git remote add exp ${EXP_REPO}\" first."
    exit 1
  fi

  git fetch exp "${REF_PATH}:remotes/exp/${REF_PATH}"
  echo "[INFO] Fetched ${REF_PATH}."

  git new-branch --upstream "exp/${REF_PATH}" "${BRANCH}"
  git cl patch --force "${CL_URL}"
  echo "[INFO] Fetched ${CL_URL} into branch ${BRANCH}."

  # This might return non-zero, when there are conflicts.
  git rebase -q \
    || echo '[ERROR] Please ask the CL author to rebase and resolve conflicts.'
  echo "[INFO] Rebased ${BRANCH} on top of ${REF_PATH}."

  (
    git log --format=%B -n1
    echo "Reviewed-on: ${CL_URL}"
  ) | git commit --amend --date now -F - -q
  echo '[INFO] Appended CL info to the commit message.'

  echo '[INFO] Please locally verify the CL works, and then push it by running:'
  echo "> git push exp HEAD:${REF_PATH}"
}

cl_to_commit "${1}"
