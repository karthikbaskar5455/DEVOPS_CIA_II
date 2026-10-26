# CIA 2 — End-to-End Cloud CI/CD Pipeline (Azure + GitHub Actions)

Design and implementation of an automated CI/CD pipeline for a Java web
application: from `git push` → Maven build/test → Docker image → container
registry → Kubernetes on Azure (AKS) → live endpoint.

## Architecture

```
[GitHub Repo] --git push--> [GitHub Actions]
                                 |
        +------------------------+------------------------+
        |                        |                        |
   mvn clean verify       docker build/push          ansible + kubectl
   (compile+test+         (image tagged by         (render manifests,
    package jar)            git sha → GHCR)           deploy to AKS, verify)
                                 |
                                \/
                       Azure AKS (devops-ns)
                       Deployment + LoadBalancer Service
                       = public endpoint
```

## Tech stack / justification

| Component | Tool | Why | Cost |
|---|---|---|---|
| Cloud | Microsoft Azure | Matches your student subscription; AKS control plane free tier = $0/hr | ~$0.05/hr node |
| SCM | GitHub | Public repo → instructor accessible | $0 |
| CI/CD | GitHub Actions | Free on public repos, push-trigger, shell steps match required stages | $0 |
| Build | Maven | Required by problem statement | $0 |
| Language | Java 17 (Spring Boot 3) | Classic enterprise web stack | $0 |
| Container | Docker | Required | $0 (built on runner) |
| Registry | GitHub Container Registry (GHCR) | Free, public pullable, zero config with Actions | $0 |
| Orchestration | Azure Kubernetes Service (AKS) | Managed Kubernetes on Azure | node only |
| Cfg Mgmt | Ansible core | Assignment asks for "basic Ansible" — used to render + apply manifests | $0 |

## Pipeline stages

1. **Checkout** — `actions/checkout@v4`
2. **Compile & package** — `mvn -B clean verify` (compiles and packages jar)
3. **Run automated tests** — `mvn verify` runs JUnit via Surefire, reports uploaded
4. **Build Docker image** — tagged `${{ github.sha }}` and `latest`
5. **Push to registry** — GHCR (`ghcr.io/<owner>/<repo>/java-cicd-app`)
6. **Deploy to Kubernetes** — Ansible renders manifests, `kubectl apply`, rollout
7. **Verify** — Ansible URI check confirms served version == deployed tag

## Repository layout

```
.
├── pom.xml                      # Maven build (Spring Boot 3)
├── Dockerfile                   # multi-stage
├── .dockerignore
├── .github/workflows/ci-cd.yml  # GitHub Actions pipeline
├── src/...                      # Java app
├── k8s/*.j2                     # k8s manifests templated by Ansible
├── ansible/inventory            # localhost inventory (kubectl-driven)
├── ansible/playbook.yml         # render + deploy + verify
├── ansible/requirements.yml
└── README.md
```

## How to run locally

```bash
mvn clean verify          # compile, test, package
docker build -t java-cicd-app:local .
docker run -p 8080:8080 -e APP_VERSION=v0.1 java-cicd-app:local
curl localhost:8080/api/info
```

## Deploying to Azure

Requires a Service Principal in GitHub secrets (see `deploy` job):

```bash
az ad sp create-for-rbac --name "github-actions-sp" --role contributor \
  --scopes /subscriptions/<sub-id>/resourceGroups/rg-cia2
# JSON output -> GitHub secret AZURE_CREDENTIALS
```

## Security considerations (report)

- Secrets live only in GitHub encrypted secrets, never in the repo.
- Images served over HTTPS by default (Show endpoints).
- `cluster-admin` RoleBinding is a demo simplification; production should
  scope to least-privilege roles and use a dedicated service account.
- Public LoadBalancer endpoint is appropriate for this coursework demo.
- Limitations: single-node cluster, no autoscaler beyond replicas, no TLS
  certificate, no persistent storage (stateless).

## Notes for the repo

Before pushing, replace repository placeholder comments as needed and
enable the `Packages` visibility in repo settings (public repo → GHCR
public packages → instructor can pull).
