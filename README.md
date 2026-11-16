# CS-160-Group-2-Banking-System
Software Engineering class project

## Running the Frontend and Backend for dev
Run the following commands in the terminal inside the corresponding folder (backend or frontend). The first line for each should only be needed once to install the dependencies. You may need to download uv and npm first if you don't have them

**backend**

`uv add "fastapi[standard]"`

`uv run fastapi dev main.py`

**frontend**   
`npm install`

`npm run dev`

## Running the Backend for Authentication
Run the following commands in the terminal from the project root to ensure auth0 in run with the backend.

`.\.venv\Scripts\Activate.ps1`

`$env:PYTHONPATH="$PWD\backend"`

Set JAVA_HOME to the location of your Java 17 installation:
`$env:JAVA_HOME="C:\Users\ivana\AppData\Local\Programs\Eclipse Adoptium\jdk-17-runtime"`

`$env:AUTH0_DOMAIN="dev-fg7mzenn1walaym8.us.auth0.com"`

`$env:AUTH0_AUDIENCE="https://team2bank-api"`

`python -m uvicorn backend.main:app`