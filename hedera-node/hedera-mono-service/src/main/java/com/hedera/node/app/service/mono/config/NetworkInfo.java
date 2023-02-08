/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.config;

import static com.hedera.node.app.spi.config.PropertyNames.LEDGER_ID;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkInfo {

    private final PropertySource properties;
    private ByteString ledgerId;

    @Inject
    public NetworkInfo(@CompositeProps final PropertySource properties) {
        this.properties = properties;
    }

    public ByteString ledgerId() {
        if (ledgerId == null) {
            /*
             * Permanent ledger ids are to be set in a future specification. The provisional ids are,
             *   0x00 -> mainnet
             *   0x01 -> testnet
             *   0x02 -> previewnet
             *   0x03 -> other dev or preprod networks
             */
            ledgerId = rationalize(properties.getStringProperty(LEDGER_ID));
        }
        return ledgerId;
    }

    private ByteString rationalize(final String ledgerProperty) {
        if (!ledgerProperty.startsWith("0x")) {
            throw new IllegalArgumentException("Invalid LedgerId provided in properties ");
        } else {
            try {
                final var hex = ledgerProperty.substring(2);
                final var bytes = unhex(hex);
                return ByteString.copyFrom(bytes);
            } catch (final Exception e) {
                throw new IllegalArgumentException("Invalid LedgerId provided in properties ", e);
            }
        }
    }
}
