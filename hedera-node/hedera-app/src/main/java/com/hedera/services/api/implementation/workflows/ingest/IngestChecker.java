/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.ingest;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface IngestChecker {
    void checkSignedTransaction(SignedTransaction tx) throws PreCheckException;

    void checkTransactionBody(TransactionBody txBody, Account account) throws PreCheckException;

    void checkSignatures(ByteString signedTransactionBytes, SignatureMap signatureMap, JKey key)
            throws PreCheckException;

    void checkThrottles(TransactionBody.DataCase type) throws ThrottleException;
}
