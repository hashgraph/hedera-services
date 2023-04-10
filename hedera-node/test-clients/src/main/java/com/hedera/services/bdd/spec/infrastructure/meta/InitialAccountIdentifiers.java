/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.meta;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Function;

/**
 * Represents a choice of the two account identifiers (key, alias) that can be used to
 * customize a {@link CryptoCreateTransactionBody}.
 *
 * <p>Helps the user by initializing a list of factories that, given an ECDSA key, will return one
 * of the 6 possible combinations of identifiers. (The key can only be present or not; but the
 * "secondary" alias identifier may be absent; present and congruent with the key; or
 * present and incongruent with the key.)
 *
 * @param key the ECDSA key to give as initial identifier (null if none)
 * @param alias the EVM address alias to give as initial identifier (null if none)
 */
@SuppressWarnings({"java:S6218", "java:S3358"})
public record InitialAccountIdentifiers(@Nullable Key key, @Nullable byte[] alias) {

    private enum KeyStatus {
        ABSENT,
        PRESENT
    }

    private enum SecondaryIdStatus {
        ABSENT,
        CONGRUENT_WITH_KEY,
        INCONGRUENT_WITH_KEY
    }

    private static final byte[] INCONGRUENT_ALIAS = randomUtf8Bytes(20);

    private static final List<Function<Key, InitialAccountIdentifiers>> ALL_COMBINATIONS = Arrays.stream(
                    KeyStatus.values())
            .flatMap(keyStatus -> Arrays.stream(SecondaryIdStatus.values())
                    .flatMap(aliasStatus -> Arrays.stream(SecondaryIdStatus.values())
                            .map(addressStatus -> fuzzerFor(keyStatus, aliasStatus))))
            .toList();

    private static final SplittableRandom RANDOM = new SplittableRandom();

    private static final int NUM_CHOICES = ALL_COMBINATIONS.size();

    public static InitialAccountIdentifiers fuzzedFrom(final Key key) {
        throwIfNotEcdsa(key);
        return ALL_COMBINATIONS.get(RANDOM.nextInt(NUM_CHOICES)).apply(key);
    }

    public void customize(final CryptoCreateTransactionBody.Builder op) {
        if (key != null) {
            op.setKey(key);
        }
        if (alias != null) {
            op.setAlias(ByteStringUtils.wrapUnsafely(alias));
        }
    }

    private static Function<Key, InitialAccountIdentifiers> fuzzerFor(
            final KeyStatus keyStatus, final SecondaryIdStatus aliasStatus) {
        return key -> new InitialAccountIdentifiers(
                keyStatus == KeyStatus.ABSENT ? null : key,
                aliasStatus == SecondaryIdStatus.ABSENT
                        ? null
                        : (aliasStatus == SecondaryIdStatus.CONGRUENT_WITH_KEY
                                ? EthSigsUtils.recoverAddressFromPubKey(key.toByteArray())
                                : INCONGRUENT_ALIAS));
    }

    public static void throwIfNotEcdsa(final Key key) {
        if (!key.hasECDSASecp256K1()) {
            throw new IllegalArgumentException("Key must be an ECDSA key to imply an address");
        }
    }
}
