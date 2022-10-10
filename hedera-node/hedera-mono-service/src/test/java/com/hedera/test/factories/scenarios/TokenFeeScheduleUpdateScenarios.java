/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.scenarios;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenFeeScheduleUpdateFactory.newSignedTokenFeeScheduleUpdate;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum TokenFeeScheduleUpdateScenarios implements TxnHandlingScenario {
    UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(MISSING_TOKEN)
                                    .withCustom(fixedFee(1, null, MISSING_ENTITY_ID, false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                                    .withCustom(fixedFee(1, null, MISSING_ENTITY_ID, false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var feeCollectorSigReq = EntityId.fromGrpcAccountId(RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(fixedFee(1, null, feeCollectorSigReq, false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var feeCollectorNoSigReq = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(fixedFee(1, null, feeCollectorNoSigReq, false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var feeCollectorNoSigReq = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            final var feeCollectorWithSigReq = EntityId.fromGrpcAccountId(RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(fixedFee(1, null, feeCollectorNoSigReq, false))
                                    .withCustom(fixedFee(2, null, feeCollectorWithSigReq, false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .payer(RECEIVER_SIG_ID)
                                    .payerKt(RECEIVER_SIG_KT)
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(
                                            fixedFee(
                                                    2,
                                                    null,
                                                    EntityId.fromGrpcAccountId(RECEIVER_SIG),
                                                    false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .payer(NO_RECEIVER_SIG_ID)
                                    .payerKt(NO_RECEIVER_SIG_KT)
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(
                                            fixedFee(
                                                    2,
                                                    null,
                                                    EntityId.fromGrpcAccountId(NO_RECEIVER_SIG),
                                                    false))
                                    .get()));
        }
    },
    UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var missingFeeCollector = EntityId.fromGrpcAccountId(MISSING_ACCOUNT);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenFeeScheduleUpdate()
                                    .updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
                                    .withCustom(fixedFee(1, null, missingFeeCollector, false))
                                    .get()));
        }
    },
}
