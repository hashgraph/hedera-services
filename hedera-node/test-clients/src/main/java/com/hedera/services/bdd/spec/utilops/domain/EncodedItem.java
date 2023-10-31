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

package com.hedera.services.bdd.spec.utilops.domain;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Base64;
import java.util.Objects;

/**
 * A POJO for Jackson to use in storing a Base64-encoded {@code (TransactionBody, TransactionRecord)} pair.
 */
public class EncodedItem {
    private String b64Body;
    private String b64Record;

    public static EncodedItem fromParsed(
            @NonNull final TransactionBody itemBody, @NonNull final TransactionRecord itemRecord) {
        Objects.requireNonNull(itemBody);
        Objects.requireNonNull(itemRecord);
        final var item = new EncodedItem();
        item.setB64Body(Base64.getEncoder().encodeToString(itemBody.toByteArray()));
        item.setB64Record(Base64.getEncoder().encodeToString(itemRecord.toByteArray()));
        return item;
    }

    public String getB64Body() {
        return b64Body;
    }

    public void setB64Body(@NonNull final String b64Body) {
        this.b64Body = Objects.requireNonNull(b64Body);
    }

    public String getB64Record() {
        return b64Record;
    }

    public void setB64Record(@NonNull final String b64Record) {
        this.b64Record = Objects.requireNonNull(b64Record);
    }

    public ParsedItem asParsedItem() {
        try {
            final var itemBody = TransactionBody.parseFrom(Base64.getDecoder().decode(b64Body));
            final var itemRecord =
                    TransactionRecord.parseFrom(Base64.getDecoder().decode(b64Record));
            return new ParsedItem(itemBody, itemRecord);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
