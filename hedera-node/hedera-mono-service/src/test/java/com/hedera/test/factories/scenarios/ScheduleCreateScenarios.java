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
import static com.hedera.test.factories.txns.CryptoUpdateFactory.newSignedCryptoUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.ScheduleCreateFactory.newSignedScheduleCreate;
import static com.hedera.test.factories.txns.ScheduleSignFactory.newSignedScheduleSign;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER_ID;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;

public enum ScheduleCreateScenarios implements TxnHandlingScenario {
    SCHEDULE_CREATE_NESTED_SCHEDULE_SIGN {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .missingAdmin()
                                    .creating(
                                            newSignedScheduleSign()
                                                    .signing(KNOWN_SCHEDULE_IMMUTABLE)
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_NONSENSE {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedScheduleCreate().schedulingNonsense().get()));
        }
    },
    SCHEDULE_CREATE_XFER_NO_ADMIN {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .missingAdmin()
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_INVALID_XFER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .missingAdmin()
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISSING_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DEFAULT_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DEFAULT_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    DEFAULT_PAYER_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_SELF_AND_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    DEFAULT_PAYER_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_CUSTOM_PAYER_AND_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DILIGENT_SIGNING_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    CUSTOM_PAYER_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    CUSTOM_PAYER_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_SELF_SENDER_AND_PAYER_AS_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(CUSTOM_PAYER_ACCOUNT)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    DEFAULT_PAYER_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_ADMIN_CUSTOM_PAYER_SENDER_AND_PAYER_AS_SELF {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(DEFAULT_PAYER)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    CUSTOM_PAYER_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .missingAdmin()
                                    .designatingPayer(MISSING_ACCOUNT)
                                    .creating(
                                            newSignedCryptoTransfer()
                                                    .skipPayerSig()
                                                    .transfers(
                                                            tinyBarsFromTo(
                                                                    MISC_ACCOUNT_ID,
                                                                    RECEIVER_SIG_ID,
                                                                    1_000L))
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(IdUtils.asAccount(TREASURY_PAYER_ID))
                                    .creating(
                                            newSignedCryptoUpdate(TREASURY_PAYER_ID)
                                                    .skipPayerSig()
                                                    .newAccountKt(NEW_ACCOUNT_KT)
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .designatingPayer(IdUtils.asAccount(MASTER_PAYER_ID))
                                    .creating(
                                            newSignedCryptoUpdate(SYS_ACCOUNT_ID)
                                                    .skipPayerSig()
                                                    .newAccountKt(NEW_ACCOUNT_KT)
                                                    .get())
                                    .get()));
        }
    },
    SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedScheduleCreate()
                                    .payer(MASTER_PAYER_ID)
                                    .designatingPayer(IdUtils.asAccount(MASTER_PAYER_ID))
                                    .creating(
                                            newSignedCryptoUpdate(SYS_ACCOUNT_ID)
                                                    .skipPayerSig()
                                                    .newAccountKt(NEW_ACCOUNT_KT)
                                                    .get())
                                    .get()));
        }
    },
}
