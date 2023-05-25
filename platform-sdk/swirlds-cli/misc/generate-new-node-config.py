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

# This script can be used to generate a config.txt file for a new node being added to the network. It is designed
# to be used for configuration files in a release 0.38.2.
#
# This script accepts exactly three arguments:
#   1) the path to a config.txt file that has incorrect (or incomplete) consensus weights.
#   2) the path to a currentAddressBook.txt file generated using release 0.38.2, where the node memos are missing.
#   3) the path/filename of the new config file to be generated. This file will contain all data from the original
#      config.txt file, but with the correct consensus weights from the currentAddressBook.txt file.

import sys
import os

if len(sys.argv) != 4:
    print("This script requires exactly three arguments: path to original config.txt (with " + \
           "incorrect weights), path to currentAddressBook.txt (with correct weights), " + \
           "and path/filename of new config.txt to be generated.")
    sys.exit(1)

original_config_path = sys.argv[1]
current_address_book_path = sys.argv[2]
new_config_path = sys.argv[3]

if not os.path.exists(original_config_path):
    print("Error: file does not exist: " + original_config_path)
    sys.exit(1)

if not os.path.exists(current_address_book_path):
    print("Error: file does not exist: " + current_address_book_path)
    sys.exit(1)

if os.path.exists(new_config_path):
    print("Error: output file already exists: " + new_config_path)
    sys.exit(1)

# A list of lines starting with the "address" keyword, split apart into substrings by ",".
current_address_book_entries = []

expected_entry_count = (8, 9)
minimum_entry_count = 8
consensus_weight_index = 3

for line in open(current_address_book_path, 'r'):
    line = line.strip()
    if line.startswith("address"):
        entries = line.split(",")
        entries = [entry.strip() for entry in entries]
        if len(entries) not in expected_entry_count:
            print("Error: address book file " + current_address_book_path + \
                  " file contains an address entry with an unexpected number of fields: " + line)
            sys.exit(1)
        current_address_book_entries.append(entries)

new_config = open(new_config_path, 'w')

current_address_book_entries_index = 0
for line in open(original_config_path, 'r'):
    stripped_line = line.strip()
    entries = stripped_line.split(",")
    entries = [entry.strip() for entry in entries]

    if len(entries) not in expected_entry_count or not stripped_line.startswith("address"):
         new_config.write(line)
         continue

    address_book_entries = current_address_book_entries[current_address_book_entries_index]
    current_address_book_entries_index += 1

    # Do some basic sanity checks, the only fields that should differ are the consensus weights and the memo.
    for index in range(minimum_entry_count):
        if index == consensus_weight_index:
            # expected to differ
            continue
        if entries[index] != address_book_entries[index]:
            print("There are unexpected differences between lines in the original config.txt file and the " + \
                  "currentAddressBook.txt file. The following lines differ: " + \
                  "\n\t" + line + \
                  "\n\t" + ", ".join(address_book_entries))
            sys.exit(1)

    entries[consensus_weight_index] = address_book_entries[consensus_weight_index]
    new_config.write(", ".join(entries) + "\n")

new_config.close()

if current_address_book_entries_index != len(current_address_book_entries):
    print("Number of entries in address book file does not match the number of entries in the " + \
          "original config.txt file. Something is likely wrong.")
    sys.exit(1)
