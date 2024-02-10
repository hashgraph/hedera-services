/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.iss;

import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.state.notifications.IssNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This class is responsible for handling the response to an ISS event.
 */
public class IssHandler {
    private final StateConfig stateConfig;
    private final HaltRequestedConsumer haltRequestedConsumer;
    private final FatalErrorConsumer fatalErrorConsumer;
    private final Scratchpad<IssScratchpad> issScratchpad;

    private boolean halted;

    /**
     * Create an object responsible for handling ISS events.
     *
     * @param stateConfig           settings for the state
     * @param haltRequestedConsumer consumer to invoke when a system halt is desired
     * @param fatalErrorConsumer    consumer to invoke if a fatal error occurs
     * @param issScratchpad         scratchpad for ISS data, is persistent across restarts
     */
    public IssHandler(
            @NonNull final StateConfig stateConfig,
            @NonNull final HaltRequestedConsumer haltRequestedConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final Scratchpad<IssScratchpad> issScratchpad) {
        this.haltRequestedConsumer =
                Objects.requireNonNull(haltRequestedConsumer, "haltRequestedConsumer must not be null");
        this.fatalErrorConsumer = Objects.requireNonNull(fatalErrorConsumer, "fatalErrorConsumer must not be null");
        this.stateConfig = Objects.requireNonNull(stateConfig, "stateConfig must not be null");
        this.issScratchpad = Objects.requireNonNull(issScratchpad);
    }

    /**
     * This method is called whenever an ISS event is observed.
     *
     * @param issNotification the notification of the ISS event
     */
    public void issObserved(@NonNull final IssNotification issNotification) {
        switch (issNotification.getIssType()) {
            case SELF_ISS -> selfIssObserver(issNotification.getRound());
            case OTHER_ISS -> otherIss();
            case CATASTROPHIC_ISS -> catastrophicIssObserver(issNotification.getRound());
        }
    }

    /**
     * This method is called whenever any node is observed in disagreement with the consensus hash.
     */
    private void otherIss() {
        if (halted) {
            // don't take any action once halted
            return;
        }
        if (stateConfig.haltOnAnyIss()) {
            haltRequestedConsumer.haltRequested("other node observed with ISS");
            halted = true;
        }
    }

    /**
     * Record the latest ISS round in the scratchpad. Does nothing if this is not the latest ISS that has been
     * observed.
     *
     * @param issRound the round of the observed ISS
     */
    private void updateIssRoundInScratchpad(final long issRound) {
        issScratchpad.atomicOperation(data -> {
            final SerializableLong lastIssRound = (SerializableLong) data.get(IssScratchpad.LAST_ISS_ROUND);

            if (lastIssRound == null || lastIssRound.getValue() < issRound) {
                data.put(IssScratchpad.LAST_ISS_ROUND, new SerializableLong(issRound));
                return true;
            }

            // Data was not modified, no need to flush data to disk. It is possible to observe ISS rounds out of order,
            // and we only want to increment this number.
            return false;
        });
    }

    /**
     * This method is called when there is a self ISS.
     *
     * @param round    the round of the ISS
     */
    private void selfIssObserver(@NonNull final Long round) {

        if (halted) {
            // don't take any action once halted
            return;
        }

        updateIssRoundInScratchpad(round);

        if (stateConfig.haltOnAnyIss()) {
            haltRequestedConsumer.haltRequested("self ISS observed");
            halted = true;
        } else if (stateConfig.automatedSelfIssRecovery()) {
            // Automated recovery is a fancy way of saying "turn it off and on again".
            fatalErrorConsumer.fatalError("Self ISS", null, SystemExitCode.ISS);
        }
    }

