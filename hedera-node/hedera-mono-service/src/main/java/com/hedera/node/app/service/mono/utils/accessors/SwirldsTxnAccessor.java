/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.utils.accessors;

import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;
import java.util.function.Function;

public interface SwirldsTxnAccessor extends TxnAccessor {

    /* --- These methods are needed by the signature management infrastructure to take a transaction through the
    expandSignatures -> handleTransaction lifecycle --- */
    byte[] getTxnBytes();

    PubKeyToSigBytes getPkToSigsFn();

    List<TransactionSignature> getCryptoSigs();

    void clearCryptoSigs();

    void addAllCryptoSigs(List<TransactionSignature> signatures);

    /* --- Used to track entities with keys linked to a transaction --- */
    void setLinkedRefs(LinkedRefs linkedRefs);

    LinkedRefs getLinkedRefs();

    /* --- Used to track the results of creating signatures for all linked keys --- */
    void setExpandedSigStatus(ResponseCodeEnum status);

    ResponseCodeEnum getExpandedSigStatus();

    /* --- Used to track the results of validating derived signatures --- */
    void setSigMeta(RationalizedSigMeta sigMeta);

    RationalizedSigMeta getSigMeta();

    Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn();

    TxnAccessor getDelegate();
}
