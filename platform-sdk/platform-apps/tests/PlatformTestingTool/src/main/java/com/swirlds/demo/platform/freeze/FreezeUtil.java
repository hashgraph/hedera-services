/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.freeze;

import java.util.Calendar;
import java.util.TimeZone;

public class FreezeUtil {

    /**
     * get UTC Hour and Minutes from utcMillis
     * @param utcMillis
     * @return
     */
    public static int[] getUTCHourMinFromMillis(final long utcMillis) {
        int[] hourMin = new int[2];
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(utcMillis);
        hourMin[0] = cal.get(Calendar.HOUR_OF_DAY);
        hourMin[1] = cal.get(Calendar.MINUTE);
        return hourMin;
    }
}
