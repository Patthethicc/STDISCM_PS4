# Setup Guide (P4: Distributed Enrollment System)

Every node runs on its own VM (or laptop). Each node has its own database, so stopping one
VM only breaks that node's features. All node-to-node calls are gRPC.

| Node | Runs | Port(s) open to the network | Example IP |
|---|---|---|---|
| View | React site (nginx) + gateway | 80 | 192.168.56.10 |
| Auth | auth-service + auth DB | 9001 | 192.168.56.11 |
| Course | course-service + course DB | 9002 | 192.168.56.12 |
| Grade | grade-service + grade DB | 9003 | 192.168.56.13 |
| Feature | announcements (Bonus 2) + its DB | 9004 | 192.168.56.14 |

Fewer machines? Any node can share a machine with another; the profiles still keep them
as separate containers. For the failure demo, at least auth, course and grade should be on
machines you can stop separately.

---

## 1. Network

All VMs must reach each other.

- **VirtualBox:** give each VM a second adapter set to **Host-only Adapter** (same network,
  e.g. `vboxnet0` / 192.168.56.x). Set a static IP on each, or note the ones it gets.
- **Separate laptops:** connect all of them to the same Wi-Fi or hotspot, and use each
  laptop's IP (`ip a` on Linux, `ipconfig` on Windows).

Check from the view VM: `ping 192.168.56.11` (and the others).

Open the ports in the table on each VM's firewall, e.g. on Ubuntu: `sudo ufw allow 9001/tcp`.
On Windows, allow Docker Desktop through the firewall when asked.

## 2. Install on every VM

- Docker with the Compose plugin (`docker compose version` should work).
  Ubuntu: `curl -fsSL https://get.docker.com | sh`, then `sudo usermod -aG docker $USER` and log in again.
- git

```bash
git clone https://github.com/Patthethicc/STDISCM_PS4.git
cd STDISCM_PS4/deploy
```

## 3. JWT keys (generate once, copy to every VM)

On **one** machine:

```bash
./gen-keys.sh          # creates deploy/keys/private.pem and deploy/keys/public.pem
```

Copy `deploy/keys/public.pem` to **every** VM's `deploy/keys/`.
Copy `deploy/keys/private.pem` **only** to the auth VM. Never commit either (they're in `.gitignore`).

Example: `scp keys/public.pem user@192.168.56.12:~/STDISCM_PS4/deploy/keys/`

## 4. Environment config

On every VM:

```bash
cp .env.example .env
```

Edit `.env` and set `AUTH_HOST`, `COURSE_HOST`, `GRADE_HOST`, `FEATURE_HOST` to the real IPs.
Only the view node uses them, but keeping the same `.env` everywhere is simpler.

## 5. Start each node

Run on each VM, from `deploy/`:

| VM | Command |
|---|---|
| Auth | `docker compose --profile auth up -d --build` |
| Course | `docker compose --profile course up -d --build` |
| Grade | `docker compose --profile grade up -d --build` |
| Feature | `docker compose --profile feature up -d --build` |
| View | `docker compose --profile view up -d --build` |

The first build downloads Maven and npm packages, so it takes a few minutes.
Check it's up: `docker compose --profile <name> ps` and `docker compose --profile <name> logs -f`.

Open `http://<view VM IP>` in a browser and log in (accounts below).

**All on one machine (for testing):** delete the `*_HOST` lines from `.env`, then
`docker compose --profile all up -d --build`, and open `http://localhost`.

## 6. Stop / restart a node (failure demo)

```bash
docker compose --profile course stop      # node goes down
docker compose --profile course start     # node comes back; others keep running
```

Shutting the whole VM down works the same way. The gateway retries a restarted node right away,
so its pages recover within a few seconds.

Reset a node's database (re-runs schema and seed): `docker compose --profile course down -v`.

## Test accounts (password for all: `password123`)

| Username | Role |
|---|---|
| alice, bob, carol | Student |
| reyes, santos | Faculty |
| load01 ... load20 | Student (only for the concurrent-enrollment test) |

## Settings each service reads

| Service | Variables |
|---|---|
| gateway | `AUTH_ADDR`, `COURSE_ADDR`, `GRADE_ADDR`, `ANNOUNCEMENT_ADDR`, `GRPC_DEADLINE_MS`, `JWT_PUBLIC_KEY_PATH` |
| auth-service | `AUTH_DB_*`, `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`, `JWT_TTL_MINUTES`, `GRPC_PORT` |
| course-service | `COURSE_DB_HOST`, `COURSE_DB_PASSWORD` (user/db `course`/`course_db`), `JWT_PUBLIC_KEY_PATH`, port 9002 |
| grade-service | `GRADE_DB_HOST`, `GRADE_DB_PASSWORD` (user/db `grade`/`grade_db`), `JWT_PUBLIC_KEY_PATH`, port 9003 |
| feature-service | `FEATURE_DB_*`, `JWT_PUBLIC_KEY_PATH`, `GRPC_PORT` |

Course and grade services: read these names in your `application.yml`, the same way
auth-service does, so Compose can configure you.

## Running without Docker (development)

See the root `README.md` for Maven + local Postgres. For the website:

```bash
java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar   # port 8080, services on localhost by default
cd frontend && npm install && npm run dev             # http://localhost:5173
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Every page says "service temporarily unavailable" | View VM can't reach that node: check the IP in `.env`, `ping`, and the firewall port |
| Login works but other pages say "Please log in" | The node has a different `public.pem`; copy the same one everywhere and restart it |
| `public.pem` not found | Run `gen-keys.sh` / copy the keys into `deploy/keys/` before `up` |
| DB schema changed but nothing happens | Init scripts only run on an empty DB: `docker compose --profile <name> down -v`, then `up` |
| Build fails on `-pl course-service` | That module isn't in the root `pom.xml` yet |
