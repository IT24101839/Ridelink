# RideLink backend integration

Implemented on `feature/backend-integration`. No cross-service database access. Account, Driver, and Payment retain Java 17 source targets; Ride retains Java 21. Existing Spring Boot versions are unchanged.

## Phase 0 changes

Payment uses MongoDB documents and string identifiers consistently. Removed incompatible JPA mappings and mismatched constructor/getter/setter calls. Payment now runs on 8084 and Ride's Fare URL defaults to that port. Removed the literal Atlas URI and Account's production JWT fallback. Previously exposed database credentials must be rotated by their owner; removing a working-tree value does not remove Git history.

The four-module package build passed before implementing the later phases.

## Account and Driver

Account continues to issue `sub=email`, `userId`, `role`, `iat`, and `exp`. Its validator now rejects missing identity/role/expiration claims without null-pointer failures.

Driver now uses MongoDB (see [migration guide](MONGODB_LOCAL.md)). Driver and Vehicle IDs are generated string UUIDs. Driver.accountId and licenseNumber have database uniqueness constraints; plateNumber is unique. Driver profile creation derives accountId from the authenticated JWT. Extra accountId fields are rejected. User APIs require DRIVER or ADMIN, except registration which requires DRIVER. Vehicle access is checked against the owning driver.

Driver fields: id, accountId, fullName, email, phone, licenseNumber, status, serviceAreas, currentLat/currentLng, rating, totalRides, createdAt. Rating and totalRides are read-only initialized statistics; no rating/statistics workflow was added. New profiles start OFFLINE. An active vehicle is required to become AVAILABLE. BUSY is reserved for the internal reservation flow.

Public endpoints:
- POST /api/v1/drivers
- GET /api/v1/drivers/{id}
- GET /api/v1/drivers/account/{accountId}
- PATCH /api/v1/drivers/{id}/availability - `{"status":"AVAILABLE"}` or OFFLINE
- PATCH /api/v1/drivers/{id}/location - `{"currentLat":6.9,"currentLng":79.8}`
- PATCH /api/v1/drivers/{id}/service-areas - `{"serviceAreas":["Colombo"]}`
- POST /api/v1/vehicles
- GET /api/v1/vehicles/driver/{driverId}

Profile registration:
```json
{"fullName":"Driver One","email":"driver@example.com","phone":"0771234567","licenseNumber":"LIC-001","serviceAreas":["Colombo"]}
```

Vehicle registration:
```json
{"driverId":"<profile-id>","plateNumber":"ABC-1234","make":"Toyota","model":"Aqua","year":2020,"color":"Blue","type":"CAR","seatCapacity":4}
```

## Driver and Ride

All internal Driver APIs require `X-Service-Token: <DRIVER_SERVICE_TOKEN>`:
- GET /api/internal/drivers/available - bare profile array
- GET /api/internal/drivers/{driverId}
- GET /api/internal/drivers/account/{accountId}
- PUT /api/internal/drivers/{driverId}/reservation - `{"rideId":"..."}`
- DELETE /api/internal/drivers/{driverId}/reservation/{rideId}

Profile response: `{"id":"driver-profile-id","accountId":"account-id","status":"AVAILABLE"}`.

Reservation uses an atomic conditional MongoDB findAndModify. AVAILABLE becomes BUSY. The same ride can retry; a competing ride gets 409. Wrong-ride release gets 409. Releasing an already-free driver is harmless. Explicit OFFLINE is preserved on matching release.

Ride's identity mapping remains:
`JWT userId -> Driver.accountId -> Driver.id -> Ride.driverId`.

Ride persists a REQUESTED assignment intent using its activeDriverId before calling Driver. A successful remote reservation is followed by saving ASSIGNED with Driver.id. If the response or final write is uncertain, the durable intent is retried rather than blindly releasing a possibly valid reservation.

A scheduler retries persisted assignment, completion, and release operations every 10 seconds (`ride.recovery-delay-ms`). It handles batches of up to 50 per operation type. A confirmed reservation 404/409 clears an unfulfilled assignment intent. Persistent failures remain visible in logs and database state.

Completion/cancellation first saves terminal Ride state with a pending release. Driver outages do not undo an already completed/cancelled ride. The response includes `driverReleasePending: true` until release acknowledgement is saved. A later Driver 404 or a newer reservation confirms this ride no longer holds that driver. The old reservation is never used to release the newer ride.

Cancellation is rejected while an assignment or completion intent is pending, avoiding reserve/cancel and fare/cancel races. Retry the original operation or restore its dependency; recovery will continue automatically.

## Ride and Payment

