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

package com.swirlds.test.game;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.common.utility.Units.MILLISECONDS_TO_NANOSECONDS;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.RandomExtended;
import com.swirlds.platform.Utilities;
import java.awt.Color;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The state for the game demo. See the comments for GameDemoMain.
 *
 * There are several issues to consider when designing what state to store for a game, and what actions the
 * players can perform. For example, is a game involves each player having a location in 2D space, the
 * obvious choice of state and action for each player would be:
 *
 * <pre>
 *     State:  (x, y)
 *     Action: (dx, dy)
 * </pre>
 *
 * So the (x,y) coordinates for the player Alice would be stored. And when she wants to move a distance dx
 * horizontally and a distance dy vertically, she would take that (dx,dy) action and add any additional
 * information she needed, to form a transaction, which she would send to all the other players. Then Bob
 * would change Alice's position in his state to (x+dx,y+dy), and move the marker on his screen to show
 * Alice's new location.
 *
 * But there is a problem. Movement will not be smooth. On Bob's screen, Alice will seem to be motionless
 * for a while, then jump to a new location, then be motionless for a while, and so on. So a better approach
 * might be:
 *
 * <pre>
 *     State:  (t0, x0, y0, vx, vy)
 *     Action: (vx, vy)
 * </pre>
 *
 * Now, the state records that at time t0, Alice was at location (x0,y0) and moving with velocity (vx,vy).
 * So at time t, Bob can draw Alice's marker on the screen at location (x0 + t*vx, y0 + t*vy). Then the
 * marker for Alice will move smoothly at all times. And when Alice wants to change her velocity, she can
 * create a transaction out of the action (vs,vy), which gives her new velocity.
 *
 * But there is still a problem. Suppose Alice wants to move straight north for a while until she is at the
 * same latitude as the goal, then turn east until she reaches the goal. The problem is that she will need
 * to make sure that the transaction that changes her direction from north to east will have a consensus
 * timestamp of exactly the moment when her position is due west of the goal. But when she creates the
 * transaction, she can never be sure exactly what its consensus timestamp will end up being. So she will
 * not be able to navigate well. The solution to that problem is to do this:
 *
 * <pre>
 *     State:  (t0, x0, y0, gx, gy, s)
 *     Action: (gx, gy, s)
 * </pre>
 *
 * Now, the state for Alice includes the information that at time t0 she was at location (x0,y0), and that
 * she was heading toward a goal position of (gx,gy) at a speed of s units per second. If she ever decides
 * to change this, her action will give a new goal location and speed.
 *
 * Now, at time t, Bob can calculate the fraction r of the distance to the goal that Alice has moved:
 *
 * <pre>
 * r = min(1,
 * 		(t - t0) * s / sqrt((gx - x0) * (gx - x0) + (gy - y0) * (gy - y0)));
 * </pre>
 *
 * This is the time that she has been moving (seconds), times her speed (meters per second), divided by the
 * distance she needed to travel (meters). That is the fraction of the distance she has covered so far, as
 * of time t. This is then clipped at 1, so that she won't overshoot the goal. So on Bob's screen, it will
 * show Alice moving smoothly toward the goal at the appropriate speed, and then stopping once the goal has
 * been reached.
 *
 * This is also a good system for Alice. She can submit a transaction to move due north, then another to
 * move due east, to end up at the goal. But she will actually implement that as a first transaction that
 * says to move north to the turning point, followed by a later transaction that says to move directly to
 * the goal. She will submit the second at a moment chosen so that its consensus timestamp matches the time
 * she will actually reach the turning point.
 *
 * This still involves guessing what the consensus timestamp will be. But small errors in the guess won't do
 * any harm. If the timestamp is later than expected, then she will move to the turning point, pause
 * briefly, then move to the goal. If the timestamp is earlier than expected, then she will move to a point
 * just short of the turning point, then turn onto an angle that is mostly east and slightly tilted to the
 * north, and then end up exactly at the goal. Either way, she ends up exactly at the goal. And she turns at
 * a time and location close to her desired time and location.
 *
 * The only remaining issues are what t0 Bob should store in the state, what t he should use when displaying
 * Alice's position, and what t Alice should use when estimating what consensus timestamp a given action
 * will end up with.
 *
 * When Bob receives a transaction that updates Alice's position, he should set t0 to the time that the
 * Platform passes to handleTransaction. If the transaction happens to have consensus==true, then this time
 * is correct. If consensus==false, then the time is an estimate that the Platform calculates as to what the
 * consensus timestamp will probably be when the consensus is eventually reached for that transaction. The
 * Platform estimates this based on measurements of recent network and consensus performance.
 *
 * When Bob is displaying Alice's position, he needs to know the time t to use in the above equations. When
 * Alice is deciding what action to do at a given moment, she must guess what time t will end up being the
 * consensus timestamp for a transaction created at that moment. In both cases, the best value of t is found
 * by calling platform.estTime(). This estimate by the Platform is also based on measurements of recent
 * network and consensus performance. So with this estimate, the screen will display how the world should
 * look at that time, and when a user gives some input, it should actually take effect at the moment that
 * the screen is displaying. There can be estimation errors, but this should give the best results.
 */
