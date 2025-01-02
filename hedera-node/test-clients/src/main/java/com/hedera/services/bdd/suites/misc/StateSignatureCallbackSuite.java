/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.services.bdd.suites.misc;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.services.bdd.junit.EmbeddedReason.MANIPULATES_EVENT_VERSION;
import static com.hedera.services.bdd.junit.EmbeddedReason.USES_STATE_SIGNATURE_TRANSACTION_CALLBACK;
import static com.hedera.services.bdd.junit.hedera.embedded.SyntheticVersion.PAST;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usingStateSignatureTransactionCallback;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usingVersion;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;

import com.hedera.services.bdd.junit.EmbeddedHapiTest;
import com.hedera.services.bdd.spec.assertions.StateSignatureCallbackAsserts;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

public class StateSignatureCallbackSuite {

    @EmbeddedHapiTest(USES_STATE_SIGNATURE_TRANSACTION_CALLBACK)
    @DisplayName("skips pre-upgrade event and streams result with BUSY status")
    final Stream<DynamicTest> callsStateSignatureCallback() {
        final var preHandleCallback = new StateSignatureCallbackAsserts.StateSignatureTransactionCallbackMock();
        final var handleCallback = new StateSignatureCallbackAsserts.StateSignatureTransactionCallbackMock();
        return hapiTest(

                cryptoCreate("somebody").balance(0L)
                        .withSubmissionStrategy(usingStateSignatureTransactionCallback(preHandleCallback, handleCallback))
                        .has(),
                cryptoTransfer(tinyBarsFromTo(GENESIS, "somebody", ONE_HBAR))
                        .setNode("0.0.4")
                        .withSubmissionStrategy(usingVersion(PAST))
                        .hasKnownStatus(com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY),
                getAccountBalance("somebody").hasTinyBars(0L));
    }


}
