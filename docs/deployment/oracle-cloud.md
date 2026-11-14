# Deploying ByteHarvest on Oracle Cloud (Always Free)

One Always Free ARM VM runs the whole cluster with Docker Compose: the master, five storage nodes and the UI. Only nginx on port 80 is reachable from outside, protected by a password and by a firewall rule that allows only your IP.

Files used: `compose.prod.yaml`, `deploy/nginx.prod.conf`. Nothing in the application code changes.

## What was not tested

This kit was written from your project files and Oracle's published limits. It has not been run on a real VM. In particular:

- The memory limits (section 1) are estimates. Check them with `docker stats` and adjust.
- The three base images used by your Dockerfiles (`maven`/`eclipse-temurin`, `node`, `nginx`) are official multi-architecture images, as far as I know, so the arm64 builds should work. If a build fails with `no matching manifest for linux/arm64`, the failing image line is what to fix.
- Oracle's console menus change. The names below are descriptive, not exact.

## 1. How it is laid out

```
Internet --(port 80, your IP only)--> nginx (basic auth)
                                        |-- /          static UI
                                        |-- /api/...   --> master:8080   (internal only)
                                                            |-- storage-node-1..5 (internal only)
```

| Container | Memory limit | JVM heap cap |
| --- | --- | --- |
| master | 896 MB | 512 MB |
| storage-node-1..5 | 448 MB each | 192 MB |
| client-ui (nginx) | 64 MB | n/a |

That is about 3.2 GB at most, on a VM that has 12 GB (Oracle's Always Free ARM allowance is 2 OCPUs and 12 GB in total).

Why `/api`: the UI's own pages (`/files`, `/upload`, `/logs`, `/nodes`) use the same paths as master endpoints. Putting the API under `/api` keeps a browser refresh on `/files` from hitting the master by mistake. nginx removes the `/api` prefix before forwarding.

## 2. Create the VM

1. Create an Oracle Cloud account and choose your home region carefully. Always Free compute and disk must be created in the home region.
2. Create a compute instance:
   - Shape: `VM.Standard.A1.Flex` (Ampere ARM), 2 OCPUs, 12 GB memory.
   - Image: Ubuntu (an `aarch64` image). The default login user on Oracle's Ubuntu images is `ubuntu`.
   - Add your SSH public key, and keep the public IPv4 address option on.
   - Boot volume: the default is fine (about 47 to 50 GB).
3. If you get an "out of host capacity" error, try again later, try another availability domain if your region has several, or upgrade the account to pay-as-you-go. Oracle states that upgrading does not charge you for Always Free resources, only for usage above the free limits.

## 3. Open port 80, to your IP only

Two places block traffic, and both must allow it.

**a) Oracle's network rules.** In the instance's VCN, open its security list (or network security group) and add an ingress rule:

- Source CIDR: your own public IP followed by `/32` (find it by searching "what is my IP")
- Protocol: TCP, destination port: 80

Leave the existing rule for port 22 (SSH) in place. If your home IP changes, update the rule.

**b) The VM's own firewall.** Oracle's Ubuntu images commonly ship iptables rules that block inbound ports other than 22. On the VM:

```bash
sudo iptables -I INPUT 1 -p tcp --dport 80 -m conntrack --ctstate NEW -j ACCEPT
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

You will check that this worked in section 6.

## 4. Install Docker and get the code

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# log out and back in so the group change applies, then:
docker compose version
```

Get the project onto the VM, either way:

- If it is on GitHub: `git clone <your repo url> byteharvest && cd byteharvest`
- Otherwise, from your computer: `scp -r` the project folder to the VM, leaving out `node_modules` and `target` folders.

The `deploy/` folder and `compose.prod.yaml` must be present.

## 5. Set the password and start

From the project folder on the VM:

```bash
# creates deploy/.htpasswd with one user; replace the name and the password
printf 'admin:%s\n' "$(openssl passwd -apr1 'choose-a-strong-password')" > deploy/.htpasswd

docker compose -f compose.prod.yaml up -d --build
```

Create `deploy/.htpasswd` before the first start. If the file is missing, Docker creates a folder with that name and nginx will fail to start.

The first build downloads Maven and npm dependencies and compiles six images on two ARM cores, so expect it to take several minutes.

## 6. Check it

```bash
docker compose -f compose.prod.yaml ps      # all services "running"
docker stats --no-stream                    # memory use against the limits
```

- From your own computer, open `http://<the VM's public IP>`. Your browser asks for the user name and password.
- If the page never loads, the cause is almost always section 3: check the Oracle rule (your current IP, port 80) and the iptables rule.
- On **Node Status**, all five nodes should show ACTIVE after about 10 to 15 seconds. Upload a file and look at it on **Files**.

## 7. Everyday use

| Task | Command |
| --- | --- |
| Logs of one service | `docker compose -f compose.prod.yaml logs -f master` |
| Restart one service | `docker compose -f compose.prod.yaml restart master` |
| Deploy new code | `git pull`, then `docker compose -f compose.prod.yaml up -d --build` |
| Stop everything | `docker compose -f compose.prod.yaml down` (data volumes are kept) |
| Delete all data too | `docker compose -f compose.prod.yaml down -v` |

Containers use `restart: unless-stopped`, and Docker starts on boot, so the cluster comes back after a VM reboot.

## 8. Things to know

- **HTTP only.** Basic auth over plain HTTP sends the password unencrypted. The firewall rule limiting port 80 to your IP is the real protection. For a public demo link, add HTTPS with a free domain name and a reverse proxy that gets certificates automatically (Caddy is a common choice). That is not set up here.
- **Idle reclaim.** Oracle may reclaim an Always Free ARM instance if, over 7 days, CPU, network and memory use all stay below 20%. Your cluster idles at modest memory use, so use the VM, and keep an eye on `docker stats`, before you rely on it for a deadline.
- **Data lives in Docker volumes** on the VM. There are no backups. Recreating the VM loses the stored files and metadata.
- **No authentication in the app itself.** nginx's password and the IP rule are the only protection. Do not open port 80 to the whole internet until the master has real authentication.
- **The Chaos Simulator** works through the same `/api` proxy, behind the same password.
