/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An {@link InsufficientBalanceException} that exists only for backward
 * compatibility with historical network behavior.
 *
 * <p>It is thrown when the payer is willing and able to pay all the fees, but will then not
 * be able to afford the other debits they will incur in the transaction.
 *
 * <p>For example, if the payer balance is {@code 100 hbar}, and they sign a
 * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} that debits {@code 100 hbar}
 * from their account, although they can clearly afford the transfer fee, after paying it
 * they will no longer be able to afford the transfer debit itself.
 *
 * <p>There is no compelling reason to treat this as a special case instead of letting the
 * downstream handler fail; but for historical reasons we continue to do so.
 */
public class InsufficientNonFeeDebitsException extends InsufficientBalanceException {
    public InsufficientNonFeeDebitsException(@NonNull ResponseCodeEnum responseCode, long estimatedFee) {
        super(responseCode, estimatedFee);
    }
}