public class GameTestState extends PartialMerkleLeaf implements SwirldState1, MerkleLeaf {

    /**
     * The version history of this class.
     * Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
    }

    private static final long CLASS_ID = 0x981e54b616cc916cL;

    /** max speed a player is allowed to move, in steps per millisecond */
    public static final int MAX_SPEED = 1000;
    /** number of goal symbols or player symbols can fit side by side across the playing board */
    public static final int SYMBOL_FRACTION = 15;

    ////////////////////////////
    // the following define the state of the simulated world

    /** number of players (where the last "player" is actually the goal) */
    private int numPlayers;
    /** used to randomly move the target after a point is scored */
    private RandomExtended random;
    /** width of the board, in steps */
    private long xBoardSize = 10_000_000;
    /** height of the board, steps */
    private long yBoardSize = 20_000_000;
    /** sum of all players' scores */
    private int totalScore = 0;
    /** score for each player */
    private int[] score;
    /** # transactions so far, per player */
    private int[] numTrans;
    /** color of each player (the last one is the color of the goal) */
    private Color[] color;
    /** time that the game started (consensus timestamp of the first transaction in consensus order */
    private Instant gameStart = null;

    /**
     * current time of the world in this state, in nanoseconds since the consensus timestamp of the first
     * transaction in the game
     */
    private long prevTime = 0;
    /** speed of each player (in steps per millisecond) */
    private long[] speed;
    /** x coordinate of each player's current position (at time currTime) */
    private long[] x;
    /** y coordinate of each player's current position (at time currTime) */
    private long[] y;
    /** x coordinate of each player's goal position they are moving toward */
    private long[] xDest;
    /** y coordinate of each player's goal position they are moving toward */
    private long[] yDest;

    private static final int DEFAULT_MAX_ARRAY_SIZE = 1024 * 8;

    ////////////////////////////
    // getters and setters

    /** @return the maximum speed a player is allowed to move (in steps per millisecond) */
    public synchronized long getMaxSpeed() {
        return MAX_SPEED;
    }

    /** @return current board width, in cells */
    public synchronized long getxBoardSize() {
        return xBoardSize;
    }

    /** @return current board height, in cells */
    public synchronized long getyBoardSize() {
        return yBoardSize;
    }

    /** @return sum of all player scores */
    public synchronized int getTotalScore() {
        return totalScore;
    }

    /** @return score for each player */
    public synchronized int[] getScore() {
        return score;
    }

    /** @return number of transactions so far for each player */
    public synchronized int[] getNumTrans() {
        return numTrans;
    }

    /** @return x coordinate for each player */
    public synchronized long[] getX() {
        return x;
    }

    /** @return y coordinate for each player */
    public synchronized long[] getY() {
        return y;
    }

    /** @return color for each player */
    public synchronized Color[] getColor() {
        return color;
    }

    /** @return the random number generator used to move the goal after each point is scored */
    public synchronized RandomExtended getRandom() {
        return random;
    }

    /////////////////////////////////////////////////////////////////////
    // methods that GameDemoState implements, but a SwirldState doesn't require

    public GameTestState() {}

    private GameTestState(final GameTestState sourceState) {
        super(sourceState);
        this.numPlayers = sourceState.numPlayers;
        this.random = sourceState.random.clone(); // RandomExtended is cloneable, unlike Random
        this.xBoardSize = sourceState.xBoardSize;
        this.yBoardSize = sourceState.yBoardSize;
        this.totalScore = sourceState.totalScore;
        this.gameStart = sourceState.gameStart;
        this.prevTime = sourceState.prevTime;

        // each of the following is an array of either primitives or immutables, so clone is ok
        this.score = sourceState.score.clone();
        this.numTrans = sourceState.numTrans.clone();
        this.color = sourceState.color.clone();
        this.speed = sourceState.speed.clone();
        this.x = sourceState.x.clone();
        this.y = sourceState.y.clone();
        this.xDest = sourceState.xDest.clone();
        this.yDest = sourceState.yDest.clone();
    }

