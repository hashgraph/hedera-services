/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.state;

import com.hedera.node.app.spi.fixtures.state.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

public class StateTestBase extends TestBase {
    @NonNull
    protected MapReadableKVState<String, String> readableFruitState() {
        return MapReadableKVState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, String> writableFruitState() {
        return MapWritableKVState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapReadableKVState<String, String> readableAnimalState() {
        return MapReadableKVState.<String, String>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, String> writableAnimalState() {
        return MapWritableKVState.<String, String>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableSpaceState() {
        return new ReadableSingletonStateBase<>(SPACE_STATE_KEY, () -> ASTRONAUT);
    }

    @NonNull
    protected WritableSingletonState<String> writableSpaceState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(ASTRONAUT);
        return new WritableSingletonStateBase<>(SPACE_STATE_KEY, backingValue::get, backingValue::set);
    }

    @NonNull
    protected MapReadableKVState<String, String> readableSTEAMState() {
        return MapReadableKVState.<String, String>builder(STEAM_STATE_KEY)
                .value(A_KEY, ART)
                .value(B_KEY, BIOLOGY)
                .value(C_KEY, CHEMISTRY)
                .value(D_KEY, DISCIPLINE)
                .value(E_KEY, ECOLOGY)
                .value(F_KEY, FIELDS)
                .value(G_KEY, GEOMETRY)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, String> writableSTEAMState() {
        return MapWritableKVState.<String, String>builder(STEAM_STATE_KEY)
                .value(A_KEY, ART)
                .value(B_KEY, BIOLOGY)
                .value(C_KEY, CHEMISTRY)
                .value(D_KEY, DISCIPLINE)
                .value(E_KEY, ECOLOGY)
                .value(F_KEY, FIELDS)
                .value(G_KEY, GEOMETRY)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableCountryState() {
        return new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> AUSTRALIA);
    }

    @NonNull
    protected WritableSingletonState<String> writableCountryState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(AUSTRALIA);
        return new WritableSingletonStateBase<>(COUNTRY_STATE_KEY, backingValue::get, backingValue::set);
    }

    @NonNull
    protected MapReadableStates allReadableStates() {
        return MapReadableStates.builder()
                .state(readableFruitState())
                .state(readableCountryState())
                .state(readableAnimalState())
                .state(readableSTEAMState())
                .state(readableSpaceState())
                .build();
    }

    @NonNull
    protected MapWritableStates allWritableStates() {
        return MapWritableStates.builder()
                .state(writableAnimalState())
                .state(writableCountryState())
                .state(writableAnimalState())
                .state(writableSTEAMState())
                .state(writableSpaceState())
                .build();
    }
}
