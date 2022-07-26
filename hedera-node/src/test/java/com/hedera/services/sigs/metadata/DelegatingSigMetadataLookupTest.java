/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelegatingSigMetadataLookupTest {
    private static final String SYMBOL = "NotAnHbar";
    private static final String TOKEN_NAME = "TokenName";
    private static final int DECIMALS = 2;
    private static final long TOTAL_SUPPLY = 1_000_000;
    private static final boolean FREEZE_DEFAULT = true;
    private static final boolean ACCOUNTS_KYC_GRANTED_BY_DEFAULT = true;

    private static final EntityId treasury = new EntityId(1, 2, 3);
    private static final TokenID id = IdUtils.asToken("1.2.666");

    private static final MerkleToken token =
            new MerkleToken(
                    Long.MAX_VALUE,
                    TOTAL_SUPPLY,
                    DECIMALS,
                    SYMBOL,
                    TOKEN_NAME,
                    FREEZE_DEFAULT,
                    ACCOUNTS_KYC_GRANTED_BY_DEFAULT,
                    treasury);
    private static final JKey freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());

    private TokenStore tokenStore;

    private Function<TokenID, SafeLookupResult<TokenSigningMetadata>> subject;

    @BeforeEach
    void setup() {
        tokenStore = mock(TokenStore.class);

        subject = DelegatingSigMetadataLookup.REF_LOOKUP_FACTORY.apply(tokenStore);
    }

    @Test
    void returnsExpectedFailIfExplicitlyMissing() {
        given(tokenStore.resolve(id))
                .willReturn(
                        TokenID.newBuilder()
                                .setShardNum(0L)
                                .setRealmNum(0L)
                                .setTokenNum(0L)
                                .build());

        final var result = subject.apply(id);

        assertEquals(KeyOrderingFailure.MISSING_TOKEN, result.failureIfAny());
    }

    @Test
    void returnsExpectedFailIfMissing() {
        given(tokenStore.resolve(id)).willReturn(TokenStore.MISSING_TOKEN);

        final var result = subject.apply(id);

        assertEquals(KeyOrderingFailure.MISSING_TOKEN, result.failureIfAny());
    }

    @Test
    void returnsExpectedMetaIfPresent() {
        token.setFreezeKey(freezeKey);
        final var expected = TokenMetaUtils.signingMetaFrom(token);
        given(tokenStore.resolve(id)).willReturn(id);
        given(tokenStore.get(id)).willReturn(token);

        final var result = subject.apply(id);

        assertEquals(KeyOrderingFailure.NONE, result.failureIfAny());
        assertEquals(expected.adminKey(), result.metadata().adminKey());
        assertEquals(expected.freezeKey(), result.metadata().freezeKey());
    }
}
