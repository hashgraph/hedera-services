#!/usr/bin/env python3

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

# A log piped into this script will be printed to the console with extra colors.

from sys import stdin
import re

# The format of the lines colorized by this script is as follows. Any lines not conforming to this format
# are ignored and forwarded to the standard out without any added colorization.
#
# 2023-05-18 09:03:03.618 80       INFO  PLATFORM_STATUS  <main> SwirldsPlatform: Platform status changed to...
#         |               |          |           |           |           |                        |
#      timestamp          |          |        marker         |           |                  arbitrary string
#                     log number     |                  thread name      |
#                                    |                                class name
#                                 log level

whitespace_regex = '\s+'
captured_whitespace_regex = '(\s+)'
timestamp_regex = '(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)'
log_number_regex = '(\d+)'
log_level_regex = '(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)'
marker_regex = '([A-Za-z0-9_]+)'
thread_name_regex = '(<<?[^>]+>>?)'
class_thread_name_regex = '([A-Za-z0-9_]+)'
remainder_of_line_regex = '(.*)'

full_regex = timestamp_regex + whitespace_regex + \
             log_number_regex + captured_whitespace_regex + \
             log_level_regex + captured_whitespace_regex + \
             marker_regex + captured_whitespace_regex + \
             thread_name_regex + whitespace_regex + \
             class_thread_name_regex + remainder_of_line_regex

regex = re.compile(full_regex)

RED = '\033[91m'
TEAL = '\033[96m'
YELLOW = '\033[93m'
GREEN = '\033[92m'
BRIGHT_BLUE = '\033[94m'
GRAY = '\033[90m'
PURPLE = '\033[95m'
WHITE = '\033[37m'
BRIGHT_WHITE = '\033[97m'
END = '\033[0m'

def format_timestamp(timestamp):
  return GRAY + timestamp + END

def format_log_number(log_number):
  return WHITE + log_number + END

def format_log_level(log_level):
    if log_level in ('TRACE', 'DEBUG', 'INFO'):
      return GREEN + log_level + END
    elif log_level == 'WARN':
      return YELLOW + log_level + END
    else:
      return RED + log_level + END

def format_marker(marker):
  return BRIGHT_BLUE + marker + END

def format_thread_name(thread_name):
  return BRIGHT_WHITE + thread_name + END

def format_class_name(class_name):
  return TEAL + class_name + END

def format(line):
  match = regex.match(line)
  if match is None:
    return line

  index = 1

  timestamp = match.group(index)
  index += 1

  log_number = match.group(index)
  index += 1

  log_number_whitespace = match.group(index)
  index += 1

  log_level = match.group(index)
  index += 1

  log_level_whitespace = match.group(index)
  index += 1

  marker = match.group(index)
  index += 1

  marker_whitespace = match.group(index)
  index += 1

  thread_name = match.group(index)
  index += 1

  class_name = match.group(index)
  index += 1

  remainder = match.group(index)
  index += 1

  return format_timestamp(timestamp) + ' ' + \
          format_log_number(log_number) + log_number_whitespace + \
          format_log_level(log_level) + log_level_whitespace + \
          format_marker(marker) + marker_whitespace + \
          format_thread_name(thread_name) + ' ' + \
          format_class_name(class_name) + \
          remainder + "\n"

try:
  for line in stdin:
    print(format(line), end='')
except KeyboardInterrupt:
  pass
