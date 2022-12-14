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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

public class CustomFee {

    private FixedFee fixedFee;
    private FractionalFee fractionalFee;
    private RoyaltyFee royaltyFee;

    public void setFixedFee(FixedFee fixedFee) {
        this.fixedFee = fixedFee;
    }

    public void setFractionalFee(FractionalFee fractionalFee) {
        this.fractionalFee = fractionalFee;
    }

    public void setRoyaltyFee(RoyaltyFee royaltyFee) {
        this.royaltyFee = royaltyFee;
    }

    public FixedFee getFixedFee() {
        return fixedFee;
    }

    public FractionalFee getFractionalFee() {
        return fractionalFee;
    }

    public RoyaltyFee getRoyaltyFee() {
        return royaltyFee;
    }
}
