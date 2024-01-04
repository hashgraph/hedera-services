/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.factories.txns.TokenAssociateFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public enum TokenAssociateScenarios implements TxnHandlingScenario {
    TOKEN_ASSOCIATE_WITH_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenAssociateFactory.newSignedTokenAssociate()
                    .targeting(MISC_ACCOUNT)
                    .associating(KNOWN_TOKEN_WITH_KYC)
                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .nonPayerKts(MISC_ACCOUNT_KT)
                    .get());
        }
    },
    TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenAssociateFactory.newSignedTokenAssociate()
                    .targeting(SignedTxnFactory.DEFAULT_PAYER)
                    .associating(KNOWN_TOKEN_WITH_KYC)
                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
    TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenAssociateFactory.newSignedTokenAssociate()
                    .targeting(CUSTOM_PAYER_ACCOUNT)
                    .associating(KNOWN_TOKEN_WITH_KYC)
                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
    TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenAssociateFactory.newSignedTokenAssociate()
                    .targeting(SignedTxnFactory.STAKING_FUND)
                    .associating(KNOWN_TOKEN_WITH_KYC)
                    .get());
        }
    },
    TOKEN_ASSOCIATE_WITH_MISSING_TARGET {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenAssociateFactory.newSignedTokenAssociate()
                    .targeting(MISSING_ACCOUNT)
                    .associating(KNOWN_TOKEN_WITH_KYC)
                    .associating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
}
