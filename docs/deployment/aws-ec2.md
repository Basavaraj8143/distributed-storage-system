# Running ByteHarvest on AWS EC2 (for learning)

This guide runs the production-style setup (`compose.prod.yaml`) on one EC2 instance that you start when you want to use it and stop when you are done. It is meant for learning and demos, not for continuous hosting.

The app steps are the same as in [oracle-cloud.md](oracle-cloud.md). Only the AWS parts are new here.

## 1. What it costs

AWS changed its free tier for accounts created on or after 15 July 2025. A new account gets **$100 in credits** (up to $100 more for completing a few starter activities), and the free plan ends after **6 months or when the credits run out, whichever comes first**. After that the account must be upgraded to a paid plan, and usage is billed at normal rates. Accounts created earlier are on the old free tier.

You pay per hour only while the instance is running. A few hours a week uses very little of the credits. Check the current prices and which instance types your plan allows before you launch, because both can change.

Before anything else, create a budget alert: **Billing, Budgets, Create budget**, with a small monthly amount and your email.

## 2. Pick an instance

The whole cluster needs about 3.2 GB of memory (master 896 MB, five nodes at 448 MB each, nginx 64 MB), so it needs a **4 GB instance** such as a `t3.medium`. A 1 GB or 2 GB instance will run out of memory with all five nodes.

If you must use a smaller instance, run fewer nodes (for example 3 nodes with a replication factor of 2). That means editing `compose.prod.yaml` and `STORAGE_NODES`, and it is not covered here.

## 3. Launch the instance

In the EC2 console, choose **Launch instance** and set:

| Setting | Value |
| --- | --- |
| Image | Ubuntu Server (a recent LTS release), 64-bit x86 |
| Instance type | `t3.medium` (4 GB) |
| Key pair | Create one and keep the `.pem` file safe. You need it to connect. |
| Storage | 20 GB gp3 is enough |
| Public IP | Enabled (the default) |

**Security group.** This is the firewall. Add these rules:

| Type | Port | Source |
| --- | --- | --- |
| SSH | 22 | My IP |
| HTTP | 80 | My IP |

Do not open 8080 or 5001 to 5005. The master and nodes are meant to stay on the internal Docker network, and only nginx (port 80) is reachable. Do not use `0.0.0.0/0` for port 80: the setup is plain HTTP with Basic auth, so the password is not encrypted on the way. If your IP changes (home, college), edit the rule to your new IP.

## 4. Connect and install Docker

```bash
ssh -i your-key.pem ubuntu@<the instance's public IP>
```

On Windows, use PowerShell's `ssh`, or run the same command from WSL. If it complains about key permissions, restrict the `.pem` file to your own user.

Install Docker and the Compose plugin using Docker's official instructions for Ubuntu (https://docs.docker.com/engine/install/ubuntu/). Then let your user run Docker without `sudo`:

```bash
sudo usermod -aG docker ubuntu
# log out and connect again, then check:
docker compose version
```

## 5. Get the project and set the secrets

```bash
git clone https://github.com/Basavaraj8143/distributed-storage-system.git
cd distributed-storage-system

cp .env.example .env
nano .env        # set APP_PASSWORD and NODE_TOKEN (generate with: openssl rand -hex 32)

# nginx password file: same user and password as APP_USERNAME and APP_PASSWORD
printf 'admin:%s\n' "$(openssl passwd -apr1 'the-same-password-as-APP_PASSWORD')" > deploy/.htpasswd
```

Create `deploy/.htpasswd` before the first start. If the file is missing, Docker creates a folder with that name and nginx will not start. `.env` is git-ignored, so keep it on the server only.

## 6. Start and check

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
docker stats --no-stream
```

The first build downloads dependencies and compiles six images, so expect several minutes.

Open `http://<the instance's public IP>` in your browser. You see nginx's password prompt, then the dashboard's own sign-in page. Use the same credentials for both. On **Node Status**, all five nodes should show `ACTIVE` after about 15 seconds.

Swagger UI is off in this setup. To turn it on temporarily, add `SWAGGER_ENABLED=true` to `.env` and restart the master (and remove it afterwards).

## 7. Stop and start (the everyday routine)

**When you are done for the day: stop the instance** in the EC2 console (Instance state, Stop instance). Do not terminate it. Stopping keeps the disk, so your Docker volumes (stored chunks and metadata) survive, and you pay nothing for compute while it is stopped.

**Next time:**

1. Start the instance in the console.
2. Copy its **new public IP**. The public IP changes after every stop and start. This is fine: the dashboard reaches the master through nginx on the same address, so nothing in the project needs to change.
3. Update the security group's HTTP and SSH rules if your own IP changed.
4. Connect with SSH and run:

```bash
cd distributed-storage-system
docker compose -f compose.prod.yaml up -d
```

The containers use `restart: unless-stopped`, so they usually come back on their own, but this command makes sure. The nodes become `ACTIVE` again within about 15 seconds, and your files are still there.

## 8. Clean up when you are finished with the project

Stopped instances still bill a little for their disk (a 20 GB volume is roughly a dollar or two a month). When you are done:

1. **Terminate** the instance. This deletes the volume and all stored data.
2. Delete any Elastic IPs, snapshots and extra volumes you created (you do not need any for this setup).
3. Check **Billing** one more time.

## 9. Limits and cautions

- **Do not attach an Elastic IP** for this use. An unused one is billed, and the changing public IP is harmless here.
- **HTTP only.** Basic auth over plain HTTP is not encrypted. The IP-restricted security group is your real protection. For a shared public link you would add HTTPS with a domain name and a certificate, which is not set up here.
- **No backups.** Data lives in Docker volumes on the instance's disk. Terminating the instance loses it.
- **Credits are not unlimited.** Keep the budget alert on, and stop the instance when you are not using it.
- **Secrets** are in `.env` on the server and in the containers' environment. Anyone with shell access to the instance can read them.
- **Two sign-ins, one credential.** `deploy/.htpasswd` must match `APP_USERNAME` and `APP_PASSWORD`. If they differ, every request fails at nginx.
- **Rotating `NODE_TOKEN`** needs the master and all five nodes restarted together.