Final fare calculation:
```http
POST /api/fare-payment/fares/calculate
X-Service-Token: <FARE_SERVICE_TOKEN>
Idempotency-Key: ride-completion-<rideId>
Content-Type: application/json

{"rideId":"<string-id>","distanceKm":5.2,"durationMinutes":12}
```

The canonical idempotency key is required. A deterministic fare document ID plus Mongo insert prevents duplicate creation. Repeating equivalent distance/duration returns the same fare. Conflicting data returns 409. Response includes string id/rideId, distanceKm, durationMinutes, totalAmount, currency, createdAt, idempotencyKey.

Formula remains `100 + distanceKm * 80 + durationMinutes * 10`, rounded to two decimal places, currency LKR. There is no separate estimate endpoint in this implementation.

Ride freezes distance and completion-request time before calling Fare. Retrying uses the same duration even if time has elapsed. It remains IN_PROGRESS until Fare succeeds. Retries with a different distance return 409. Automatic recovery may finish a previously accepted completion after an outage.

Payment creation:
```http
POST /api/fare-payment/payments
Authorization: Bearer <Account PASSENGER/RIDER JWT>
Content-Type: application/json

{"rideId":"<ride-id>","paymentMethod":"CARD"}
```

Only rideId and paymentMethod are accepted. Payment calls `GET /api/internal/rides/{id}/payment-context` with `RIDE_INTERNAL_TOKEN`, verifies COMPLETED and matching JWT userId/riderId, and copies the authoritative fare. A deterministic payment ID plus Mongo insert guarantees one payment document per ride; duplicates return 409, including attempts after failure. No client amount, userId, or riderId is accepted.

Final fare calculation does not call the completed-only payment-context endpoint. That endpoint is used after completion for payment creation, avoiding a completion dependency cycle.

Payment endpoints:
- GET /api/fare-payment/payments/{id} - owner RIDER or ADMIN
- POST /api/fare-payment/payments/{id}/process - owner RIDER or ADMIN; `{"success":true}` or false
- PUT /api/fare-payment/payments/{id}/status - internal Fare service token only; COMPLETED or FAILED
- POST /api/fare-payment/payments/{id}/receipt - generate/retrieve receipt, owner RIDER or ADMIN
- GET /api/fare-payment/payments/{id}/receipt - retrieve existing receipt, owner RIDER or ADMIN
- GET /api/fare-payment/fares/ride/{rideId} - Fare service token only

Processing is an academic simulation: no gateway, card details, or money movement. States are PENDING -> COMPLETED or FAILED. A repeated identical terminal result is idempotent; changing a terminal result or processing to PENDING returns 409. Optimistic locking prevents concurrent state overwrites.

A successful payment receives a stable SIM-prefixed transaction reference and paidAt. Receipts exist only for COMPLETED payments. Their document ID is paymentId, making generation idempotent. Fields: paymentId, rideId, riderId, amount, currency, transactionReference, paidAt, issuedAt.

## Security and errors

Account's PASSENGER role is normalized to RIDER in Ride and Payment (Driver accepts DRIVER/ADMIN operations). User requests use JWT; internal calls use separate service tokens. A service token does not authenticate user endpoints. A user/admin JWT does not replace an internal token.

- 400: invalid payload or idempotency key
- 401: missing/invalid/expired token, or invalid internal token
- 403: role/ownership denial
- 404: missing record
- 409: duplicate profile/reservation/payment, invalid state, conflicting idempotency data, concurrent modification
- 503: downstream or database unavailable

JWT signatures use the same UTF-8 JWT_SECRET across services, at least 32 bytes. There is no production default. Each service token defaults empty and then denies internal access. Keep tokens distinct and configure matching producer/consumer values.

## Configuration and ports

| Service | Port default | Database |
| --- | --- | --- |
| Account | 8081 | MongoDB ridelink_account |
| Driver | 8082 | MongoDB ridelink_driver |
| Ride | 8083 | MongoDB ridelink_ride |
| Payment | 8084 | MongoDB ridelink_payment |

