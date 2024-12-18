# Grafana dashboards for benchmarks

### Why

Benchmarks are heavily used by engineers to measure performance and, sometimes, as functional
tests, too. Benchmark outputs are just TPS numbers printed to stdout, and some basic stats
reported by JMH.

For deeper analysis, benchmarks can be configured to output various metrics (including the
forementioned TPS). The metrics are written to a CSV file. CSV is not very human friendly,
with lots of values reported it becomes very tricky to parse, even if copied to a table.

This folder contains files to visualize collected metrics using Grafana:

* startup scripts
* CSV plugin configuration
* dashboards

### How to start

Pre-requisites:

* docker

To run on Mac/Linux:

```shell
$ ./grafana/run-docker.sh
```

To run on Windows:

```cmd
$ ./grafana/run-docker.cmd
```

To stop:

```shell / cmd
$ docker stop grafana
```

### Dashboards

After grafana server is started in a docker container, it can be accessed using port 3000 on localhost:

```
http://localhost:3000/
```

All benchmark dashboards are available in `CryptoBench` folder. Currently only `CryptoBench` benchmark
is supported, although many dashboards are generic and can be reused for other benchmarks. Support for
other benchmarks is to come.

When a benchmark is re-run, it generates a new `BenchmarkMetrics.csv` file in the data folder. To load
the new data to grafana, just reload the web page above. No need to restart docker container.
