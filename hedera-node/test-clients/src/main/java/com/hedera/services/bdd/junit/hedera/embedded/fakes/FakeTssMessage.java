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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.tss.api.TssMessage;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public record FakeTssMessage(byte[] bytes) implements TssMessage {

    @Override
    public byte[] toBytes() {
        return bytes;
    }
}
