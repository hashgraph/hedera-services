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

from datetime import datetime
from typing import Literal, Optional

from tap import Tap

from configuration import Configuration as C


class Args(Tap):
    action: Literal['get_blob_names',
                    'reget_blob_names',
                    'download_blobs',
                    'load_bloblist']  # action (command) to perform
    local_storage_rootdir: str  # root directory where all files should be stored
    bloblist_filename: str  # filename (rel. to root) for the blob list and blobs status
    hostnamemap_filename: str  # filename (rel. to root) for the node# <==> hostname map
    interval_start_time: datetime  # start of the data-pull interval
    interval_end_time: datetime  # end of hte data-pull interval
    node: int  # node to get blobs from
    n_batch_size: int  # number of blobs to download in a batch
    concurrent_type: Literal['thread', 'process']
    n_concurrent: int  # number of processes to use for downloads

    def configure(self):
        self.add_argument('action')
        self.add_argument('-r', '-root', '--local-storage-rootdir', default=C.LOCAL_STORAGE_ROOT)
        self.add_argument('-b', '-bloblist', '--bloblist-filename', default=C.MAINNET_BLOBLIST_FILENAME)
        self.add_argument('-m', '-hosts', '--hostnamemap-filename', default=C.MAINNET_HOSTNAMES_FILENAME)
        self.add_argument('-s', '-start', '--interval-start-time', default=C.INTERVAL_START_TIME,
                          type=Args.to_datetime)
        self.add_argument('-e', '-end', '--interval-end-time', default=C.INTERVAL_END_TIME,
                          type=Args.to_datetime)
        self.add_argument('-n', '-node', '--node', default=C.DEFAULT_NODE_NUMBER)
        self.add_argument('-t', '-batch', '--n_batch-size', default=C.DOWNLOAD_BATCH_SIZE)
        self.add_argument('-w', '-worker', '--concurrent_type', default=C.CONCURRENT_TYPE)
        self.add_argument('-c', '-concurrency', '--n_concurrent', default=C.CONCURRENT_DOWNLOADS)

    @staticmethod
    def to_datetime(s: str):
        return datetime.fromisoformat(s)

    def process_arguments(self):
        if isinstance(self.interval_start_time, str): self.interval_start_time = datetime.fromisoformat(self.interval_start_time)
        if isinstance(self.interval_end_time, str): self.interval_end_time = datetime.fromisoformat(self.interval_end_time)

    @staticmethod
    def parse():
        return Args().parse_args()

    def __str__(self):
        return f"""Command arguments:
    action:     {self.action}
    root:       {self.local_storage_rootdir}
    bloblist:   {self.bloblist_filename}
    hosts:      {self.hostnamemap_filename}
    start:      {self.interval_start_time}
    end:        {self.interval_end_time}
    node:       {self.node}
    batch size: {self.n_batch_size}
    worker type:{self.concurrent_type}
    #workers:   {self.n_concurrent}
"""


a: Optional[Args] = None
