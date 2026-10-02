# Current Ride Management Architecture

Inspection baseline: `feature/ride-management-service`, initially clean. The existing service contained only `pom.xml`, `application.properties`, the application bootstrap, and `HealthController`. There was no `src/test/java` directory. No branch switch, commit, or push was performed.

Before implementation:
- Java 17 inherited from `../pom.xml`; Spring Boot 3.3.3.
- Dependencies: Web, Data JPA, MySQL Connector/J, and Spring Boot Test.
- MySQL database `ridelink_ride_management_db`, port 8084, root/empty-password local datasource, Hibernate schema update and SQL logging.
- Package `com.ridelink.ride`, with only a `controller` subpackage.
- No ride service, repository, entity/document, DTO, enum, security configuration, client, or tests.

After the user's explicit MongoDB instruction:
- Java 21 for this module; Spring Boot remains 3.3.3. The parent and other modules are unchanged.
- Web, Data MongoDB, Security, Validation, JJWT 0.12.6, Springdoc 2.6.0, Spring Boot Test.
- Packages: `client`, `config`, `controller`, `dto`, `exception`, `model`, `repository`, `security`, `service`.
- Controllers: existing `HealthController`, new `RideController` and `InternalRideController`.
- `RideService` owns lifecycle and ownership rules; `RideRepository` persists immutable MongoDB records.
- Models: `Ride`, `Location`, `FareDetails`; enum `RideStatus`.
- Request DTOs: create, assign, complete, cancel; response DTO `PaymentContext`.
- MongoDB URI: `MONGODB_URI`, default `mongodb://localhost:27017/ridelink_ride_management_db`.
- Collection: `rides`. Indexes: MongoDB's unique `_id`; rider/createdAt and driver/createdAt history indexes; partial unique `activeDriverId` index for string values. Terminal rides release that active-driver claim.
- `@Version` protects writes against concurrent modifications. Automatic index creation is enabled; the database account must have index permissions.
- No other service's database is accessed. No Atlas credentials are embedded.

# Existing Endpoints

The only endpoint before this change was public `GET /api/ride/health`.

The following table describes the implemented API. Account's `PASSENGER` is normalized to `RIDER`; role claims with a `ROLE_` prefix are also accepted.

| Method | Path | Access |
| --- | --- | --- |
| GET | /api/ride/health | Public; unchanged |
| POST | /api/rides | RIDER; riderId comes from JWT |
| GET | /api/rides/{id} | Owning RIDER, assigned DRIVER, or ADMIN |
| GET | /api/rides/rider/history | RIDER; own account only |
| GET | /api/rides/driver/history | DRIVER; own Driver profile only |
| GET | /api/rides/available-drivers | RIDER or ADMIN |
| POST | /api/rides/{id}/assign | ADMIN |
| POST | /api/rides/{id}/accept | Assigned DRIVER |
| POST | /api/rides/{id}/start | Assigned DRIVER |
| POST | /api/rides/{id}/complete | Assigned DRIVER |
| POST | /api/rides/{id}/cancel | Owning RIDER or ADMIN |
| GET | /api/internal/rides/{id}/payment-context | Matching X-Service-Token only |
| GET | /swagger-ui/index.html | Public documentation |
| GET | /v3/api-docs | Public OpenAPI schema |

History parameters: `page=0`, `size=20`; page must be nonnegative, size 1–100. Histories are ordered newest first.

# Existing Security

Ride Management originally had no security. Account Service has JWT authentication and roles `PASSENGER`, `DRIVER`, `ADMIN`, not `RIDER`. Account JWTs carry an email subject plus string `userId` and `role` claims.

