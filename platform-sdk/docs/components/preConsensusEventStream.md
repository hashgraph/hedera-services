[↩](../platformWiki.md)

# Pre-Consensus Event Stream

## Purpose

The goal of the pre-consensus event stream is to ensure that all events are written to disk before we 
handle the transactions contained within those events.

In order to achieve this goal with minimal latency, we start writing each event to disk BEFORE it reaches consensus.

## Keystone Events

## File Format

## Data Flow

![](./preConsensusEventStream.svg)

TODO expand docoument