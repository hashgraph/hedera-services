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
package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.spi.StaticContext;
import com.hedera.services.sigs.order.SignatureWaivers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Placeholder implementation for giving access to {@link com.hedera.services.txns.auth.SystemOpPolicies}
 * and {@link com.hedera.services.sigs.order.SignatureWaivers} until they are refactored.
 * This will be deleted/refactored when both the above classes are implemented
 */
public class StaticNetworkContext implements StaticContext {
    private final SignatureWaivers signatureWaivers;

    public StaticNetworkContext(final SignatureWaivers signatureWaivers){
        this.signatureWaivers = signatureWaivers;
    }

    @Override
    public boolean isTargetAccountSignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        return signatureWaivers.isTargetAccountKeyWaived(cryptoUpdateTxn, payer);
    }

    @Override
    public boolean isNewKeySignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        return signatureWaivers.isNewAccountKeyWaived(cryptoUpdateTxn, payer);
    }
}
