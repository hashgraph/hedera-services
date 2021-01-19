package com.hedera.test.factories.scenarios;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.ScheduleSignFactory.newSignedScheduleSign;
import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;

public enum ScheduleSignScenarios implements TxnHandlingScenario {
    SCHEDULE_SIGN_MISSING_SCHEDULE {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return new PlatformTxnAccessor(from(
                    newSignedScheduleSign()
                            .updating(UNKNOWN_SCHEDULE)
                            .get()
            ));
        }
    },
    SCHEDULE_SIGN_KNOWN_SCHEDULE {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return new PlatformTxnAccessor(from(
                    newSignedScheduleSign()
                            .updating(KNOWN_SCHEDULE_WITH_ADMIN)
                            .get()
            ));
        }

        @Override
        public byte[] extantScheduleTxnBytes() throws Throwable {
            return newSignedCryptoTransfer()
					.sansTxnId()
                    .transfers(tinyBarsFromTo(MISC_ACCOUNT_ID, RECEIVER_SIG_ID, 1))
                    .get()
                    .getBodyBytes()
                    .toByteArray();
        }
    }
}
