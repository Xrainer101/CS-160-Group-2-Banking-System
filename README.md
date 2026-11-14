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



# Authentication Security Module
 
## Overview
 
- Password hashing using BCrypt
- Password verification using BCrypt
- Dedicated security package for authentication related functionality

PasswordUtil.java 
Password123 → generate random salt → password + salt → BCrypt hashing algorithm → hash output

AuthenticationService.java
Registration → call PasswordUtil → store hash → login → authenticate user with password

 
## Files Added
 
security/
├── PasswordUtil.java
├── AuthenticationService.java
└── TwoFactorAuth.java
 
## Dependency Added
 
pom.xml
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>