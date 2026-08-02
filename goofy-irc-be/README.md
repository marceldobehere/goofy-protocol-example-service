# Goofy IRC Backend (Goofy Protocol Example Service)

WIP Example of a Service using the [Goofy Protocol](https://github.com/marceldobehere/goofy-protocol).

## General Infos
(TODO)

## Notes
This is still very WIP.

Demo instance currently hosted here: https://goofy-irc.rocc.systems

## Features
(TODO)


## Basic Layout
(TODO)

## Setup
(TODO)
* Clone the Repository
* Inside the `/src/main/resources` directory:
  * Change `application.properties` to use your wanted profile, probably `prod`
    * Only if you're not using docker compose / don't want the default dev profile
  * Copy the `application-prod.example.properties` to be `application-prod.properties`
  * Check the `application-prod.properties` and adapt/edit it to your needs
  * Copy the `docker-compose.example.yml` to be `docker-compose.yml` and adapt it to your needs
* Run the Application, it should create the DB and the needed tables automatically
  * Either via `mvn clean spring-boot:run` or via `docker compose build && docker compose up
* Get the Admin Register Code from the Logs and Register your Admin Account
* Profit?


## TODOs
(TODO)

## Profiles
There are currently 3 Profiles:
* `dev` - Development Profile, used for local development and testing
* `prod` - Production Profile, used for production deployment
* `test` - Test Profile, used internally for executing tests

The dev and prod Profiles use different databases and the test Profile uses an in-memory database for testing purposes.


## API Docs
The actual specs can be found by starting the application in the `dev` Profile and checking http://localhost:8080/swagger-ui/index.html.

(TODO)


### Error Codes
(TODO)
For now, Errors are split into ClientErrors and ServerErrors, which all use unique Error Codes and have the following structure:
```
{
    "errorCode": <INT>,
    "message": <Message>,
    "details": {
        <Details depending on exact error>
    }
}
```
The Error Codes can be found [here](src/main/java/com/masl/goofy_irc_be/exception) in the `client` and `server` directories.


### Cryptography
The Service (using the Core Lib) supports the main crypto algos outlined in the Goofy Protocol, which currently are:
* Symmetric
  * AES-128-GCM
  * AES-196-GCM
  * AES-256-GCM (**Recommended**)
  * ChaCha20 (**Recommended**)
* Asymmetric 
  * RSA 2048
  * RSA 3072 (**Recommended**)
  * RSA 4096 (**Recommended**)
  * EC_P256
  * EC_P384
  * EC_C25519 (**Recommended**)
  * ML-KEM (512) + ML-DSA (44)
  * ML-KEM (768) + ML-DSA (65)
  * ML-KEM (1024) + ML-DSA (87)

