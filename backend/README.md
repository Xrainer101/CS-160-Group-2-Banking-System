## Running the Test Environment

`cd backend"`

`python -m uvicorn main:app --reload --port 8000"`

## Initialize the Java Database from Python

From the project root, compile the Java code and copy its dependencies:

`mvn compile dependency:copy-dependencies -DoutputDirectory=target/dependency`


## Testing Page FastAPI
http://127.0.0.1:8000/docs

## Description

POST /api/auth/register   - creates a new user profile
POST /api/auth/login      - checks the username and password and returns a token
GET /api/auth/me          - shows logged in user when valid token provided
GET /api/admin/auth-check - tests for manager permissions from logged in user

## Status Code
200 = successful request
201 = user successfully created