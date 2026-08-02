# Goofy IRC (Goofy Protocol Example Service)

WIP Example of a Service using the [Goofy Protocol](https://github.com/marceldobehere/goofy-protocol).

Currently hosted here: https://fe.goofy-irc.rocc.systems


## Get Started
* Clone the repo
* Install dependencies with `npm install`
* Run the tests with `npm run test`
* Run the dev server with `npm run dev`
* Profit?



## Other Stuff


## Current Support

### Symmetric Cryptography
Supported Types:
* AES-128-GCM
* AES-196-GCM
* AES-256-GCM
* ChaCha20

### Asymmetric Cryptography
Supported Types:
* RSA 2048
* RSA 3072
* RSA 4096
* EC_C25519

Not (yet) Supported Types:
* EC_P256
* EC_P384
* ML-KEM (512) + ML-DSA (44)
* ML-KEM (768) + ML-DSA (65)
* ML-KEM (1024) + ML-DSA (87)

### User Handle Derivation
Is supported

### Signed Requests
Is supported
