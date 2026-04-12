$ErrorActionPreference = "Stop"

Write-Host "Validating root Docker Compose..."
docker compose --profile apps --profile postgres config | Out-Null

Write-Host "Validating monitoring Docker Compose..."
docker compose -f ops/monitoring/docker-compose.monitoring.yml config | Out-Null

$helm = Get-Command helm -ErrorAction SilentlyContinue
if (-not $helm) {
    throw "helm is required to validate the Helm chart."
}

Write-Host "Linting Helm chart..."
helm lint ops/deployment/helm/lsf-service

Write-Host "Rendering Helm chart..."
helm template lsf-service ops/deployment/helm/lsf-service `
    --set image.repository=ghcr.io/example/lsf-service `
    --set image.tag=ci `
    --set env.SPRING_APPLICATION_NAME=lsf-service `
    --set env.LSF_KAFKA_BOOTSTRAP_SERVERS=kafka.default.svc.cluster.local:9092 | Out-Null

Write-Host "Deployment artifact validation completed."
