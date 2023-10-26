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

import java.util.List;

/**
 * A POJO for Jackson to use in storing a list of Base64-encoded {@code (TransactionBody, TransactionRecord)} pairs
 * along with a placeholder entity number used to fuzzy-match the entity ids in these pairs.
 */
public class RecordSnapshot {
    private long placeholderNum;
    private List<EncodedItem> encodedItems;

    public long getPlaceholderNum() {
        return placeholderNum;
    }

    public void setPlaceholderNum(long placeholderNum) {
        this.placeholderNum = placeholderNum;
    }

    public List<EncodedItem> getEncodedItems() {
        return encodedItems;
    }

    public void setEncodedItems(List<EncodedItem> encodedItems) {
        this.encodedItems = encodedItems;
    }

    public List<ParsedItem> parsedItems() {
        return encodedItems.stream().map(EncodedItem::asParsedItem).toList();
    }
}
