/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.fixtures.state.MapReadableState;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.fixtures.state.MapWritableState;
import com.hedera.node.app.spi.fixtures.state.TestBase;
import edu.umd.cs.findbugs.annotations.NonNull;

public class StateTestBase extends TestBase {
    @NonNull
    protected MapReadableState<String, String> readableFruitState() {
        return MapReadableState.<String, String>builder(FRUIT_STATE_KEY)
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
    protected MapReadableState<String, String> readableAnimalState() {
        return MapReadableState.<String, String>builder(ANIMAL_STATE_KEY)
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
    protected MapReadableState<String, String> readableSpaceState() {
        return MapReadableState.<String, String>builder(SPACE_STATE_KEY)
                .value(A_KEY, ASTRONAUT)
                .value(B_KEY, BLASTOFF)
                .value(C_KEY, COMET)
                .value(D_KEY, DRACO)
                .value(E_KEY, EXOPLANET)
                .value(F_KEY, FORCE)
                .value(G_KEY, GRAVITY)
                .build();
    }

    @NonNull
    protected MapReadableState<String, String> readableSTEAMState() {
        return MapReadableState.<String, String>builder(STEAM_STATE_KEY)
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
    protected MapReadableState<String, String> readableCountryState() {
        return MapReadableState.<String, String>builder(COUNTRY_STATE_KEY)
                .value(A_KEY, AUSTRALIA)
                .value(B_KEY, BRAZIL)
                .value(C_KEY, CHAD)
                .value(D_KEY, DENMARK)
                .value(E_KEY, ESTONIA)
                .value(F_KEY, FRANCE)
                .value(G_KEY, GHANA)
                .build();
    }

    @NonNull
    protected MapReadableStates allReadableStates() {
        return MapReadableStates.builder()
                .state(readableFruitState())
                .state(readableCountryState())
                .state(readableAnimalState())
                .state(readableSTEAMState())
                .build();
    }

    @NonNull
    protected MapWritableState<String, String> writableFruitState() {
        return MapWritableState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }
}
