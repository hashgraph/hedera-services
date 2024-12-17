/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.suites;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class CreateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateSuite.class);

    public static String NOVELTY = "novel";

    private final Map<String, String> specConfig;
    private final String memo;
    private final long initialBalance;
    private final int numBusyRetries;

    private final boolean receiverSigRequired;

    private final String novelTarget;

    private final AtomicLong createdNo = new AtomicLong(0);

    public CreateSuite(
            final Map<String, String> specConfig,
            final long initialBalance,
            final String memo,
            final String novelTarget,
            final int numBusyRetries,
            final boolean receiverSigRequired) {
        this.memo = memo;
        this.specConfig = specConfig;
        this.novelTarget = novelTarget;
        this.numBusyRetries = numBusyRetries;
        this.initialBalance = initialBalance;
        this.receiverSigRequired = receiverSigRequired;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doCreate());
    }

    final Stream<DynamicTest> doCreate() {
        if (!novelTarget.endsWith(NOVELTY + ".pem")) {
            throw new IllegalArgumentException("Only accepts tentative new key material named 'novel.pem'");
        }

        final var newAccount = "newAccount";
        final var newKey = "newKey";
        final var success = new AtomicBoolean(false);
        final var novelPass = TxnUtils.randomAlphaNumeric(12);
        return HapiSpec.customHapiSpec("DoCreate")
                .withProperties(specConfig)
                .given(UtilVerbs.newKeyNamed(newKey)
                        .shape(SigControl.ED25519_ON)
                        .exportingTo(novelTarget, novelPass)
                        .includingEd25519Mnemonic())
                .when(UtilVerbs.withOpContext((spec, opLog) -> {
                    int attemptNo = 1;
                    do {
                        System.out.print("Creation attempt #" + attemptNo + "...");
                        final var creation = TxnVerbs.cryptoCreate(newAccount)
                                .balance(initialBalance)
                                .blankMemo()
                                .entityMemo(memo)
                                .key(newKey)
                                .receiverSigRequired(receiverSigRequired)
                                .hasPrecheckFrom(OK, BUSY)
                                .exposingCreatedIdTo(id -> createdNo.set(id.getAccountNum()))
                                .noLogging();
                        CustomSpecAssert.allRunFor(spec, creation);
                        if (creation.getActualPrecheck() == OK) {
                            System.out.println("SUCCESS");
                            success.set(true);
                            break;
                        } else {
                            final var retriesLeft = numBusyRetries - attemptNo + 1;
                            System.out.println("BUSY"
                                    + (retriesLeft > 0
                                            ? ", retrying " + retriesLeft + " more times"
                                            : " again, giving up"));
                        }
                    } while (attemptNo++ <= numBusyRetries);
                }))
                .then(UtilVerbs.withOpContext((spec, opLog) -> {
                    if (success.get()) {
                        final var locs = new String[] {
                            novelTarget, novelTarget.replace(".pem", ".pass"), novelTarget.replace(".pem", ".words"),
                        };
                        final var accountId = "account" + createdNo.get();
                        for (final var loc : locs) {
                            try (final var fin = Files.newInputStream(Paths.get(loc))) {
                                fin.transferTo(Files.newOutputStream(Paths.get(loc.replace(NOVELTY, accountId))));
                            }
                            new File(loc).delete();
                        }
                    }
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    public AtomicLong getCreatedNo() {
        return createdNo;
    }
}
