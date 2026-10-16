// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.SIMPLE_FEE_SCHEDULE;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.hapi.support.fees.ServiceFeeDefinition;
import org.hiero.hapi.support.fees.ServiceFeeSchedule;
import org.junit.jupiter.api.DynamicTest;

/**
 * Verifies that an update of the simple fee schedule file is rejected, leaving the current schedule in place, when the
 * new schedule cannot price a transaction the network supports: it omits a function definition, an extra reference a
 * fee calculator charges, or an extra that a handler reads directly.
 */
public class SimpleFeeScheduleCoverageTest {
    private static final String ORIGINAL = "originalSimpleFeeSchedule";
    private static final String MODIFIED = "modifiedSimpleFeeSchedule";
    private static final String PAYER = "payer";

    @LeakyHapiTest(requirement = ContextRequirement.NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> scheduleWithoutAFunctionDefinitionIsRejected() {
        return transferAfterInstalling(
                withoutFunction(HederaFunctionality.CRYPTO_TRANSFER), ResponseCodeEnum.OK, "missing CRYPTO_TRANSFER");
    }

    @LeakyHapiTest(requirement = ContextRequirement.NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> scheduleWithoutAnExtraReferenceTheCalculatorNeedsIsRejected() {
        return transferAfterInstalling(
                withoutExtraRef(HederaFunctionality.CRYPTO_TRANSFER, Extra.ACCOUNTS),
                ResponseCodeEnum.OK,
                "CRYPTO_TRANSFER without ACCOUNTS ref");
    }

    @LeakyHapiTest(requirement = ContextRequirement.NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> scheduleWithoutAnExtraDefinitionAHandlerReadsIsRejected() {
        return hapiTest(
                cryptoCreate(PAYER).balance(100 * ONE_HBAR),
                cryptoCreate("receiver").maxAutomaticTokenAssociations(0),
                tokenCreate("token").treasury(PAYER).initialSupply(100L),
                getFileContents(SIMPLE_FEE_SCHEDULE).saveToRegistry(ORIGINAL),
                withOpContext((spec, opLog) -> {
                    final var original = FeeSchedule.PROTOBUF.parse(
                            Bytes.wrap(spec.registry().getBytes(ORIGINAL)));
                    final var mutated = FeeSchedule.PROTOBUF.toBytes(
                            withoutExtra(Extra.TOKEN_ASSOCIATE).apply(original));
                    spec.registry().saveBytes(MODIFIED, ByteString.copyFrom(mutated.toByteArray()));
                }),
                updateLargeFile(GENESIS, SIMPLE_FEE_SCHEDULE, MODIFIED),
                tokenAirdrop(moving(1L, "token").between(PAYER, "receiver"))
                        .payingWith(PAYER)
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                restore());
    }

    private static UnaryOperator<FeeSchedule> withoutExtra(final Extra extra) {
        return schedule -> schedule.copyBuilder()
                .extras(schedule.extras().stream()
                        .filter(def -> def.name() != extra)
                        .toList())
                .services(schedule.services().stream()
                        .map((ServiceFeeSchedule service) -> service.copyBuilder()
                                .schedule(service.schedule().stream()
                                        .map((ServiceFeeDefinition def) -> def.copyBuilder()
                                                .extras(def.extras().stream()
                                                        .filter(ref -> ref.name() != extra)
                                                        .toList())
                                                .build())
                                        .toList())
                                .build())
                        .toList())
                .node(schedule.node()
                        .copyBuilder()
                        .extras(schedule.node().extras().stream()
                                .filter(ref -> ref.name() != extra)
                                .toList())
                        .build())
                .build();
    }

    private static Stream<DynamicTest> transferAfterInstalling(
            final UnaryOperator<FeeSchedule> mutation, final ResponseCodeEnum expectedPrecheck, final String label) {
        return hapiTest(
                cryptoCreate(PAYER).balance(10 * ONE_HBAR),
                getFileContents(SIMPLE_FEE_SCHEDULE).saveToRegistry(ORIGINAL),
                withOpContext((spec, opLog) -> {
                    final var original = FeeSchedule.PROTOBUF.parse(
                            Bytes.wrap(spec.registry().getBytes(ORIGINAL)));
                    final var mutated = FeeSchedule.PROTOBUF.toBytes(mutation.apply(original));
                    spec.registry().saveBytes(MODIFIED, ByteString.copyFrom(mutated.toByteArray()));
                }),
                updateLargeFile(GENESIS, SIMPLE_FEE_SCHEDULE, MODIFIED),
                cryptoTransfer(tinyBarsFromTo(PAYER, GENESIS, 1L))
                        .payingWith(PAYER)
                        .hasPrecheck(expectedPrecheck)
                        .logging(),
                restore(),
                withOpContext((spec, opLog) -> opLog.info("{}: transfer precheck was {}", label, expectedPrecheck)));
    }

    private static SpecOperation restore() {
        return withOpContext((spec, opLog) -> allRunFor(spec, updateLargeFile(GENESIS, SIMPLE_FEE_SCHEDULE, ORIGINAL)));
    }

    private static UnaryOperator<FeeSchedule> withoutFunction(final HederaFunctionality function) {
        return schedule -> schedule.copyBuilder()
                .services(schedule.services().stream()
                        .map(service -> service.copyBuilder()
                                .schedule(service.schedule().stream()
                                        .filter(def -> def.name() != function)
                                        .toList())
                                .build())
                        .filter(service -> !service.schedule().isEmpty())
                        .toList())
                .build();
    }

    private static UnaryOperator<FeeSchedule> withoutExtraRef(final HederaFunctionality function, final Extra extra) {
        return schedule -> schedule.copyBuilder()
                .services(schedule.services().stream()
                        .map((ServiceFeeSchedule service) -> service.copyBuilder()
                                .schedule(service.schedule().stream()
                                        .map((ServiceFeeDefinition def) -> def.name() == function
                                                ? def.copyBuilder()
                                                        .extras(def.extras().stream()
                                                                .filter(ref -> ref.name() != extra)
                                                                .toList())
                                                        .build()
                                                : def)
                                        .toList())
                                .build())
                        .toList())
                .build();
    }
}
