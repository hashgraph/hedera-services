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
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.RandomStringUtils;

public class AutoCreateUtils {
    public static ByteString randomValidEd25519Alias() {
        final var alias = RandomStringUtils.random(128, true, true);
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(alias)).build().toByteString();
    }

    public static ByteString randomValidECDSAAlias() {
        final var alias = RandomStringUtils.random(128, true, true);
        return Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFromUtf8(alias))
                .build()
                .toByteString();
    }

    public static Key asKey(final ByteString alias) {
        Key aliasKey;
        try {
            aliasKey = Key.parseFrom(alias);
        } catch (InvalidProtocolBufferException ex) {
            return Key.newBuilder().build();
        }
        return aliasKey;
    }

    public static void updateSpecFor(HapiSpec spec, String alias) {
        var accountIDAtomicReference = new AtomicReference<AccountID>();
        allRunFor(spec, getAliasedAccountInfo(alias).exposingIdTo(accountIDAtomicReference::set));
        final var aliasKey = spec.registry().getKey(alias).toByteString().toStringUtf8();
        spec.registry().saveAccountAlias(aliasKey, accountIDAtomicReference.get());
        spec.registry().saveAccountId(alias, accountIDAtomicReference.get());
    }
}
