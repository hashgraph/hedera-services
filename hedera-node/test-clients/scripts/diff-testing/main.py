#!/usr/bin/env python3

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

""" Fetch record streams, event streams, and state files for Hedera mainnet """

import copy

from bloblist import Blob, BlobList
from cli_arguments import Args, a
from gcp_access import *
from globals import Streams, g
from utils import *


# Configuration setup functions

def set_derived_configuration():
    """Set configuration settings that are derived, by some formula, from other configuration settings"""

    g.interval_start_time_with_T = a.interval_start_time.isoformat('T', 'seconds')
    g.interval_end_time_with_T = a.interval_end_time.isoformat('T', 'seconds')

    g.interval_start_time_as_filename_component = g.interval_start_time_with_T.replace(':', '_')
    g.interval_end_time_as_filename_component = g.interval_end_time_with_T.replace(':', '_')
    g.interval_common_prefix = os.path.commonprefix(
        [g.interval_start_time_as_filename_component, g.interval_end_time_as_filename_component])

    if not g.interval_common_prefix:
        print(f"*** Possible problem: interval_comon_prefix has 0-length; will match all timestamps, no filtering")


def set_fixed_configuration():
    """Set configuration settings that are so fixed you can just put them in the code"""
    g.n_nodes = C.N_NODES


def set_default_paths_and_patterns():
    """Set default paths and path patterns (path patterns: GCP patterns for Hedera stream files)"""
    g.gcp_project = C.GCP_PROJECT
    g.network = C.NETWORK
    g.gcp_export_bucket = C.GCP_EXPORT_BUCKET

    # each pattern has two arguments in this order: ( node_number, timestamp_common_prefix )
    g.stream_patterns = {Streams.EVENTS: 'eventsStreams/events_0.0.{}/{}*.evts',
                         Streams.RECORDS: 'recordstreams/record0.0.{}/{}*.rcd.gz',
                         Streams.SIDECARS: 'recordstreams/record0.0.{}/sidecar/{}*.rcd.gz'}

    g.match_node_number_re = re.compile(r'_?0[.]0[.][0-9]{1,2}')


def read_mainnet_hostnames():
    """Read the map from node# to hostnames, from a file"""
    g.mainnet_hostnames_path = f"{a.local_storage_rootdir}/{a.hostnamemap_filename}"
    f = open(g.mainnet_hostnames_path, 'r')
    lines = f.readlines()
    lines = [line.removeprefix('node') for line in lines]
    lines = [line.split() for line in lines]
    lines = [(int(line[0]), line[1]) for line in lines]
    g.mainnet_hostnames = dict(lines)


def get_ready_to_go():
    """Get all configuration settings, or provide defaults"""
    set_derived_configuration()
    set_fixed_configuration()
    set_default_paths_and_patterns()
    read_mainnet_hostnames()


# node# <=> hostname mapping functions

def get_name_of_node(node_number):
    return g.mainnet_hostnames[node_number]


def get_number_of_node(node_name):
    """Given a host's name, return the number of its node.

    In this script - throughout, not just here - a node is always identified by its number (0..29/30),
    whether it is called a number or an id or whatever ..."""
    return next(k for k, v in g.mainnet_hostnames.items() if v == node_name)


# Handle lists of available blobs in gcp

def get_blobs_for_stream(bucket, stream):
    """Return names of all blobs (as gcp Blobs) in the bucket for the given stream that are in the desired interval"""
    blob_glob = get_blob_glob(stream, a.node, g.interval_common_prefix)
    first_and_last_names = get_interval_names_from_interval_and_pattern(stream, a.node)

    print(f"getting blobs for {stream} with glob {blob_glob}")
    all_prefixed_blobs = get_blobs_in_bucket(bucket, blob_glob, first_and_last_names=first_and_last_names)
    print(f"found {len(all_prefixed_blobs)} matching blobs")
    print(f"   first: {format_blob_id(all_prefixed_blobs[0].id)}")
    print(f"   last:  {format_blob_id(all_prefixed_blobs[-1].id)}")

    return all_prefixed_blobs


def map_gcp_blob_to_blob(stream_name, blobs):
    """"Return list of (local) Blobs from list of gcp Blobs"""
    return [Blob(type=stream_name, name=get_blob_filename(b.id), have=False, node=a.node, size=b.size,
                 crc32c=b.crc32c, md5hash=b.md5_hash) for b in blobs]


def get_blob_names_in_interval(bucket):
    """Return, from gcp, all the names of all the stream files (blobs) in the time interval specified"""
    print(f"do_get_blobs_json:")
    print(f"interval_start_time: {g.interval_start_time_with_T}")
    print(f"interval_end_time:   {g.interval_end_time_with_T}")

    event_blobs = map_gcp_blob_to_blob("event", get_blobs_for_stream(bucket, Streams.EVENTS))
    record_blobs = map_gcp_blob_to_blob("record", get_blobs_for_stream(bucket, Streams.RECORDS))
    sidecar_blobs = map_gcp_blob_to_blob("sidecar", get_blobs_for_stream(bucket, Streams.SIDECARS))

    all_blobs = event_blobs + record_blobs + sidecar_blobs

    print(f"Have {len(all_blobs)} blobs ({len(event_blobs)} events, " +
          f"{len(record_blobs)} records, {len(sidecar_blobs)} sidecars)")

    return all_blobs


