/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.exports;

@FunctionalInterface
public interface SigFileWriter {

    byte TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    byte TYPE_FILE_HASH = 4; // next 48 bytes are hash384 of content of the file to be signed

    String writeSigFile(String signedFile, byte[] sig, byte[] signedFileHash);
}