    /**
     * return a random color
     *
     * @return the random color
     */
    private Color randColor() {
        return Color.getHSBColor(
                random.nextFloat(), random.nextFloat() * .25f + .75f, random.nextFloat() * .25f + .75f);
    }

    /**
     * Simulate the world to advance it to time toTime. If the current time is already toTime or greater,
     * then do nothing.
     *
     * @param toTime
     * 		the time to which it should advance the world simulation
     */
    public synchronized void advanceWorld(final Instant toTime) {
        /** current time (in nanoseconds since the game began) */
        final long newTime;
        if (gameStart == null || toTime == null) {
            return; // we haven't started yet (we start with the first consensus transaction)
        }
        newTime = ChronoUnit.NANOS.between(gameStart, toTime);
        final long period = newTime - prevTime;
        prevTime = newTime;
        if (period < 0) {
            return; // time in the simulated world can only advance, not go backward
        }
        for (int i = 0; i < numPlayers; i++) {
            final long dx = xDest[i] - x[i]; // distance to move in x direction to reach the destination
            final long dy = yDest[i] - y[i]; // distance to move in y direction to reach the destination
            final long dDest = (long) Math.hypot(dx, dy); // distance to move to reach the destination
            // find max distance that can be traveled during the period (in steps), which is:
            // speed (in steps per millisecond)
            // times by the length of the period (in nanoseconds)
            // divided by the number of nanoseconds in a millisecond (one million)
            final long dMax = speed[i] * period / MILLISECONDS_TO_NANOSECONDS;
            if (dDest > 0 && speed[i] > 0) {
                // we still need to move because speed is nonzero and we aren't at the destination yet
                if (dDest < dMax) {
                    // we reach the goal partway through this time period being simulated
                    x[i] = xDest[i];
                    y[i] = yDest[i];
                } else {
                    // we move throughout the time period being simulated, without reaching the goal
                    x[i] += dx * dMax / dDest;
                    y[i] += dy * dMax / dDest;
                }
            }

            // wrap around the board (as on a torus)
            x[i] = mod(x[i], xBoardSize);
            y[i] = mod(y[i], yBoardSize);

            // handle a point being scored, when the player and goal are close enough that their symbols
            // would touch horizontally.
            if (Math.hypot(x[i] - x[numPlayers], y[i] - y[numPlayers]) < xBoardSize / SYMBOL_FRACTION) {
                score[i]++; // the winner gets a point
                totalScore++; // so the sum of everyone's points increments
                color[i] = color[numPlayers]; // the winner's color changes to match the goal
                x[numPlayers] = mod(random.nextLong(), xBoardSize); // goal jumps to random location
                y[numPlayers] = mod(random.nextLong(), yBoardSize);
                color[numPlayers] = randColor(); // goal changes to random color
            }
        }
    }

    /** The modulus (a mod b), which is different for negative arguments from both % and Math.floorMod. */
    long mod(final long a, final long b) {
        final long m = a % b;
        return m < 0 ? m + b : m;
    }

    /////////////////////////////////////////////////////////////////////
    // methods any SwirldState must implement

