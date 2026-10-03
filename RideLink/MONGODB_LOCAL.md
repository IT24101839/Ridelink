# MongoDB standardization and local integration

## Existing database configuration

Audit on 2026-10-03, before this migration:

| Service | Persistence / repository | Database | MONGODB_URI / fallback |
| --- | --- | --- | --- |
| Account | MongoDB starter / MongoRepository, String IDs | ridelink_account_db, explicitly configured separately from URI | Supported; no fallback |
| Driver | JPA starter + MySQL connector / JpaRepository, String UUID IDs | ridelink_driver_vehicle_db | Not supported; local MySQL fallback via DRIVER_DB_URL |
| Ride | MongoDB starter / MongoRepository, String IDs | ridelink_ride_management_db | Supported; credential-free localhost fallback |
| Payment | MongoDB starter / MongoRepository, String IDs | ridelink_fare_payment_db | Supported; credential-free localhost fallback |

Each active config is `<module>/src/main/resources/application.properties`. No hardcoded remote credentials were found in these configurations. Account uses Spring Boot 4.1.1; other services use Boot 3.3.3. Account, Driver, Payment target Java 17; Ride targets Java 21. Compilation targets remain unchanged; validation runs on JDK 21.

## MongoDB standardization changes

All services now support a credential-free local default with MONGODB_URI override. Account uses Boot 4's `spring.mongodb.uri`; the others use `spring.data.mongodb.uri`. All four services explicitly select their service database, even when a supplied URI names another database. Driver and Payment also accept DRIVER_MONGODB_URI and PAYMENT_MONGODB_URI ahead of MONGODB_URI.

| Service | Default port | Default MongoDB URI |
| --- | --- | --- |
| Account | 8081 | mongodb://localhost:27017/ridelink_account |
| Driver | 8082 | mongodb://localhost:27017/ridelink_driver |
| Ride | 8083 | mongodb://localhost:27017/ridelink_ride |
| Payment | 8084 | mongodb://localhost:27017/ridelink_payment |

Every port supports `${PORT:<default>}`. MONGODB_URI must be configured separately per service when overriding it. Do not point all four services to the same database.

## Driver MySQL to MongoDB migration

Removed JPA, MySQL connector, H2 test dependency, datasource settings, and Hibernate configuration. Added the MongoDB starter and converted Driver/Vehicle to `drivers`/`vehicles` documents and MongoRepository interfaces. String UUID IDs remain independent of Account user IDs.

Unique indexes preserve Driver.accountId, Driver.licenseNumber, and Vehicle.plateNumber constraints. Additional indexes cover Driver.status and Vehicle.driverId. Automatic index creation is enabled. Registration uses insert and maps duplicate key failures to 409. No public API, JWT, ownership, service-token, or Ride/Payment HTTP contract changes were made.

No existing database data is copied automatically. Existing MySQL data requires a separate migration preserving Driver/Vehicle IDs and reservations before switching an existing deployment. Changed default database names also select new databases; migrate existing data into the designated service databases before using this configuration.

## Atomic reservations

Reservation uses MongoTemplate.findAndModify with predicates for driver ID, AVAILABLE status, and absent reservation. One update sets BUSY and the ride ID. Concurrent contenders cannot both win. A same-ride retry returns the current reservation; another ride receives 409. Unknown drivers receive 404.

Release conditionally matches driver ID, ride ID, and BUSY or OFFLINE status. BUSY becomes AVAILABLE; OFFLINE stays OFFLINE. A concurrent explicit OFFLINE update cannot be overwritten by a stale release. Already-free release is idempotent; wrong-ride release is rejected. No Mongo multi-document transaction or replica set is required for these single-document operations.

Availability changes to AVAILABLE also require an absent reservation in the database update predicate. Location and service-area updates only update those fields. Vehicle eligibility is checked separately; there is currently no vehicle deactivation/deletion API that can race that check.

## Port and service URL matrix

| Caller | Provider | Variable | Default |
| --- | --- | --- | --- |
| Ride | Driver | DRIVER_SERVICE_URL | http://localhost:8082 |
| Ride | Payment | FARE_SERVICE_URL | http://localhost:8084 |
| Payment | Ride | RIDE_SERVICE_URL | http://localhost:8083 |

## Environment variables

- All services: JWT_SECRET, the same signing secret, at least 32 UTF-8 bytes. No default is supplied.
- Driver and Ride: matching DRIVER_SERVICE_TOKEN.
- Ride and Payment: matching FARE_SERVICE_TOKEN and RIDE_INTERNAL_TOKEN.
- Optional per process: MONGODB_URI and PORT. Driver prefers DRIVER_MONGODB_URI; Payment prefers PAYMENT_MONGODB_URI. Database names are fixed per service.
- Optional service URLs: those listed above.
- Existing JWT_EXPIRATION_MS and HTTP timeout settings remain unchanged.

Missing service tokens retain the existing empty/disabled behavior: internal requests are denied. No usable secret defaults were added. The only new defaults are credential-free localhost database URIs.

## Tests

