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

package com.swirlds.platform.state.iss;

import static com.swirlds.base.ArgumentUtils.throwArgNull;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.error.CatastrophicIssTrigger;
import com.swirlds.platform.dispatch.triggers.error.SelfIssTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import com.swirlds.platform.system.SystemExitReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * This class is responsible for handling the response to an ISS event.
 */
public class IssHandler {

    private static final String ISS_DUMP_CATEGORY = "iss";

    private final RateLimiter issDumpRateLimiter;
    private final StateConfig stateConfig;
    private final StateDumpRequestedTrigger stateDumpRequestedDispatcher;
    private final HaltRequestedConsumer haltRequestedConsumer;
    private final IssConsumer issConsumer;
    private final FatalErrorConsumer fatalErrorConsumer;

    private boolean halted;

    private final long selfId;

    /**
     * Create an object responsible for handling ISS events.
     *
     * @param dispatchBuilder       builds dispatchers
     * @param stateConfig           settings for the state
     * @param selfId                the self ID of this node
     * @param haltRequestedConsumer consumer to invoke when a system halt is desired
     * @param fatalErrorConsumer    consumer to invoke if a fatal error occurs
     * @param issConsumer           consumer to invoke if an ISS is detected
     */
    public IssHandler(
            @NonNull final Time time,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final StateConfig stateConfig,
            final long selfId,
            @NonNull final HaltRequestedConsumer haltRequestedConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final IssConsumer issConsumer) {

        this.issConsumer = throwArgNull(issConsumer, "issConsumer");
        this.haltRequestedConsumer = throwArgNull(haltRequestedConsumer, "haltRequestedConsumer");
        this.fatalErrorConsumer = throwArgNull(fatalErrorConsumer, "fatalErrorConsumer");
        this.stateDumpRequestedDispatcher =
                dispatchBuilder.getDispatcher(this, StateDumpRequestedTrigger.class)::dispatch;

        this.stateConfig = throwArgNull(stateConfig, "stateConfig");
        this.issDumpRateLimiter = new RateLimiter(time, Duration.ofSeconds(stateConfig.secondsBetweenISSDumps()));

        this.selfId = throwArgNull(selfId, "selfId");
    }

    /**
     * This method is called whenever any node is observed in disagreement with the consensus hash.
     *
     * @param round         the round of the ISS
     * @param nodeId        the ID of the node that had an ISS
     * @param nodeHash      the incorrect hash computed by the node
     * @param consensusHash the correct hash computed by the network
     */
    @Observer(StateHashValidityTrigger.class)
    public void stateHashValidityObserver(
            @NonNull final Long round,
            @NonNull final Long nodeId,
            @NonNull final Hash nodeHash,
            @NonNull final Hash consensusHash) {

        if (consensusHash.equals(nodeHash)) {
            // no need to take action when the hash is valid
            return;
        }

        if (nodeId == selfId) {
            // let the logic in selfIssObserver handle self ISS events
            return;
        }

        if (halted) {
            // don't take any action once halted
            return;
        }

        issConsumer.iss(round, IssNotification.IssType.OTHER_ISS, nodeId);

        if (stateConfig.haltOnAnyIss()) {
            // If we are halting then we always should dump.
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, false);

            haltRequestedConsumer.haltRequested("other node observed with ISS");
            halted = true;
        } else if (stateConfig.dumpStateOnAnyISS() && issDumpRateLimiter.request()) {
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, false);
        }
    }

    /**
     * This method is called when there is a self ISS.
     *
     * @param round         the round of the ISS
     * @param selfStateHash the incorrect hash computed by this node
     * @param consensusHash the correct hash computed by the network
     */
    @Observer(SelfIssTrigger.class)
    public void selfIssObserver(
            @NonNull final Long round, @NonNull final Hash selfStateHash, @NonNull final Hash consensusHash) {

        if (halted) {
            // don't take any action once halted
            return;
        }

        issConsumer.iss(round, IssNotification.IssType.SELF_ISS, selfId);

        if (stateConfig.haltOnAnyIss()) {
            // If configured to halt then always do a dump.
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, false);
            haltRequestedConsumer.haltRequested("self ISS observed");
            halted = true;
        } else if (stateConfig.automatedSelfIssRecovery()) {
            // Automated recovery is a fancy way of saying "turn it off and on again".
            // If we are powering down, always do a state dump.
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, true);
            fatalErrorConsumer.fatalError("Self ISS", null, SystemExitReason.ISS.getExitCode());
        } else if (stateConfig.dumpStateOnAnyISS() && issDumpRateLimiter.request()) {
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, false);
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
     * @param round         the round of the ISS
     * @param selfStateHash the hash computed by this node
     */
    @Observer(CatastrophicIssTrigger.class)
    public void catastrophicIssObserver(@NonNull final Long round, @NonNull final Hash selfStateHash) {

        if (halted) {
            // don't take any action once halted
            return;
        }

        issConsumer.iss(round, IssNotification.IssType.CATASTROPHIC_ISS, null);

        if (stateConfig.haltOnAnyIss() || stateConfig.haltOnCatastrophicIss()) {
            // If configured to halt then always do a dump.
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, false);
            haltRequestedConsumer.haltRequested("catastrophic ISS observed");
            halted = true;
        } else if (stateConfig.dumpStateOnAnyISS() && issDumpRateLimiter.request()) {
            stateDumpRequestedDispatcher.dispatch(round, ISS_DUMP_CATEGORY, stateConfig.automatedSelfIssRecovery());
        }
    }
}
