/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.files;

import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;

/**
 * Intermediate representation of a single transaction record
 *
 * @param hashSerializedRecordStreamItem Optional serialized record stream item for hashing
 * @param protobufSerializedRecordStreamItem Protobuf serialized record stream item
 * @param sideCarItemsBytes Protobuf serialized sidecar items
 * @param sideCarItems Original sidecar items
 */
public record SerializedSingleTransactionRecord(
        Bytes hashSerializedRecordStreamItem,
        Bytes protobufSerializedRecordStreamItem,
        List<Bytes> sideCarItemsBytes,
        List<TransactionSidecarRecord> sideCarItems) {}
