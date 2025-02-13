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

package com.hedera.node.app.spi.fixtures.ids;

import static com.swirlds.common.utility.CommonUtils.hex;
import static java.lang.System.arraycopy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.node.app.spi.ids.EntityIdFactory;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Fixed shard/realm implementation of {@link EntityIdFactory}.
 */
public class EntityIdFactoryImpl implements EntityIdFactory {
    private final long shard;
    private final long realm;

    public EntityIdFactoryImpl(final long shard, final long realm) {
        this.shard = shard;
        this.realm = realm;
    }

    @Override
    public TokenID newTokenId(long number) {
        return new TokenID(shard, realm, number);
    }

    @Override
    public TopicID newTopicId(long number) {
        return new TopicID(shard, realm, number);
    }

    @Override
    public ScheduleID newScheduleId(long number) {
        return new ScheduleID(shard, realm, number);
    }

    @Override
    public AccountID newAccountId(long number) {
        return new AccountID(shard, realm, new OneOf<>(AccountID.AccountOneOfType.ACCOUNT_NUM, number));
    }

    @Override
    public ContractID newContractId(long number) {
        return new ContractID(shard, realm, new OneOf<>(ContractID.ContractOneOfType.CONTRACT_NUM, number));
    }

    @Override
    public AccountID newAccountId(Bytes alias) {
        return new AccountID(shard, realm, new OneOf<>(AccountID.AccountOneOfType.ALIAS, alias));
    }

    @Override
    public String hexLongZero(long number) {
        final byte[] evmAddress = new byte[20];
        final var shardBytes = Ints.toByteArray((int) shard);
        final var realmBytes = Longs.toByteArray(realm);
        final var numBytes = Longs.toByteArray(number);

        arraycopy(shardBytes, 0, evmAddress, 0, 4);
        arraycopy(realmBytes, 0, evmAddress, 4, 8);
        arraycopy(numBytes, 0, evmAddress, 12, 8);

        return hex(evmAddress);
    }
}
