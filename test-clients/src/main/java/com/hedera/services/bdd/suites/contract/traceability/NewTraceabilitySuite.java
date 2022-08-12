package com.hedera.services.bdd.suites.contract.traceability;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewTraceabilitySuite extends HapiApiSuite {
    public static final String EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";
    private static final Logger log = LogManager.getLogger(NewTraceabilitySuite.class);

    /* Fields for sidecar assertion */
    private static SidecarWatcher sidecarWatcher;
    private static CompletableFuture<Void> sidecarWatcherTask;

    public static void main(String... args) {
        new NewTraceabilitySuite().runSuiteSync();
    }

    private static void initWatching() {
        sidecarWatcher = new SidecarWatcher();
        sidecarWatcherTask =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                String recordStreamPath;
                                if (HapiApiSpec.isRunningInCi()) {
                                    recordStreamPath =
                                            HapiApiSpec.ciPropOverrides().get("recordStream.path");
                                } else {
                                    recordStreamPath =
                                            HapiSpecSetup.getDefaultPropertySource()
                                                    .get("recordStream.path");
                                }
                                sidecarWatcher.startWatching(recordStreamPath);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        initWatching();
        return List.of(vanillaBytecodeSidecar(), vanillaBytecodeSidecar2(), assertSidecars());
    }

    private HapiApiSpec vanillaBytecodeSidecar() {
        final var vanillaBytecodeSidecar = "vanillaBytecodeSidecar";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(vanillaBytecodeSidecar)
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .hasKnownStatus(SUCCESS)
                                .via(firstTxn),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var txnRecord = getTxnRecord(firstTxn);
                                    final String runtimeBytecode = "runtimeBytecode";
                                    final var contractBytecode =
                                            getContractBytecode(EMPTY_CONSTRUCTOR_CONTRACT)
                                                    .saveResultTo(runtimeBytecode);
                                    allRunFor(spec, txnRecord, contractBytecode);
                                    final var consensusTimestamp =
                                            txnRecord.getResponseRecord().getConsensusTimestamp();
                                    final var initCode =
                                            extractBytecodeUnhexed(
                                                    getResourcePath(
                                                            EMPTY_CONSTRUCTOR_CONTRACT, ".bin"));
                                    sidecarWatcher.addExpectedSidecar(
                                            Pair.of(
                                                    vanillaBytecodeSidecar,
                                                    TransactionSidecarRecord.newBuilder()
                                                            .setConsensusTimestamp(
                                                                    consensusTimestamp)
                                                            .setBytecode(
                                                                    ContractBytecode.newBuilder()
                                                                            .setContractId(
                                                                                    txnRecord
                                                                                            .getResponseRecord()
                                                                                            .getContractCreateResult()
                                                                                            .getContractID())
                                                                            .setInitcode(initCode)
                                                                            .setRuntimeBytecode(
                                                                                    ByteString
                                                                                            .copyFrom(
                                                                                                    spec.registry()
                                                                                                            .getBytes(
                                                                                                                    runtimeBytecode)))
                                                                            .build())
                                                            .build()));
                                }));
    }

    HapiApiSpec vanillaBytecodeSidecar2() {
        final var contract = "CreateTrivial";
        final String trivialCreate = "vanillaBytecodeSidecar2";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(trivialCreate)
                .given(uploadInitCode(contract), contractCreate(contract).via(firstTxn))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var txnRecord = getTxnRecord(firstTxn);
                                    final String runtimeBytecode = "runtimeBytecode2";
                                    final var contractBytecode =
                                            getContractBytecode(contract)
                                                    .saveResultTo(runtimeBytecode);
                                    allRunFor(spec, txnRecord, contractBytecode);
                                    final var consensusTimestamp =
                                            txnRecord.getResponseRecord().getConsensusTimestamp();
                                    final var initCode =
                                            extractBytecodeUnhexed(
                                                    getResourcePath(contract, ".bin"));

                                    sidecarWatcher.addExpectedSidecar(
                                            Pair.of(
                                                    trivialCreate,
                                                    TransactionSidecarRecord.newBuilder()
                                                            .setConsensusTimestamp(
                                                                    consensusTimestamp)
                                                            .setBytecode(
                                                                    ContractBytecode.newBuilder()
                                                                            .setContractId(
                                                                                    txnRecord
                                                                                            .getResponseRecord()
                                                                                            .getContractCreateResult()
                                                                                            .getContractID())
                                                                            .setInitcode(initCode)
                                                                            .setRuntimeBytecode(
                                                                                    ByteString
                                                                                            .copyFrom(
                                                                                                    spec.registry()
                                                                                                            .getBytes(
                                                                                                                    runtimeBytecode)))
                                                                            .build())
                                                            .build()));
                                }));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                // send a dummy transaction to trigger externalization of last sidecars
                .given(
                        withOpContext(
                                (spec, opLog) -> sidecarWatcher.finishWatchingAfterNextSidecar()),
                        cryptoCreate("assertSidecars").delayBy(2000))
                .when()
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    // wait until assertion thread is finished
                                    sidecarWatcherTask.join();

                                    assertTrue(
                                            sidecarWatcher.thereAreNoMismatchedSidecars(),
                                            sidecarWatcher.printErrors());
                                    assertTrue(
                                            sidecarWatcher.thereAreNoWaitingSidecars(),
                                            "There are some sidecars that have not been yet externalized in the sidecar files after all specs.");
                                    //                                    assertTrue(
                                    //
                                    // failedSidecars.isEmpty(),
                                    //                                            "Mismatch(es)
                                    // between actual/expected sidecars present: "
                                    //                                                    +
                                    // prettyPrintFailedSidecars());
                                    //                                    assertEquals(
                                    //                                            0,
                                    //
                                    // expectedSidecars.size(),
                                    //                                            "There are some
                                    // sidecars that have not been yet externalized in the sidecar
                                    // files after all specs.");
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
