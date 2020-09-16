package com.hedera.services.bdd.spec.utilops.pauses;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class HapiSpecWaitUntil extends UtilOp {
    static final Logger log = LogManager.getLogger(HapiSpecWaitUntil.class);

    private long timeMs;

    public HapiSpecWaitUntil(String timeOfDay) throws ParseException {
        timeMs = convertToEpoc(timeOfDay);
    }

    @Override
    protected boolean submitOp(HapiApiSpec spec) throws Throwable {
        log.info("waiting until we reach to " + timeMs + "of the day");
        long currentTime = Instant.now().getEpochSecond();
        Thread.sleep(timeMs - currentTime);
        return false;
    }

    private long convertToEpoc(String timeOfDay) throws ParseException {
        SimpleDateFormat dateMonthYear = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat dateMonthYearTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        Date currentDate = new Date();
        String currDateInString = dateMonthYear.format(currentDate);

        String currDateTimeInString = currDateInString + " " + timeOfDay;

        return dateMonthYearTime.parse(currDateTimeInString).getTime();
    }
}
