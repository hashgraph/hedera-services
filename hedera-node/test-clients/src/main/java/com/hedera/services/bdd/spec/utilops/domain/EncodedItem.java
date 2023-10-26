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
import java.util.Base64;

public class EncodedItem {
    private String b64Body;
    private String b64Record;

    public static EncodedItem fromParsed(final TransactionBody itemBody, final TransactionRecord itemRecord) {
        final var item = new EncodedItem();
        item.setB64Body(Base64.getEncoder().encodeToString(itemBody.toByteArray()));
        item.setB64Record(Base64.getEncoder().encodeToString(itemRecord.toByteArray()));
        return item;
    }

    public String getB64Body() {
        return b64Body;
    }

    public void setB64Body(String b64Body) {
        this.b64Body = b64Body;
    }

    public String getB64Record() {
        return b64Record;
    }

    public void setB64Record(String b64Record) {
        this.b64Record = b64Record;
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
