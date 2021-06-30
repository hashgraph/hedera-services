package com.hedera.services.bdd.spec.queries.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;

import java.util.Optional;
import java.util.OptionalLong;

public class HapiTokenNftInfo {
    OptionalLong expectedSerialNum;
    Optional<ByteString> expectedMetadata;
    Optional<String> expectedTokenID;
    Optional<String> expectedAccountID;

    private HapiTokenNftInfo(Optional<String> tokenId, OptionalLong serialNum, Optional<String> accountId, Optional<ByteString> metadata) {
        this.expectedSerialNum = serialNum;
        this.expectedMetadata = metadata;
        this.expectedTokenID = tokenId;
        this.expectedAccountID = accountId;
    }

    public static HapiTokenNftInfo newTokenNftInfo(String tokenId, long serialNum, String accountId, ByteString metadata) {
        return new HapiTokenNftInfo(
                Optional.of(tokenId),
                OptionalLong.of(serialNum),
                Optional.of(accountId),
                Optional.of(metadata)
        );
    }
}
