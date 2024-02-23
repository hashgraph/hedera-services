# Copyright (C) 2024 Hedera Hashgraph, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

def naiveify(dt):
    """
    Takes a datetime object and strips its timezone (works best if it is a UTC datetime, but doesn't check that)
    :param dt: a datetime with a timezone (preferably UTC)
    :return: same datetime but "naive" (without timezone)
    """
    return dt.replace(tzinfo=None)


def set_to_csv(s):
    return ', '.join( [str(e) for e in sorted(s)])


def split_list(batch_size, a):
    """Split a list into batches of the given size (last batch may be short)"""
    for i in range(0, len(a), batch_size):
        yield a[i:i+batch_size]
