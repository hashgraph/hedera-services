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

import static com.hedera.test.factories.txns.ContractDeleteFactory.newSignedContractDelete;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;

public enum ContractDeleteScenarios implements TxnHandlingScenario {
    CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractDelete(MISC_CONTRACT_ID)
                                    .withBeneficiary(RECEIVER_SIG)
                                    .get()));
        }
    },
    CONTRACT_DELETE_IMMUTABLE_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractDelete(IMMUTABLE_CONTRACT_ID)
                                    .withBeneficiary(RECEIVER_SIG)
                                    .get()));
        }
    },
    CONTRACT_DELETE_XFER_CONTRACT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractDelete(MISC_CONTRACT_ID)
                                    .withBeneficiary(MISC_RECIEVER_SIG_CONTRACT)
                                    .get()));
        }
    },
    CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractDelete(MISC_CONTRACT_ID)
                                    .withBeneficiary(MISSING_ACCOUNT)
                                    .get()));
        }
    },
    CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedContractDelete(MISC_CONTRACT_ID)
                                    .withBeneficiary(MISSING_CONTRACT)
                                    .get()));
        }
    }
}
