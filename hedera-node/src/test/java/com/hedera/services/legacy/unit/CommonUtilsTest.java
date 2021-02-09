package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class CommonUtilsTest {

	public static int[] getUTCHourMinFromMillis(final long utcMillis) {
		int[] hourMin = new int[2];
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(utcMillis);
		hourMin[0] = cal.get(Calendar.HOUR_OF_DAY);
		hourMin[1] = cal.get(Calendar.MINUTE);
		return hourMin;
	}

	@Test
	public void convertUTCMillisTest() {
		long utcMillis = 1554331942000L;
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(utcMillis);
		Assertions.assertEquals(cal.get(Calendar.YEAR) , 2019);
		Assertions.assertEquals(cal.get(Calendar.HOUR), 10);
		Assertions.assertEquals(cal.get(Calendar.HOUR_OF_DAY), 22);
		Assertions.assertEquals(cal.get(Calendar.MINUTE), 52);
	}

	@Test
	public void getUTCHourMinFromSecsTest() {
		long utcMillis = 1554331942000l;
		int[] hourMin = getUTCHourMinFromMillis(utcMillis);
		Assertions.assertEquals(hourMin[0] , 22);
		Assertions.assertEquals(hourMin[1] , 52);
	}


}