    /**
     * Do initial setup at genesis.
     */
    private void genesisInit(final Platform platform) {

        final String[] parameters = ((PlatformWithDeprecatedMethods) platform).getParameters();

        this.numPlayers = platform.getAddressBook().getSize(); // the element after the last player is the goal
        this.xBoardSize = 10_000; // default board takes 10 or 20 seconds to move across
        this.yBoardSize = 20_000;
        if (parameters.length >= 2) {
            this.xBoardSize = (long) Integer.parseInt(parameters[1].trim()) * MAX_SPEED;
            this.yBoardSize = (long) Integer.parseInt(parameters[0].trim()) * MAX_SPEED;
        }
        this.totalScore = 0;
        this.gameStart = null;
        this.prevTime = 0;
        this.score = new int[numPlayers + 1];
        this.numTrans = new int[numPlayers + 1];
        this.color = new Color[numPlayers + 1];
        this.speed = new long[numPlayers + 1];
        this.x = new long[numPlayers + 1];
        this.y = new long[numPlayers + 1];
        this.xDest = new long[numPlayers + 1];
        this.yDest = new long[numPlayers + 1];

        for (int i = 0; i < numPlayers + 1; i++) {
            score[i] = 0;
            numTrans[i] = 0;
            x[i] = mod(random.nextLong(), xBoardSize);
            y[i] = mod(random.nextLong(), yBoardSize);
            color[i] = Color.getHSBColor((float) i / (numPlayers + 1), 1, 1);
            xDest[i] = x[i];
            yDest[i] = y[i];
            speed[i] = 0L;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {

        // Must be initialized for every trigger, because it is not serialized/deserialized
        this.random = new RandomExtended(0); // must seed with a constant, not the time;

        if (trigger == InitTrigger.GENESIS) {
            genesisInit(platform);
        }
    }

    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        round.forEachEventTransaction((event, transaction) ->
                handleTransaction(event.getCreatorId(), transaction.getConsensusTimestamp(), transaction));
    }

    private void checkGameStart(final Instant timestamp) {
        if (gameStart == null) {
            gameStart = timestamp;
        }
    }

    @Override
    public void handleTransaction(
            final long creatorId,
            final Instant timeCreated,
            final Instant timestamp,
            final Transaction trans,
            final SwirldDualState swirldDualState) {
        throwIfImmutable();
        handleTransaction(creatorId, timestamp, trans);
    }

    private void handleTransaction(final long id, final Instant timestamp, final Transaction trans) {
        checkGameStart(timestamp);

        /** the member doing this transaction */
        final int mem = (int) id;
        numTrans[mem]++; // remember how many transactions were handled for each member

        // You can make the consensus latency visible by making
        // the goal jump around while it's deciding the consensus.
        // You can do that by making the random number generator
        // depend on the EXACT order of all the transactions.
        // To do that, uncomment the following line:

        // random.absorbEntropy(mem + trans[0]);

        // simulate the world up to the time of this transaction
        advanceWorld(timestamp);

        // make this transaction affect the world, by making that player move toward that goal
        speed[mem] = Utilities.toLong(trans.getContents(), 0);
        xDest[mem] = Utilities.toLong(trans.getContents(), 8);
        yDest[mem] = Utilities.toLong(trans.getContents(), 16);
        speed[mem] = Math.max(0, Math.min(MAX_SPEED, speed[mem]));
    }

    @Override
    public GameTestState copy() {
        throwIfImmutable();
        return new GameTestState(this);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {

        out.writeInt(numPlayers);
        out.writeSerializable(random, true);
        out.writeLong(xBoardSize);
        out.writeLong(yBoardSize);
        out.writeInt(totalScore);
        out.writeInstant(gameStart);
        out.writeLong(prevTime);

        // each of the following is an array of either primitives or immutables, so clone is ok
        out.writeIntArray(score);
        out.writeIntArray(numTrans);

        out.writeInt(color.length);
        for (final Color c : color) {
            final float[] comps = c.getRGBComponents(null);
            out.writeFloat(comps[0]); // r
            out.writeFloat(comps[1]); // g
            out.writeFloat(comps[2]); // b
            out.writeFloat(comps[3]); // a
        }

        out.writeLongArray(speed);
        out.writeLongArray(x);
        out.writeLongArray(y);
        out.writeLongArray(xDest);
        out.writeLongArray(yDest);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        numPlayers = in.readInt();
        random = in.readSerializable();
        xBoardSize = in.readLong();
        yBoardSize = in.readLong();
        totalScore = in.readInt();
        gameStart = in.readInstant();
        prevTime = in.readLong();

        score = in.readIntArray(DEFAULT_MAX_ARRAY_SIZE);
        numTrans = in.readIntArray(DEFAULT_MAX_ARRAY_SIZE);

        final int colorSize = in.readInt();
        if (colorSize > DEFAULT_MAX_ARRAY_SIZE) {
            throw new IOException("Length exceeds maximum");
        }
        color = new Color[colorSize];
        for (int index = 0; index < colorSize; index++) {
            final float r = in.readFloat();
            final float g = in.readFloat();
            final float b = in.readFloat();
            final float a = in.readFloat();
            color[index] = new Color(r, g, b, a);
        }

        speed = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        x = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        y = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        xDest = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        yDest = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }
}
