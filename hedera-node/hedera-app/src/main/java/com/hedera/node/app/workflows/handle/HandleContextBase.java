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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.records.RecordListBuilder;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * A container of all data that is going to be shared between all transactions associated with a single user
 * transaction.
 *
 * @param keyVerifications a map of key to signature verification that was collected during pre-handle
 * @param recordListBuilder a facility to maintain the list of record builders
 */
public record HandleContextBase(
        @NonNull Map<Key, SignatureVerification> keyVerifications, @NonNull RecordListBuilder recordListBuilder) {

    public HandleContextBase {
        requireNonNull(keyVerifications, "keyVerifications must not be null");
        requireNonNull(recordListBuilder, "recordListBuilder must not be null");
    }
}
