package com.hedera.services.txns.span;

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

import static org.mockito.BDDMockito.given;

import com.hedera.services.utils.TxnAccessor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanMapAccessorTest {
  private Map<String, Object> span = new HashMap<>();

  @Mock private TxnAccessor accessor;

  private ExpandHandleSpanMapAccessor subject;

  @BeforeEach
  void setUp() {
    subject = new ExpandHandleSpanMapAccessor();

    given(accessor.getSpanMap()).willReturn(span);
  }

  @Test
  void testsForImpliedXfersAsExpected() {
    // expect:
    Assertions.assertDoesNotThrow(() -> subject.getImpliedTransfers(accessor));
  }
}