Implemented:
- Signed JWT verification using the same UTF-8 `JWT_SECRET` as Account, expiration required, nonblank string userId, recognized role required.
- No default JWT secret; startup fails if missing or too short. Configure at least 32 UTF-8 bytes.
- Stateless requests; no form login, HTTP Basic, or default generated user.
- Missing, expired, malformed, wrongly signed, or incomplete JWT: 401.
- Wrong role or ownership: 403.
- Principal identity is JWT userId, never the email subject or a supplied riderId.
- Driver identity: `JWT userId -> Driver.accountId -> Driver.id -> Ride.driverId`.
- Internal routes use a separate constant-time comparison of `X-Service-Token` against `RIDE_INTERNAL_TOKEN`. Missing/empty configured token disables internal access. A user/admin JWT cannot replace it.
- An internal token does not authenticate public ride operations.

# Existing Integrations

There were no Ride Management integrations. No Account HTTP client is needed for JWT verification. Driver and Fare adapters are now implemented and tested against mocked HTTP contracts; the other checked-in services do not yet satisfy those contracts.

**Reported incompatibilities; other services were not edited:**
- Driver Service exposes only `GET /api/driver-vehicle/health`; there are no driver profiles, account mapping, availability, or internal APIs.
- Fare DTOs currently require numeric rideId/userId, while Account uses string MongoDB IDs and Ride now uses string MongoDB IDs.
- Fare/Payment repositories are MongoDB repositories but their models still use JPA annotations and Long IDs.
- Fare service code calls constructors, `getTotalFare()`, and `setStatus()` that do not match those model classes.
- Neither downstream service currently implements the required internal-token or fare-idempotency contract.

Required Driver contract (to coordinate with its owner):
- `GET /api/internal/drivers/available` -> JSON array.
- `GET /api/internal/drivers/{driverId}` -> one profile.
- `GET /api/internal/drivers/account/{accountId}` -> one profile.
- Each profile: `{"id":"driver-profile-id","accountId":"account-id","status":"AVAILABLE"}`.
- `AVAILABLE` must mean eligible for assignment. Other statuses cannot be assigned.
- Authenticate `X-Service-Token` using the value configured as `DRIVER_SERVICE_TOKEN`.
- Missing profile -> 404. Timeouts, other failed responses, malformed data, and mismatched identities -> Ride returns 503.
- A missing lookup route also returns 404 if the downstream cannot distinguish it from a missing profile; configure the integration only after deploying its contract. A missing availability route returns 503.

Required Fare contract:
- `POST /api/fare-payment/fares/calculate`.
- Header `X-Service-Token` configured through `FARE_SERVICE_TOKEN`.
- Header `Idempotency-Key: ride-completion-{rideId}`; Fare must implement idempotency for retries and reject conflicting repeated inputs.
- Body: `{"rideId":"string-id","distanceKm":10,"durationMinutes":5}`.
- Response: `{"id":"fare-id","rideId":"string-id","totalAmount":950.00,"currency":"LKR"}`.
- Duration is computed from the saved start timestamp, in whole elapsed minutes.
- All upstream failures or invalid responses become 503; Ride remains IN_PROGRESS.
- After completion, Payment can retrieve trusted ride/rider/driver/fare data from the protected payment-context endpoint. Ride does not create or charge a payment.

Environment:
- `DRIVER_SERVICE_URL`: default `http://localhost:8082`.
- `FARE_SERVICE_URL`: default `http://localhost:8083`.
- `DRIVER_SERVICE_TOKEN`, `FARE_SERVICE_TOKEN`, `RIDE_INTERNAL_TOKEN`: no default secret; blank disables corresponding access.
- Connect/read timeouts: `SERVICE_CONNECT_TIMEOUT_MS=2000`, `SERVICE_READ_TIMEOUT_MS=5000`; must be positive.
- `PORT=8084` by default.

# Features Already Complete

At inspection:
- [x] Existing Spring Boot module and health endpoint.
- [x] Local MySQL configuration.
- [ ] All requested ride functionality, security, validation, integrations, and tests.

After implementation:
- [x] Create/read, rider and driver histories, availability lookup, assignment.
- [x] Accept/start/complete/cancel lifecycle and ownership.
- [x] Pickup/destination, coordinate validation, fare details, transition timestamps.
- [x] JWT and internal authentication; structured errors.
- [x] MongoDB model, indexes, optimistic locking.
- [x] Driver/Fare HTTP adapters and completed-ride payment context.
- [x] Automated service, MVC/security, and HTTP contract tests.
- [ ] Live integration with actual Driver/Fare services: contracts are missing/incompatible.
- [ ] Live MongoDB/index/concurrency verification.

