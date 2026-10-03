# Ride Management Service

Runs on port 8083 with MongoDB and Java 21. Public ride APIs remain under /api/rides; the health endpoint remains /api/ride/health.

See [backend integration guide](../INTEGRATION.md) for current architecture, contracts, configuration, security, validation, tests, recovery behavior, and the four-service walkthrough.

Lifecycle: REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED, with cancellation from nonterminal states unless an integration intent is pending.

Driver identity is JWT userId -> Driver.accountId -> Driver.id -> Ride.driverId. Assignment reserves Driver through HTTP. Completion freezes fare inputs before calling Payment on port 8084. Durable intents and a scheduled worker recover interrupted assignment, completion, and release operations.

Completion/cancellation can return driverReleasePending=true when Driver is unavailable. The worker retries safely. User JWT and internal service tokens remain separate.
