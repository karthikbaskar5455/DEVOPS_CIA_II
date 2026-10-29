# CIA II — End-to-End Cloud CI/CD Pipeline (Azure + GitHub Actions)

**Report: automated CI/CD pipeline for a Java (Spring Boot) web application —
from `git push` to a live Kubernetes endpoint on Microsoft Azure.**

| | |
|---|---|
| **Author** | Karthik Baskar |
| **Date** | October 29, 2026 |
| **Repository** | [`karthikbaskar5455/DEVOPS_CIA_II`](https://github.com/karthikbaskar5455/DEVOPS_CIA_II) (`master`) |
| **Cloud** | Microsoft Azure for Students (subscription `cc71fc44-…-ddaac`) |
| **Live endpoint** | `http://4.224.114.226/` and `http://4.224.114.226/api/info` |

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Technology stack & justification](#3-technology-stack--justification)
4. [Pipeline stages](#4-pipeline-stages)
5. [The application](#5-the-application)
6. [Azure & Kubernetes provisioning](#6-azure--kubernetes-provisioning)
7. [Ansible as the configuration-management layer](#7-ansible-as-the-configuration-management-layer)
8. [Verification & evidence](#8-verification--evidence)
9. [Change-and-redeploy (zero-downtime rollout)](#9-change-and-redeploy)
10. [Challenges & how they were solved](#10-challenges--how-they-were-solved)
11. [Cost analysis](#11-cost-analysis)
12. [Security considerations & limitations](#12-security-considerations--limitations)
13. [How to reproduce locally](#13-how-to-reproduce)
14. [File mapping for evidence screenshots](#14-file-mapping-for-evidence-screenshots)

---

## 1. Project overview

This project implements a **fully automated DevOps pipeline** for a small Java
web application. The pipeline starts the moment a developer pushes code to the
`master` branch and ends with a **new version of the application running on an
Azure Kubernetes Service (AKS) cluster**, reachable by a public IP address —
no manual build, packaging, or deployment steps required.

The pipeline combines the five technologies requested by the assignment:

| Technology | Role in the pipeline |
|---|---|
| **Maven** | Compiles, tests and packages the Java app into a JAR |
| **Docker** | Builds a container image from that JAR |
| **Kubernetes** | Runs and updates the app on Azure AKS; exposes a LoadBalancer endpoint |
| **Ansible** | Renders the Kubernetes manifests and applies them (configuration management) |
| **GitHub Actions** | Orchestrates stage 1 → 2 → 3 automatically on every push |

Every stage is triggered automatically by a `git push` to `master` — there is
no job scheduler or manual hand-off. The final proof is a running pod that
serves the exact Git commit that was pushed.

---

## 2. Architecture

```
                          git push                      GitHub public repo
  [Dev laptop] ───────────────────────────▶ github.com/karthikbaskar5455/DEVOPS_CIA_II
                                                    │  master (this README lives here)
                                                    ▼
                                          GitHub Actions (ci-cd.yml)
                                          ┌───────────────────────────────┐
                     ┌────────────────────┤  Stage 1 · Build & Test        │
                     │                    │  mvn -B clean verify            │
                     │                    │  (compile → test → package jar) │
                     │                    └───────────────────────────────┘
                     │                                  │ succeeds
                     │                                  ▼
                     │                    ┌───────────────────────────────┐
                     │   takes ./target/  │  Stage 2 · Docker image       │
                     │   + Dockerfile     │  buildx build → push to GHCR  │
                     │                    │  ghcr.io/.../java-cicd-app     │
                     │                    │  tags: <full sha> + latest     │
                     │                    └───────────────────────────────┘
                     │                                  │ succeeds
                     │                                  ▼
                     │                    ┌───────────────────────────────┐
                     │   azure/login,     │  Stage 3 · Deploy to AKS      │
                     │   aks-set-context  │  Ansible renders k8s manifests │
                     │                    │  kubectl apply → rollout →     │
                     │                    │  verify /api/info version      │
                     │                    └───────────────────────────────┘
                     │                                  │
                     ▼                                  ▼
          Azure AKS cluster (aks-cia2, centralindia)
          namespace: devops-ns
          Deployment: java-cicd-app  (image: <ghcr>:<sha>)
          Service:    java-cicd-app-svc (LoadBalancer)
                                │
                                ▼
                    http://4.224.114.226/   (public HTTP endpoint)
                    http://4.224.114.226/api/info
```

**Key design property — the artefact is the commit.** The Docker image is
tagged with the full Git SHA (`github.sha`) and the commit's short SHA is
injected into the app as `APP_VERSION`. Because of this, *every push creates a
unique, reproducible image*, and the deployed version can be verified
end-to-end: **Git SHA in → same SHA on the container → same SHA served by the
endpoint** (see [§9](#9-change-and-redeploy)).

---

## 3. Technology stack & justification

| Component | Choice | Why this choice | Cost |
|---|---|---|---|
| Cloud | **Microsoft Azure** | Student subscription (Azure for Students, $100/12 months); AKS control plane is free ⇒ only the worker node is billed | node only |
| SCM | **GitHub** | Public repo → accessible to the instructor; natural home for Actions + GHCR | $0 |
| CI/CD | **GitHub Actions** | Free on public repos; push-triggered; built-in docker/login/setup actions | $0 |
| Build / test | **Maven** (`pom.xml`) | Required by the problem statement; `mvn verify` is one command for compile+test+package | $0 |
| Language | **Java 17 / Spring Boot 3** | Classic enterprise web stack; simple REST controller; runs on a small VM | $0 |
| Container | **Docker** (multi-stage `Dockerfile`) | Required; image is built on the GitHub runner, not locally | $0 |
| Registry | **GitHub Container Registry (GHCR)** | Same org as the repo; image marked **public** so AKS pulls with no credentials | $0 |
| Orchestration | **Azure Kubernetes Service (AKS)** | Managed K8s on Azure; free control plane tier | worker node only |
| Cfg mgmt / deploy | **Ansible core** | "Basic Ansible" requirement: Jinja-template the manifests, then deploy + verify | $0 |

All costs are $0 except the single AKS node — see [§11](#11-cost-analysis) for
the `< $5` estimate.

---

## 4. Pipeline stages

The pipeline is defined in
[`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) and triggers
`on: push: branches: [main, master]` plus manual `workflow_dispatch`. It has
**three jobs**, each one a pipeline stage. The later stages only run if the
earlier one succeeds.

### Stage 1 — Checkout, Build, Test (Maven)
```yaml
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: '17'
      cache: maven
  - run: mvn -B clean verify          # compile + test + package
  - uses: actions/upload-artifact@v4  # surefire reports + built jar
```
`mvn -B clean verify` compiles the source, runs the JUnit tests through
Surefire, and packages `target/java-cicd-app.jar`. Test reports and the jar are
uploaded as GitHub artifacts so they can be downloaded after the run.

### Stage 2 — Build & Push Docker image to GHCR
```yaml
env:
  APP_IMAGE: ghcr.io/karthikbaskar5455/devops_cia_ii/java-cicd-app

steps:
  - uses: docker/login-action@v3        # login ghcr.io with GITHUB_TOKEN
  - uses: docker/setup-buildx-action@v3
  - uses: docker/build-push-action@v4
    with:
      context: .
      push: true
      tags: |
        ${{ env.APP_IMAGE }}:${{ github.sha }}   # e.g. ...:a75ee10f…
        ${{ env.APP_IMAGE }}:latest
```
The multi-stage `Dockerfile` builds the jar inside a Maven image and then copies
it into a slim runtime image:

```dockerfile
# Stage 1: build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml . && RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: slim runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/java-cicd-app.jar app.jar
ENV APP_VERSION=local APP_BUILD=local
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

> **Registry naming gotcha:** GHCR package names must be **lowercase**, but
> `github.repository` (the env var) contains the uppercase repo name
> `DEVOPS_CIA_II`, which `buildx` rejects. The image path is therefore
> **hardcoded** to `devops_cia_ii` in the workflow `env` (see the note at the
> top of `ci-cd.yml`).

### Stage 3 — Deploy to AKS (Ansible + kubectl)
```yaml
if: vars.ENABLE_DEPLOY == 'true'   # gated by an Actions variable

steps:
  - uses: azure/login@v2                     # service principal from AZURE_CREDENTIALS
  - uses: azure/aks-set-context@v4           # picks up kubeconfig for aks-cia2
  - run: kubectl create namespace devops-ns --dry-run=client -o yaml | kubectl apply -f -
  - run: pip install --user ansible-core && echo "$HOME/.local/bin" >> "$GITHUB_PATH"
  - run: >-
      ansible-playbook -i ansible/inventory
      -e pipeline_image="${{ env.APP_IMAGE }}"
      -e pipeline_tag="${{ github.sha }}"
      ansible/playbook.yml
  - run: kubectl get pods -n devops-ns
  - run: kubectl get svc -n devops-ns
```
The `deploy` job is gated behind the repo **variable** `ENABLE_DEPLOY`. Until
that variable is `true`, the pipeline still runs stage 1 + 2 (and is all-green)
— useful while testing your build on a fresh repo before AKS exists. In this
repo the variable is set to `true`, so the full three-stage pipeline runs.

---

## 5. The application

A deliberately small Spring Boot app
([`src/main/java/com/cia2/controller/AppController.java`](src/main/java/com/cia2/controller/AppController.java))
with two endpoints:

| Endpoint | Returns |
|---|---|
| `GET /` | HTML home page showing `Version`, `Build`, hostname and timestamp |
| `GET /api/info` | JSON: `app`, `version`, `build`, `hostname`, `timestamp` |

```java
@GetMapping("/")
public String home() {
    return "<h1>" + appName + "</h1>"
         + "Version: <code>" + appVersion + "</code><br/>"
         + "Build: <code>" + appBuild + "</code> ...";
}

@GetMapping("/api/info")
public Map<String, Object> info() {
    return Map.of(
        "app", appName,
        "version", appVersion,          // = the git SHA (set by the pipeline)
        "build", appBuild,              // = "pipelines/<short sha>"
        "hostname", hostname(),
        "timestamp", OffsetDateTime.now().toString());
}
```

The `version`/`build` values are **injected at deploy time** — the Ansible
template sets the `APP_VERSION` / `APP_BUILD` environment variables on the pod
to the git SHA. This is the hook that lets us prove a new release went live.

---

## 6. Azure & Kubernetes provisioning

### 6.1 One-time cloud setup (done once, outside the pipeline)

```bash
# 1) Resource group
az group create --name rg-cia2 --location centralindia

# 2) AKS cluster (free control plane + 1 small node)
az aks create \
  --resource-group rg-cia2 \
  --name aks-cia2 \
  --node-count 1 \
  --node-vm-size Standard_B2s_v2  \
  --location centralindia \
  --enable-cluster-autoscaler false

# 3) Credentials for the pipeline's deploy job
az ad sp create-for-rbac --name "github-actions-sp" \
  --role contributor \
  --scopes /subscriptions/cc71fc44-dbd3-476f-ac58-ce7f557ddaac/resourceGroups/rg-cia2
# JSON output -> GitHub secret AZURE_CREDENTIALS
```

The evidence shots are in [`screenshots/azure_screenshots/`](screenshots/azure_screenshots/):
resource group `rg-cia2` (**figure A1**), the AKS cluster overview with power
state `Running` (**A2**), the Kubernetes version **1.36.4** (**A2/A4**), the
**Workloads** blade (**A5**) and the **Node pools** blade (**A7**).

*Screenshot 10* in the main set shows `az aks list --output table` with the
cluster state `Succeeded`.

### 6.2 Kubernetes workload

Three manifests live in [`k8s/`](k8s/) (all written as **Jinja templates**
`.j2` that Ansible renders — see §7):

- `namespace.yaml` — the `devops-ns` namespace.
- `deployment.yaml.j2` — the Deployment with the correct image
  `ghcr.io/…/java-cicd-app:<git-sha>`, environment variables, a readiness probe
  on `/api/info`, and resource requests/limits.
- `service.yaml.j2` — a `LoadBalancer` Service that gives the app a public IP.

```yaml
# k8s/deployment.yaml.j2 (key parts)
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: app
          image: "{{ image }}:{{ tag }}"        # rendered by Ansible
          imagePullPolicy: Always
          ports: [{ containerPort: 8080 }]
          env:
            - name: APP_VERSION
              value: "{{ tag }}"
            - name: APP_BUILD
              value: "pipelines/{{ tag[:7] }}"
          readinessProbe:
            httpGet: { path: /api/info, port: 8080 }
            initialDelaySeconds: 5
            periodSeconds: 5
```

> **Public-image note:** the GHCR package is **public**, so the Deployment has
> **no `imagePullSecrets`** — the node can pull it anonymously. (Originally a
> private image + a dead pull-secret caused `ImagePullBackOff`; see §10.)

---

## 7. Ansible as the configuration-management layer

Ansible satisfies the assignment's *configuration management* requirement and
owns the actual deploy step. [`ansible/playbook.yml`](ansible/playbook.yml) has
**two plays**:

**Play 1 — render (configuration management).** Uses the `template` module to
render each `.j2` manifest, injecting the pushed image and tag as variables
passed from the pipeline (`-e pipeline_image=… -e pipeline_tag=…`):

```yaml
- name: Render Kubernetes manifests
  hosts: kube
  connection: local
  gather_facts: false
  tasks:
    - name: Render deployment manifest from the Jinja template
      ansible.builtin.template:
        src: "../k8s/deployment.yaml.j2"
        dest: "../k8s/deployment.yaml"
        mode: "0644"
```

**Play 2 — deploy + verify (automation).** Applies the rendered manifests with
`kubectl`, waits for the rollout, discovers the LoadBalancer IP and **verifies
the live endpoint actually serves the pushed tag**:

```yaml
- name: Verify the application endpoint serves the new version
  ansible.builtin.uri:
    url: "http://{{ svc_ip.stdout }}/api/info"
    return_content: true
  register: app_resp

- name: Confirm the served version matches the deployed tag
  ansible.builtin.assert:
    that:
      - "'{{ tag }}' in app_resp.content"
    success_msg: "Endpoint verified: version {{ tag }} is live"
```

Because the `assert` compares the **HTTP response body** against the tag, the
job *fails* if Kubernetes accepts a config that the app does not actually
serve — a true end-to-end smoke test, not just "the pod is running".

---

## 8. Verification & evidence

The main screenshot set lives in
[`screenshots/new_updated/`](screenshots/new_updated/) (17 figures) and is
cross-referenced in [§14](#14-file-mapping-for-evidence-screenshots). The
combined picture proves every link in the chain:

1. **Git → Actions:** commits and the run history (*figures 01, 02, 04*); the
   pipeline run shows all jobs green (*figure 05*).
2. **Stage 1 (Maven):** `BUILD SUCCESS`, tests executed (*figure 06*).
3. **Stage 2 (Docker → GHCR):** buildx push log and the public GHCR package
   page with tags (*figures 07, 09*).
4. **Stage 3 (deploy):** Ansible deploy-job logs (*figure 08*).
5. **Cluster readiness:** `az aks list` → `Succeeded`; `kubectl get nodes` →
   `Ready`; pods `Running` (*figures 10, 11, 12*).
6. **Live endpoint:** `kubectl get deploy,svc` shows the LoadBalancer IP
   `4.224.114.226` (*figure 13*); the browser at `http://4.224.114.226/`
   returns the app with Version + Build, and `/api/info` returns the JSON
   (*figures 14, 15*).
7. **Re-release:** after a new commit, the endpoint shows the new build SHA
   (*figures 16, 17* — see §9).

Live results at capture time:

```
$ kubectl get nodes -o wide
NAME          STATUS   ROLES   AGE   VERSION    INTERNAL-IP     OS-IMAGE
aks-nodepool… Ready    agent   25h   v1.36.4    10.224.0.4     Ubuntu 22.04

$ kubectl get pods -n devops-ns -o wide
NAME                              READY   STATUS    RESTARTS   IP
java-cicd-app-8598f78794-8zmmg    1/1     Running   0          10.244.0.5

$ kubectl get svc -n devops-ns
NAME                TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)
java-cicd-app-svc   LoadBalancer   10.0.108.7     4.224.114.226  80:31234/TCP
```

```
$ curl -s http://4.224.114.226/api/info
{"app":"CIA2-Java-App","version":"a75ee10fb…","build":"pipelines/a75ee10",
 "hostname":"java-cicd-app-8598f78794-8zmmg","timestamp":"2026-10-29T04:03:31Z"}
```

---

## 9. Change-and-redeploy

One of the assignment's deliverables is proving that a **change is delivered
automatically**. Two sequential commits demonstrate this:

1. Commit `2d93e91` — "Fix Ansible deploy: correct kubectl apply paths …". The pipeline re-ran and re-deployed; the endpoint's rollout (from the deploy job logs) reflected the fix.
2. Commit `a75ee10` — "Feature: show release build label (APP_BUILD) on home
   page and /api/info". The pipeline pushed `ghcr.io/…/java-cicd-app:a75ee10f…`
   and deployed it. The live `/api/info` then returned
   `"build":"pipelines/a75ee10"` — **the running pod is the new commit**.

Because `kubectl apply` + Deployment rollout update the running pods (rolling
update, one replica at a time), there is **no manual SSH, no rebuild by hand,
and no downtime**. The commits page (*figure 16*) and the updated endpoint
(*figure 17*) are the whole story.

---

## 10. Challenges & how they were solved

This section records the real problems hit during the build — useful evidence
that the pipeline was actually exercised.

| # | Problem | Symptom | Root cause | Fix |
|---|---|---|---|---|
| 1 | **GHCR rejects the image path** | `buildx` fails with `repository name must be lowercase` | `github.repository` yields `DEVOPS_CIA_II` (uppercase) | Hardcode lowercase `ghcr.io/karthikbaskar5455/devops_cia_ii/java-cicd-app` in the workflow `env` (commit `b7def61`) |
| 2 | **Pod pulls image → `ImagePullBackOff` (HTTP 401/403 from GHCR)** | Pod stuck `ErrImagePull`/`ImagePullBackOff`; `kubectl describe` shows `pull access denied` | `imagePullSecrets` referenced an auto-regenerated `ghs_…` GitHub token that had **expired/died** → auth failed against GHCR | Made the package **public** and removed `imagePullSecrets`/the `ghcr-secret` step from the template and workflow. **Public image ⇒ anonymous pull ⇒ no secret to expire** (commits `e84b4cf` → `2d93e91`) |
| 3 | **Jinja `{{ }}` swallowed by templates** | Rendered manifest had empty `{{ }}` in comments | Literal `{{` in a template comment is treated as a Jinja expression | Escaped/parenthesized the double-brace in the `.j2` comment so it survives rendering (commit `e84b4cf`) |
| 4 | **Ansible working-dir mismatch** | `kubectl apply -f` could not find manifests from the `ansible/` playbook dir | Relative paths resolved against the playbook directory | Use `../k8s/…` paths in the playbook (commit `2d93e91`) |
| 5 | **Deploy job firing before AKS existed** | Pipeline "failed" because deploy job hit a non-existent cluster | No guard on the deploy stage | Gate the job behind the `vars.ENABLE_DEPLOY == 'true'` **variable** — set it only when AKS is ready (commit `93c9013`) |
| 6 | **Cost control** | Cluster billed while idle | — | Keep 1 × `Standard_B2s_v2`; **stopped the cluster** (`az aks stop`) after verification → no compute spend |

**On problem 2 (the most instructive):** a private GHCR image requires a
pull-secret holding a token. The pipeline created it from the `GITHUB_TOKEN`,
but that token is short-lived and rotated, so the stored secret eventually
stopped authenticating — classic `ImagePullBackOff`. The cleanest engineering
fix for a coursework app (no sensitive data) was to flip the package to
**public** and drop the secret machinery entirely. The fixed rendering was
verified in a live rollout: `kubectl get deploy` confirmed `imagePullSecrets`
was empty and the pod **pulled the public image with no secret and came up
`1/1 Running`**.

---

## 11. Cost analysis

| Item | Rate (approx.) | Time billed | Cost |
|---|---|---|---|
| AKS control plane (managed) | **$0/hr** | full period | $0.00 |
| Node `Standard_B2s_v2` (2 vCPU / 4 GiB, centralindia) | ~ **$0.10/hr** pay-as-you-go | ≈ 35 hr, then **stopped** | ≈ **$3.50** |
| Public GitHub repo + Actions | $0 | — | $0.00 |
| GHCR (public, pull-free) | $0 | — | $0.00 |
| Maven/Docker/Java/Ansible | $0 | — | $0.00 |
| **Total** | | | **≈ $3.50 (< $5.00)** |

Cost control measures taken:
- Single small node, no autoscale.
- Cluster **stopped** (`az aks stop -g rg-cia2 -n aks-cia2`) immediately after
  the final verification — compute spend ceased (`powerState = Stopped`).
- All build/test work runs on **free GitHub-hosted runners**, not paid cloud
  VMs.

---

## 12. Security considerations & limitations

**Security**
- All secrets (`AZURE_CREDENTIALS`, `GITHUB_TOKEN`) live in **GitHub encrypted
  secrets / Actions**, never in the repo. The service principal is scoped to
  `rg-cia2` only.
- The GHCR image is **public** for this demo, meaning anonymous pulls — no
  secret storage on the cluster (also removes the token-expiry failure mode).
- The app endpoint is **HTTP** (coursework demo, no sensitive data). Adding TLS
  (e.g. NGINX ingress + cert-manager or Azure Application Gateway) is a
  straightforward follow-up.
- `cluster-admin`/RoleBinding usage is a **demo simplification**; production
  should use least-privilege RBAC and a dedicated service account with no
  broad permissions.

**Limitations / notes for graders**
- Single-node cluster ⇒ rolling updates can only run 1 replica at a time.
- No cluster autoscaler; scaling is via `replicas` only.
- The LoadBalancer IP (`4.224.114.226`) may change if the Service is deleted;
  it is stable while the cluster is running.
- The cluster is currently **stopped** to save the budget — re-run
  `az aks start` (or re-run the pipeline with `ENABLE_DEPLOY=true`) and the
  deployment will come back with the stored image.

---

## 13. How to reproduce

**Locally (app only):**
```bash
mvn clean verify                     # compile + test + package
docker build -t java-cicd-app:local .
docker run -p 8080:8080 \
  -e APP_VERSION=v0.1 -e APP_BUILD=local-build \
  java-cicd-app:local
curl localhost:8080/          # HTML home
curl localhost:8080/api/info  # JSON {app, version, build, …}
```

**Full pipeline on your own repo:**
1. Fork/clone, then add GitHub secrets:
   - `AZURE_CREDENTIALS` — JSON from `az ad sp create-for-rbac …` (§6.1).
2. Set the repo **variable** `ENABLE_DEPLOY` to `true` once your AKS cluster
   exists (and update `resource-group`/`cluster-name` in the workflow
   `deploy` job + the `APP_IMAGE` path).
3. `git push origin master` — watch
   `https://github.com/your/repo/actions` run all three stages and deploy.
4. `kubectl get svc -n devops-ns` → `curl http://<EXTERNAL-IP>/api/info`.

---

## 14. File mapping for evidence screenshots

### Main set — `screenshots/new_updated/`

| Fig | File | What it proves |
|---|---|---|
| 01 | `01_git_repository_commits.png` | Git history (`git log`, status) — change & redeploy narrative |
| 02 | `02_github_repo_main.png` | Public repo on GitHub, `master` branch |
| 03 | `03_github_repo_ci_cd_yml.png` | The pipeline source in the web UI |
| 04 | `04_github_actions_run_history.png` | Actions run history — green runs, one expected failure |
| 05 | `05_github_actions_run_success_all_jobs.png` | Run summary: 3 jobs all green |
| 06 | `06_github_actions_maven_build_log.png` | Stage 1 — `mvn verify` BUILD SUCCESS |
| 07 | `07_github_actions_docker_push_log.png` | Stage 2 — buildx build + push log |
| 08 | `08_github_actions_deploy_job_logs.png` | Stage 3 — deploy job steps (Azure login, Ansible, kubectl get) |
| 09 | `09_ghcr_package_page.png` | Public GHCR package + tags |
| 10 | `10_azure_cli_aks_cluster.png` | `az aks list` → cluster Succeeded |
| 11 | `11_kubectl_get_nodes_ready.png` | Node Ready (v1.36.4) |
| 12 | `12_kubectl_get_pods_running.png` | Pod `1/1 Running` |
| 13 | `13_kubectl_get_deploy_svc.png` | Deployment + LoadBalancer service, IP `4.224.114.226` |
| 14 | `14_live_endpoint_home.png` | `http://4.224.114.226/` → Version + Build shown |
| 15 | `15_live_endpoint_api_info.png` | `http://4.224.114.226/api/info` → JSON |
| 16 | `16_github_commits_change.png` | New commit `a75ee10` + changed files |
| 17 | `17_live_endpoint_redeployed_new_build.png` | Endpoint after redeploy → `build: pipelines/a75ee10` |

### Supporting Azure portal set — `screenshots/azure_screenshots/`

Renamed from generic `Screenshot From 2026-10-29 HH-MM-SS.png` captures after
visual/OCR review (see `SCREENSHOT_CHECKLIST.md`).

| Fig | File | Content |
|---|---|---|
| A1 | `01_azure_portal_resource_group_rg-cia2_overview.png` | Resource group `rg-cia2` overview + Essentials |
| A2 | `02_azure_portal_aks_cluster_akscia2_overview_running.png` | AKS `aks-cia2` overview — **Running**, K8s 1.36.4 |
| A3 | `03_azure_portal_aks_cluster_troubleshooting_stop_node_pool.png` | AKS troubleshooting / "stop the node pool" recommendations |
| A4 | `04_azure_portal_aks_cluster_overview_monitoring_recs.png` | AKS overview with monitoring/Periscope recommendations |
| A5 | `05_azure_portal_aks_cluster_workloads.png` | AKS **Workloads** blade (running pods) |
| A6 | `06_azure_portal_aks_cluster_overview_essentials.png` | AKS overview Essentials / Connect / Start / Stop |
| A7 | `07_azure_portal_aks_cluster_node_pools.png` | AKS **Node pools** blade |
| A8 | `08_app_homepage_version_build.png` | `http://4.224.114.226/` — homepage (Version/Build) |
| A9 | `09_app_api_info_json_endpoint.png` | `/api/info` JSON endpoint output |

---

*Report end. Pipeline auto-triggers on every push to `master`; see
[`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml).*
