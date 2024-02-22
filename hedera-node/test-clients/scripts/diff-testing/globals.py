from enum import Enum
from google.api_core.retry import Retry
from re import Pattern


class Streams(Enum):
    EVENTS = 1
    RECORDS = 2
    SIDECARS = 3


class Globals:
    interval_start_time_with_T: str
    interval_end_time_with_T: str
    interval_start_time_as_filename_component: str
    interval_end_time_as_filename_component: str
    interval_common_prefix: str
    n_nodes: int
    gcp_project: str
    network: str
    gcp_export_bucket: str
    stream_patterns: dict[Streams, str]
    match_node_number_re: Pattern
    mainnet_hostnames: dict[int, str]
    gcp_retry_instance: Retry

    def __str__(self):
        return f"""Derived globals:
    interval_start_time_with_T: {self.interval_start_time_with_T}
    interval_end_time_with_T: {self.interval_end_time_with_T}
    interval_start_time_as_filename_component: {self.interval_start_time_as_filename_component}
    interval_end_time_as_filename_component: {self.interval_end_time_as_filename_component}
    interval_common_prefix: {self.interval_common_prefix}
    n_nodes: {self.n_nodes}
    gcp_project: {self.gcp_project}
    network: {self.network}
    gcp_export_bucket: {self.gcp_export_bucket}
    stream_patterns: {self.stream_patterns}
"""


g: Globals = Globals()
