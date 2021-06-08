# E2E Testing
## Getting Started

This document describes the steps that need to be taken to run a e2e test from your machine that points to a 
pre-configured network with a mirror-node.


## Setup

Create a configuration file `application.yaml` that has the node configuration, publisher configuration and 
subscriber configuration along with an operator id and its key.

Example :
```
hedera:
  mirror:
    monitor:
      network: PREVIEWNET
      operator:
        accountId: 0.0.12345
        privateKey: <privateKey>
      publish:
        clients: 4
        threads: 40
        warmupPeriod: 120s
        scenarios:
          - name: CryptoTransfer
            duration: 5m
            properties:
              senderAccountId: 0.0.12345
              recipientAccountId: 0.0.12346
              amount: 1
              transferType: CRYPTO
            tps: 100
            type: CRYPTO_TRANSFER
      subscribe:
        clients: 4
        grpc:
        rest:
          - name: CryptoTransferSub
            enabled: true
            samplePercent: 0.05
```

> The above config targets the `PREVIEWNET` by sending cryptoTransfer transactions for 5 mins with 100 tps
> using the publisher and then using the subscriber we are sending rest requests to the mirror node to validate these
> transactions.

#### Follow Up:
Explore the possible transaction types [here](https://github.com/hashgraph/hedera-mirror-node/tree/master/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier)
and publish/subscribe configuration [here](https://github.com/hashgraph/hedera-mirror-node/tree/master/hedera-mirror-monitor/src/main/java/com/hedera/mirror/monitor)

Run the following command that will start the monitor on a docker container.

```
docker run -it --rm -p 8082:8082 -v $(pwd)/application.yaml:/usr/etc/hedera-mirror-monitor/application.yml -e "SPRING_CONFIG_ADDITIONAL_LOCATION=file:/usr/etc/hedera-mirror-monitor/" gcr.io/mirrornode/hedera-mirror-monitor:0.34.0-rc1
```

Once the monitor starts sending traffic, we see logs like this
``` 
2021-05-27T13:54:59.906-0600 INFO scheduling-1 c.h.m.m.p.PublishMetrics Published 1297 transactions in 22.51 s at 73.6/s. Errors: {} 
2021-05-27T13:55:02.135-0600 INFO pool-18-thread-1 c.h.m.m.s.r.RestSubscriber CryptoTransfer: 54 transactions in 20.00 s at 2.7/s. Errors: {} 
```

The metrics can be scraped at `http://localhost:8082/actuator/prometheus`.
We can start using these metrics that the monitor collects to display them through Grafana.

Easiest way I found was to use `dockprom`. 
```
git clone https://github.com/stefanprodan/dockprom
cd dockprom
```

Edit the file `prometheus/prometheus.yml` to add a job that collects the data from the scraper mentioned above.
```
  - job_name: 'hedera-services-monitor'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['<hostIP>:8082']
```

> I had to use my host ip instead of localhost or docker.host.internal to get it working.

Run docker compose and you will see prometheus and Grafana containers come up.

```
docker-compose up -d
```

`localhost:9090` will show you the health of all endpoints that are configured.

`localhost:3000` will give us the Grafana dashboard.

On the Grafana dashboard, you can either create your own dashboards or use [this json](https://github.com/hashgraph/hedera-mirror-node/blob/master/charts/hedera-mirror-common/dashboards/hedera-mirror-monitor.json) and import it in.
You can edit the queries for each graph in this dashboard as well to get custom visualisations.