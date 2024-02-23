/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import org.junit.jupiter.api.Test;

public class FreezeUtilTest {
    @Test
    public void convertUTCMillisTest() {
        long utcMillis = 1554331942000l;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(utcMillis);
        assert cal.get(Calendar.YEAR) == 2019;
        assert cal.get(Calendar.HOUR) == 10;
        assert cal.get(Calendar.HOUR_OF_DAY) == 22;
        assert cal.get(Calendar.MINUTE) == 52;
    }

    @Test
    public void getUTCHourMinFromMillisTest() {
        long utcMillis = 1554331942000l;
        int[] hourMin = FreezeUtil.getUTCHourMinFromMillis(utcMillis);
        assert hourMin[0] == 22;
        assert hourMin[1] == 52;
    }
}
