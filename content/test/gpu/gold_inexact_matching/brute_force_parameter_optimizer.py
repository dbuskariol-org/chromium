# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

import iterative_parameter_optimizer as iterative_optimizer
import parameter_set


class BruteForceParameterOptimizer(
    iterative_optimizer.IterativeParameterOptimizer):
  """A ParameterOptimizer for use with any number of changing parameters.

  VERY slow, but provides the most complete information when trying to
  optimize multiple parameters.
  """

  def _VerifyArgs(self):
    # range/xrange(x, y) returns the range in [x, y), so adjust by 1 to make
    # the range inclusive.
    # We go from max to min instead of min to max for this parameter, so
    # decrease the min by 1 instead of increasing the max by 1.
    self._args.min_edge_threshold -= 1
    self._args.max_max_diff += 1
    self._args.max_delta_threshold += 1

  def _RunOptimizationImpl(self):
    should_continue = True
    # Look for the minimum max_delta that results in a successful comparison
    # for each possible edge_threshold/max_diff combination.
    for edge_threshold in xrange(self._args.max_edge_threshold,
                                 self._args.min_edge_threshold,
                                 -1 * self._args.edge_threshold_step):
      for max_diff in xrange(self._args.min_max_diff, self._args.max_max_diff,
                             self._args.max_diff_step):
        for max_delta in xrange(self._args.min_delta_threshold,
                                self._args.max_delta_threshold,
                                self._args.delta_threshold_step):
          parameters = parameter_set.ParameterSet(max_diff, max_delta,
                                                  edge_threshold)
          success, _, _ = self._RunComparisonForParameters(parameters)
          if success:
            print 'Found good parameters %s' % parameters
            break
          logging.info('Found bad parameters %s', parameters)
