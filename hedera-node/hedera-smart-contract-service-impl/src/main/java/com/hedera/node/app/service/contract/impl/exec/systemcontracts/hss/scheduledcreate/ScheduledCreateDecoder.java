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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateSyntheticTxnFactory.createToken;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateDecoder;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduledCreateDecoder {

    // Tuple indexes
    // Indexes for SCHEDULED_CREATE_FUNGIBLE and SCHEDULED_CREATE_NON_FUNGIBLE
    // scheduleCreateFungibleToken(HederaToken, int64, int32)
    // scheduleCreateNonFungibleToken(HederaToken)
    private static final int TOKEN_TUPLE_INDEX = 0;
    private static final int INIT_SUPPLY_TUPLE_INDEX = 1;
    private static final int DECIMALS_TUPLE_INDEX = 2;

    private final CreateDecoder createDecoder;

    @Inject
    public ScheduledCreateDecoder(@NonNull final CreateDecoder createDecoder) {
        // Dagger2
        requireNonNull(createDecoder);
        this.createDecoder = createDecoder;
    }

    public TransactionBody decodeScheduledCreateFT(@NonNull final HssCallAttempt attempt) {
        final var call = ScheduledCreateTranslator.SCHEDULED_CREATE_FUNGIBLE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .transactionID(attempt.nativeOperations().getTransactionID())
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(createToken(createDecoder.getTokenCreateWrapper(
                                        call.get(TOKEN_TUPLE_INDEX),
                                        true,
                                        call.get(INIT_SUPPLY_TUPLE_INDEX),
                                        call.get(DECIMALS_TUPLE_INDEX),
                                        attempt.senderId(),
                                        attempt.nativeOperations(),
                                        attempt.addressIdConverter())))
                                .build()))
                .build();
    }

    public TransactionBody decodeScheduledCreateNFT(@NonNull final HssCallAttempt attempt) {
        final var call = ScheduledCreateTranslator.SCHEDULED_CREATE_NON_FUNGIBLE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .transactionID(attempt.nativeOperations().getTransactionID())
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(createToken(createDecoder.getTokenCreateWrapperNonFungible(
                                        call.get(TOKEN_TUPLE_INDEX),
                                        attempt.senderId(),
                                        attempt.nativeOperations(),
                                        attempt.addressIdConverter())))
                                .build()))
                .build();
    }
}
