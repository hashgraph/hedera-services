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

import os
import re
import warnings

from google.cloud import storage
from google.cloud.storage import transfer_manager
from google.cloud.storage.retry import DEFAULT_RETRY

from configuration import Configuration as C
from globals import g
from utils import split_list


def init_gcp_and_get_streams_bucket():
    """Initialize gcp library and return the interesting gcp bucket"""
    g.gcp_retry_instance = DEFAULT_RETRY.with_delay(initial=C.GCP_RETRY_INITIAL_WAIT_TIME,
                                                    multiplier=C.GCP_RETRY_WAIT_TIME_MULTIPLIER,
                                                    maximum=C.GCP_RETRY_MAXIMUM_WAIT_TIME)

    print(f"Getting gcp bucket {g.gcp_export_bucket}")

    gcp_storage = storage.Client(project=g.gcp_project)
    bucket = gcp_storage.bucket(g.gcp_export_bucket)  # this returns a Bucket you can access blobs with
    return gcp_storage, bucket


# Fetch file list

def get_blobs_in_bucket(bucket, blob_glob, **kwargs):
    """Given a blob glob - and optional lower and high blob names (w.r.t. timestamp) return all gcp Blobs that match"""
    unambiguous_prefix = blob_glob.split('*')[0]
    first_and_last_names = kwargs.get('first_and_last_names')
    if first_and_last_names is None:
        blobs = bucket.list_blobs(prefix=unambiguous_prefix, delimiter='/', match_glob=blob_glob,
                                  timeout=C.GCP_SERVER_TIMEOUT, retry=g.gcp_retry_instance,
                                  max_results=100000)
    else:
        blobs = bucket.list_blobs(prefix=unambiguous_prefix, delimiter='/', match_glob=blob_glob,
                                  start_offset=first_and_last_names[0], end_offset=first_and_last_names[1],
                                  timeout=C.GCP_SERVER_TIMEOUT, retry=g.gcp_retry_instance,
                                  max_results=100000)
    return [b for b in blobs]


# Fetch file content

def download_blobs(bucket, blobs_to_get, actually_present_blobs, a):
    blob_list = sorted(blobs_to_get, key=lambda b: b.name)
    # get batches
    print(f"download_blobs: a = {a}")
    blob_batches = [bb for bb in split_list(a.n_batch_size, blob_list)]

    n_attempted = 0
    n_succeeded = 0
    n_failed = 0
    n_skipped_because_present = len(actually_present_blobs)

    n_batch = 0
    for batch in blob_batches:
        print(f"Download batch {n_batch} of {len(blob_batches)} ({a.n_batch_size} at a time)")

        # Need to munge the _destination_ names so they do _not_ include the node number
        blob_file_pairs = [(bucket.blob(b.name),
                            os.path.join(a.local_storage_rootdir, strip_blob_node_number(b.name)))
                           for b in batch]
        for blob_file_pair in blob_file_pairs:
            directory, _ = os.path.split(blob_file_pair[1])
            os.makedirs(directory, exist_ok=True)

        batch_results = (
            transfer_manager.download_many(blob_file_pairs,
                                           worker_type=a.concurrent_type,
                                           max_workers=a.n_concurrent,
                                           skip_if_exists=True,
                                           download_kwargs={'raw_download': C.USE_RAW_DOWNLOADS}))

        n_attempted += len(batch)
        for blob, result in zip(batch, batch_results):
            if isinstance(result, Exception):
                n_failed += 1
                print(f"*** failed to download {blob.name} due to {result}")
                blob_path = f"{a.local_storage_rootdir}/{blob.name}"
                if os.path.exists(blob_path):
                    os.remove(blob_path)  # gcp leaves partial files behind
            else:  # success
                n_succeeded += 1
                blob_index = blob_list.index(blob)  # it's gotta be in there
                blob_list[blob_index].have = True
        n_batch += 1

    return (blob_list,
            {'n_attempted': n_attempted, 'n_succeeded': n_succeeded, 'n_failed': n_failed,
             'n_skipped_because_present': n_skipped_because_present, 'n_batches': n_batch})


# Making glob names and blobs from patterns, stream kind, timestamp prefix, interval start and end times, as needed

def get_blob_glob(stream, node_number, timestamp_prefix):
    """Return the blob glob (path in bucket) given the stream kind, the node number, and the timestamp _prefix_

    This glob, passed to gcp, will return _all_ files of that stream and node and with that timestamp prefix.  This
    will probably be more files than you want as the timestamp prefix is more general than the interval specified.
    """
    bodged_node_number = node_number + 3  # don't know why but node#s are bumped by 3 in filenames in gcp
    pattern = g.stream_patterns[stream]
    print(f"get_blob_format - pattern:  {pattern}")
    filled_pattern = pattern.format(bodged_node_number, timestamp_prefix)
    print(f"                - expanded: {filled_pattern}")
    return filled_pattern


def get_interval_names_from_interval_and_pattern(stream, node_number):
    """Return the blob path (in bucket) of the earliest and latest stream file from given node in the interval"""
    bodged_node_number = node_number + 3
    pattern = g.stream_patterns[stream]
    print(f"get_interval_names_from_interval - pattern:    {pattern}")
    first_name = pattern.format(bodged_node_number, g.interval_start_time_with_T + ' ')
    last_name = pattern.format(bodged_node_number, g.interval_end_time_with_T + '~')
    print(f"                                 - first_name: {first_name}")
    print(f"                                 - last_name:  {last_name}")
    return first_name, last_name


# Reformat the blob filename for various reasons - but for one thing, we frequently want the blob filename _without_
# the (gcp supplied) "file generation number"

def split_blob_id(id):
    with_generation_stripped = id.rpartition('/')[0]
    timestamp_extracted = re.search('\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}[.]\\d*', with_generation_stripped,
                                    flags=0)
    return with_generation_stripped, timestamp_extracted.group()


def format_blob_id(id):
    """Format the blob filename as it's path followed by its timestamp"""
    path, timestamp = split_blob_id(id)
    return f"{path} (@{timestamp})"


def get_blob_filename(id):
    """Return the blob filename with bucket and generation# stripped"""
    with_generation_stripped, _ = split_blob_id(id)
    with_bucket_stripped = with_generation_stripped.removeprefix(g.gcp_export_bucket + '/')
    return with_bucket_stripped


def strip_blob_node_number(id):
    return re.sub(g.match_node_number_re, '', id)


# This is to suppress the warning that the crc32c module can't load the native crc implementation.  Unfortunately
# this message only works if the worker type is thread.  If process, the subprocesses doing the downloads don't have
# this setting transmitted to them.
# * Also I don't know why the native crc32c implementation doesn't load on my laptop.  It's not really time critical
#   though, so IDC
warnings.filterwarnings('ignore', message="As the c extension couldn't be imported", category=RuntimeWarning)
