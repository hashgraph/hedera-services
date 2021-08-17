package com.hedera.services.sigs;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.test.factories.keys.KeyTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformSigOpsTest {
  private final byte[] EMPTY_SIG = new byte[0];
  private final byte[] MOCK_SIG = "FIRST".getBytes();
  private final byte[][] MORE_MOCK_SIGS =
      new byte[][] {
        "SECOND".getBytes(), "THIRD".getBytes(), "FOURTH".getBytes(), "FIFTH".getBytes()
      };
  private final byte[][] MORE_EMPTY_SIGS =
      new byte[][] {EMPTY_SIG, EMPTY_SIG, EMPTY_SIG, EMPTY_SIG};
  private final List<JKey> pubKeys = new ArrayList<>();
  private final List<KeyTree> kts =
      List.of(
          KeyTree.withRoot(ed25519()),
          KeyTree.withRoot(list(ed25519(), ed25519())),
          KeyTree.withRoot(threshold(1, list(ed25519()), ed25519())));
  private PubKeyToSigBytes sigBytes;
  private TxnScopedPlatformSigFactory sigFactory;

  @BeforeEach
  void setup() throws Throwable {
    pubKeys.clear();
    sigBytes = mock(PubKeyToSigBytes.class);
    sigFactory = mock(TxnScopedPlatformSigFactory.class);
    for (KeyTree kt : kts) {
      pubKeys.add(kt.asJKey());
    }
  }

  @Test
  void createsOnlyNonDegenerateSigs() throws Throwable {
    given(sigBytes.sigBytesFor(any())).willReturn(MOCK_SIG, MORE_EMPTY_SIGS);

    // when:
    PlatformSigsCreationResult result =
        createEd25519PlatformSigsFrom(pubKeys, sigBytes, sigFactory);

    // then:
    AtomicInteger nextSigIndex = new AtomicInteger(0);
    for (KeyTree kt : kts) {
      kt.traverseLeaves(
          leaf -> {
            ByteString pk = leaf.asKey().getEd25519();
            if (nextSigIndex.get() == 0) {
              verify(sigFactory).create(pk.toByteArray(), MOCK_SIG);
            } else {
              verify(sigFactory, never()).create(pk.toByteArray(), EMPTY_SIG);
            }
            nextSigIndex.addAndGet(1);
          });
    }
    // and:
    assertEquals(1, result.getPlatformSigs().size());
  }

  @Test
  void createsSigsInTraversalOrder() throws Throwable {
    given(sigBytes.sigBytesFor(any())).willReturn(MOCK_SIG, MORE_MOCK_SIGS);

    // when:
    PlatformSigsCreationResult result =
        createEd25519PlatformSigsFrom(pubKeys, sigBytes, sigFactory);

    // then:
    AtomicInteger nextSigIndex = new AtomicInteger(0);
    for (KeyTree kt : kts) {
      kt.traverseLeaves(
          leaf -> {
            ByteString pk = leaf.asKey().getEd25519();
            byte[] sigBytes =
                (nextSigIndex.get() == 0) ? MOCK_SIG : MORE_MOCK_SIGS[nextSigIndex.get() - 1];
            verify(sigFactory).create(pk.toByteArray(), sigBytes);
            nextSigIndex.addAndGet(1);
          });
    }
    // and:
    assertEquals(5, result.getPlatformSigs().size());
  }

  @Test
  void ignoresAmbiguousScheduledSig() throws Throwable {
    // setup:
    JKey scheduledKey = new JEd25519Key("01234578901234578901234578901".getBytes());
    // and:
    scheduledKey.setForScheduledTxn(true);

    given(sigBytes.sigBytesFor(any())).willThrow(KeyPrefixMismatchException.class);

    // when:
    PlatformSigsCreationResult result =
        createEd25519PlatformSigsFrom(List.of(scheduledKey), sigBytes, sigFactory);

    // then:
    assertFalse(result.hasFailed());
    assertTrue(result.getPlatformSigs().isEmpty());
  }

  @Test
  void doesntIgnoreUnrecognizedProblemForScheduledSig() throws Throwable {
    // setup:
    JKey scheduledKey = new JEd25519Key("01234578901234578901234578901".getBytes());
    // and:
    scheduledKey.setForScheduledTxn(true);

    given(sigBytes.sigBytesFor(any())).willThrow(IllegalStateException.class);

    // when:
    PlatformSigsCreationResult result =
        createEd25519PlatformSigsFrom(List.of(scheduledKey), sigBytes, sigFactory);

    // then:
    assertTrue(result.hasFailed());
  }

  @Test
  void failsOnInsufficientSigs() throws Throwable {
    given(sigBytes.sigBytesFor(any())).willReturn(MOCK_SIG).willThrow(Exception.class);

    // when:
    PlatformSigsCreationResult result =
        createEd25519PlatformSigsFrom(pubKeys, sigBytes, sigFactory);

    // then:
    assertEquals(1, result.getPlatformSigs().size());
    assertTrue(result.hasFailed());
  }

  @Test
  void returnsSuccessSigStatusByDefault() {
    // given:
    PlatformSigsCreationResult subject = new PlatformSigsCreationResult();

    // when:
    final var status = subject.asCode();

    // then:
    assertEquals(OK, status);
  }

  @Test
  void reportsInvalidSigMap() {
    // given:
    PlatformSigsCreationResult subject = new PlatformSigsCreationResult();
    // and:
    subject.setTerminatingEx(new KeyPrefixMismatchException("No!"));

    // when:
    final var status = subject.asCode();

    // then:
    assertEquals(KEY_PREFIX_MISMATCH, status);
  }

  @Test
  void reportsNonspecificInvalidSig() {
    // given:
    PlatformSigsCreationResult subject = new PlatformSigsCreationResult();
    // and:
    subject.setTerminatingEx(new Exception());

    // when:
    final var status = subject.asCode();

    // then:
    assertEquals(INVALID_SIGNATURE, status);
  }
}
