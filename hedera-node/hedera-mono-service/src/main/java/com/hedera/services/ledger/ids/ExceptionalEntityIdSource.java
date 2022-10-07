/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.ids;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;

public enum ExceptionalEntityIdSource implements EntityIdSource {
    NOOP_ID_SOURCE;

    @Override
    public TopicID newTopicId(final AccountID sponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccountID newAccountId(AccountID newAccountSponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContractID newContractId(AccountID newContractSponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileID newFileId(AccountID newFileSponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TokenID newTokenId(AccountID sponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduleID newScheduleId(AccountID sponsor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reclaimLastId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reclaimProvisionalIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetProvisionalIds() {
        throw new UnsupportedOperationException();
    }
}
