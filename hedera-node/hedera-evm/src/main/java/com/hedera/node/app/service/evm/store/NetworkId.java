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
package com.hedera.node.app.service.evm.store;

import com.google.protobuf.ByteString;

public interface NetworkId {
    /**
     * Permanent ledger ids are to be set in a future specification. The provisional ids are, 0x00
     * -> mainnet 0x01 -> testnet 0x02 -> previewnet 0x03 -> other dev or preprod networks
     */
    ByteString MAIN_NET = ByteString.fromHex("0x00");

    ByteString TEST_NET = ByteString.fromHex("0x01");
    ByteString PREVIEW_NET = ByteString.fromHex("0x02");
    ByteString CUSTOM = ByteString.fromHex("0x03");
}
