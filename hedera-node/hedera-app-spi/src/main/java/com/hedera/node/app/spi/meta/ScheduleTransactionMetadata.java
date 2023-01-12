/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.meta;

/**
 * Metadata collected when schedule transactions are handled as part of "pre-handle". This happens
 * with multiple background threads. Any state read or computed as part of this pre-handle,
 * including any errors, are captured in the {@link TransactionMetadata}. This is then made
 * available to the transaction during the "handle" phase as part of the HandleContext. This
 * contains an inner {@link TransactionMetadata} for scheduled transaction.
 */
public interface ScheduleTransactionMetadata extends TransactionMetadata {
    /**
     * Gets the scheduled transaction metadata
     *
     * @return scheduled transaction metadata
     */
    TransactionMetadata scheduledMeta();
}