# Problems Found

The checkout was a skeleton rather than the feature-complete service assumed in the original request. It had no ride business rules, tests, or security. MySQL/JPA was configured instead of MongoDB. Account and downstream contracts differed from the anticipated roles and identity types.

The repository already tracked 20 files under target/: Driver 3, Fare 14, Ride 3. Root `.gitignore` already contains `**/target/`; this does not untrack existing files. Existing tracked build files are retained unchanged in the final diff, and no new generated files are added. Removing them from the index remains repository housekeeping.

There was no Maven wrapper in this repository and no Maven executable on PATH. Default Java was 22; Java 21.0.8 was available in IntelliJ's bundled runtime.

# Changes Made

Implemented the missing feature set inside the existing module, following the existing controller/service/repository structure used by sibling services. Replaced this module's MySQL/JPA configuration with MongoDB after explicit user direction. Kept the existing health endpoint and Boot version. No existing tests were removed; none existed in this module.

Errors follow Account's `{status,message,timestamp,errors?}` shape, since Ride had no previous structured error format.

# Files Changed

Paths below are relative to `RideLink/ride-management-service`.

Modified:
- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/java/com/ridelink/ride/RideManagementServiceApplication.java`

Added under `src/main/java/com/ridelink/ride/`:
- `client/DriverServiceClient.java`, `client/FareServiceClient.java`
- `config/SecurityConfig.java`, `config/HttpClientConfig.java`, `config/OpenApiConfig.java`
- `controller/RideController.java`, `controller/InternalRideController.java`
- `dto/CreateRideRequest.java`, `dto/AssignDriverRequest.java`, `dto/CompleteRideRequest.java`, `dto/CancelRideRequest.java`, `dto/PaymentContext.java`
- `exception/ApiException.java`, `exception/ErrorResponse.java`, `exception/GlobalExceptionHandler.java`
- `model/Ride.java`, `model/RideStatus.java`, `model/Location.java`, `model/FareDetails.java`
- `repository/RideRepository.java`
- `security/JwtAuthenticationFilter.java`, `security/UserPrincipal.java`
- `service/RideService.java`

Added tests under `src/test/java/com/ridelink/ride/`:
- `service/RideServiceTest.java`
- `controller/RideHttpTest.java`
- `client/ServiceClientsTest.java`

Added this `README.md`. Account, Driver, Fare/Payment, the parent POM, and root ignore rules are unchanged.

# Validation / Business Rules

`REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED`.

- Assignment only from REQUESTED, only to an AVAILABLE driver with no active local ride.
- Accept only from ASSIGNED; start only from ACCEPTED; complete only from IN_PROGRESS.
- Driver ownership is checked before state, so an unassigned or wrong-driver action is 403 even if the transition is also invalid.
- Owner RIDER or ADMIN can cancel any nonterminal state, including IN_PROGRESS. COMPLETED and CANCELLED are terminal.
- Cancellation reason is optional (no prior requirement existed), maximum 500 characters.
- Pickup and destination are required; address nonblank and at most 500 characters; latitude [-90,90], longitude [-180,180].
- Completion distance must be strictly positive.
- Fare amount must be nonnegative, currency a three-letter uppercase code, and returned rideId must match.
- Public request bodies cannot select the rider identity or lifecycle status.
- Validation -> 400; unauthenticated -> 401; forbidden -> 403; missing ride/profile -> 404; invalid state/unavailable driver/concurrent write -> 409; dependency/database outage -> 503.

# Tests Added or Updated

No existing tests were deleted or changed.
- Service tests: creation, role and ownership checks, both histories, local availability, profile mapping, assignment, transition matrix, fare failure, cancellation, payment context.
- MVC tests: real signed JWTs, missing/invalid/expired/wrong-signature tokens, required claims, PASSENGER alias, wrong roles/owners, request validation, error shape, concurrency error translation, internal-token separation.
- HTTP client tests: paths/headers/bodies, identity mismatch, malformed responses, missing drivers, upstream HTTP failures, timeouts, disabled tokens, fare idempotency header.

# Test Results

Validation uses Java 21.0.8 and cached Maven 3.9.11 because no wrapper exists.

PowerShell command from the repository root:
```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.2\jbr'
& 'C:\Users\madhu\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' -f RideLink/ride-management-service/pom.xml clean test
git diff --check
```

Final run: **122 tests, 122 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

| Suite | Tests | Failures | Errors |
| --- | ---: | ---: | ---: |
| RideServiceTest | 51 | 0 | 0 |
| RideHttpTest | 36 | 0 | 0 |
| ServiceClientsTest | 35 | 0 | 0 |

The first run found two internal-route authentication failures, which were fixed before the successful clean rerun. Maven initially needed an approved cache write to download missing test/build dependencies. The successful final run used the same clean/test goals with batch mode, transfer progress disabled, and reduced log verbosity.

Tests use mocked repository/HTTP boundaries; they do not claim live database or cross-service validation. `git diff --check` passed. The three already-tracked Ride build artifacts were restored after testing so generated output is not part of the final changes.

# Remaining Risks

- Actual Driver/Fare services must implement the documented contracts before assignment/completion/payment integration can operate end to end. This pass deliberately does not change their modules.
- No live MongoDB test was run. Docker's engine was unavailable. Verify record persistence, automatic indexes, and concurrent assignment against the deployment database.
- MongoDB optimistic locking protects Ride state, but it is not a distributed transaction with Fare. A concurrent cancellation or a failed local save after fare creation can leave an orphan fare. Downstream idempotency and reconciliation are required.
- Availability is checked remotely and reserved locally. A Driver status change racing assignment, or assignment by another system, needs a future atomic Driver reservation contract.
- Existing Account JWTs do not contain a checked issuer/audience or revocation mechanism. Ride verifies the current signature/expiry/identity contract; disabled accounts remain authenticated until token expiry unless the platform adds revocation.
- No real payment is created by Ride. Payment must use the trusted context and string IDs rather than accept client-supplied prices/identity.
- Tracked target artifacts remain an existing repository issue. Always clean before building; do not deploy checked-in class files.

# Recommended Manual Swagger Test

1. Start MongoDB and set `MONGODB_URI` and the same `JWT_SECRET` as Account. Configure service URLs/tokens only when the documented contracts are available.
2. Build/start with Java 21 and Maven `clean spring-boot:run` from this module. Open `http://localhost:8084/swagger-ui/index.html`.
3. Without credentials, verify health is 200 and ride endpoints are 401. Authorize with an Account PASSENGER/RIDER JWT.
4. Create a ride:
   ```json
   {
     "pickup": {"address": "Colombo Fort", "latitude": 6.9344, "longitude": 79.8428},
     "destination": {"address": "Bambalapitiya", "latitude": 6.8886, "longitude": 79.8581}
   }
   ```
5. Verify own GET and history; a second rider must get 403. Try missing coordinates and out-of-range values: 400.
6. Query available drivers. Until Driver is integrated, expect 503. As ADMIN, assign using `{"driverId":"profile-id"}`; busy/already-assigned driver -> 409.
7. As the matching driver's Account JWT, accept then start. A different driver must receive 403. Repeating accept/start in the wrong state -> 409.
8. Complete with `{"distanceKm":5.2}`. A Fare outage -> 503 and ride remains IN_PROGRESS; after integration, expect COMPLETED with stored fare and timestamps.
9. Using `X-Service-Token`, retrieve payment context. No/wrong token -> 401; a noncompleted ride -> 409.
10. On another ride, cancel as its rider with `{"reason":"Plans changed"}` or `{}`. Repeated cancellation or cancelling COMPLETED -> 409.
11. Inspect `rides.getIndexes()` in the configured database and attempt simultaneous assignments to the same driver: at most one should succeed; the other returns 409.
