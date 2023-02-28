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

package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleSign;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduleSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleSuite.class);

    private final Map<String, String> specConfig;
    private final String scheduleId;

    public ScheduleSuite(final Map<String, String> specConfig, final String scheduleId) {
        this.specConfig = specConfig;
        this.scheduleId = scheduleId;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(doSchedule());
    }

    private HapiSpec doSchedule() {
        var schedule = new HapiScheduleSign(DEFAULT_SHARD_REALM + scheduleId);
        return HapiSpec.customHapiSpec("DoSchedule")
                .withProperties(specConfig)
                .given()
                .when()
                .then(schedule);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
