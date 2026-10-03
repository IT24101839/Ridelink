# Driver / Vehicle Service

Runs on port 8082 with MongoDB and string UUID identifiers. Requires JWT_SECRET. Internal APIs require DRIVER_SERVICE_TOKEN.

See [backend integration guide](../INTEGRATION.md) for complete contracts, configuration, security, reservation recovery, tests, and the four-service walkthrough.

Public APIs are under /api/v1/drivers and /api/v1/vehicles. Internal APIs under /api/internal/drivers match Ride's client. New drivers are OFFLINE; add an active vehicle before becoming AVAILABLE. Only internal reservations can set BUSY.

Without MONGODB_URI, uses mongodb://localhost:27017/ridelink_driver. A local MongoDB server must be running. MongoDB indexes enforce profile/license/plate uniqueness. Reservation and release use atomic conditional document updates.

See [MongoDB migration and local startup guide](../MONGODB_LOCAL.md).

For Ride integration without Atlas or an installed MongoDB server, set SPRING_PROFILES_ACTIVE=local-integration. See [local integration setup](../LOCAL_INTEGRATION.md). Only persistence changes; JWT and service tokens remain required.
