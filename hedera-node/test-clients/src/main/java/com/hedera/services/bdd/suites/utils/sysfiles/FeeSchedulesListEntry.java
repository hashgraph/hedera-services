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
import com.hederahashgraph.api.proto.java.FeeSchedule;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeeSchedulesListEntry {
    List<ScheduleEntryWrapperPojo> nextFeeSchedule;
    List<ScheduleEntryWrapperPojo> currentFeeSchedule;

    public static List<ScheduleEntryWrapperPojo> from(FeeSchedule grpc) {
        var list = grpc.getTransactionFeeScheduleList().stream()
                .map(ScheduleEntryWrapperPojo::from)
                .collect(Collectors.toList());
        var expiryEntry = new ScheduleEntryWrapperPojo();
        expiryEntry.setExpiryTime(grpc.getExpiryTime().getSeconds());
        list.add(expiryEntry);
        return list;
    }

    public static FeeSchedulesListEntry asNextFeeSchedule(List<ScheduleEntryWrapperPojo> entry) {
        var listEntry = new FeeSchedulesListEntry();
        listEntry.setNextFeeSchedule(entry);
        return listEntry;
    }

    public static FeeSchedulesListEntry asCurrentFeeSchedule(List<ScheduleEntryWrapperPojo> entry) {
        var listEntry = new FeeSchedulesListEntry();
        listEntry.setCurrentFeeSchedule(entry);
        return listEntry;
    }

    public List<ScheduleEntryWrapperPojo> getCurrentFeeSchedule() {
        return currentFeeSchedule;
    }

    public void setCurrentFeeSchedule(List<ScheduleEntryWrapperPojo> currentFeeSchedule) {
        this.currentFeeSchedule = currentFeeSchedule;
    }

    public List<ScheduleEntryWrapperPojo> getNextFeeSchedule() {
        return nextFeeSchedule;
    }

    public void setNextFeeSchedule(List<ScheduleEntryWrapperPojo> nextFeeSchedule) {
        this.nextFeeSchedule = nextFeeSchedule;
    }
}
