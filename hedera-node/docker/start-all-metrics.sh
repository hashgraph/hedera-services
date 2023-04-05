mkdir -p ./build/grafana/
cp -r ../infrastructure/.  ./build/grafana/
cp -r ../infrastructure-develop/.  ./build/grafana/
mkdir -p ./build/prometheus/
cp -r ./prometheus/.  ./build/prometheus/
docker run -d -p 3000:3000 --mount type=bind,source="$(pwd)"/build/grafana/,target=/etc/grafana/provisioning/ --name=grafana-services grafana/grafana-oss:9.3.4
docker run -d -p 9090:9090 --mount type=bind,source="$(pwd)"/build/prometheus/,target=/etc/prometheus/ --name=prometheus-services prom/prometheus:v2.41.0