#  Copyright 2016-2022 Hedera Hashgraph, LLC
#
#  This software is the confidential and proprietary information of
#  Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
#  disclose such Confidential Information and shall use it only in
#  accordance with the terms of the license agreement you entered into
#  with Hedera Hashgraph.
#
#  HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
#  THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
#  TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
#  PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
#  ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
#  DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  This software is the confidential and proprietary information of
#  Swirlds, Inc. ("Confidential Information"). You shall not
#  disclose such Confidential Information and shall use it only in
#  accordance with the terms of the license agreement you entered into
#  with Swirlds.
#
#  SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
#  THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
#  TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
#  PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
#  ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
#  DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#
#  This software is the confidential and proprietary information of
#  Swirlds, Inc. ("Confidential Information"). You shall not
#  disclose such Confidential Information and shall use it only in
#  accordance with the terms of the license agreement you entered into
#  with Swirlds.
#
#  SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
#  THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
#  TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
#  PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
#  ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
#  DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.

import csv
import os
import sys


def calculate_median(l):
    l = sorted(l)
    l_len = len(l)
    if l_len < 1:
        return None
    if l_len % 2 == 0:
        return "{0:.2f}".format((float(l[int((l_len - 1) / 2)]) + float(l[int((l_len + 1) / 2)])) / 2.0)
    else:
        return l[int((l_len - 1) / 2)]


if len(sys.argv) < 2:
    print("please input directory to use.")

topLevelDirectory = sys.argv[1]
print(topLevelDirectory)

os.chdir(topLevelDirectory)

node0000List = []
parentDirList = []
summaryDirList = ""

overallTestSummary = []

eventsPerSec = []
c2c = []
tps = []

fileCount = 0

for root, subFolders, files in os.walk(topLevelDirectory):
    for file in files:
        print(file)
        if file == "StatsDemo0.csv":
            fileLocation = os.path.join(root, file)
            node0000List.append(os.path.join(root, file))
            parentDirList.append(os.path.basename(os.path.dirname(os.path.dirname(fileLocation))))
            summaryDirList = os.path.dirname(os.path.dirname(os.path.dirname(fileLocation)))
for csvFile in node0000List:
    fileSummary = []
    fileSummary.append(parentDirList[fileCount])
    with open(csvFile, 'r') as f:
        eventsPerSec = []
        c2c = []
        tps = []

        reader = csv.reader(f)
        count = 0
        while count < 67:
            next(reader)
            count += 1
        for row in reader:
            if count > 67:
                eventsPerSec.append(row[18])
                c2c.append(row[42])
                tps.append(row[64])
            elif count == 67:
                if fileCount == 0:
                    overallTestSummary.append(["", "", row[18], "", "", row[42], "", "", row[64]])
            count += 1
        eventsPerSec = list(filter(lambda a: '0.' not in a, eventsPerSec))
        c2c = list(filter(lambda a: '0.' not in a, c2c))
        tps = list(filter(lambda a: '0.' not in a, tps))
        eventsPerSec = [float(i) for i in eventsPerSec]
        c2c = [float(i) for i in c2c]
        tps = [float(i) for i in tps]
    fileSummary.append(min(eventsPerSec))
    fileSummary.append(max(eventsPerSec))
    fileSummary.append(calculate_median(eventsPerSec))
    fileSummary.append(min(c2c))
    fileSummary.append(max(c2c))
    fileSummary.append(calculate_median(c2c))
    fileSummary.append(min(tps))
    fileSummary.append(max(tps))
    fileSummary.append(calculate_median(tps))
    overallTestSummary.append(fileSummary)
    fileCount += 1

print(summaryDirList)
print(overallTestSummary)
