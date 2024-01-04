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
import com.hedera.test.factories.txns.CryptoTransferFactory;
import com.hedera.test.factories.txns.CryptoUpdateFactory;
import com.hedera.test.factories.txns.ScheduleCreateFactory;
import com.hedera.test.factories.txns.ScheduleSignFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.factories.txns.TinyBarsFromTo;
import com.hedera.test.utils.IdUtils;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public enum ScheduleCreateScenarios implements TxnHandlingScenario {
    SCHEDULE_CREATE_NESTED_SCHEDULE_SIGN {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .missingAdmin()
                    .creating(ScheduleSignFactory.newSignedScheduleSign()
                            .signing(KNOWN_SCHEDULE_IMMUTABLE)
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_NONSENSE {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .schedulingNonsense()
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_NO_ADMIN {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .missingAdmin()
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_INVALID_XFER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .missingAdmin()
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISSING_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(SignedTxnFactory.DEFAULT_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(SignedTxnFactory.DEFAULT_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(
                                    SignedTxnFactory.DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_SELF_AND_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(
                                    SignedTxnFactory.DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_CUSTOM_PAYER_AND_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(CUSTOM_PAYER_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(CUSTOM_PAYER_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SELF_SENDER_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(
                                    SignedTxnFactory.DEFAULT_PAYER_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_CUSTOM_PAYER_SENDER_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(SignedTxnFactory.DEFAULT_PAYER)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(CUSTOM_PAYER_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .missingAdmin()
                    .designatingPayer(MISSING_ACCOUNT)
                    .creating(CryptoTransferFactory.newSignedCryptoTransfer()
                            .skipPayerSig()
                            .transfers(TinyBarsFromTo.tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1_000L))
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(IdUtils.asAccount(SignedTxnFactory.TREASURY_PAYER_ID))
                    .creating(CryptoUpdateFactory.newSignedCryptoUpdate(SignedTxnFactory.TREASURY_PAYER_ID)
                            .skipPayerSig()
                            .newAccountKt(NEW_ACCOUNT_KT)
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .designatingPayer(IdUtils.asAccount(SignedTxnFactory.MASTER_PAYER_ID))
                    .creating(CryptoUpdateFactory.newSignedCryptoUpdate(SYS_ACCOUNT_ID)
                            .skipPayerSig()
                            .newAccountKt(NEW_ACCOUNT_KT)
                            .get())
                    .get());
        }
    },
    SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER {
        public PlatformTxnAccessor platformTxn()
                throws InvalidProtocolBufferException, SignatureException, NoSuchAlgorithmException,
                        InvalidKeyException {
            return PlatformTxnAccessor.from(ScheduleCreateFactory.newSignedScheduleCreate()
                    .payer(SignedTxnFactory.MASTER_PAYER_ID)
                    .designatingPayer(IdUtils.asAccount(SignedTxnFactory.MASTER_PAYER_ID))
                    .creating(CryptoUpdateFactory.newSignedCryptoUpdate(SYS_ACCOUNT_ID)
                            .skipPayerSig()
                            .newAccountKt(NEW_ACCOUNT_KT)
                            .get())
                    .get());
        }
    },
}
