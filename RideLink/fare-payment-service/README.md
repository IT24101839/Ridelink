# Fare / Payment Service

Runs on port 8084 with MongoDB and string IDs. Configure JWT_SECRET, MONGODB_URI, FARE_SERVICE_TOKEN, RIDE_INTERNAL_TOKEN, and optionally RIDE_SERVICE_URL (default http://localhost:8083).

See [backend integration guide](../INTEGRATION.md) for authoritative request/response contracts, idempotency rules, security, tests, and manual validation.

Final fare calculation is service-authenticated. Payment creation accepts rideId and paymentMethod only, verifies ownership through Ride, and prevents duplicate payments. Processing is a simulation. Receipts require COMPLETED status.

No literal database credentials or JWT secret defaults are included.
