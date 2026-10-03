# Account local integration

Account normally uses Spring Boot 4 MongoDB configuration. The localhost URI fallback still needs a real MongoDB server, which explains connection refusal when no server is installed.

The `local-integration` Spring profile now reuses Driver and Payment's MemoryBackend approach, using mongo-java-server 1.47.0 for Account's newer MongoDB driver compatibility. Account starts a private Mongo-compatible server on a random loopback port, with a profile-scoped MongoClient and database factory selecting `ridelink_account`. No Atlas, Docker, installed MongoDB, or MONGODB_URI is required. Inherited external MongoDB URI settings do not select the database in this profile.

Existing UserRepository, registration/login services, BCrypt password hashing, JWT generation/validation, roles, protected endpoints, and Swagger configuration remain unchanged. The profile explicitly creates the existing unique email index. Authentication still requires an externally supplied JWT_SECRET; no secret fallback is added.

## Terminal 1

```powershell
cd C:\Users\madhu\Ridelink\RideLink\account-service
$env:SPRING_PROFILES_ACTIVE="local-integration"
$env:JWT_SECRET="<same JWT secret used by Driver, Ride, and Payment; at least 32 UTF-8 bytes>"
mvn spring-boot:run
```

If JWT_SECRET is already set correctly, omit its assignment. Swagger remains at http://localhost:8081/swagger-ui/index.html. Register PASSENGER and DRIVER users through POST /api/auth/register, then login through POST /api/auth/login and use the returned JWT for /api/users/me and the other services.

## Profile boundary and lifetime

Without local-integration, Account retains its normal MongoDB behavior, MONGODB_URI support, and current database selection. The library is packaged but inactive. This profile is for development only.

Data survives while Account runs and is lost on shutdown. Recreate test accounts after restarting Account. Their new IDs will differ; recreate related local driver profiles and use fresh test rides so Atlas-backed Ride does not reference old Account IDs. The local server closes with the Spring context.

Driver and Payment keep their existing local-integration profiles. Ride continues to use the real Atlas database. No other service is modified by this Account feature.

## Tests

AccountLocalIntegrationTest exercises the full Account application with real local Mongo repositories: PASSENGER/DRIVER registration, password hashing, login, JWT claims, authenticated profile access, duplicate email and unique index enforcement, role restrictions, input validation, and Swagger. LocalIntegrationProfileTest checks default-profile isolation, startup without a URI, loopback binding, shutdown, and disposable data.

Existing Account tests are retained. The Mongo-compatible server supports the tested operations but does not replace production MongoDB validation. The live four-service workflow against Ride Atlas remains a separate manual test.

Validation on 2026-10-03: full Account suite passed on JDK 21: 62 tests, 0 failures, 0 errors, 0 skips (8 new cases, all 54 existing cases retained). git diff --check passed. The service retains its Java 17 compiler target.
