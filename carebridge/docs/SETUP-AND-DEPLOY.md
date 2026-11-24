# CareBridge from setup to deployment

The fastest route is Windows + Docker Desktop for development and an Ubuntu cloud VM for the live submission. Use the optional source-development route if you want to edit the backend in Eclipse and see React changes through Vite.

## 1. Extract and install

1. Extract the ZIP to a folder you can find, for example `C:\Projects`.
2. In File Explorer, open the extracted `carebridge` folder. It contains `compose.yml`, `backend`, and `frontend`.
3. Install Docker Desktop with the WSL 2 backend, start it, then open PowerShell in the project folder. Install Git for the GitHub submission and Postman for API testing.
4. Verify your terminal can see Docker and Git.

```powershell
docker version
docker compose version
git --version
```

If Docker reports that it cannot connect, start Docker Desktop and wait for the engine to finish booting. For WSL prerequisites, follow the [Docker Desktop Windows installation guide](https://docs.docker.com/desktop/setup/install/windows-install/).

## 2. Create the local configuration

```powershell
Copy-Item .env.example .env
notepad .env
```

| Variable | What to put there |
| --- | --- |
| `DB_PASSWORD` | A random password for the CareBridge database user. |
| `DB_ROOT_PASSWORD` | A different random password for the MySQL administrator. |
| `DOCTOR_SIGNUP_CODE` | A private code you enter in the Doctor registration form. Blank disables new doctor signup. |
| `AI_DEMO_CODE` | A separate private code for the AI screen. You can leave it blank locally. It is required by the deployed configuration. |
| `LLM_API_KEY` | Your API provider key. Paste the key into this file on your own machine. |
| `LLM_API_URL` | OpenAI defaults to `https://api.openai.com/v1/chat/completions`. A compatible provider must support the same request format. |
| `LLM_MODEL` | Defaults to `gpt-4o-mini`. Choose an enabled model that supports Chat Completions JSON mode. |
| `SITE_DOMAIN` | Leave blank locally. Set the hostname on the server during deployment. |

For a copy-friendly local setup, generate each database password and invite code separately with this PowerShell command, then paste each result into the corresponding line in `.env`.

```powershell
([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
```

Use dummy emails such as `doctor@example.test` and `patient@example.test`. Give each account a separate password of at least 10 characters. For a public test, use a dedicated API key with a provider spending limit and give the AI code only to judges or teammates. The backend uses [Chat Completions](https://developers.openai.com/api/reference/resources/chat/completions/methods/create/) with [JSON mode](https://developers.openai.com/api/docs/guides/structured-outputs/). The API key stays in Spring Boot. Where your provider requires API billing, activate it in the provider account.

## 3. Build and run everything locally

From the project root:

```powershell
docker compose -f compose.yml -f compose.local.yml up --build -d
docker compose -f compose.yml -f compose.local.yml ps
docker compose -f compose.yml -f compose.local.yml logs app --tail 80
Invoke-RestMethod http://localhost:8080/api/health
Start-Process http://localhost:8080
```

The build compiles React and packages its output inside the Spring Boot JAR. MySQL starts first, Flyway creates the tables, then Spring validates them. Expected health output is `status = up`. The app may take additional time after the Compose command exits while MySQL and Spring complete startup. Repeat the health check after checking the logs.

If something else already uses port 8080, change `127.0.0.1:8080:8080` to `127.0.0.1:8081:8080` in `compose.local.yml` and use port 8081 in the browser and Postman. The container port stays 8080.

To stop and keep the database volume:

```powershell
docker compose -f compose.yml -f compose.local.yml down
```

## 4. Run the complete workflow

1. Create a Doctor account. Enter the doctor invitation code, specialty, and hospital. Set the bio under **Doctor profile**.
2. Open a private browser window and create a Patient account. Keep the doctor window open.
3. On the patient side, open **Find a doctor**. Search for the doctor or hospital. Pick a weekday within 30 days, choose a future slot, and confirm.
4. On the doctor side, refresh and open **Appointments**. Confirm the patient name, reason, and time.
5. On the patient side, add, edit, and review a timeline entry under **Medical history**.
6. Under **Medical reports**, upload `docs/demo-report.pdf`. If experimenting with other formats, `docs/demo-report.txt` also works. The app accepts up to 20 PDF pages, 3 MB per file, and 20,000 characters of extracted text.
7. Open **Sharing**, select the booked doctor, and share. Refresh the doctor dashboard and download the report under **Shared reports**.
8. Select **Summarize** on the patient report. Confirm the synthetic-data checkbox and supply the AI demo code if configured.
9. In **AI assistant**, run Healthcare Information, Terminology, and Appointment Preparation. Pick the booked appointment for preparation.
10. Revoke report access on the patient side, then verify the doctor loses access after refresh. Cancel the visit from either account before it starts, then check that the slot can be booked again. The doctor can mark a booked visit complete once its start time has passed.

## 5. Edit the source in Eclipse and run React separately

Use this route after stopping the all-in-one local Compose services. Install a full **JDK 21**, Maven 3.9+, and Node.js 22.12+ (or a supported newer version). Verify `javac -version`, `mvn -v`, `node -v`, and `npm -v`.

1. In Eclipse, select **File → Import → Maven → Existing Maven Projects**, then select `carebridge/backend`.
2. Set JDK 21 under **Java → Installed JREs** and select it for the project. This Maven project runs Spring Boot’s embedded server, so launch `com.carebridge.CareBridge` as a Java application or use the terminal command below.
3. From the project root, start only MySQL on port 3307.

```powershell
docker compose -f compose.yml -f compose.dev.yml up -d db
docker compose -f compose.yml -f compose.dev.yml ps
```

4. In a PowerShell window at the project root, run the script below. It loads simple `KEY=value` entries from your generated `.env` into this process and points Java at the local MySQL port. If running from Eclipse, enter the same variables under **Run Configurations → Environment**.

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
  $entry = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($entry[0], $entry[1], 'Process')
}
$env:DB_URL = 'jdbc:mysql://localhost:3307/carebridge?sslMode=DISABLED&allowPublicKeyRetrieval=true'
$env:DB_USER = 'carebridge'
Set-Location backend
mvn spring-boot:run
```

5. Open another PowerShell window at the project root.

```powershell
Set-Location frontend
npm ci
npm run dev
```

6. Open [http://localhost:5173](http://localhost:5173). Vite forwards `/api` to port 8080 while you edit React.
7. If you already operate your own local MySQL instance and prefer that, create the application database and user in MySQL Workbench, then point the environment variables to your instance. Run these queries as a local database administrator and replace the password placeholder.

```sql
CREATE DATABASE carebridge CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'carebridge'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_RANDOM_PASSWORD';
GRANT ALL PRIVILEGES ON carebridge.* TO 'carebridge'@'localhost';
```

Use `jdbc:mysql://localhost:3306/carebridge` for `DB_URL` in that route and ensure the server’s authentication and TLS configuration matches the JDBC connection settings. Flyway creates the tables inside the database. Use a new migration such as `V2__your_change.sql` for later schema edits, since Flyway verifies existing migration checksums.

## 6. Verify with Postman and tests

1. Import `docs/CareBridge.postman_collection.json` into Postman. In **Collection → Variables**, set `baseUrl` to `http://localhost:8080` (or your live domain later, without a trailing slash). Set the doctor and patient passwords, invitation code, AI code if required, and an upcoming weekday for `day` in `YYYY-MM-DD` format.
2. Use a fresh email for registration, or skip the Register requests if those accounts already exist. The **Read doctor profile** request captures the account’s ID.
3. Run the numbered folders in sequence. Keep Postman’s cookies enabled and consistently use one host. Every **Get CSRF token** request stores the header name and token. Refresh it after sign-in and sign-out because Spring rotates or clears the token.
4. For **Upload a report manually**, select the local synthetic file in the request’s `form-data` file field and send the request. Postman captures `reportId`. This request requires manual selection unless you configure a Postman runner file.
5. If slot lookup returns an empty array, choose another weekday or a date with open slots. Booking captures the visit ID for follow-up actions.
6. Run the repeatable Java integration test and frontend build from their folders.

```powershell
Push-Location backend
mvn test
Pop-Location
Push-Location frontend
npm ci
npm run build
Pop-Location
```

The automated test normally uses H2 in MySQL compatibility mode and a local mock LLM. To repeat against actual MySQL, provision an empty test database and set `TEST_MYSQL_URL`, `TEST_MYSQL_USER`, and `TEST_MYSQL_PASSWORD` before `mvn test`. Use a dedicated test database because the test writes fictional records. Check the real provider separately by running all four AI tools in your live application.

## 7. Push the project to GitHub

1. Create an empty repository named `carebridge` in your GitHub account. Leave README, license, and gitignore creation unchecked since the project already has files.
2. Run the commands below from the extracted project root. Check the staged file list before committing. It should include `.env.example` and should omit `.env`, dependencies, and local build output.

```powershell
git init -b main
git add .
git status --short
git commit -m "Build CareBridge healthcare hackathon demo"
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/carebridge.git
git push -u origin main
```

If Git asks for your identity, configure `git config user.name "Your Name"` and `git config user.email "your-email@example.com"` in the repo. Authenticate using your usual GitHub method. For reference, see [GitHub’s guide for existing local code](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github).

## 8. Deploy to an Ubuntu cloud VM

This route hosts the frontend, backend, and database together. The browser uses one HTTPS address. MySQL stores data on a named Docker volume and has no host-facing port. A small VM with about 2 vCPUs and 4 GB RAM is a practical starting point for building and running the demo.

1. In your cloud provider, create an Ubuntu LTS VM. Configure SSH key access. Allow TCP port 22 from your administrative IP and TCP ports 80 and 443 from the public internet. Point a DNS A record such as `carebridge.your-domain.com` to the VM’s public IPv4. If you add an AAAA record, it must point to a reachable IPv6 address on that VM.
2. Connect from PowerShell using the VM username and hostname or IP supplied by the provider.

```powershell
ssh ubuntu@YOUR_VM_PUBLIC_IP
```

3. On the VM, install Git and install Docker Engine plus the Compose plugin following [Docker’s Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/). Then confirm the tools.

```bash
sudo apt update
sudo apt install -y git
sudo docker version
sudo docker compose version
```

4. Clone your project. For a private repository, use a credential or deploy key assigned to that repository.

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/carebridge.git
cd carebridge
cp .env.example .env
chmod 600 .env
nano .env
```

5. In the server’s `.env`, fill in all four passwords/codes, the AI provider key and model, and `SITE_DOMAIN=carebridge.your-domain.com`. Use the hostname alone, without `https://` or a path. A Linux-friendly way to generate each password or application code is `openssl rand -hex 24`.
6. Build and start the deployed configuration. Caddy obtains and renews the certificate with the configured public hostname. Verify the DNS record and inbound firewall if certificate issuance fails. See [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https).

```bash
sudo docker compose -f compose.yml -f compose.prod.yml up --build -d
sudo docker compose -f compose.yml -f compose.prod.yml ps
sudo docker compose -f compose.yml -f compose.prod.yml logs --tail 80 app proxy
curl -fsS https://carebridge.your-domain.com/api/health
```

7. Open `https://carebridge.your-domain.com` on a phone or another network. The React login proves the frontend is served. Creating a synthetic account and saving data proves the deployed backend can write to MySQL. Sign out, sign back in, and check that the appointment and report persist. Make a real AI request in each mode and rerun the sharing/revocation check.
8. Share the app URL and repository URL through the hackathon submission. For demo access, give judges synthetic account credentials and the AI code through the event’s private channel. Configure the doctor account in advance and clear the doctor signup code on the VM if new doctor registrations should close.

To apply future updates:

```bash
git pull --ff-only
sudo docker compose -f compose.yml -f compose.prod.yml up --build -d
sudo docker compose -f compose.yml -f compose.prod.yml ps
```

Keep database passwords stable for an initialized volume unless you rotate the corresponding MySQL accounts deliberately. MySQL initializes those credentials on the first startup of an empty volume.

For a database export on the VM:

```bash
umask 077
sudo docker compose -f compose.yml -f compose.prod.yml exec -T db sh -c \
  'exec mysqldump --single-transaction --no-tablespaces -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  > carebridge-demo-backup.sql
```

Store the backup outside the repository and restrict who can access it. The named volume remains after a regular Compose `down`.

## 9. Diagnose common problems

| Symptom | Check |
| --- | --- |
| Docker cannot contact its engine | Open Docker Desktop and wait for it to start. Check WSL and Docker Desktop installation. |
| Container port conflict | Change the loopback host port in `compose.local.yml`. Keep the internal app port at 8080. |
| Database login rejected | Compare `DB_PASSWORD` with the password initially used for the MySQL volume. Check `docker compose logs db app`. |
| Spring fails Flyway validation | Check app logs and the schema version. Restore any edited applied migration and add changes as a new migration. |
| Browser or Postman gets 401 | Confirm credentials or sign in again. Verify you are using the same hostname for auth and data requests. |
| Postman gets 403 on a write | Get a fresh CSRF token for the current cookie session. Check the registration code or user role as applicable. |
| Slots disappear | Verify the clinic date is a weekday within 30 days. Only future, unreserved slots are listed. |
| PDF extraction rejected | Try the synthetic PDF in `docs`. Image-only and password-protected PDFs are unsupported. |
| AI screen disabled | Configure `LLM_API_KEY` and restart the app with Compose. |
| AI endpoint returns 502 | Check that the key has access to the configured model and that the provider accepts JSON mode. Check billing and quotas at the provider and retry. |
| HTTPS won’t come up | Confirm the hostname resolves to the VM, firewall ports 80/443 are open, and no other service is using them. Check proxy logs. |
| Browser displays a blank app | Check the frontend build, server logs, and whether the asset files load from the same host. |