Environment per process:
- All: JWT_SECRET.
- Account: MONGODB_URI; JWT_EXPIRATION_MS defaults 86400000.
- Driver: MONGODB_URI (credential-free localhost Driver DB default), DRIVER_SERVICE_TOKEN.
- Ride: MONGODB_URI (credential-free localhost Ride DB default), DRIVER_SERVICE_URL (http://localhost:8082), DRIVER_SERVICE_TOKEN, FARE_SERVICE_URL (http://localhost:8084), FARE_SERVICE_TOKEN, RIDE_INTERNAL_TOKEN.
- Payment: MONGODB_URI (credential-free localhost Payment DB default), FARE_SERVICE_TOKEN, RIDE_SERVICE_URL (http://localhost:8083), RIDE_INTERNAL_TOKEN.
- All services: PORT can override the service-specific default.
- Ride/Payment: SERVICE_CONNECT_TIMEOUT_MS defaults 2000, SERVICE_READ_TIMEOUT_MS defaults 5000.

Do not share a single database URI across all process environments. Account uses Boot 4's spring.mongodb properties; Ride/Payment use Boot 3's spring.data.mongodb properties.

Mongo auto-index creation is enabled for Driver, Ride, and Payment. For a deployed database, review migrations and index permissions before startup.

## Database ownership

Every repository belongs to its own service database. Driver uses MongoDB repositories only against Driver's configured database. Ride and Payment communicate over HTTP and never import or query one another's repositories.

Existing Payment records with numeric IDs or incompatible schemas need migration before using this version. No existing database was altered by this task's unit/MVC tests; Driver tests use a test-only Mongo-compatible in-memory server.

## Tests and validation

Existing tests are retained. Added:
- Account signed-token missing-claim and role tests.
- Driver full-context/Mongo-compatible persistence tests: actual Account signing classes compile into a temporary test directory and issue a token consumed by Driver; identity spoofing, roles, duplicate profile, vehicle eligibility, internal APIs, reservation retries, wrong release, OFFLINE preservation, and concurrent atomic reservations.
- Ride reservation HTTP client tests and persisted assignment/completion/release recovery tests.
- Payment MVC/service tests for fare idempotency, strict payloads, context ownership, duplicate payment, processing, receipt rules, and token boundaries.
- Payment -> Ride HTTP client tests for path/header/identity/state and outage handling.

Run Maven package from RideLink with Java 21 to build the full reactor, retaining each module's compiler target. Account/Driver/Payment are also validated separately with Java 17. Driver's real-Account-token test expects the sibling account-service sources, so run within the complete repository checkout.

Previous integration validation (2026-10-02; superseded by the MongoDB migration results in [MONGODB_LOCAL.md](MONGODB_LOCAL.md)):

| Module | Runtime | Tests | Passed | Failures | Errors | Skipped |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Account | Java 17 | 50 | 50 | 0 | 0 | 0 |
| Driver | Java 17 | 11 | 11 | 0 | 0 | 0 |
| Ride | Java 21 | 132 | 132 | 0 | 0 | 0 |
| Payment | Java 17 | 28 | 28 | 0 | 0 | 0 |
| Total | | 221 | 221 | 0 | 0 | 0 |

All four package builds passed. The whole reactor also passed on Java 21 with per-module compiler targets retained. There are 56 additional test cases beyond the existing 165. One existing Ride test was updated to assert a persisted IN_PROGRESS completion intent on Fare failure rather than no persistence at all. git diff --check passed. Docker's engine was unavailable; live MongoDB and MySQL behavior and a live four-process walkthrough remain deployment validation, not claims made by these tests.

## Manual four-service test plan

1. Start separate local/test databases and configure per-process variables above. Use fresh test data. Supply matching JWT and service tokens.
2. Start Account :8081, Driver :8082, Ride :8083, Payment :8084. Use each module's Maven spring-boot:run or built executable jar.
3. Register/login a PASSENGER and a DRIVER via Account. Obtain an ADMIN token through the existing authorized admin setup; public ADMIN registration stays blocked.
4. With the DRIVER JWT, POST /api/v1/drivers using the example profile. Save its id, which differs from Account userId. Attempting accountId in the body must return 400.
5. Add a vehicle owned by that profile; change availability to AVAILABLE. Another driver's JWT must receive 403 for profile/vehicle operations.
6. With the PASSENGER JWT, create a Ride with pickup/destination. Verify its riderId equals Account userId. A different passenger cannot read or cancel it.
7. List available drivers through Ride. As ADMIN, assign the profile ID. Verify Driver is BUSY and no longer available. Competing reservation returns 409; same-ride retry at Driver is idempotent.
8. With the assigned driver's JWT, accept and start. A different driver's token returns 403.
9. Complete with distanceKm > 0. Verify completed fare, frozen timestamps, and Driver release. To exercise recovery, interrupt Driver/Fare availability at the relevant step, restore it, and observe the pending operation recover.
10. As the owning passenger, create Payment with only rideId and paymentMethod. Wrong passenger -> 403; incomplete ride -> 409; spoofed amount/riderId -> 400; second payment -> 409.
11. Request receipt before processing -> 409. Process with success=true, then POST/GET receipt. Repeated process/receipt calls retain transaction reference and issuedAt.
12. For a second completed ride, process payment with success=false. Receipt and later success transition must return 409.
13. For a separate assigned ride, explicitly set Driver OFFLINE and cancel the ride. Matching release must preserve OFFLINE.
14. Verify missing/invalid/expired JWT -> 401, wrong role -> 403, missing record -> 404, and missing/wrong service token -> 401. Do not use live production credentials or databases for this exercise.

## Remaining risks

- Account role/deactivation changes do not revoke already-issued JWTs. JWTs remain valid until expiry.
- Driver-submitted distance is validated positive but not independently measured.
- Recovery is durable, not a distributed transaction. Permanent upstream conflicts need operator investigation; batches are limited to 50 per operation type.
- A completion intent prevents cancellation while its result is uncertain. This is deliberate to avoid billing a cancelled ride.
- A completed payment is simulated, not proof of a real financial transaction.
- There is one payment per ride; a FAILED payment cannot create a second payment attempt under this minimal contract.
- Vehicle/Driver rating and ride-statistics maintenance, payment refunds, and fare estimates are not part of this implementation.
- Previously committed secrets remain in Git history until separately remediated.
- The repository already tracks target artifacts. Generated output is excluded from the MongoDB migration changes; rebuild from source before running.

## Files changed

All paths below are relative to the repository root. Generated target output is excluded from this inventory.

- `RideLink/INTEGRATION.md`
- `RideLink/account-service/src/main/java/com/ridelink/account/config/JwtAuthenticationFilter.java`
- `RideLink/account-service/src/main/java/com/ridelink/account/service/JwtService.java`
- `RideLink/account-service/src/main/resources/application.properties`
- `RideLink/account-service/src/test/java/com/ridelink/account/config/JwtContractTest.java`
- `RideLink/driver-vehicle-service/README.md`
- `RideLink/driver-vehicle-service/pom.xml`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/DriverVehicleServiceApplication.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/config/SecurityConfig.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/controller/DriverController.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/controller/InternalDriverController.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/controller/VehicleController.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/dto/DriverRequests.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/exception/ApiException.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/exception/ErrorResponse.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/exception/GlobalExceptionHandler.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/model/Driver.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/model/DriverStatus.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/model/Vehicle.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/repository/DriverRepository.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/repository/VehicleRepository.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/security/JwtAuthenticationFilter.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/security/UserPrincipal.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/service/DriverService.java`
- `RideLink/driver-vehicle-service/src/main/resources/application.properties`
- `RideLink/driver-vehicle-service/src/test/java/com/ridelink/drivervehicle/DriverIntegrationTest.java`
- `RideLink/fare-payment-service/README.md`
- `RideLink/fare-payment-service/pom.xml`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/FarePaymentServiceApplication.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/client/RideServiceClient.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/config/SecurityConfig.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/controller/FarePaymentController.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/dto/FareRequest.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/dto/PaymentRequest.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/dto/ProcessRequest.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/exception/ApiException.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/exception/ErrorResponse.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/exception/GlobalExceptionHandler.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/model/Fare.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/model/Payment.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/model/Receipt.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/repository/FareRepository.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/repository/PaymentRepository.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/repository/ReceiptRepository.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/security/JwtAuthenticationFilter.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/security/UserPrincipal.java`
- `RideLink/fare-payment-service/src/main/java/com/ridelink/farepayment/service/FarePaymentService.java`
- `RideLink/fare-payment-service/src/main/resources/application.properties`
- `RideLink/fare-payment-service/src/test/java/com/ridelink/farepayment/PaymentIntegrationTest.java`
- `RideLink/fare-payment-service/src/test/java/com/ridelink/farepayment/RideClientTest.java`
- `RideLink/ride-management-service/README.md`
- `RideLink/ride-management-service/src/main/java/com/ridelink/ride/client/DriverServiceClient.java`
- `RideLink/ride-management-service/src/main/java/com/ridelink/ride/config/RecoveryConfig.java`
- `RideLink/ride-management-service/src/main/java/com/ridelink/ride/model/Ride.java`
- `RideLink/ride-management-service/src/main/java/com/ridelink/ride/repository/RideRepository.java`
- `RideLink/ride-management-service/src/main/java/com/ridelink/ride/service/RideService.java`
- `RideLink/ride-management-service/src/main/resources/application.properties`
- `RideLink/ride-management-service/src/test/java/com/ridelink/ride/client/DriverReservationClientTest.java`
- `RideLink/ride-management-service/src/test/java/com/ridelink/ride/service/RideRecoveryTest.java`
- `RideLink/ride-management-service/src/test/java/com/ridelink/ride/service/RideServiceTest.java`

## Persistent Atlas configuration

See [ATLAS_LOCAL.md](ATLAS_LOCAL.md) for one-time Windows user environment setup. Driver prefers DRIVER_MONGODB_URI; Payment prefers PAYMENT_MONGODB_URI. Both retain MONGODB_URI compatibility. All services explicitly select their own ridelink_account, ridelink_driver, ridelink_ride, or ridelink_payment database.
