# CS-160-Group-2-Banking-System
Software Engineering class project

## Running the Frontend and Backend for dev
Run the following commands in the terminal inside the corresponding folder (backend or frontend). You may need to download uv, npm, and maven first if you don't have them

**backend**

from root, needed once:  `mvn compile dependency:copy-dependencies -DoutputDirectory=target/dependency`

from root: `cd backend`

`uv run uvicorn main:app --reload --port 8000`

**frontend**   

from root:  `cd frontend`

needed once:  `npm install` 

`npm run dev`

**Keep both backend and frontend running**

## Backend tests

From `backend`, run `uv run python -m pytest tests`. This runs the tests named `test_*.py` added under `tests/`.

## Running the Backend for Authentication
Run the following commands in the terminal from the project root to ensure auth0 in run with the backend.

`.\.venv\Scripts\Activate.ps1`

`$env:PYTHONPATH="$PWD\backend"`

Set JAVA_HOME to the location of your Java 17 installation:
`$env:JAVA_HOME="C:\Path\To\Your\Java17"`

`$env:AUTH0_DOMAIN="dev-fg7mzenn1walaym8.us.auth0.com"`

`$env:AUTH0_AUDIENCE="https://team2bank-api"`

Include the management client id from the .env file:
`$env:AUTH0_MANAGEMENT_CLIENT_ID="<client_id>"`

Include the management client secret from the .env file:
`$env:AUTH0_MANAGEMENT_CLIENT_SECRET="<client_secret>"`

`$env:AUTH0_MANAGEMENT_AUDIENCE="https://dev-fg7mzenn1walaym8.us.auth0.com/api/v2/"`

`python -m uvicorn backend.main:app`