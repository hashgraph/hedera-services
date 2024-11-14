/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.blskeygen.pem;

/**
 * Subset of handled Pem File Types as defined in <a href="https://www.rfc-editor.org/rfc/rfc1422">rfc1422</a>
 */
public enum PemType {

    /**
     * Represents a private key
     */
    PRIVATE_KEY("PRIVATE KEY"),

    /**
     * Represents a public key
     */
    PUBLIC_KEY("PUBLIC KEY");

    private final String pemTypeName;

    private static final String HEADER_FORMAT = "-----BEGIN %s-----\n";
    private static final String FOOTER_FORMAT = "-----END %s-----";

    PemType(final String pemTypeName) {
        this.pemTypeName = pemTypeName;
    }

    /**
     * Returns the footer.
     *
     * @return the formatted footer
     */
    public String getFooter() {
        return String.format(FOOTER_FORMAT, pemTypeName);
    }

    /**
     * Returns the formatted header.
     *
     * @return the header
     */
    public String getHeader() {
        return String.format(HEADER_FORMAT, pemTypeName);
    }
}
