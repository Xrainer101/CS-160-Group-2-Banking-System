# CS-160-Group-2-Banking-System
Software Engineering class project

## Run the web project with Docker Compose

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

## Run locally without Docker

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

## Run the Android app in Android Studio

1. Start the backend using Docker Compose or the non-Docker backend steps above. Leave the backend running. The Android emulator reaches it at `10.0.2.2:8000`. The web frontend is optional.
2. Open the `android/` folder as a project in an Android Studio version that supports Android Gradle Plugin 9.0. Let Gradle Sync finish. The included wrapper downloads Gradle and the app dependencies. Install **Android 16 (API 36)** through **SDK Manager** if prompted.
3. In **Device Manager**, create or select an Android emulator and download an API 36 system image if needed. Select the **app** run configuration and the emulator, then click **Run**.

## Backend tests

From `backend/`, run `uv run python -m pytest tests`.