def get_blob_names(bucket):
    all_blobs = get_blob_names_in_interval(bucket)
    blob_list = BlobList(interval_start_time=a.interval_start_time,
                         interval_end_time=a.interval_end_time,
                         nodes={a.node},
                         blobs=all_blobs)
    print()
    return blob_list


def load_bloblist(blob_list_path):
    blob_list = BlobList.load(blob_list_path)
    print(f"loaded {len(blob_list.blobs)} blobs " +
          f"from {blob_list.interval_start_time} to {blob_list.interval_end_time}, " +
          f"nodes: {set_to_csv(blob_list.nodes)}")
    return blob_list


def merge_blob_lists(base_blob_list, new_blob_list):
    """Merge new_blob_list into base_blob_list"""
    print(f"merge_blob_lists: base has {len(base_blob_list)} blobs from nodes {set_to_csv(base_blob_list.nodes)}, " +
          f"new has {len(new_blob_list)} from node {set_to_csv(new_blob_list.nodes)}")

    base_blob_names = {strip_blob_node_number(b.name) for b in base_blob_list.blobs}

    merged = copy.deepcopy(base_blob_list)

    n_merged = 0
    for b in new_blob_list.blobs:
        if not strip_blob_node_number(b.name) in base_blob_names:
            n_merged += 1
            merged.blobs.append(copy.copy(b))

    print(f"merged {n_merged} new blobs")
    return merged


def get_blob_path(filename):
    return os.path.join(a.local_storage_rootdir, strip_blob_node_number(filename))


def main():
    global a
    a = Args.parse()
    print(a)

    get_ready_to_go()

    print(g)

    gcp_storage, bucket = init_gcp_and_get_streams_bucket()

    blob_list_path = f"{a.local_storage_rootdir}/{a.bloblist_filename}"

    match a.action:
        case 'get_blob_names':
            blob_list = get_blob_names(bucket)
            BlobList.save(blob_list_path, blob_list)

        case 'load_bloblist':
            blob_list = load_bloblist(blob_list_path)
            print(f"as object: \n", blob_list)

        case 'reget_blob_names':
            # first load bloblist
            original_blob_list = load_bloblist(blob_list_path)

            # then get blob names for given node
            new_blob_list = get_blob_names(bucket)

            # then _merge_ by adding newly fetched names that were missing in the loaded bloblist
            merged_blob_list = merge_blob_lists(original_blob_list, new_blob_list)

            # then write the bloblist again (keeping old version by renaming it)
            BlobList.save(blob_list_path, merged_blob_list)

        case 'download_blobs':
            # get the list of blobs we want
            blob_list = load_bloblist(blob_list_path)
            print(f"blob list has {len(blob_list.blobs)} blobs")

            # get missing blobs
            dont_have_blobs = {b for b in blob_list.blobs if not b.have}
            print(f"{len(dont_have_blobs)} missing blobs, of {len(blob_list.blobs)}")

            # see if any of those blobs actually are present on disk
            actually_present_blobs = {b for b in dont_have_blobs if os.path.isfile(get_blob_path(b.name))}
            # check the actually present files were fully downloaded (ideally we'd check the CRC/MD5 instead)
            mismatched_size_blobs = {b for b in actually_present_blobs
                                     if os.path.getsize(get_blob_path(b.name) != b.size)}
            if len(actually_present_blobs) > 0:
                newline_and_indent = '\n   '
                print(f"{len(actually_present_blobs)} are marked 'have=False' yet exist on disk:" +
                      f"{newline_and_indent.join([b.name for b in actually_present_blobs])}")
                if len(mismatched_size_blobs) > 0:
                    print(f"{len(mismatched_size_blobs)}, marked 'have=False', exist on disk but have the wrong size")
                    # If mismatched remove the file and try again (maybe script crashed previously, or fell to ^C)
                    for blob in mismatched_size_blobs:
                        os.remove(get_blob_path(blob.name))

            print(f"batch size: {a.n_batch_size}")
            result_blob_list, counters = download_blobs(bucket, dont_have_blobs, actually_present_blobs, a)

            BlobList.save(blob_list_path, blob_list)

            print(f"""Downloaded {counters['n_batches']} batches:
    {counters['n_attempted']} blobs attempted
    {counters['n_skipped_because_present']} blobs skipped because they were already present
    {counters['n_succeeded']} blobs download succeeded
    {counters['n_failed']} blobs failed to download""")


if __name__ == "__main__":
    main()
