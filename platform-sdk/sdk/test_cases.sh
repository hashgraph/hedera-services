#!/usr/bin/env bash

#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

CWD=`dirname "$0"`
cd $CWD

JAVA="/usr/bin/env java"
CP="/usr/bin/env cp"
TIMEOUT="/usr/bin/env gtimeout"
RM="/usr/bin/env rm"
MKDIR="/usr/bin/env mkdir -p"

PROFILER="$CWD/profiler_cli.sh"
TEST_DATE="$(date '+%Y-%m-%d %H-%M-%S')"

echo "==============================================================================================================="
echo "Executing Test Cases"
echo "==============================================================================================================="

echo
echo "Running 2 member tests...."
echo

$PROFILER "4m" 2 1 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
$PROFILER "4m" 2 2 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
$PROFILER "4m" 2 3 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
$PROFILER "4m" 2 4 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"

#echo
#echo
#echo "Running 4 member tests...."
#echo
#
#$PROFILER "4m" 4 1 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 4 2 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 4 3 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 4 4 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#
#echo
#echo
#echo "Running 6 member tests...."
#echo
#
#$PROFILER "4m" 6 1 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 6 2 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 6 3 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 6 4 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#
#echo
#echo
#echo "Running 8 member tests...."
#echo
#
#$PROFILER "4m" 8 1 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 8 2 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 8 3 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"
#$PROFILER "4m" 8 4 "/Volumes/Data/Swirlds/Platform/Tests/Local/Optimized Immutable/pending" "$TEST_DATE"

echo
echo "==============================================================================================================="
echo "Finished Test Case Execution"
echo "==============================================================================================================="