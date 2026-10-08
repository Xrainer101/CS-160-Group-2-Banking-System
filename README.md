# CS-160-Group-2-Banking-System
Software Engineering class project

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