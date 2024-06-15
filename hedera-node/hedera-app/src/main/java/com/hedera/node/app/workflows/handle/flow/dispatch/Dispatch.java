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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.Set;

/**
 * The context needed for executing business logic of a service. This has two implementations - one for user transactions scope
 * and one for dispatched child transactions scope.
 */
public interface Dispatch {
    /**
     * The builder for the transaction record of this dispatch.
     *
     * @return the builder
     */
    SingleTransactionRecordBuilderImpl recordBuilder();

    /**
     * The configuration for the dispatch.
     *
     * @return the configuration
     */
    Configuration config();

    /**
     * The fees calculated for the transaction
     * @return the fees
     */
    Fees fees();

    /**
     * The transaction info for the transaction
     * @return the transaction info
     */
    TransactionInfo txnInfo();

    /**
     * The payer of the transaction. This will be the synthetic payer for child transactions.
     * @return the payer
     */
    AccountID syntheticPayer();

    /**
     * The readable store factory for the transaction
     * @return the store factory
     */
    ReadableStoreFactory readableStoreFactory();

    /**
     * The fee accumulator for the transaction
     * @return the fee accumulator
     */
    FeeAccumulator feeAccumulator();

    /**
     * The key verifier for the transaction
     * @return the key verifier
     */
    KeyVerifier keyVerifier();

    /**
     * The creator node info of the transaction
     * @return the creator
     */
    NodeInfo creatorInfo();

    /**
     * The consensus time of the user transaction
     * @return the consensus time
     */
    Instant consensusNow();

    /**
     * The required keys needed to sign the transaction
     * @return the required keys
     */
    Set<Key> requiredKeys();

    /**
     * The hollow accounts that will be finalized in the transaction
     * @return the hollow accounts
     */
    Set<Account> hollowAccounts();

    /**
     * The handle context for the transaction scope
     * @return the handle context
     */
    HandleContext handleContext();

    /**
     * The savepoint stack for the transaction scope
     * @return the savepoint stack
     */
    SavepointStackImpl stack();

    /**
     * The transaction category for the transaction that is dispatched
     * @return the transaction category
     */
    HandleContext.TransactionCategory txnCategory();

    /**
     * The finalize context for the transaction based on the transaction category
     * @return the finalize context
     */
    FinalizeContext finalizeContext();

    /**
     * The record list builder for the user transaction
     * @return the record list builder
     */
    RecordListBuilder recordListBuilder();

    /**
     * The platform state for the transaction
     * @return the platform state
     */
    PlatformState platformState();

    /**
     * The pre-handle result for the transaction; will be a synthetic result for a child dispatch.
     *
     * @return the pre-handle result
     */
    PreHandleResult preHandleResult();
}
