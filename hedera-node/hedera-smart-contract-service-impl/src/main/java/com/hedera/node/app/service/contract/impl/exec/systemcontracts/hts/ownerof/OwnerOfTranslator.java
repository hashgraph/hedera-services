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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code ownerOf()} calls to the HTS system contract.
 */
@Singleton
public class OwnerOfTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function OWNER_OF = new Function("ownerOf(uint256)", ReturnTypes.ADDRESS);

    @Inject
    public OwnerOfTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(OWNER_OF);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerOfCall callFrom(@NonNull final HtsCallAttempt attempt) {
        // Since zero is never a valid serial number, if we clamp the passed value, the result
        // will be a revert with INVALID_NFT_ID as reason
        final var serialNo = asExactLongValueOrZero(
                OWNER_OF.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new OwnerOfCall(
                attempt.systemContractGasCalculator(), attempt.enhancement(), attempt.redirectToken(), serialNo);
    }
}
