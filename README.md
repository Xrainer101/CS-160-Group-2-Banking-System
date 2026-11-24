# CS-160-Group-2-Banking-System
Software Engineering class project

This README is the user guide for the Team 2 Online Banking System. It explains how to install and configure the system (section 2), how to use it (section 3), and lists the API status codes (section 4).

## 1. Overview

| Part | Folder | Technology | Purpose |
|------|--------|-----------|---------|
| Web frontend | `frontend/` | React + Vite | Customer and manager web interface (http://localhost:5173) |
| Backend API | `backend/` | Python, FastAPI | REST API on port 8000; validates Auth0 tokens |
| Database layer | `src/main/java/` | Java 17, SQLite | DAOs and the `bank.db` database, called from Python through JPype |
| Android app | `android/` | Java, Android Studio | Mobile client that uses the same backend |

Sign-in is handled by Auth0. The backend checks the Auth0 access token on every request, and data is stored in a local SQLite database (`bank.db`).

## 2. Installation and Configuration

Choose a setup:

| Option | Best for | Requires |
|--------|----------|----------|
| Docker Compose | Quickest way to run the web app; no Java, Python, or Node install | Docker Desktop or OrbStack |
| Local, without Docker | Developing and debugging the frontend or backend | Java 17, Maven, uv, Node.js + npm |
| Android Studio | Running the mobile app | The backend running, plus Android Studio with API 36 |

### 2.1 Configuration files

Both setups need two files that your team supplies. They hold Auth0 settings and are git-ignored, so they are never committed. Don't share them publicly.

| File | Location | Contains |
|------|----------|----------|
| `.env` | repository root | Backend settings, such as `AUTH0_DOMAIN`, `AUTH0_AUDIENCE`, and the Auth0 management credentials |
| `.env.local` | `frontend/` | Frontend settings, all prefixed with `VITE_AUTH0_` (domain, client ID, audience) |

Optional backend variables:

| Variable | Description |
|----------|-------------|
| `JAVA_HOME` | Path to your JDK 17, if Maven or the backend can't find Java |
| `DATABASE_PATH` | Overrides the SQLite file location |
| `JAVA_DATABASE_PATH` | Overrides the Java layer's `bank.db` location |
| `CHECK_DEPOSIT_DB_PATH` | Overrides where mobile check-deposit requests are stored |

Restart the backend or run `npm run dev` again after changing any of these files. The backend only accepts requests from `http://localhost:5173`, so open the web app at `localhost` and not `127.0.0.1`.

Restart the backend or run `npm run dev` again after changing any of these files. The backend only accepts requests from `http://localhost:5173`, so open the web app at `localhost` and not `127.0.0.1`.

### 2.2 Run the web project with Docker Compose

1. Start Docker with Compose (for example, Docker Desktop or OrbStack). Stop any dev server using port 5173.
2. Copy the supplied `.env` file to the repository root and the supplied `.env.local` file to `frontend/.env.local`.
3. From the repository root, run:

   ```bash
   docker compose --env-file .env --env-file frontend/.env.local up --build -d
   ```

   Open [http://localhost:5173](http://localhost:5173).

**Stop the project**

```bash
docker compose --env-file .env --env-file frontend/.env.local down
```

### 2.3 Run locally without Docker

Install Java 17, Maven, uv, and Node.js with npm. Place the supplied `.env` in the repository root and `.env.local` in `frontend/`, as in the Docker setup.

From the repository root, compile the Java classes and copy their dependencies:

```bash
mvn compile dependency:copy-dependencies -DoutputDirectory=target/dependency
```

Then start the backend:

```bash
cd backend
uv run --env-file ../.env uvicorn app_with_checks:app --host 127.0.0.1 --port 8000
```

If Maven or the backend cannot find Java 17, set `JAVA_HOME` to your JDK installation. In a second terminal, start the frontend from the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The backend is started as `app_with_checks:app`, which includes the mobile check deposit endpoints. While it is running, interactive API docs are at http://127.0.0.1:8000/docs.

### 2.4 Run the Android app in Android Studio

1. Start the backend using Docker Compose or the non-Docker backend steps above. Leave the backend running. The Android emulator reaches it at `10.0.2.2:8000`. The web frontend is optional.
2. Open the `android/` folder as a project in an Android Studio version that supports Android Gradle Plugin 9.0. Let Gradle Sync finish. The included wrapper downloads Gradle and the app dependencies. Install **Android 16 (API 36)** through **SDK Manager** if prompted.
3. In **Device Manager**, create or select an Android emulator and download an API 36 system image if needed. Select the **app** run configuration and the emulator, then click **Run**.

### 2.5 Backend tests

From `backend/`, run `uv run python -m pytest tests`.

### 2.6 Troubleshooting

| Symptom | Likely cause and fix |
|---------|----------------------|
| Port 5173 or 8000 already in use | Stop the other dev server, or stop the containers with the `down` command |
| `JVMNotFoundException` or a JPype error at startup | Java 17 isn't installed or `JAVA_HOME` isn't set. Set it and restart the backend |
| `ClassNotFoundException` or missing classes | The Maven step was skipped. Run `mvn compile dependency:copy-dependencies -DoutputDirectory=target/dependency` from the repository root |
| `401 Unauthorized` on every request | `AUTH0_DOMAIN` or `AUTH0_AUDIENCE` in `.env` is missing or doesn't match the frontend's values |
| Sign-in fails or the app can't reach Auth0 | `frontend/.env.local` is missing or wrong. Restart the frontend after editing it |
| Browser shows a CORS error | Open the app at `http://localhost:5173`, not `127.0.0.1` |
| "Failed to fetch" in the app | The backend isn't running on port 8000 |
| Android app can't connect | Make sure the backend is running. The emulator reaches it at `10.0.2.2:8000` |
| Account locked after failed sign-ins | After 10 failed attempts the account is locked for 15 minutes |
| You want a clean database | Local: stop the backend, delete `bank.db`, and start it again. Docker: the data lives in the `banking-data` volume, so run `docker compose --env-file .env --env-file frontend/.env.local down -v` (this deletes all saved data) |

## 3. Using the Application

### 3.1 Creating an account and signing in

1. Open http://localhost:5173 and choose **Sign up**.
2. Enter your full name, email, phone number, username, and a password. The password needs at least 8 characters, an uppercase letter, a lowercase letter, a number, and a special character. A checklist on the form shows which rules you've met.
3. Username, email, and phone number must be unique. If one is already taken you'll see an error.
4. Download the Duo Mobile app, scan the QR code using it, and follow the instructions in the app to add 2-factor authentication and get your 6-digit code.
5. Sign in with your username and password.
6. If you forget your password, use **Forgot password** on the sign-in page.

For security, 10 failed sign-in attempts lock the account for 15 minutes.

### 3.2 Navigation

Use the left sidebar to move between pages:

| Page | What it's for |
|------|---------------|
| Overview | Dashboard with balances, quick actions, recent transactions, and upcoming payments |
| My Accounts | Your accounts; open or close an account |
| Transactions | Full transaction history |
| Transfers | Move money between your accounts or to a saved external account |
| Pay Bills | Schedule one-time or recurring bill payments |
| Deposit / Withdraw | Add money to or remove money from an account |
| Mobile Check Deposit | Upload a photo of a check |
| Find Chase ATMs | Map of nearby ATMs |
| Settings | Profile and notification preferences |

On small screens, the menu button in the top bar opens and closes the sidebar.

### 3.3 Managing accounts

- **Open an account:** on **My Accounts**, choose **Open account**, select the account type, and confirm. The new account appears in your list.
- **View details:** click an account to see its balance, details, and transaction history.
- **Close an account:** from the account, choose **Close account** and confirm. The app tells you if the account can't be closed, for example because of its balance.

### 3.4 Deposits and withdrawals

1. Choose **Deposit** or **Withdraw** in the sidebar.
2. Pick the account and enter an amount such as `25` or `25.50`. It must be greater than $0.00.
3. Confirm. The balance updates and the transaction appears in your history. A withdrawal can't exceed the available balance.

### 3.5 Transferring money

1. Open **Transfers**.
2. Choose the source account and a destination: another of your accounts or a saved external account.
3. Enter the amount and click **Review transfer**.
4. Check the summary, then confirm, or click **Edit transfer** to go back.

### 3.6 Paying bills

1. Open **Pay Bills**. The cards at the top summarize your upcoming and recurring payments.
2. Choose **Schedule a bill payment** and enter the payee, the account to pay from, the amount, and the date.
3. To repeat the payment, set a repeat schedule.
4. Review the **Payment summary** and confirm. Scheduled payments are listed below, where you can manage them.

### 3.7 Mobile check deposit

1. Open **Mobile Check Deposit**.
2. Select the account to deposit into and enter the check amount.
3. Upload a clear photo of the check (5 MB maximum).
4. Submit. Your requests are listed under **Check submission history**.

### 3.8 Finding an ATM

Open **Find Chase ATMs** to see ATMs on an interactive map. Allow location access in your browser to see results near you.

### 3.9 Settings

**Settings** shows your profile and lets you adjust notification preferences.

### 3.10 Manager features

Users with the `read:manager-reports` permission see an extra link in the sidebar that opens the manager dashboard. Managers can:

- View a summary report for the bank.
- Browse customers and see each customer's accounts and transactions (account numbers are masked).
- Close an account or deactivate a customer when needed.

Regular customers don't see these pages, and the API rejects their requests with `403 Forbidden`.

### 3.11 Using the Android app

The Android app offers the same core features: sign in or register, view accounts, move money, pay bills, deposit checks with the camera, find ATMs, and manage settings. It uses the same backend, so balances stay in sync with the web app.

## 4. API Status Codes

| Code | Meaning |
|------|---------|
| `200 OK` | Request completed |
| `201 Created` | A new resource was created |
| `400 Bad Request` | The request data is invalid or incomplete |
| `401 Unauthorized` | The Auth0 token is missing, invalid, expired, or has the wrong audience |
| `403 Forbidden` | You don't have permission (for example, manager-only endpoints) |
| `404 Not Found` | The endpoint or resource doesn't exist |
| `409 Conflict` | The username, email, or phone number is already in use |
| `422 Unprocessable Entity` | A field failed validation (for example, an invalid amount) |
| `500 Internal Server Error` | Unexpected backend or database error |
| `502 Bad Gateway` | The backend couldn't retrieve required information from Auth0 |
