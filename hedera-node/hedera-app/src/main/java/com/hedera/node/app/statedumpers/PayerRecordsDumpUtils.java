/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.statedumpers;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.swirlds.state.merkle.queue.QueueNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PayerRecordsDumpUtils {

    public static void dumpModTxnRecordQueue(
            @NonNull final Path path,
            @NonNull final QueueNode<TransactionRecordEntry> queue,
            final JsonWriter jsonWriter) {
        var transactionRecords = gatherTxnRecordsFromMod(queue);
        System.out.println("=== Dumping payer records ===");
        System.out.println(transactionRecords.size() + " records found");
        jsonWriter.write(transactionRecords, path.toString());
        System.out.printf("=== payer records report is %d bytes %n", transactionRecords.size());
    }

    private static List<TransactionRecordEntry> gatherTxnRecordsFromMod(QueueNode<TransactionRecordEntry> queue) {
        var iterator = queue.iterator();
        var records = new ArrayList<TransactionRecordEntry>();
        while (iterator.hasNext()) {
            records.add(iterator.next());
        }

        return records;
    }
}
