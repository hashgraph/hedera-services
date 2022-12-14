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

import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class EvmTokenInfo {

    private String name;
    private String symbol;
    private int defaultFreezeStatus;
    private int defaultKycStatus;
    private String memo;
    private int tokenType;
    private int supplyType;
    private List<CustomFee> customFees;
    private int pauseStatus;
    private byte[] ledgerId;
    private boolean deleted;
    private long totalSupply;
    private long maxSupply;
    private int decimals;
    private long expiry;
    private Address treasury;

    private EvmKey adminKey;
    private EvmKey freezeKey;
    private EvmKey kycKey;
    private EvmKey supplyKey;
    private EvmKey wipeKey;
    private EvmKey feeScheduleKey;
    private EvmKey pauseKey;
    private Address autoRenewAccount;
    private long autoRenewPeriod;

    public EvmTokenInfo(
            byte[] ledgerId,
            int tokenType,
            int supplyType,
            boolean deleted,
            String symbol,
            String name,
            String memo,
            Address treasury,
            long totalSupply,
            long maxSupply,
            int decimals,
            long expiry) {
        this.ledgerId = ledgerId;
        this.name = name;
        this.symbol = symbol;
        this.memo = memo;
        this.treasury = treasury;
        this.tokenType = tokenType;
        this.supplyType = supplyType;
        this.deleted = deleted;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.decimals = decimals;
        this.expiry = expiry;
    }

    public void setCustomFees(List<CustomFee> customFees) {
        this.customFees = customFees;
    }

    public void setAdminKey(EvmKey adminKey) {
        this.adminKey = adminKey;
    }

    public void setDefaultFreezeStatus(int defaultFreezeStatus) {
        this.defaultFreezeStatus = defaultFreezeStatus;
    }

    public void setFreezeKey(EvmKey freezeKey) {
        this.freezeKey = freezeKey;
    }

    public void setDefaultKycStatus(int defaultKycStatus) {
        this.defaultKycStatus = defaultKycStatus;
    }

    public void setKycKey(EvmKey kycKey) {
        this.kycKey = kycKey;
    }

    public void setSupplyKey(EvmKey supplyKey) {
        this.supplyKey = supplyKey;
    }

    public void setWipeKey(EvmKey wipeKey) {
        this.wipeKey = wipeKey;
    }

    public void setFeeScheduleKey(EvmKey feeScheduleKey) {
        this.feeScheduleKey = feeScheduleKey;
    }

    public void setPauseKey(EvmKey pauseKey) {
        this.pauseKey = pauseKey;
    }

    public void setPauseStatus(int pauseStatus) {
        this.pauseStatus = pauseStatus;
    }

    public void setAutoRenewAccount(Address autoRenewAccount) {
        this.autoRenewAccount = autoRenewAccount;
    }

    public void setAutoRenewPeriod(long autoRenewPeriod) {
        this.autoRenewPeriod = autoRenewPeriod;
    }
}