    /*                 Heaven help us if the code below is ever executed in production.

                                     .';coxk0KXNWWWMMMMMMMMWNXKOkdl:,..
                                 .:okKNWMMWNXK0OkxxddddddddxkO0KNWMMWX0ko:.
                              'lkXWWN0koc:,...                ...,:ldkKNWWXOo'
                           .;kXWNOo:'.                                .':oONWNk;
                          ;kNW0o'.                                         'oKWNk,
                        .dNW0c.                                              .lKWXl.
                       'kWXo.                                                  .dNWk.
                      'OW0;                                                      cXWk.
                     .xW0,                                                        :KWd.
                     cNX:                                                          cXX:
                    .xWd.                                                          .xWx.
                    .OX:   ''                                                  .,.  ;X0'
                    '00'  .d:                                                  ,x,  .OK,
                    .k0'  'Ox.                                                 l0;  .k0'
                     dK;  .kX:                                                ,0K,  .Ok.
                     ,0o   lN0;                                              .kWx.  ;0c
                      ok.  '0Mk.                                            .dWX:  .ox.
                      .dc  .kMX;       ...',,;'.            .';;,'...       '0M0'  ;d'
                       .c, .OMN:  .cdkO0KXNWWWN0;          ;ONWWNNXK0Okdl'  ,0MK, 'c.
                        .,';KMX: :KWMMMMMMMMMMMMO'        .kMMMMMMMMMMMMMXl.'0MNl',.
                          .xWMK,.OMMMMMMMMMMMMMMO.        .kMMMMMMMMMMMMMM0'.kMMO'
           .'..           .OMMk. oNMMMMMMMMMMMM0,          ,OWMMMMMMMMMMMWd. oWMK;           .,;,.
         :OXNX0o'         :XMNl  .xWMMMMMMMMMWk'            .xWMMMMMMMMMWk.  ;KMWo         ;xKWWN0c.
        cXMNOkXWXl.       dWMK,   .kWMMMMMMMXl.              .cKWMMMMMMWk'   .kMMk.      .dNWKdkNMXc
       .kMMk. ,kWNo.      dWMK,    .:xKNWNKd'     ,xo'.lx,     .o0NWNKk:.    .kMMk.     .dWNx. .kMMk.
       ;KMWd.  .dNNx'     cNMN:       .','.     .oXMk'.dWNo.     .','.       ,KMWo     'xNNo.   dWMK:
     'dXWWK:    .:kXXOo,. .kWMO'                oNMMk'.dMMWd.               .xWM0' .,lOXXk;     ;0WMXd'
    lXMWKo.        .;lxOOd:cOWWKo,.            '0MMMk,.dMMMK,            .;o0WWKocdkOxl;.        .l0WMXo
    NMWO;.  ....       .':lox0NNWNKOd:.        ;XMMMk'.dMMMX:        .:oOXNNNX0xlc;..       ....  .,OWMW
    KWMN0OOO0KK0Od:.        ..,:cx0kOXKx;      '0MW0:. ;0WM0,      'd0XOx0x;,'..        .:okKKK0OOO0NMW0
    .cdO0KK0OxxxOXNXOo;.         'ko;xdlxl.     ,l:.    .:l,      :xdkk;lk'         .;oOXNXOxdxO0K00Od:.
         ..      .,cdOKKkl,.      dXdxO;.:,                      ':.:0doXd.     .'cx0K0xl;.     ...
                      .;cdkxo;.   :NKx0k..'......        ........,..O0dKNc   .;lxxdl;..
                           .,:c;'.cXWxdK:'c;:l:loloolodoodloo:lc;l;cKodWNl.';::,.
                                .'xWWo,kolk:ll.cc'o;'od;,l,cc.lo:kodx'lWMk'.
                                 .kMWo.,:lklkOoOOx0xd0Kxd0xOOlOkcxl;' lWMO.
                               ..;OMMk. .co,oo,xkd0xxKXxx0dkx,ol'lc. .xMM0:..
                          .':ll:'.oWMX;  .,:ddcxocx:;dx:;dcoxcddc,.  ;KMWx'':lol:'.
           .';ccc;'..';cdkOko;.   '0MMO'    .,codxkkkkOkxxddoc,.    'OMM0,   .'cxOOxl:'..':ccc;'.
          c0NWMWWWNXXXX0xc'.       ;KMW0;          ......          ,OWMK:        .;oOXNXXNWWWWWN0l.
         ,KMW0c;;:clc:'.        .''':OWMXd'                      'dXMWO:,,,.         .;cc:;,,ckWMNl
         .kWMXd'           .':lol:.  .cONWXx:'.              .'cxXWNk:.  .:odoc,.           .lKWW0,
          .lKWMXd.      'cxO0xc'       .,okKXX0xl:,..  ..,:lx0XX0xl'        .cx00ko;.     .dXWMXd.
            .lXMWk.   .xNXk:.              .',:ccc:,.  .';:c::,..              .:dXNO;    oWMNd.
              dWMK,  :0WK:                                                        ;0WXc  .kMMO.
              cNMNo;xNW0;                                                          ,0MNx;cKMWd.
              .xWMWNWNx'                                                            .dXWWWMWk'
               .xNMW0:.                                                               ;kNWNx'
     */

    /**
     * This method is called when there is a catastrophic ISS.
     *
     * @param round the round of the ISS
     */
    private void catastrophicIssObserver(@NonNull final Long round) {

        if (halted) {
            // don't take any action once halted
            return;
        }

        updateIssRoundInScratchpad(round);

        if (stateConfig.haltOnAnyIss() || stateConfig.haltOnCatastrophicIss()) {
            haltRequestedConsumer.haltRequested("catastrophic ISS observed");
            halted = true;
        }
    }
}
