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

package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.BLOCK_STREAMS_DIR;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.RECORD_STREAMS_DIR;
import static com.hedera.services.bdd.junit.support.BlockStreamAccess.BLOCK_STREAM_ACCESS;
import static com.hedera.services.bdd.junit.support.StreamFileAccess.STREAM_FILE_ACCESS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.hedera.hapi.block.stream.Block;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.junit.support.RecordStreamValidator;
import com.hedera.services.bdd.junit.support.StreamFileAccess;
import com.hedera.services.bdd.junit.support.validators.BalanceReconciliationValidator;
import com.hedera.services.bdd.junit.support.validators.BlockNoValidator;
import com.hedera.services.bdd.junit.support.validators.ExpiryRecordsValidator;
import com.hedera.services.bdd.junit.support.validators.TokenReconciliationValidator;
import com.hedera.services.bdd.junit.support.validators.TransactionBodyValidator;
import com.hedera.services.bdd.junit.support.validators.block.BlockContentsValidator;
import com.hedera.services.bdd.junit.support.validators.block.StateChangesValidator;
import com.hedera.services.bdd.junit.support.validators.block.TransactionRecordParityValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * A {@link UtilOp} that validates the streams produced by the target network of the given
 * {@link HapiSpec}. Note it suffices to validate the streams produced by a single node in
 * the network since at minimum log validation will fail in case of an ISS.
 */
public class StreamValidationOp extends UtilOp {
    private static final Logger log = LogManager.getLogger(StreamValidationOp.class);

    private static final long MAX_BLOCK_TIME_MS = 2000L;
    private static final long BUFFER_MS = 500L;
    private static final long MIN_GZIP_SIZE_IN_BYTES = 26;
    private static final String ERROR_PREFIX = "\n  - ";

    private static final List<RecordStreamValidator> RECORD_STREAM_VALIDATORS = List.of(
            new BlockNoValidator(),
            new TransactionBodyValidator(),
            new ExpiryRecordsValidator(),
            new BalanceReconciliationValidator(),
            new TokenReconciliationValidator());

    private static final List<BlockStreamValidator.Factory> BLOCK_STREAM_VALIDATOR_FACTORIES = List.of(
            TransactionRecordParityValidator.FACTORY, StateChangesValidator.FACTORY, BlockContentsValidator.FACTORY);

    public static void main(String[] args) {}

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        // Prepare streams for record validators that depend on querying the network and hence
        // cannot be run after submitting a freeze
        allRunFor(
                spec,
                // Ensure the CryptoTransfer below will be in a new block period
                sleepFor(MAX_BLOCK_TIME_MS + BUFFER_MS),
                cryptoTransfer((ignore, b) -> {}).payingWith(GENESIS),
                // Wait for the final record file to be created
                sleepFor(2 * BUFFER_MS));
        // Validate the record streams
        final AtomicReference<StreamFileAccess.RecordStreamData> dataRef = new AtomicReference<>();
        readMaybeRecordStreamDataFor(spec)
                .ifPresentOrElse(
                        data -> {
                            final var maybeErrors = RECORD_STREAM_VALIDATORS.stream()
                                    .flatMap(v -> v.validationErrorsIn(data))
                                    .peek(t -> log.error("Record stream validation error!", t))
                                    .map(Throwable::getMessage)
                                    .collect(joining(ERROR_PREFIX));
                            if (!maybeErrors.isBlank()) {
                                throw new AssertionError(
                                        "Record stream validation failed:" + ERROR_PREFIX + maybeErrors);
                            }
                            dataRef.set(data);
                        },
                        () -> Assertions.fail("No record stream data found"));
        // If there are no block streams to validate, we are done
        if (spec.startupProperties().getStreamMode("blockStream.streamMode") == RECORDS) {
            return false;
        }
        // Freeze the network
        allRunFor(
                spec,
                freezeOnly().payingWith(GENESIS).startingIn(2).seconds(),
                // Wait for the final stream files to be created
                sleepFor(10 * BUFFER_MS));
        readMaybeBlockStreamsFor(spec)
                .ifPresentOrElse(
                        blocks -> {
                            // Re-read the record streams since they may have been updated
                            readMaybeRecordStreamDataFor(spec)
                                    .ifPresentOrElse(
                                            dataRef::set, () -> Assertions.fail("No record stream data found"));
                            final var data = requireNonNull(dataRef.get());
                            final var maybeErrors = BLOCK_STREAM_VALIDATOR_FACTORIES.stream()
                                    .filter(factory -> factory.appliesTo(spec))
                                    .map(factory -> factory.create(spec))
                                    .flatMap(v -> v.validationErrorsIn(blocks, data))
                                    .map(Throwable::getMessage)
                                    .collect(joining(ERROR_PREFIX));
                            if (!maybeErrors.isBlank()) {
                                throw new AssertionError(
                                        "Block stream validation failed:" + ERROR_PREFIX + maybeErrors);
                            }
                        },
                        () -> Assertions.fail("No block streams found"));
        return false;
    }

    private static Optional<List<Block>> readMaybeBlockStreamsFor(@NonNull final HapiSpec spec) {
        List<Block> blocks = null;
        final var blockPaths = spec.getNetworkNodes().stream()
                .map(node -> node.getExternalPath(BLOCK_STREAMS_DIR))
                .map(Path::toAbsolutePath)
                .toList();
        for (final var path : blockPaths) {
            try {
                log.info("Trying to read blocks from {}", path);
                blocks = BLOCK_STREAM_ACCESS.readBlocks(path);
                log.info("Read {} blocks from {}", blocks.size(), path);
            } catch (Exception ignore) {
                // We will try to read the next node's streams
            }
            if (blocks != null && !blocks.isEmpty()) {
                break;
            }
        }
        return Optional.ofNullable(blocks);
    }

    static Optional<StreamFileAccess.RecordStreamData> readMaybeRecordStreamDataFor(@NonNull final HapiSpec spec) {
        StreamFileAccess.RecordStreamData data = null;
        final var streamLocs = spec.getNetworkNodes().stream()
                .map(node -> node.getExternalPath(RECORD_STREAMS_DIR))
                .map(Path::toAbsolutePath)
                .map(Object::toString)
                .toList();
        for (final var loc : streamLocs) {
            try {
                log.info("Trying to read record files from {}", loc);
                data = STREAM_FILE_ACCESS.readStreamDataFrom(
                        loc, "sidecar", f -> new File(f).length() > MIN_GZIP_SIZE_IN_BYTES);
                log.info("Read {} record files from {}", data.records().size(), loc);
            } catch (Exception ignore) {
                // We will try to read the next node's streams
            }
            if (data != null && !data.records().isEmpty()) {
                break;
            }
        }
        return Optional.ofNullable(data);
    }
}
