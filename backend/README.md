## Running the Test Environment

`cd backend"`

`python -m uvicorn main:app --reload --port 8000"`

## Initialize the Java Database from Python

From the project root, compile the Java code and copy its dependencies:

`mvn compile dependency:copy-dependencies -DoutputDirectory=target/dependency`

## Running the backend with auth0

From the project root open a terminal and run:

    .\.venv\Scripts\Activate.ps1

    $env:PYTHONPATH="$PWD\backend"

    $env:JAVA_HOME="C:\Users\ivana\AppData\Local\Programs\Eclipse Adoptium\jdk-17-runtime"

    $env:AUTH0_DOMAIN="dev-fg7mzenn1walaym8.us.auth0.com"

    $env:AUTH0_AUDIENCE="https://team2bank-api"

    python -m uvicorn backend.main:app

## Testing Page
http://127.0.0.1:8000

## Status Codes

- `200 OK` — Request completed successfully
- `201 Created` — A new resource was created successfully
- `400 Bad Request` — The request data is invalid or incomplete
- `401 Unauthorized` — The auth0 token is missing, invalid, expired, or has the wrong audience
- `404 Not Found` — The requested endpoint or resource does not exist
- `409 Conflict` - The username, email, or phone number is already in use
- `500 Internal Server Error` — An unexpected backend or database error occurred
- `502 Bad Gateway` — The backend could not retrieve required information from Auth0
