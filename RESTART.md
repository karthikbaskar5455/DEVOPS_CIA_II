# How to Start / Restart the Project

The Azure AKS cluster is **stopped** to prevent compute billing. Follow these steps
to bring the application back online and re-run the full pipeline. All steps are
one-time setup; subsequent runs need only step 1 + 5.

---

## Prerequisites

- Azure CLI (`az`) installed and logged in:
  ```bash
  az login --use-device-code
  az account set --subscription cc71fc44-dbd3-476f-ac58-ce7f557ddaac
  ```
- Your GitHub personal access token (PAT) with `repo` scope stored in
  `~/.git-credentials` as `https://YOUR_USERNAME:TOKEN@github.com` (so `git push` works).
  Or use `gh auth login` for a password-less experience.
- The repo is at: `https://github.com/karthikbaskar5455/DEVOPS_CIA_II`

---

## Step 1 — Start the AKS cluster

```bash
az aks start \
  --resource-group rg-cia2 \
  --name aks-cia2
```

Wait until it's ready (usually 3–5 minutes):

```bash
az aks show -g rg-cia2 -n aks-cia2 \
  --query '{name:name, powerState:powerState.code}' -o table
```

Look for `powerState: Running` before proceeding.

---

## Step 2 — Verify kubectl can talk to the cluster

```bash
az aks get-credentials \
  --resource-group rg-cia2 \
  --name aks-cia2 \
  --overwrite-existing

kubectl get nodes
# Expected: one node with STATUS = Ready
```

---

## Step 3 — Re-run the GitHub Actions pipeline

**Option A — Push a trivial commit (auto-triggers the pipeline):**

```bash
cd /path/to/DEVOPS_CIA_II
git checkout master
git pull origin master
# Make a tiny harmless change
echo "# $(date)" >> RESTART.md
git add RESTART.md
git commit -m "ci: trigger pipeline after cluster start"
git push origin master
```

**Option B — Re-run from the GitHub web UI (no push needed):**
1. Go to https://github.com/karthikbaskar5455/DEVOPS_CIA_II/actions
2. Click the latest run → **Re-run all jobs** (top right)

The pipeline will run all 4 jobs:
```
Gitleaks Secret Scan  →  Maven Build & Test  →  Docker → GHCR  →  Deploy to AKS
```

---

## Step 4 — Watch the pipeline and get the endpoint IP

```bash
# In another terminal:
watch -n 5 "curl -s https://api.github.com/repos/karthikbaskar5455/DEVOPS_CIA_II/actions/runs?per_page=3 \
  | python3 -c \"import json,sys; [print(r['run_number'], r['status'], r['conclusion']) for r in json.load(sys.stdin)['workflow_runs'][:3]]\""
```

Or watch the Actions tab on GitHub directly.

When the `Deploy to AKS` job finishes (all-green ✅), fetch the public IP:

```bash
kubectl get svc -n devops-ns
# NAME                TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)
# java-cicd-app-svc   LoadBalancer   10.0.x.x      4.224.114.226   80:31234/TCP
```

The **EXTERNAL-IP** column is your app URL.

---

## Step 5 — Verify the app is live

```bash
# HTML home page
curl http://4.224.114.226/
# JSON info
curl http://4.224.114.226/api/info
```

Expected JSON response:
```json
{
  "app":"CIA2-Java-App",
  "version":"<git-sha-of-pushed-commit>",
  "build":"pipelines/<short-sha>",
  "hostname":"java-cicd-app-...",
  "timestamp":"2026-..."
}
```

---

## Step 6 — Stop the cluster to save money

**After you're done demonstrating / taking screenshots:**

```bash
az aks stop --resource-group rg-cia2 --name aks-cia2 --no-wait
echo "Cluster stopping… compute billing halted."
```

The app will go offline (HTTP 503 / connection refused). Billing stops immediately.

---

## Quick Reference

| Task | Command |
|---|---|
| Start cluster | `az aks start -g rg-cia2 -n aks-cia2` |
| Stop cluster | `az aks stop -g rg-cia2 -n aks-cia2 --no-wait` |
| Check cluster state | `az aks show -g rg-cia2 -n aks-cia2 --query powerState.code -o tsv` |
| Get app IP | `kubectl get svc -n devops-ns -o jsonpath='{.items[0].status.loadBalancer.ingress[0].ip}'` |
| Test app | `curl http://<IP>/api/info` |
| Re-run pipeline | Push a commit, or click **Re-run all jobs** in GitHub Actions |
| Check pipeline status | https://github.com/karthikbaskar5455/DEVOPS_CIA_II/actions |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `az aks start` times out | Wait 5 more minutes and retry; Azure takes time to provision the VM |
| `kubectl get nodes` hangs | Run `az aks get-credentials …` again to refresh the kubeconfig |
| Pipeline stuck on Gitleaks | A secret was detected — check the job log, remove the secret, amend commit |
| Deploy job skipped | `ENABLE_DEPLOY` variable is not `true` — set it at: GitHub repo → Settings → Actions → Variables → `ENABLE_DEPLOY = true` |
| App returns 503 | Cluster is stopped — run `az aks start` |
| Old app version shown | Pipeline may not have run yet, or cluster was stopped during deploy — re-run |

---

## Cost reminder

- AKS control plane: **$0** (free tier)
- Node (`Standard_B2s_v2`, 1 node): ~**$0.10/hr**
- Strategy: **run ~30–35 hours total** (~$3.50) then stop with `az aks stop`
- The cluster has only been run for demonstrations — total spend is well **under $5**.