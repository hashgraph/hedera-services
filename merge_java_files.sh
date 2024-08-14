#!/bin/bash

#
# Copyright (C) 2024 Hedera Hashgraph, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

OUTPUT_FILE="merged.txt"  # Name of the output file
DELIMITER="// ==============================="

# Find all .java files recursively and sort them
find . -type f -name "*.java" | sort > java_files.txt

# Remove existing output file if it exists
if [ -f "$OUTPUT_FILE" ]; then
  rm "$OUTPUT_FILE"
fi

# Iterate through the list of .java files
while read -r file; do
  # Check if the file exists and is not empty
  if [ -f "$file" ] && [ -s "$file" ]; then
    echo "$DELIMITER" >> "$OUTPUT_FILE"
    echo "// $file" >> "$OUTPUT_FILE" 
    cat "$file" >> "$OUTPUT_FILE"
  fi
done < java_files.txt

echo "All .java files merged into '$OUTPUT_FILE'"

# Clean up temporary file
rm java_files.txt


