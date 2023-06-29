docker stop grafana-services
docker rm grafana-services
docker stop prometheus-services
docker rm prometheus-services
rm -r ./build/grafana/

