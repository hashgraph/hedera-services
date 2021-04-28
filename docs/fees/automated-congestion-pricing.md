# Automated congestion pricing

A Hedera Services network can be configured to automatically increase its 
fees during extended periods of high congestion. 

An integral `multiplier` fixes the size of the increase at any 
moment; for example, if `multiplier=2` then fees are twice as large. 
This multiplier can itself also increase as congestion worsens. 
[Two properties](../../hedera-node/src/main/resources/bootstrap.properties) 
determine the exact behavior, 
```
...
fees.minCongestionPeriod=60
fees.percentCongestionMultipliers=90,10x,95,25x,99,100x
...
```
Both properties are [dynamic](../services-configuration.md); that is, a
network admin can change them without a network restart. This document
explains how congestion is measured, and exactly how these two 
properties control the automated price increases.

## Measuring congestion 

The network measures congestion by monitoring the "fullness" of the 
[throttle bucket(s)](../throttle-design.md) to which the `CryptoTransfer` 
operation has been assigned. (By [default](../../hedera-node/src/main/resources/bootstrap.properties), 
`CryptoTransfer` is assigned to just one bucket, named `ThroughputLimits`.)
The network focuses on the _most full_ bucket. If the most full bucket has `x%` 
of its capacity used, then congestion is simply taken to be `x%`.

:information_desk_person:&nbsp; These congestion buckets are "filled" a 
little bit each time the network handles a consensus transaction; and 
they "drain" as consensus time passes---**not** clock time! So a congestion bucket 
**does not** reflect how the HAPI gRPC service on a given node is 
being throttled; but just the rate at which the network is handling 
consensus transactions, relative to consensus time.

## The minCongestionPeriod property

We may not want the network to increase prices because of a very brief
spike of traffic. The `minCongestionPeriod` gives us this control. It is
a minimum number of seconds that congestion must stay at a given level
for that level's `multiplier` to apply. (Note that seconds are the default
units for any property that represents a duration.)

## The fees.percentCongestionMultipliers property 

We may want to increase the `multiplier` to different sizes at different 
levels of sustained congestion. For example, after a minute at `90%` 
congestion, perhaps we want to scale fees by `10x`. But if congestion worsens,
and stays above `95%` for a minute, we want to scale fees by `25x`. The
`fees.percentCongestionMultipliers` property lets us define such a "step 
function" for the `multiplier`.

It is a comma-separated list that is read in pairs of values, where the
first value in a pair---e.g., `90`---is a congestion level; and the second
value---e.g. `10x`---is a multiplier. Only integral values are supported.
(All calculations in automated congestion pricing are done with integral
arithmetic to eliminate the risk of nodes getting slightly different answers
from a floating point calculation.)

:bangbang:&nbsp; If there is more than one pair in the list,
then both the congestion level and multiplier must strictly increase going 
from left-to-right. Services will ignore any attempt to set a non-compliant
value via a `FileUpdate` of file `0.0.121` or a _data/config/application.properties_ override.

## Disabling congestion pricing

To turn off congestion pricing completely, set `fees.percentCongestionMultipliers=1,1x`.
