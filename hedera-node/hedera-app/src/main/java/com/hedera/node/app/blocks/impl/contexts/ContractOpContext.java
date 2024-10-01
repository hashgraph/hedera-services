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

package com.hedera.node.app.blocks.impl.contexts;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.blocks.impl.RecordTranslationContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A {@link RecordTranslationContext} implementation with the id of an involved contract.
 * @param memo The memo for the transaction
 * @param txnId The transaction ID
 * @param transaction The transaction
 * @param functionality The functionality of the transaction
 * @param contractId The id of the involved contract
 */
public record ContractOpContext(
        @NonNull String memo,
        @NonNull TransactionID txnId,
        @NonNull Transaction transaction,
        @NonNull HederaFunctionality functionality,
        @Nullable ContractID contractId)
        implements RecordTranslationContext {}
