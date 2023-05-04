/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("hedera")
public record HederaConfig(@ConfigProperty(defaultValue = "1001") long firstUserEntity,
                           @ConfigProperty(defaultValue = "0") long realm,
                           @ConfigProperty(defaultValue = "0") long shard,
                           @ConfigProperty(value = "recordStream.sidecarMaxSizeMb", defaultValue = "256") int recordStreamSidecarMaxSizeMb,
                           @ConfigProperty(value = "transaction.maxMemoUtf8Bytes", defaultValue = "100") int transactionMaxMemoUtf8Bytes,
                           @ConfigProperty(value = "transaction.maxValidDuration", defaultValue = "180") long transactionMaxValidDuration,
                           @ConfigProperty(value = "transaction.minValidDuration", defaultValue = "15") long transactionMinValidDuration,
                           @ConfigProperty(value = "transaction.minValidityBufferSecs", defaultValue = "10") int transactionMinValidityBufferSecs,
                           @ConfigProperty(value = "recordStream.recordFileVersion", defaultValue = "6") int recordStreamRecordFileVersion,
                           @ConfigProperty(value = "recordStream.signatureFileVersion", defaultValue = "6") int recordStreamSignatureFileVersion) {


}
