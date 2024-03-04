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

from __future__ import annotations

import copy
from dataclasses import dataclass, replace as clone
from datetime import datetime
from typing import List, Set, Optional

import jsons

from utils import *
from versioned_output_file import VersionedOutputFile


@dataclass
class Blob:
    name: str
    type: str
    have: bool
    node: int
    crc32c: str  # base64, big-endian
    md5hash: str  # base64, big-endian (presumably)
    size: int

    def __copy__(self):
        return clone(self)

    def __hash__(self):
        """For these (mutable) objects, the 'primary key" is the type+name"""
        return hash((self.name, self.type))


@dataclass
class BlobList:
    interval_start_time: datetime
    interval_end_time: datetime
    nodes: Set[int]
    blobs: List[Blob]

    def __len__(self):
        return len(self.blobs)

    def __copy__(self):
        return clone(self)

    def __deepcopy__(self, memo):
        bl = clone(self)
        bl.nodes = bl.nodes.copy()
        bl.blobs = [copy.copy(b) for b in bl.blobs]
        return bl

    @staticmethod
    def save(path: str, bloblist: BlobList) -> None:
        contents = jsons.dumps(bloblist, ensure_ascii=False, jdkwargs={"indent": 4})
        with VersionedOutputFile(path) as f:
            f.write(contents)

    @staticmethod
    def load(filename: str) -> Optional[BlobList]:
        try:
            with open(filename, 'r', encoding='utf-8') as file:
                contents = file.read()
        except IOError as ex:
            print(f"*** BlobList.load({filename}): IOError: {ex}")
            contents = None
        bloblist = jsons.loads(contents, BlobList) if contents is not None else None

        # Our use of datetime.fromisoformat needs naive datetimes (without timezone) that json package puts there
        bloblist.interval_start_time = naiveify(bloblist.interval_start_time)
        bloblist.interval_end_time = naiveify(bloblist.interval_end_time)
        return bloblist


# For various reasons related to formatting blob names we use naive datetime - without timezones - so we must tell
# jsons library not to complain about that
jsons.suppress_warning('datetime-without-tz')
