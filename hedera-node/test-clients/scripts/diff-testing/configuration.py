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

from dataclasses import dataclass
from datetime import datetime


@dataclass
class Configuration:
    # paths and filenames
    LOCAL_STORAGE_ROOT: str = '/users/user/new-state/'  # where the states and streams will be stored
    MAINNET_HOSTNAMES_FILENAME: str = 'mainnet-hostnames-2024-02-04.txt'  # file containing node# <-> hostname map
    MAINNET_BLOBLIST_FILENAME: str = 'bloblist.txt'  # file for ongoing state (blobs to fetch, blobs fetched, etc.)

    # interval to pull - start and end must _not_ cross 00:00Z (i.e., must be on same GMT day, except can have 00:00Z
    # at either end)
    INTERVAL_START_TIME: datetime = datetime.fromisoformat('2024-02-01T00:00:00')
    INTERVAL_END_TIME: datetime = datetime.fromisoformat('2024-02-01T03:00:00')  # '2024-02-02T00:00:00')

    # nodes
    N_NODES: int = 29
    DEFAULT_NODE_NUMBER: int = 5

    # gcp buckets
    GCP_PROJECT: str = 'mainnet-exports'
    NETWORK: str = 'mainnet'
    GCP_EXPORT_BUCKET: str = 'hedera-mainnet-streams'

    # gcp download parameters
    USE_RAW_DOWNLOADS: bool = False
    DOWNLOAD_BATCH_SIZE: int = 100
    CONCURRENT_TYPE: str = 'thread'
    CONCURRENT_DOWNLOADS: int = 5

    # gcp networking parameters
    GCP_SERVER_TIMEOUT: float = 180.0
    GCP_RETRY_INITIAL_WAIT_TIME: float = 1.5
    GCP_RETRY_WAIT_TIME_MULTIPLIER: float = 1.5
    GCP_RETRY_MAXIMUM_WAIT_TIME: float = 100.0
