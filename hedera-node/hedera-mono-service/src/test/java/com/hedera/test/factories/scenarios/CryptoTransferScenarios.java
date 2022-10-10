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

import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND_ID;
import static com.hedera.test.factories.txns.TinyBarsFromTo.approvedTinyBarsFromTo;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromAccountToAlias;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromAliasToAlias;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static com.hedera.test.utils.IdUtils.asAliasAccount;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum CryptoTransferScenarios implements TxnHandlingScenario {
    CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromAccountToAlias(
                                                    FIRST_TOKEN_SENDER_ID,
                                                    CURRENTLY_UNUSED_ALIAS,
                                                    1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromAliasToAlias(
                                                    CURRENTLY_UNUSED_ALIAS,
                                                    NO_RECEIVER_SIG_ALIAS,
                                                    1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromAccountToAlias(
                                                    DEFAULT_PAYER_ID,
                                                    NO_RECEIVER_SIG_ALIAS,
                                                    1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    FIRST_TOKEN_SENDER_ID, STAKING_FUND_ID, 1_000L))
                                    .get()));
        }
    },

    CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .adjusting(DEFAULT_PAYER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(STAKING_FUND, KNOWN_TOKEN_NO_SPECIAL_KEYS, 1_000)
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT,
                                            asAliasAccount(
                                                    ByteString.copyFromUtf8(
                                                            CURRENTLY_UNUSED_ALIAS)),
                                            FIRST_TOKEN_SENDER)
                                    .get()));
        }
    },

    CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT,
                                            FIRST_TOKEN_SENDER,
                                            asAliasAccount(
                                                    ByteString.copyFromUtf8(
                                                            CURRENTLY_UNUSED_ALIAS)))
                                    .get()));
        }
    },

    CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT,
                                            STAKING_FUND,
                                            asAliasAccount(
                                                    ByteString.copyFromUtf8(
                                                            CURRENTLY_UNUSED_ALIAS)))
                                    .get()));
        }
    },

    CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT, FIRST_TOKEN_SENDER, STAKING_FUND)
                                    .get()));
        }
    },

    CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    STAKING_FUND_ID, NO_RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, NO_RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_CUSTOM_PAYER_SENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    CUSTOM_PAYER_ACCOUNT_ID,
                                                    NO_RECEIVER_SIG_ID,
                                                    1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(RECEIVER_SIG_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(RECEIVER_SIG_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(RECEIVER_SIG_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, MISSING_ACCOUNT_ID, 1_000L))
                                    .get()));
        }
    },
    VALID_QUERY_PAYMENT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(MISC_ACCOUNT_KT, RECEIVER_SIG_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
                                            tinyBarsFromTo(
                                                    MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    QUERY_PAYMENT_MISSING_SIGS_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(MISC_ACCOUNT_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
                                            tinyBarsFromTo(
                                                    MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    QUERY_PAYMENT_INVALID_SENDER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(MISC_ACCOUNT_KT)
                                    .transfers(
                                            tinyBarsFromTo(
                                                    DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L),
                                            tinyBarsFromTo(
                                                    MISSING_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_EXTANT_SENDERS {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .adjusting(DEFAULT_PAYER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(
                                            SECOND_TOKEN_SENDER,
                                            KNOWN_TOKEN_NO_SPECIAL_KEYS,
                                            -1_000)
                                    .adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
                                    .nonPayerKts(SECOND_TOKEN_SENDER_KT)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .adjustingHbars(FIRST_TOKEN_SENDER, -2_000)
                                    .adjustingHbars(TOKEN_RECEIVER, +2_000)
                                    .nonPayerKts(FIRST_TOKEN_SENDER_KT)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .adjustingHbars(FIRST_TOKEN_SENDER, -2_000)
                                    .adjustingHbars(RECEIVER_SIG, +2_000)
                                    .nonPayerKts(FIRST_TOKEN_SENDER_KT, RECEIVER_SIG_KT)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .adjusting(
                                            FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(
                                            SECOND_TOKEN_SENDER,
                                            KNOWN_TOKEN_NO_SPECIAL_KEYS,
                                            -1_000)
                                    .adjusting(RECEIVER_SIG, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
                                    .nonPayerKts(
                                            FIRST_TOKEN_SENDER_KT,
                                            SECOND_TOKEN_SENDER_KT,
                                            RECEIVER_SIG_KT)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_MISSING_SENDERS {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .adjusting(
                                            FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(MISSING_ACCOUNT, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_SPECIAL_KEYS, +2_000)
                                    .nonPayerKts(FIRST_TOKEN_SENDER_KT, SECOND_TOKEN_SENDER_KT)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT, FIRST_TOKEN_SENDER, TOKEN_RECEIVER)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT,
                                            FIRST_TOKEN_SENDER_ALIAS,
                                            TOKEN_RECEIVER)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT, FIRST_TOKEN_SENDER, RECEIVER_SIG)
                                    .changingOwner(
                                            ROYALTY_TOKEN_NFT, SECOND_TOKEN_SENDER, DEFAULT_PAYER)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT, FIRST_TOKEN_SENDER, NO_RECEIVER_SIG)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            ROYALTY_TOKEN_NFT, FIRST_TOKEN_SENDER, NO_RECEIVER_SIG)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(ROYALTY_TOKEN_NFT, MISC_ACCOUNT, NO_RECEIVER_SIG)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            ROYALTY_TOKEN_NFT, FIRST_TOKEN_SENDER, NO_RECEIVER_SIG)
                                    .adjustingHbars(FIRST_TOKEN_SENDER, +1_000)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            ROYALTY_TOKEN_NFT, FIRST_TOKEN_SENDER, NO_RECEIVER_SIG)
                                    .adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_IMMUTABLE, +1_000)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            MISSING_TOKEN_NFT, FIRST_TOKEN_SENDER, NO_RECEIVER_SIG)
                                    .adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_IMMUTABLE, +1_000)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(KNOWN_TOKEN_NFT, MISSING_ACCOUNT, TOKEN_RECEIVER)
                                    .get()));
        }
    },
    TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .changingOwner(
                                            KNOWN_TOKEN_NFT, FIRST_TOKEN_SENDER, MISSING_ACCOUNT)
                                    .get()));
        }
    },
    CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .nonPayerKts(DEFAULT_PAYER_KT)
                                    .transfers(
                                            approvedTinyBarsFromTo(
                                                    OWNER_ACCOUNT_ID, NO_RECEIVER_SIG_ID, 1_000L))
                                    .get()));
        }
    },
    TOKEN_TRNASFER_ALLOWANCE_SPENDER_SCENARIO {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .approvedAdjusting(
                                            OWNER_ACCOUNT, KNOWN_TOKEN_NO_SPECIAL_KEYS, -1_000)
                                    .adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_SPECIAL_KEYS, +1_000)
                                    .get()));
        }
    },
    NFT_TRNASFER_ALLOWANCE_SPENDER_SCENARIO {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedCryptoTransfer()
                                    .approvedChangingOwner(
                                            KNOWN_TOKEN_NFT, OWNER_ACCOUNT, TOKEN_RECEIVER)
                                    .get()));
        }
    }
}
