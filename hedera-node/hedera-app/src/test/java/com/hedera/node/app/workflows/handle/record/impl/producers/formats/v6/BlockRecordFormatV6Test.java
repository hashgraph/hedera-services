/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.impl.producers.formats.v6;

import static com.hedera.node.app.records.RecordTestData.BLOCK_NUM;
import static com.hedera.node.app.records.RecordTestData.TEST_BLOCKS;
import static com.hedera.node.app.records.RecordTestData.VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.streams.RecordStreamItem;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class BlockRecordFormatV6Test {
    @Test
    void serialization() throws IOException {
        for (var testBlocks : TEST_BLOCKS) {
            for (var rec : testBlocks) {
                // Serialize the record, and then parse the object back in from the protobuf just to make sure we can
                // round-trip the writing and parsing without losing any data.
                final var serializedRec = BlockRecordFormatV6.INSTANCE.serialize(rec, BLOCK_NUM, VERSION);
                final var parsedRecordStreamItem = RecordStreamItem.PROTOBUF.parse(
                        serializedRec.protobufSerializedRecordStreamItem().toReadableSequentialData());
                assertThat(rec.transaction()).isEqualTo(parsedRecordStreamItem.transaction());
                assertThat(rec.transactionRecord()).isEqualTo(parsedRecordStreamItem.record());
                assertThat(rec.transactionSidecarRecords()).hasSameSizeAs(serializedRec.sideCarItems());
                assertThat(rec.transactionSidecarRecords()).hasSameSizeAs(serializedRec.sideCarItemsBytes());
                for (int i = 0; i < rec.transactionSidecarRecords().size(); i++) {
                    final var sideCarRecord = rec.transactionSidecarRecords().get(i);
                    final var sideCarRecord2 = serializedRec.sideCarItems().get(i);
                    assertThat(sideCarRecord).isEqualTo(sideCarRecord2);
                    final var sideCarRecordBytes =
                            serializedRec.sideCarItemsBytes().get(i);
                    assertThat(TransactionSidecarRecord.PROTOBUF
                                    .toBytes(sideCarRecord)
                                    .toHex())
                            .isEqualTo(sideCarRecordBytes.toHex());
                }
            }
        }
    }
}
