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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.annotations.HandleContextScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import dagger.BindsInstance;
import dagger.Subcomponent;

@Subcomponent(modules = {})
@HandleContextScope
public interface SRPHandleContextComponent {
    @Subcomponent.Factory
    interface Factory {
        SRPHandleContextComponent create(
                @BindsInstance TransactionBody txnBody,
                @BindsInstance HederaFunctionality functionality,
                @BindsInstance int signatureMapSize,
                @BindsInstance AccountID payer,
                @BindsInstance Key payerkey,
                @BindsInstance HandleContext.TransactionCategory txnCategory,
                @BindsInstance SingleTransactionRecordBuilderImpl recordBuilder,
                @BindsInstance SavepointStackImpl stack,
                @BindsInstance KeyVerifier keyVerifier);
    }

    FlowHandleContext handleContext();

    FeeAccumulator feeAccumulator();
}
