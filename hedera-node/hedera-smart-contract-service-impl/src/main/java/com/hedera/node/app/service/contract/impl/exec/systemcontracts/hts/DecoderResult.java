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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This wrapper is used to better dispatch the decoding result from the decoder to the translator.
 * Its purpose is to help us replicate the mono behavior by either reverting the transaction or returning certain status.
 * - body: Represents the transaction body.
 * - shouldRevert: Indicates if the transaction should be reverted.
 * - status: Provides the specific response status code for the transaction, if needed.
 */
public record DecoderResult(
        @NonNull TransactionBody body, @Nullable ResponseCodeEnum wantedStatus, boolean shouldRevert) {}
