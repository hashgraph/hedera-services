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
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.state.submerkle.FcCustomFee.royaltyFee;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.TokenCreateFactory.newSignedTokenCreate;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum TokenCreateScenarios implements TxnHandlingScenario {
    TOKEN_CREATE_WITH_ADMIN_ONLY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().nonPayerKts(TOKEN_ADMIN_KT).get()));
        }
    },
    TOKEN_CREATE_WITH_ADMIN_AND_FREEZE {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .frozen()
                                    .nonPayerKts(TOKEN_ADMIN_KT, TOKEN_FREEZE_KT)
                                    .get()));
        }
    },
    TOKEN_CREATE_MISSING_ADMIN {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(from(newSignedTokenCreate().missingAdmin().get()));
        }
    },
    TOKEN_CREATE_WITH_AUTO_RENEW {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().missingAdmin().autoRenew(MISC_ACCOUNT).get()));
        }
    },
    TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().missingAdmin().autoRenew(DEFAULT_PAYER).get()));
        }
    },
    TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .autoRenew(CUSTOM_PAYER_ACCOUNT)
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_MISSING_AUTO_RENEW {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().missingAdmin().autoRenew(MISSING_ACCOUNT).get()));
        }
    },
    TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(fixedFee(123L, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(
                                            fixedFee(123L, MISSING_ENTITY_ID, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(fixedFee(123L, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(DEFAULT_PAYER);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(fixedFee(123L, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(
                                            fractionalFee(1, 2, 3, 4, false, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(royaltyFee(1, 2, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(royaltyFee(1, 2, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(
                                            royaltyFee(
                                                    1,
                                                    2,
                                                    new FixedFeeSpec(1, new EntityId(2, 3, 4)),
                                                    collector,
                                                    false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(
                                            royaltyFee(
                                                    1,
                                                    2,
                                                    new FixedFeeSpec(1, MISSING_ENTITY_ID),
                                                    collector,
                                                    false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_MISSING_COLLECTOR {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            final var collector = EntityId.fromGrpcAccountId(MISSING_ACCOUNT);
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .plusCustomFee(fixedFee(123L, null, collector, false))
                                    .get()));
        }
    },
    TOKEN_CREATE_WITH_MISSING_TREASURY {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().missingAdmin().treasury(MISSING_ACCOUNT).get()));
        }
    },
    TOKEN_CREATE_WITH_TREASURY_AS_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedTokenCreate().missingAdmin().treasury(DEFAULT_PAYER).get()));
        }
    },
    TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedTokenCreate()
                                    .missingAdmin()
                                    .treasury(CUSTOM_PAYER_ACCOUNT)
                                    .get()));
        }
    },
}
