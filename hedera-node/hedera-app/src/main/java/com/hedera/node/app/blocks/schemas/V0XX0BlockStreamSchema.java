package com.hedera.node.app.blocks.schemas;

/**
 * Defines the schema for two forms of state,
 * <ol>
 *     <li>State needed for a new or reconnected node to construct the next block exactly as will
 *     nodes already in the network.</li>
 *     <li>State derived from the block stream, and hence the natural provenance of the same service
 *     that is managing and producing blocks.</li>
 * </ol>
 * <p>
 * The two pieces of state in the first category are,
 * <ol>
 *     <li>The <b>number of the last completed block</b>, which each node must increment in the next block.</li>
 *     <li>The <b>hash of the last completed block</b>, which each node must include in the header and proof
 *     of the next block.</li>
 * </ol>
 * <p>
 * State in the second category has three parts,
 * <ol>
 *     <li>The <b>first consensus time of the last finished block</b>, for comparison with the consensus
 *     time at the start of the current block. Depending on the elapsed period between these times,
 *     the network may deterministically choose to purge expired entities, adjust node stakes and
 *     reward rates, or take other actions.</li>
 *     <li>The <b>last four values of the input block item running hash</b>, used to generate pseudorandom
 *     values for the {@link com.hedera.hapi.node.base.HederaFunctionality#UTIL_PRNG} operation.</li>
 *     <li>The <b>trailing 256 block hashes</b>, used to implement the EVM {@code BLOCKHASH} opcode.</li>
 * </ol>
 */
public class V0XX0BlockStreamSchema {
}
