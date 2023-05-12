/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.threading;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.threading.PauseAndClear;
import com.swirlds.platform.threading.PauseAndLoad;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PauseAndDoTest {
    @Test
    void pauseAndClearBasic() {
        final StoppableThread thread = Mockito.mock(StoppableThread.class);
        final Clearable clearable = Mockito.mock(Clearable.class);

        final PauseAndClear pauseAndClear = new PauseAndClear(thread, clearable);
        pauseAndClear.clear();

        Mockito.verify(thread).pause();
        Mockito.verify(clearable).clear();
        Mockito.verify(thread).resume();
    }

    @Test
    void pauseAndClearException() {
        final StoppableThread thread = Mockito.mock(StoppableThread.class);
        final Clearable clearable = () -> {
            throw new RuntimeException();
        };

        final PauseAndClear pauseAndClear = new PauseAndClear(thread, clearable);
        Assertions.assertThrows(RuntimeException.class, pauseAndClear::clear);

        Mockito.verify(thread).pause();
        Mockito.verify(thread).resume();
    }

    @Test
    void pauseAndLoadBasic() {
        final StoppableThread thread = Mockito.mock(StoppableThread.class);
        final LoadableFromSignedState loadable = Mockito.mock(LoadableFromSignedState.class);

        final PauseAndLoad pauseAndLoad = new PauseAndLoad(thread, loadable);
        pauseAndLoad.loadFromSignedState(null);

        Mockito.verify(thread).pause();
        Mockito.verify(loadable).loadFromSignedState(null);
        Mockito.verify(thread).resume();
    }

    @Test
    void pauseAndLoadException() {
        final StoppableThread thread = Mockito.mock(StoppableThread.class);
        final LoadableFromSignedState loadable = (ss) -> {
            throw new RuntimeException();
        };

        final PauseAndLoad pauseAndLoad = new PauseAndLoad(thread, loadable);
        Assertions.assertThrows(RuntimeException.class, () -> pauseAndLoad.loadFromSignedState(null));

        Mockito.verify(thread).pause();
        Mockito.verify(thread).resume();
    }
}