All existing test methods are retained. Driver's H2 fixture was replaced with a test-scope Mongo-compatible in-memory server on a random port. It exercises actual MongoRepository/MongoTemplate calls, document persistence, unique indexes, public/internal APIs, ownership, idempotent reservation/release, OFFLINE preservation, and competing reservations. Added explicit document/index/availability persistence checks.

Each service now has MongoConfigurationTest covering its own default URI, MONGODB_URI override, and default/overridden port. These resolve the main configuration without relying on the developer's environment. The test server is not included in production runtime and does not replace the need to validate on real MongoDB.

## How to run all four services locally

1. Install/start MongoDB listening on localhost:27017. Use development data.
2. Open four PowerShell terminals. Use JDK 21 and Maven in each terminal. Set the same JWT_SECRET in each; set matching service tokens in the relevant terminals. Supply your own locally generated values through environment variables, not tracked files.
3. In each terminal, remove any inherited service-specific URI and shared URI if testing defaults: `Remove-Item Env:MONGODB_URI -ErrorAction SilentlyContinue`. Also remove an inherited PORT if using default ports.
4. Change directory to the relevant module and run `mvn spring-boot:run`:

```powershell
# Run each pair in its own terminal, after setting the required secrets.
cd C:\Users\madhu\Ridelink\RideLink\account-service
mvn spring-boot:run

cd C:\Users\madhu\Ridelink\RideLink\driver-vehicle-service
mvn spring-boot:run

cd C:\Users\madhu\Ridelink\RideLink\ride-management-service
mvn spring-boot:run

cd C:\Users\madhu\Ridelink\RideLink\fare-payment-service
mvn spring-boot:run
```

To test an override, set `$env:MONGODB_URI="mongodb://localhost:27017/custom_driver_test"` in the Driver terminal before starting it. The Driver database remains ridelink_driver even if the URI names custom_driver_test. For a remote deployment, supply its URI privately in that process environment. See [persistent Atlas setup](ATLAS_LOCAL.md).

Follow the [four-service lifecycle walkthrough](INTEGRATION.md#manual-four-service-test-plan) to register accounts, create a driver/vehicle, complete a ride, process payment, and retrieve a receipt.

## Important local MongoDB requirement

The localhost fallback is configuration, not an embedded database. MongoDB must be running at localhost:27017. JWT_SECRET and integration tokens are still needed for the secured workflow. No production embedded MongoDB or real credentials were introduced.

Previously tracked target files are excluded from this change. Always rebuild from source before running; historical generated files are not authoritative.

## Validation results (2026-10-03)

Full reactor `mvn package` passed on JDK 21, preserving existing compiler targets.

| Service | Total | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Account | 53 | 53 | 0 | 0 | 0 |
| Driver | 17 | 17 | 0 | 0 | 0 |
| Ride | 135 | 135 | 0 | 0 | 0 |
| Payment | 31 | 31 | 0 | 0 | 0 |
| Total | 236 | 236 | 0 | 0 | 0 |

Added 15 test cases; retained the previous 221. The Driver concurrency test now exercises Mongo conditional updates instead of JPA locks. `git diff --check` passed. No credential-bearing Mongo URIs or literal JWT/service-token settings were found in tracked production configuration. Test credentials remain test-only.

The packaged Driver application excludes MySQL, Hibernate ORM, H2, and the test-only Mongo server. Hibernate Validator remains for request validation; it is not JPA/Hibernate ORM.

Docker's engine and a local MongoDB listener were unavailable. Real MongoDB and the four-running-service walkthrough are not validated by this run. Database index permissions, existing-data migration, and deployment behavior must be checked against the target MongoDB server.

## Files changed

Paths below are relative to the repository root. Generated target changes have been excluded.

- `RideLink/INTEGRATION.md`
- `RideLink/MONGODB_LOCAL.md`
- `RideLink/account-service/src/main/resources/application.properties`
- `RideLink/account-service/src/test/java/com/ridelink/account/MongoConfigurationTest.java`
- `RideLink/driver-vehicle-service/README.md`
- `RideLink/driver-vehicle-service/pom.xml`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/model/Driver.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/model/Vehicle.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/repository/DriverRepository.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/repository/VehicleRepository.java`
- `RideLink/driver-vehicle-service/src/main/java/com/ridelink/drivervehicle/service/DriverService.java`
- `RideLink/driver-vehicle-service/src/main/resources/application.properties`
- `RideLink/driver-vehicle-service/src/test/java/com/ridelink/drivervehicle/DriverIntegrationTest.java`
- `RideLink/driver-vehicle-service/src/test/java/com/ridelink/drivervehicle/MongoConfigurationTest.java`
- `RideLink/fare-payment-service/src/main/resources/application.properties`
- `RideLink/fare-payment-service/src/test/java/com/ridelink/farepayment/MongoConfigurationTest.java`
- `RideLink/ride-management-service/src/main/resources/application.properties`
- `RideLink/ride-management-service/src/test/java/com/ridelink/ride/MongoConfigurationTest.java`
