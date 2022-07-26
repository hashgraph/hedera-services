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

import static com.hedera.test.factories.txns.ConsensusUpdateTopicFactory.newSignedConsensusUpdateTopic;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;

import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import java.time.Instant;

public enum ConsensusUpdateTopicScenarios implements TxnHandlingScenario {
    CONSENSUS_UPDATE_TOPIC_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID).get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(newSignedConsensusUpdateTopic(MISSING_TOPIC_ID).get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .adminKey(UPDATE_TOPIC_ADMIN_KT)
                                    .get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .adminKey(UPDATE_TOPIC_ADMIN_KT)
                                    .autoRenewAccountId(MISC_ACCOUNT_ID)
                                    .get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .adminKey(UPDATE_TOPIC_ADMIN_KT)
                                    .autoRenewAccountId(DEFAULT_PAYER_ID)
                                    .get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .adminKey(UPDATE_TOPIC_ADMIN_KT)
                                    .autoRenewAccountId(CUSTOM_PAYER_ACCOUNT_ID)
                                    .get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .autoRenewAccountId(MISSING_ACCOUNT_ID)
                                    .get()));
        }
    },
    CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO {
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    from(
                            newSignedConsensusUpdateTopic(EXISTING_TOPIC_ID)
                                    .expirationTime(Instant.ofEpochSecond(1578331832))
                                    .get()));
        }
    }
}
