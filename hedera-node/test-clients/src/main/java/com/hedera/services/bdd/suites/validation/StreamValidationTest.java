/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.validation;

import static com.hedera.services.bdd.junit.support.RecordStreamAccess.RECORD_STREAM_ACCESS;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static java.util.stream.Collectors.joining;

import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.support.RecordStreamAccess;
import com.hedera.services.bdd.junit.support.RecordStreamValidator;
import com.hedera.services.bdd.junit.support.validators.BalanceReconciliationValidator;
import com.hedera.services.bdd.junit.support.validators.BlockNoValidator;
import com.hedera.services.bdd.junit.support.validators.ExpiryRecordsValidator;
import com.hedera.services.bdd.junit.support.validators.TokenReconciliationValidator;
import com.hedera.services.bdd.junit.support.validators.TransactionBodyValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag("STREAM_VALIDATION")
// Order this last to validate stream files after all other tests have run
@Order(Integer.MAX_VALUE)
public class StreamValidationTest {
    private static final long MAX_BLOCK_TIME_MS = 2000L;
    private static final long BUFFER_MS = 500L;
    private static final long MIN_GZIP_SIZE_IN_BYTES = 26;
    private static final String ERROR_PREFIX = "\n  - ";

    private static final List<RecordStreamValidator> STREAM_VALIDATORS = List.of(
            new BlockNoValidator(),
            new TransactionBodyValidator(),
            new ExpiryRecordsValidator(),
            new BalanceReconciliationValidator(),
            new TokenReconciliationValidator());

    @LeakyHapiTest
    final Stream<DynamicTest> validateStreamFiles() {
        return hapiTest(
                // Ensure the next transaction will be in a new block period
                sleepFor(MAX_BLOCK_TIME_MS + BUFFER_MS),
                // Submit a transaction
                cryptoTransfer((spec, b) -> {}).payingWith(GENESIS),
                // Wait for the next record file to be created
                sleepFor(2 * BUFFER_MS),
                // Run our validators
                withOpContext((spec, opLog) -> {
                    readMaybeStreamDataFor(spec, opLog).ifPresent(data -> {
                        final var maybeErrors = STREAM_VALIDATORS.stream()
                                .flatMap(v -> v.validationErrorsIn(data))
                                .map(Throwable::getMessage)
                                .collect(joining(ERROR_PREFIX));
                        if (!maybeErrors.isBlank()) {
                            throw new AssertionError("Record stream validation failed:" + ERROR_PREFIX + maybeErrors);
                        }
                    });
                }));
    }

    private static Optional<RecordStreamAccess.Data> readMaybeStreamDataFor(
            @NonNull final HapiSpec spec, @NonNull final Logger opLog) {
        RecordStreamAccess.Data data = null;
        final var streamLocs = spec.getNetworkNodes().stream()
                .map(HederaNode::getRecordStreamPath)
                .map(Path::toAbsolutePath)
                .map(Object::toString)
                .toList();
        for (final var loc : streamLocs) {
            try {
                opLog.info("Trying to read record files from {}", loc);
                data = RECORD_STREAM_ACCESS.readStreamDataFrom(
                        loc, "sidecar", f -> new File(f).length() > MIN_GZIP_SIZE_IN_BYTES);
                opLog.info("Read {} record files from {}", data.records().size(), loc);
            } catch (Exception ignore) {
                // We will try the next location, if any
            }
            if (data != null && !data.records().isEmpty()) {
                break;
            }
        }
        return Optional.ofNullable(data);
    }
}
