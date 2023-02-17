/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleEntryWrapperPojo {
    Long expiryTime;
    ScheduleEntryPojo transactionFeeSchedule;

    public static ScheduleEntryWrapperPojo from(TransactionFeeSchedule grpc) {
        var pojo = new ScheduleEntryWrapperPojo();
        pojo.setTransactionFeeSchedule(ScheduleEntryPojo.from(grpc));
        return pojo;
    }

    public ScheduleEntryPojo getTransactionFeeSchedule() {
        return transactionFeeSchedule;
    }

    public void setTransactionFeeSchedule(ScheduleEntryPojo transactionFeeSchedule) {
        this.transactionFeeSchedule = transactionFeeSchedule;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }
}
