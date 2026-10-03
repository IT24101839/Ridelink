# Persistent local Atlas configuration

## Findings

The inspected Account and Ride source/generated configuration contains placeholders, not Atlas credentials. No application-local/profile YAML or properties files, repository IDE overrides, PowerShell profile assignments, or persistent Windows user/machine Mongo settings were found. This agent's process environment has no Mongo URI variable. No running Account/Ride Java process was available to inspect.

PowerShell history contains earlier session-scoped `$env:MONGODB_URI` assignments with Atlas URIs. Such assignments remain available for subsequent starts from that terminal, but do not propagate to independent terminals. This supports retained terminal environment as the explanation; the exact live environment of Terminal 1 and Terminal 3 could not be verified. Credentials were not printed or copied from history.

Driver and Payment's previous configuration used the localhost fallback whenever their own process lacked MONGODB_URI. A variable set in Terminal 1 or Terminal 3 is not automatically available in Terminal 2 or Terminal 4.

## Resolution order

- Driver: DRIVER_MONGODB_URI, then MONGODB_URI, then credential-free localhost.
- Payment: PAYMENT_MONGODB_URI, then MONGODB_URI, then credential-free localhost.
- Account and Ride: MONGODB_URI, then credential-free localhost.
- Standard higher-precedence Spring command-line/system property overrides still apply.

Database properties explicitly select ridelink_account, ridelink_driver, ridelink_ride, and ridelink_payment respectively. A shared URI's database path does not merge all services into one database. Atlas users must have access to their designated databases. URI authentication options such as authSource remain relevant; use the Atlas connection string for the correct database user.

## Terminal 2: one-time setup and startup

Paste the Driver Atlas connection URI only into the hidden prompt. Do not paste it into source files or chat. This saves the URI in the Windows user environment and sets it for the current terminal.

```powershell
cd C:\Users\madhu\Ridelink\RideLink\driver-vehicle-service
$atlasInput = Read-Host 'Driver Atlas URI' -AsSecureString
try {
    $env:DRIVER_MONGODB_URI = [System.Net.NetworkCredential]::new('', $atlasInput).Password
    [Environment]::SetEnvironmentVariable('DRIVER_MONGODB_URI', $env:DRIVER_MONGODB_URI, 'User')
} finally {
    $atlasInput.Dispose()
    Remove-Variable atlasInput
}
mvn spring-boot:run
```

## Terminal 4: one-time setup and startup

```powershell
cd C:\Users\madhu\Ridelink\RideLink\fare-payment-service
$atlasInput = Read-Host 'Payment Atlas URI' -AsSecureString
try {
    $env:PAYMENT_MONGODB_URI = [System.Net.NetworkCredential]::new('', $atlasInput).Password
    [Environment]::SetEnvironmentVariable('PAYMENT_MONGODB_URI', $env:PAYMENT_MONGODB_URI, 'User')
} finally {
    $atlasInput.Dispose()
    Remove-Variable atlasInput
}
mvn spring-boot:run
```

These are user environment settings, not an encrypted secret vault. The hidden prompt avoids putting the URI literal in shell command history; Windows stores the environment value persistently outside Git. No credentials have been provisioned automatically by this change.

JWT_SECRET and the existing service tokens must still be configured as before. Their behavior has not changed. Use JDK 21 for integration testing.

## Subsequent starts

Fully restart the IDE/terminal host after the one-time setup so new terminals inherit the updated Windows environment. Then run only `mvn spring-boot:run` from the module directory.

To refresh an already-open terminal without typing the URI again:

```powershell
# Terminal 2
$env:DRIVER_MONGODB_URI = [Environment]::GetEnvironmentVariable('DRIVER_MONGODB_URI', 'User')

# Terminal 4
$env:PAYMENT_MONGODB_URI = [Environment]::GetEnvironmentVariable('PAYMENT_MONGODB_URI', 'User')
```

The existing .gitignore already ignores .env and .env.*. This approach does not create or load application-local files and does not rely on .env auto-loading. Normal application.properties remains tracked.

## Validation limits

Automated tests cover service-specific precedence, shared URI compatibility, localhost fallback, fixed database names, and ports. Atlas authentication/network access requires the user's valid connection URI, database permissions, and Atlas network access configuration; no live Atlas connection was attempted or claimed.

Validation: Driver 20/20 tests passed; Payment 34/34 passed. Account and Ride configuration tests each passed 4/4. No failures/errors/skips. git diff --check passed. No credential-bearing Mongo URI was found in scanned tracked text files. Existing test-only JWT and service-token fixtures remain unchanged.
