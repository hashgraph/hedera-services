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
import com.hedera.test.factories.txns.TokenUpdateFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public enum TokenUpdateScenarios implements TxnHandlingScenario {
    UPDATE_WITH_NO_FIELDS_CHANGED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .get());
        }
    },
    UPDATE_REPLACING_TREASURY {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newTreasury(TOKEN_TREASURY)
                    .get());
        }
    },
    UPDATE_REPLACING_TREASURY_AS_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newTreasury(SignedTxnFactory.DEFAULT_PAYER)
                    .get());
        }
    },
    UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newTreasury(CUSTOM_PAYER_ACCOUNT)
                    .get());
        }
    },
    UPDATE_REPLACING_WITH_MISSING_TREASURY {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newTreasury(MISSING_ACCOUNT)
                    .get());
        }
    },
    UPDATE_REPLACING_ADMIN_KEY {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newAdmin(TOKEN_REPLACE_KT)
                    .get());
        }
    },
    UPDATE_WITH_SUPPLY_KEYED_TOKEN_REPLACEMENT_KEY_NOT_REQUIRED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_WITH_SUPPLY)
                    .replacingSupply()
                    .notValidatingRoleKeySignatures()
                    .get());
        }
    },
    UPDATE_WITH_KYC_KEYED_TOKEN_REPLACEMENT_KEY_REQUIRED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_WITH_KYC)
                    .replacingKyc()
                    .get());
        }
    },
    UPDATE_WITH_KYC_KEYED_TOKEN_REPLACEMENT_KEY_NOT_REQUIRED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_WITH_KYC)
                    .replacingKyc()
                    .notValidatingRoleKeySignatures()
                    .get());
        }
    },
    UPDATE_WITH_FREEZE_KEYED_TOKEN_REPLACEMENT_KEY_NOT_REQUIRED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_WITH_FREEZE)
                    .replacingFreeze()
                    .notValidatingRoleKeySignatures()
                    .get());
        }
    },
    UPDATE_WITH_WIPE_KEYED_TOKEN_REPLACEMENT_KEY_NOT_REQUIRED {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_WITH_WIPE)
                    .replacingWipe()
                    .notValidatingRoleKeySignatures()
                    .get());
        }
    },
    UPDATE_WITH_MISSING_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(MISSING_TOKEN)
                    .newAutoRenew(MISC_ACCOUNT)
                    .get());
        }
    },
    UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_IMMUTABLE)
                    .get());
        }
    },
    TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newAutoRenew(MISC_ACCOUNT)
                    .get());
        }
    },
    TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newAutoRenew(SignedTxnFactory.DEFAULT_PAYER)
                    .get());
        }
    },
    TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newAutoRenew(CUSTOM_PAYER_ACCOUNT)
                    .get());
        }
    },
    TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT {
        @Override
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(TokenUpdateFactory.newSignedTokenUpdate()
                    .updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
                    .newAutoRenew(MISSING_ACCOUNT)
                    .get());
        }
    }
}
