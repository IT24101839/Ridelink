# Driver and Payment without an external database

## Existing problem

Driver and Payment normally use Spring Data MongoDB repositories and create indexes at startup. Driver has drivers/vehicles documents and atomic MongoTemplate reservation updates. Payment has fares/payments/receipts documents with unique ride indexes and payment optimistic locking. Both use Spring Boot 3.3.3. Driver already used mongo-java-server 1.45.0 for tests; neither service had a local runtime persistence profile. Payment's existing API tests mocked repositories.

## Local-integration profile

Set SPRING_PROFILES_ACTIVE=local-integration in Driver and Payment only. Each process starts its own mongo-java-server MemoryBackend, listening on 127.0.0.1 on an automatically allocated port. It does not download or launch mongod and requires no Docker, Atlas URI, database password, or local MongoDB installation.

The profile supplies a MongoClient and MongoDatabaseFactory so Spring Boot's external connection defaults back off. The database is ridelink_driver or ridelink_payment. Even inherited MONGODB_URI, DRIVER_MONGODB_URI, PAYMENT_MONGODB_URI, or spring.data.mongodb.uri settings do not select an external server in this profile.

Existing MongoTemplate, repositories, indexes, controllers, JWT validation, ownership checks, service tokens, and HTTP clients are reused. Only persistence is replaced. Driver retains available/lookup/reservation/release APIs; Payment retains trusted Ride context, fare idempotency, payment processing, and receipts. No new CRUD routes were added beyond the current API.

Data survives for the lifetime of the running service and is discarded at shutdown. The Spring context closes the client and server. Restarting Driver requires recreating local driver profiles and vehicles. Restarting Payment discards local fares/payments/receipts. Use fresh test rides after resets; Ride Atlas records can outlive the supporting services' local state.

## Default / production behavior

LocalIntegrationMongoConfig is guarded by @Profile("local-integration"). Without that profile, normal MongoDB auto-configuration, URI precedence, fixed service database names, and index creation remain unchanged. Account and Ride persistence/security are not modified by this feature.

The Mongo-compatible library is now an optional compile/runtime dependency in both supporting services so ordinary mvn spring-boot:run can activate the profile. It is packaged but dormant unless explicitly activated. Do not activate this disposable-storage profile in production. This is a compatibility emulator for development, not a production MongoDB server.

## Environment variables

All services must still use the same JWT_SECRET (at least 32 UTF-8 bytes). Do not use the literal placeholders below as real values.

- Driver: SPRING_PROFILES_ACTIVE=local-integration, JWT_SECRET, DRIVER_SERVICE_TOKEN.
- Payment: SPRING_PROFILES_ACTIVE=local-integration, JWT_SECRET, FARE_SERVICE_TOKEN, RIDE_INTERNAL_TOKEN.
- Ride: keep the existing Atlas MONGODB_URI, JWT_SECRET, DRIVER_SERVICE_TOKEN, FARE_SERVICE_TOKEN, RIDE_INTERNAL_TOKEN.
- Account: retain the existing working environment.

Matching service tokens must agree with Ride. Payment's RIDE_SERVICE_URL defaults to http://localhost:8083. Ride's Driver/Fare defaults remain http://localhost:8082 and http://localhost:8084. Ports remain 8081/8082/8083/8084. Use JDK 21 for the integration setup.

## Terminal 2: Driver

```powershell
cd C:\Users\madhu\Ridelink\RideLink\driver-vehicle-service
$env:SPRING_PROFILES_ACTIVE="local-integration"
$env:JWT_SECRET="<same JWT secret as Account and Ride>"
$env:DRIVER_SERVICE_TOKEN="<same driver token as Ride>"
mvn spring-boot:run
```

No DRIVER_MONGODB_URI or MONGODB_URI is needed. Existing inherited URI settings are ignored for persistence in this profile.

## Terminal 4: Payment

```powershell
cd C:\Users\madhu\Ridelink\RideLink\fare-payment-service
$env:SPRING_PROFILES_ACTIVE="local-integration"
$env:JWT_SECRET="<same JWT secret as Account and Ride>"
$env:FARE_SERVICE_TOKEN="<same fare token as Ride>"
$env:RIDE_INTERNAL_TOKEN="<same internal Ride token as Ride>"
mvn spring-boot:run
```

No PAYMENT_MONGODB_URI or MONGODB_URI is needed. Payment still needs the real Ride service for payment-context lookup.

## Account and Ride

Start Account and Ride from their own terminals as before. Do not set local-integration globally. Keep your Ride Atlas URI and existing authentication environment. This feature does not provision Account or change how it connects to its database.

## Tests and manual checks

DriverLocalIntegrationTest runs the existing Driver behavior scenarios against the actual profile and repositories: profile/vehicle persistence, uniqueness, ownership, availability/location/areas, internal lookup/security, concurrent reservations, retries, matching release, and OFFLINE preservation.

PaymentLocalIntegrationTest uses the actual local Mongo repositories and the full application context: fare calculation/idempotency, payment creation/duplicates, processing/optimistic locking, receipts, and security. Only the external Ride client is mocked in that suite; the existing RideClientTest continues to test its HTTP contract. Both full application contexts start HTTP servers on random ports and deliberately supply an unreachable external Mongo URI to verify isolation.

LocalIntegrationProfileTest in each service verifies the profile is absent by default, starts without a URI, binds only to loopback, closes its socket on shutdown, and starts with empty data after restart. Previous production-path tests are retained. No claim is made that this emulator validates every production MongoDB feature.

For a manual check, keep Account and Ride running, start Driver and Payment with the commands above, register a driver and vehicle, make it AVAILABLE, then use Ride to assign/accept/start/complete a new ride. Create and process a payment and request its receipt. Security tokens remain required throughout. See INTEGRATION.md for the detailed API walkthrough.

## Files for this feature

- Driver and Payment pom.xml: local server dependency availability.
- Driver and Payment config/LocalIntegrationMongoConfig.java: profile-scoped persistence and lifecycle.
- DriverLocalIntegrationTest.java and PaymentLocalIntegrationTest.java: real local persistence API coverage.
- LocalIntegrationProfileTest.java in both services: default boundary, lifecycle, and disposal checks.
- This guide and links from the service READMEs.

Earlier MongoDB migration/configuration changes in the working tree are preserved separately. No branch switch, commit, or push is performed.

## Validation results (2026-10-03)

All requested tests ran on JDK 21 with the existing compiler targets retained.

| Service | Total | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Driver | 36 | 36 | 0 | 0 | 0 |
| Payment | 54 | 54 | 0 | 0 | 0 |
| Ride | 136 | 136 | 0 | 0 | 0 |
| Total | 226 | 226 | 0 | 0 | 0 |

The new local-profile coverage adds 36 tests. Account was not modified or retested in this task. git diff --check passed. Generated tracked build artifacts were restored to their pre-task contents, preserving any earlier changes.

Receipt idempotency checks compare issuedAt at BSON's millisecond precision; the initial in-memory response may have finer timestamp precision. Existing production behavior and API fields were not changed.

The complete workflow with your running Account and Atlas-backed Ride remains a manual integration check. Payment's local API tests mock the external Ride client; they do not bypass that dependency in the running service.
