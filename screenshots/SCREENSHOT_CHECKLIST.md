# Screenshot Checklist (final, all currently live)

Capture each item and save into `screenshots/` with the exact filename below.
Every file must end `.png`. URLs are included.

## Git & repository
- `01_git_repository_commits.png` — terminal: `git log --oneline -8 && git status`
- `02_github_repo_main.png` — https://github.com/karthikbaskar5455/DEVOPS_CIA_II (code page, master)
- `03_github_repo_ci_cd_yml.png` — open `.github/workflows/ci-cd.yml` in the web UI (pipeline source)

## Pipeline runs (GitHub Actions)
- `04_github_actions_run_history.png` — https://github.com/karthikbaskar5455/DEVOPS_CIA_II/actions (show runs: #5 #4 green, #3 failed, #2 success)
- `05_github_actions_run_success_all_jobs.png` — open run #5 → summary: 3 jobs all green
- `06_github_actions_maven_build_log.png` — run #5 → "Checkout, Build, Test (Maven)" → expand `mvn verify` logs (BUILD SUCCESS / Tests run)
- `07_github_actions_docker_push_log.png` — run #5 → "Build & Push Docker Image to GHCR" (docker buildx + pushed tag)
- `08_github_actions_deploy_job_logs.png` — run #5 → "Deploy to AKS" → expand steps (Azure login, Ansible, kubectl get pods/svc, endpoint)

## Registry (GHCR)
- `09_ghcr_package_page.png` — https://github.com/users/karthikbaskar5455/packages/container/package/devops_cia_ii%2Fjava-cicd-app (public, tags)

## Azure + AKS
- `10_azure_cli_aks_cluster.png` — terminal: `az aks list -o table` (show aks-cia2 Succeeded)
- `11_kubectl_get_nodes_ready.png` — terminal: `kubectl get nodes -o wide` (Ready)
- `12_kubectl_get_pods_running.png` — terminal: `kubectl get pods -n devops-ns -o wide` (Running)
- `13_kubectl_get_deploy_svc.png` — terminal: `kubectl get deploy,svc -n devops-ns` (LoadBalancer EXTERNAL-IP 4.224.114.226)

## Live endpoint
- `14_live_endpoint_home.png` — browser: `http://4.224.114.226/` (shows Version + Build)
- `15_live_endpoint_api_info.png` — browser: `http://4.224.114.226/api/info` (JSON: version/build)

## Change-and-redeploy (deliverable #4)
- `16_github_commits_change.png` — repo → Commits → show `a75ee10 Feature: show release build label…` (and the changed files)
- `17_live_endpoint_redeployed_new_build.png` — browser `/api/info` after redeploy (build: pipelines/a75ee10 confirmed)

## Supporting Azure Portal + live-app captures (`screenshots/azure_screenshots/`)

These supplement the 17 canonical shots above. Each file was renamed from the
original generic `Screenshot From 2026-10-29 HH-MM-SS.png` capture name to a
descriptive name after visual/OCR review.

- `01_azure_portal_resource_group_rg-cia2_overview.png` — Azure Portal → Resource group `rg-cia2` (Overview / Essentials: subscription, location, subscription ID)
- `02_azure_portal_aks_cluster_akscia2_overview_running.png` — Azure Portal → Kubernetes service `aks-cia2` Overview (Essentials: K8s 1.36.4, Power state Running, API address)
- `03_azure_portal_aks_cluster_troubleshooting_stop_node_pool.png` — Azure Portal → `aks-cia2` (Copilot/troubleshooting recommendations incl. "Stop the node pool"; node pools, network, CIDR essentials)
- `04_azure_portal_aks_cluster_overview_monitoring_recs.png` — Azure Portal → `aks-cia2` Overview (recommendation bar: protection status / Periscope / monitoring, Essentials)
- `05_azure_portal_aks_cluster_workloads.png` — Azure Portal → `aks-cia2 | Workloads` (running pods)
- `06_azure_portal_aks_cluster_overview_essentials.png` — Azure Portal → `aks-cia2` Overview (Essentials + Connect/Start/Stop actions)
- `07_azure_portal_aks_cluster_node_pools.png` — Azure Portal → `aks-cia2 | Node pools` (agent pools, scale/stop)
- `08_app_homepage_version_build.png` — browser `http://4.224.114.226/` → CIA2-Java-App home page (Deployed, Version, Build, Host, Time)
- `09_app_api_info_json_endpoint.png` — browser/terminal `http://4.224.114.226/api/info` → JSON (app, version, build, timestamp)

### Notes
- Suggested numbering/order matches a 5–8 page report narrative.
- If any single capture is awkward, you may include the whole run page; filenames matter more than exact framing.
- The endpoint IP is `4.224.114.226`; it may change if the service is recreated — confirm with `kubectl get svc -n devops-ns`.
- Screenshots are committed with the repo so the README figures resolve in GitHub's web UI.
