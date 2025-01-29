/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssBlockHashSignerTest {
    private static final Bytes FAKE_BLOCK_HASH = Bytes.wrap("FAKE_BLOCK_HASH");
    private static final Bytes FAKE_HINTS_SIGNATURE = CommonUtils.noThrowSha384HashOf(FAKE_BLOCK_HASH);
    private static final Bytes FAKE_PROOF = Bytes.wrap("FAKE_PROOF");
    private static final Bytes FAKE_VK = Bytes.wrap("FAKE_VK");

    @Mock
    private HintsService hintsService;

    @Mock
    private HistoryService historyService;

    @Mock
    private ConfigProvider configProvider;

    private TssBlockHashSigner subject;

    enum HintsEnabled {
        YES,
        NO
    }

    enum HistoryEnabled {
        YES,
        NO
    }

    @Test
    void asExpectedWithNothingEnabled() {
        givenSubjectWith(HintsEnabled.NO, HistoryEnabled.NO);

        assertTrue(subject.isReady());

        final var signature = subject.signFuture(FAKE_BLOCK_HASH).join();

        assertEquals(FAKE_HINTS_SIGNATURE, signature);
    }

    @Test
    void asExpectedWithJustHintsEnabled() {
        givenSubjectWith(HintsEnabled.YES, HistoryEnabled.NO);

        assertFalse(subject.isReady());
        assertThrows(IllegalStateException.class, () -> subject.signFuture(FAKE_BLOCK_HASH));
        given(hintsService.isReady()).willReturn(true);
        assertTrue(subject.isReady());
        given(hintsService.signFuture(FAKE_BLOCK_HASH))
                .willReturn(CompletableFuture.completedFuture(FAKE_HINTS_SIGNATURE));

        final var signature = subject.signFuture(FAKE_BLOCK_HASH).join();

        assertSame(FAKE_HINTS_SIGNATURE, signature);
    }

    @Test
    void asExpectedWithJustHistoryEnabled() {
        givenSubjectWith(HintsEnabled.NO, HistoryEnabled.YES);

        assertFalse(subject.isReady());
        assertThrows(IllegalStateException.class, () -> subject.signFuture(FAKE_BLOCK_HASH));
        given(historyService.isReady()).willReturn(true);
        assertTrue(subject.isReady());
        given(historyService.getCurrentProof(Bytes.EMPTY)).willReturn(FAKE_PROOF);

        final var signature = subject.signFuture(FAKE_BLOCK_HASH).join();

        assertEquals(TssBlockHashSigner.assemble(FAKE_HINTS_SIGNATURE, Bytes.EMPTY, FAKE_PROOF), signature);
    }

    @Test
    void asExpectedWithBothEnabled() {
        givenSubjectWith(HintsEnabled.YES, HistoryEnabled.YES);

        assertFalse(subject.isReady());
        assertThrows(IllegalStateException.class, () -> subject.signFuture(FAKE_BLOCK_HASH));
        given(historyService.isReady()).willReturn(true);
        assertFalse(subject.isReady());
        assertThrows(IllegalStateException.class, () -> subject.signFuture(FAKE_BLOCK_HASH));
        given(hintsService.isReady()).willReturn(true);
        assertTrue(subject.isReady());
        given(hintsService.activeVerificationKeyOrThrow()).willReturn(FAKE_VK);
        given(hintsService.signFuture(FAKE_BLOCK_HASH))
                .willReturn(CompletableFuture.completedFuture(FAKE_HINTS_SIGNATURE));
        given(historyService.getCurrentProof(FAKE_VK)).willReturn(FAKE_PROOF);

        final var signature = subject.signFuture(FAKE_BLOCK_HASH).join();

        assertEquals(TssBlockHashSigner.assemble(FAKE_HINTS_SIGNATURE, FAKE_VK, FAKE_PROOF), signature);
    }

    private void givenSubjectWith(
            @NonNull final HintsEnabled hintsEnabled, @NonNull final HistoryEnabled historyEnabled) {
        given(configProvider.getConfiguration())
                .willReturn(new VersionedConfigImpl(configWith(hintsEnabled, historyEnabled), 123));
        subject = new TssBlockHashSigner(hintsService, historyService, configProvider);
    }

    private Configuration configWith(
            @NonNull final HintsEnabled hintsEnabled, @NonNull final HistoryEnabled historyEnabled) {
        return HederaTestConfigBuilder.create()
                .withValue("tss.hintsEnabled", "" + (hintsEnabled == HintsEnabled.YES))
                .withValue("tss.historyEnabled", "" + (historyEnabled == HistoryEnabled.YES))
                .getOrCreateConfig();
    }
}
