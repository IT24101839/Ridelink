# Driver / Vehicle Service

Runs on port 8082 with MySQL/JPA and string UUID identifiers. Requires JWT_SECRET. Internal APIs require DRIVER_SERVICE_TOKEN.

See [backend integration guide](../INTEGRATION.md) for complete contracts, configuration, security, reservation recovery, tests, and the four-service walkthrough.

Public APIs are under /api/v1/drivers and /api/v1/vehicles. Internal APIs under /api/internal/drivers match Ride's client. New drivers are OFFLINE; add an active vehicle before becoming AVAILABLE. Only internal reservations can set BUSY.
