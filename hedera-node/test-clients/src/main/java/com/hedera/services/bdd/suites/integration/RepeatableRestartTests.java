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

package com.hedera.services.bdd.suites.integration;

import static com.hedera.hapi.node.state.tss.RosterToKey.NONE;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.KEYING_COMPLETE;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_ENCRYPTION_KEYS;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_KEY_MATERIAL_GENERATOR;
import static com.hedera.services.bdd.junit.restart.RestartType.GENESIS;
import static com.hedera.services.bdd.junit.restart.StartupAssets.ROSTER_AND_FULL_TSS_KEY_MATERIAL;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcingContextual;
import static com.swirlds.platform.roster.RosterUtils.rosterFrom;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.ConfigOverride;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.hedera.TssKeyMaterial;
import com.hedera.services.bdd.junit.restart.RestartHapiTest;
import com.hedera.services.bdd.spec.utilops.EmbeddedVerbs;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Order(3)
@Tag(INTEGRATION)
@TargetEmbeddedMode(REPEATABLE)
public class RepeatableRestartTests {
    @RestartHapiTest(
            restartType = GENESIS,
            bootstrapOverrides = {
                @ConfigOverride(key = "tss.keyCandidateRoster", value = "true"),
                @ConfigOverride(key = "addressBook.useRosterLifecycle", value = "true")
            },
            startupAssets = ROSTER_AND_FULL_TSS_KEY_MATERIAL)
    Stream<DynamicTest> genesisMigrationIncorporatesAllAvailableTssMaterial() {
        final AtomicReference<TssKeyMaterial> expectedMaterial = new AtomicReference<>();
        return hapiTest(
                doingContextual(spec -> expectedMaterial.set(CLASSIC_KEY_MATERIAL_GENERATOR.apply(rosterFrom(
                        spec.getNetworkNodes().getFirst().startupNetwork().orElseThrow())))),
                // --- TSS STATE VALIDATIONS ---
                // . TssStatus
                sourcing(() -> EmbeddedVerbs.<TssStatus>viewSingleton(
                        TssBaseService.NAME,
                        TSS_STATUS_KEY,
                        status -> assertEquals(
                                new TssStatus(
                                        KEYING_COMPLETE,
                                        NONE,
                                        expectedMaterial.get().ledgerId()),
                                status))),
                // . TssEncryptionKeys
                sourcingContextual(spec -> EmbeddedVerbs.<EntityNumber, TssEncryptionKeys>viewKVState(
                        TssBaseService.NAME, TSS_ENCRYPTION_KEYS_KEY, encryptionKeys -> {
                            final var actualKeys = StreamSupport.stream(
                                            spliteratorUnknownSize(encryptionKeys.keys(), NONNULL), false)
                                    .sorted(Comparator.comparingLong(EntityNumber::number))
                                    .map(encryptionKeys::get)
                                    .filter(Objects::nonNull)
                                    .map(TssEncryptionKeys::currentEncryptionKey)
                                    .toList();
                            assertEquals(
                                    spec.getNetworkNodes().size(),
                                    actualKeys.size(),
                                    "Wrong number of encryption keys");
                            StreamSupport.stream(spliteratorUnknownSize(encryptionKeys.keys(), NONNULL), false)
                                    .forEach(key -> assertEquals(
                                            CLASSIC_ENCRYPTION_KEYS.get(key.number()),
                                            requireNonNull(encryptionKeys.get(key))
                                                    .currentEncryptionKey()));
                        })),
                // . TssMessages
                EmbeddedVerbs.<TssMessageMapKey, TssMessageTransactionBody>viewKVState(
                        TssBaseService.NAME, TSS_MESSAGE_MAP_KEY, tssMessageOps -> {
                            final var ops = StreamSupport.stream(
                                            spliteratorUnknownSize(tssMessageOps.keys(), NONNULL), false)
                                    .sorted(Comparator.comparingLong(TssMessageMapKey::sequenceNumber))
                                    .map(tssMessageOps::get)
                                    .toList();
                            assertEquals(expectedMaterial.get().tssMessages(), ops);
                        }),
                // . TssVotes
                sourcingContextual(spec -> EmbeddedVerbs.<TssVoteMapKey, TssVoteTransactionBody>viewKVState(
                        TssBaseService.NAME, TSS_VOTE_MAP_KEY, tssVotes -> {
                            final var votes = StreamSupport.stream(
                                            spliteratorUnknownSize(tssVotes.keys(), NONNULL), false)
                                    .sorted(Comparator.comparingLong(TssVoteMapKey::nodeId))
                                    .map(tssVotes::get)
                                    .toList();
                            assertEquals(spec.getNetworkNodes().size(), votes.size(), "Wrong number of votes");
                            final var bitSet = new BitSet();
                            for (int i = 0,
                                            n =
                                                    expectedMaterial
                                                            .get()
                                                            .tssMessages()
                                                            .size();
                                    i < n;
                                    i++) {
                                bitSet.set(i);
                            }
                            final var tssVote = Bytes.wrap(bitSet.toByteArray());
                            StreamSupport.stream(spliteratorUnknownSize(tssVotes.keys(), NONNULL), false)
                                    .forEach(key -> assertEquals(
                                            tssVote,
                                            requireNonNull(tssVotes.get(key)).tssVote()));
                        })));
    }
}
