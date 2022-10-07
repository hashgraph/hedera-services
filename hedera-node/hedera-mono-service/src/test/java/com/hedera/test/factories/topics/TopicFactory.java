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
package com.hedera.test.factories.topics;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Optional;
import java.util.OptionalLong;

public class TopicFactory {
    private boolean isDeleted;
    private OptionalLong expiry = OptionalLong.empty();
    private OptionalLong autoRenewDuration = OptionalLong.empty();
    private Optional<Key> adminKey = Optional.empty();
    private Optional<Key> submitKey = Optional.empty();
    private Optional<String> memo = Optional.empty();
    private Optional<AccountID> autoRenewAccount = Optional.empty();

    private TopicFactory() {}

    public MerkleTopic get() throws Exception {
        MerkleTopic value = new MerkleTopic();

        value.setDeleted(isDeleted);
        memo.ifPresent(s -> value.setMemo(s));
        expiry.ifPresent(
                secs ->
                        value.setExpirationTimestamp(
                                RichInstant.fromGrpc(
                                        Timestamp.newBuilder().setSeconds(secs).build())));
        autoRenewDuration.ifPresent(value::setAutoRenewDurationSeconds);
        adminKey.ifPresent(k -> value.setAdminKey(uncheckedMap(k)));
        submitKey.ifPresent(k -> value.setSubmitKey(uncheckedMap(k)));
        autoRenewAccount.ifPresent(
                id -> value.setAutoRenewAccountId(EntityId.fromGrpcAccountId(id)));

        return value;
    }

    private JKey uncheckedMap(Key k) {
        try {
            return JKey.mapKey(k);
        } catch (Exception ignore) {
        }
        throw new AssertionError("Valid key failed to map!");
    }

    public static TopicFactory newTopic() {
        return new TopicFactory();
    }

    public TopicFactory memo(String s) {
        memo = Optional.of(s);
        return this;
    }

    public TopicFactory deleted(boolean flag) {
        isDeleted = flag;
        return this;
    }

    public TopicFactory adminKey(Key key) {
        adminKey = Optional.of(key);
        return this;
    }

    public TopicFactory submitKey(Key key) {
        submitKey = Optional.of(key);
        return this;
    }

    public TopicFactory autoRenewId(AccountID id) {
        autoRenewAccount = Optional.of(id);
        return this;
    }

    public TopicFactory expiry(long secs) {
        expiry = OptionalLong.of(secs);
        return this;
    }

    public TopicFactory autoRenewDuration(long secs) {
        autoRenewDuration = OptionalLong.of(secs);
        return this;
    }
}
