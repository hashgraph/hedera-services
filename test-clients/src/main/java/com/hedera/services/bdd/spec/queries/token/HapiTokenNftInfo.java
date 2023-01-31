/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.queries.token;

import com.google.protobuf.ByteString;
import java.util.Optional;
import java.util.OptionalLong;

public class HapiTokenNftInfo {
    private OptionalLong expectedSerialNum;
    private Optional<ByteString> expectedMetadata;
    private Optional<String> expectedTokenID;
    private Optional<String> expectedAccountID;
    private Optional<String> expectedLedgerID;

    private HapiTokenNftInfo(
            Optional<String> tokenId,
            OptionalLong serialNum,
            Optional<String> accountId,
            Optional<ByteString> metadata,
            Optional<String> ledgerId) {
        this.expectedSerialNum = serialNum;
        this.expectedMetadata = metadata;
        this.expectedTokenID = tokenId;
        this.expectedAccountID = accountId;
        this.expectedLedgerID = ledgerId;
    }

    public static HapiTokenNftInfo newTokenNftInfo(
            final String tokenId,
            final long serialNum,
            final String accountId,
            final ByteString metadata,
            final String ledgerId) {
        return new HapiTokenNftInfo(
                Optional.of(tokenId),
                OptionalLong.of(serialNum),
                Optional.of(accountId),
                Optional.of(metadata),
                Optional.of(ledgerId));
    }

    public static HapiTokenNftInfo newTokenNftInfo(
            final String tokenId,
            final long serialNum,
            final String accountId,
            final ByteString metadata) {
        return newTokenNftInfo(tokenId, serialNum, accountId, metadata, "0x03");
    }

    public OptionalLong getExpectedSerialNum() {
        return expectedSerialNum;
    }

    public Optional<ByteString> getExpectedMetadata() {
        return expectedMetadata;
    }

    public Optional<String> getExpectedTokenID() {
        return expectedTokenID;
    }

    public Optional<String> getExpectedAccountID() {
        return expectedAccountID;
    }

    public Optional<String> getExpectedLedgerID() {
        return expectedLedgerID;
    }
}
