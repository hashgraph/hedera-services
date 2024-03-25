get_host_ip() {
    case "$(uname -s)" in
        Linux*)     ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+' ;;
        Darwin*)    "host.docker.internal" ;;  # Assuming you're using Wi-Fi
        *)          echo "Unsupported platform"; exit 1 ;;
    esac
}
# Get host IP
BASE_EXAMPLE_HOST=$(get_host_ip)

# Generate Prometheus configuration file
cat > prometheus/prometheus.yml <<EOF
global:
  scrape_interval: 1s

scrape_configs:
  - job_name: 'sample-scrap'
    static_configs:
      - targets: ['${BASE_EXAMPLE_HOST}:9999']
EOF

echo "Prometheus configuration file generated successfully."
docker-compose up
