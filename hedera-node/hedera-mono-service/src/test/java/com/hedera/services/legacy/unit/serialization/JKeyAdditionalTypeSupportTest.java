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
package com.hedera.services.legacy.unit.serialization;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JContractAliasKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.ByteArrayInputStream;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

class JKeyAdditionalTypeSupportTest {
    @Test
    void serializingJContractIDKeyTest() throws Exception {
        final var cid = ContractID.newBuilder().setShardNum(0).setRealmNum(0).setContractNum(1001);
        final var key = Key.newBuilder().setContractID(cid).build();
        commonAssertions(key);
    }

    @Test
    void serializingJRSA_3072KeyTest() throws Exception {
        final var keyBytes = TxnUtils.randomUtf8ByteString(3072 / 8);
        final var key = Key.newBuilder().setRSA3072(keyBytes).build();
        commonAssertions(key);
    }

    @Test
    void canMapAndUnmapContractAliasKeys() throws DecoderException {
        final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var input =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(mockAddr)).build();
        final var subject = new JContractAliasKey(input);

        final var asGrpc = JKey.mapJKey(subject);
        assertEquals(input, asGrpc.getContractID());

        final var reconstructed = JKey.mapKey(asGrpc);
        assertTrue(JKey.equalUpToDecodability(subject, reconstructed));
    }

    @Test
    void canMapAndUnmapContractDelegateAliasKeys() throws DecoderException {
        final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var input =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(mockAddr)).build();
        final var subject = new JDelegatableContractAliasKey(input);

        final var asGrpc = JKey.mapJKey(subject);
        assertEquals(input, asGrpc.getDelegatableContractId());

        final var reconstructed = JKey.mapKey(asGrpc);
        assertTrue(JKey.equalUpToDecodability(subject, reconstructed));
    }

    private void commonAssertions(final Key key) throws Exception {
        final var jKey = JKey.mapKey(key);

        final var ser = JKeySerializer.serialize(jKey);
        final var in = new ByteArrayInputStream(ser);
        final var dis = new SerializableDataInputStream(in);

        final var jKeyReborn = (JKey) JKeySerializer.deserialize(dis);
        final var keyReborn = JKey.mapJKey(jKeyReborn);
        assertEquals(key, keyReborn);

        final var serReborn = JKeySerializer.serialize(jKeyReborn);
        assertArrayEquals(ser, serReborn);
    }
}
