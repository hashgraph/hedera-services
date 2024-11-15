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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A parsed PEM file
 *
 * @param contents the contents of the file
 * @param pemType  the type of the file
 */
public record ParsedPemFile(@NonNull String contents, @NonNull PemType pemType) {
    /** Creates a new instance of this class */
    public ParsedPemFile {
        Objects.requireNonNull(contents, "contents must not be null");
        Objects.requireNonNull(pemType, "pemType must not be null");
    }
}
