/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.operations;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import java.util.function.BiPredicate;

import com.hedera.node.app.hapi.utils.HederaExceptionalHaltReason;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.DelegateCallOperation;

/**
 * Hedera adapted version of the {@link DelegateCallOperation}.
 *
 * <p>Performs an existence check on the {@link Address} to be called Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 */
public class HederaDelegateCallOperation extends DelegateCallOperation {

    private final BiPredicate<Address, MessageFrame> addressValidator;

    public HederaDelegateCallOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {

        // FUTURE(#12991): The one-argument method `AbstractCallOperation.cost()` is deprecated, the
        //  preferred overload adds an `accountIsWarm` parameter.  This method needs to be updated
        //  to include a proper inspection of whether the account is warm or not.  Currently
        //  supplying the constant `ACCOUNT_IS_WARM` because that's what the deprecated one-argument
        //  method does.

        final boolean ACCOUNT_IS_WARM = true;
        return HederaEvmOperationsUtil.addressCheckExecution(
                frame,
                () -> to(frame),
                () -> cost(frame, ACCOUNT_IS_WARM),
                () -> super.execute(frame, evm),
                addressValidator);
    }
}
