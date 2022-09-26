/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigMapScheduleClassifierTest {
    static final TransactionSignature VALID_SIG = new ValidSignature();
    static final TransactionSignature INVALID_PRESENT_SIG = new PresentInvalidSignature();

    static String aPrefix = "a", abPrefix = "ab", cPrefix = "c";
    static SignaturePair aPair =
            SignaturePair.newBuilder().setPubKeyPrefix(ByteString.copyFromUtf8(aPrefix)).build();
    static SignatureMap sigMap = SignatureMap.newBuilder().addSigPair(aPair).build();

    static JKey a = new JECDSASecp256k1Key(pretendKeyStartingWith(aPrefix));
    static JKey both = new JEd25519Key(pretendKeyStartingWith(abPrefix));
    static JKey neither = new JEd25519Key(pretendKeyStartingWith(cPrefix));
    static JKey incomparable = new JContractIDKey(0, 0, 1234);

    @Mock Function<byte[], TransactionSignature> sigsFn;

    SigMapScheduleClassifier subject = new SigMapScheduleClassifier();

    @Test
    void returnsEmptyOptionalIfNonPrimitiveInner() {
        var answer =
                subject.validScheduleKeys(
                        List.of(incomparable), sigMap, sigsFn, new MatchingInvalidASig());

        // then:
        assertEquals(Optional.empty(), answer);
    }

    @Test
    void returnsEmptyOptionalIfInvalidInner() {
        given(sigsFn.apply(a.getECDSASecp256k1Key())).willReturn(INVALID_MISSING_SIG);

        // when:
        var answer =
                subject.validScheduleKeys(List.of(a), sigMap, sigsFn, new MatchingInvalidASig());

        // then:
        assertEquals(Optional.empty(), answer);
    }

    @Test
    void ignoresInvalidInnerIfMatchingOuter() {
        given(sigsFn.apply(a.getECDSASecp256k1Key())).willReturn(VALID_SIG);

        // when:
        var answer =
                subject.validScheduleKeys(List.of(a), sigMap, sigsFn, new MatchingInvalidASig());

        // then:
        assertEquals(Optional.of(Collections.emptyList()), answer);
    }

    @Test
    void prioritizesValidScheduledSig() {
        // when:
        var answer =
                subject.validScheduleKeys(
                        List.of(neither), sigMap, sigsFn, new MatchingValidAInvalidABSig());

        // then:
        assertEquals(Optional.of(List.of(a)), answer);
    }

    private static class MatchingInvalidASig
            implements Consumer<BiConsumer<JKey, TransactionSignature>> {
        @Override
        public void accept(BiConsumer<JKey, TransactionSignature> visitor) {
            visitor.accept(a, INVALID_PRESENT_SIG);
        }
    }

    private static class MatchingValidAInvalidABSig
            implements Consumer<BiConsumer<JKey, TransactionSignature>> {
        @Override
        public void accept(BiConsumer<JKey, TransactionSignature> visitor) {
            visitor.accept(a, VALID_SIG);
            visitor.accept(both, INVALID_PRESENT_SIG);
        }
    }

    static byte[] pretendKeyStartingWith(String prefix) {
        return (prefix + PRETEND_KEY.substring(prefix.length())).getBytes();
    }

    static final String PRETEND_KEY = "01234567890123456789012345678901";

    private static class ValidSignature extends TransactionSignature {
        private static byte[] MEANINGLESS_BYTE = new byte[] {(byte) 0xAB};

        public ValidSignature() {
            super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
        }

        @Override
        public VerificationStatus getSignatureStatus() {
            return VALID;
        }
    }

    private static class PresentInvalidSignature extends TransactionSignature {
        private static byte[] MEANINGLESS_BYTE = new byte[] {(byte) 0xAB};

        public PresentInvalidSignature() {
            super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
        }

        @Override
        public VerificationStatus getSignatureStatus() {
            return INVALID;
        }
    }
}
