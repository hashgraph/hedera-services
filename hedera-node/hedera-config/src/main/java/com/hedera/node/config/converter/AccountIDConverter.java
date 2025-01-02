/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.converter;

import com.hedera.hapi.node.base.AccountID;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;

/**
 * Config api {@link ConfigConverter} implementation for the type {@link AccountID}.
 */
public class AccountIDConverter implements ConfigConverter<AccountID> {

    @Override
    public AccountID convert(@NonNull final String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("null can not be converted");
        }
        try {
            final long[] nums =
                    Stream.of(value.split("[.]")).mapToLong(Long::parseLong).toArray();
            if (nums.length != 3) {
                throw new IllegalArgumentException("Does not match pattern 'A.B.C'");
            }
            if (nums[0] < 0) {
                throw new IllegalArgumentException("Shared num of value is negative");
            }
            if (nums[1] < 0) {
                throw new IllegalArgumentException("Realm num of value is negative");
            }
            if (nums[2] < 0) {
                throw new IllegalArgumentException("Account num of value is negative");
            }
            return AccountID.newBuilder()
                    .shardNum(nums[0])
                    .realmNum(nums[1])
                    .accountNum(nums[2])
                    .build();
        } catch (final Exception e) {
            throw new IllegalArgumentException("'" + value + "' can not be parsed to " + AccountID.class.getName(), e);
        }
    }
}
