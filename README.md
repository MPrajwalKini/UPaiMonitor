# UPai Monitor

UPai Monitor is an Android app that scans incoming SMS messages for payment/UPI transaction notifications and stores parsed transactions locally using Room. It is implemented in Kotlin with Jetpack Compose for the UI, Room for persistence, and a small SMS broadcast receiver to capture messages.

> Note: This repository is based on the original concept and implementation demonstrated by [Adithya Pai B](https://github.com/adithyapaib).  
> This version builds upon his work with additional development, restructuring, and enhancements for learning and experimentation.

## Features

- Listen for incoming SMS messages (RECEIVE_SMS / READ_SMS)
- Parse messages for currency amounts (basic regex)
- Store parsed transactions in a local Room database
- Simple Jetpack Compose UI (theme + placeholders)
- Debug helper to simulate SMS for development
- Transaction repository and ViewModel scaffolding

## Requirements

- Android Studio (recommended)
- JDK 11+
- Gradle (wrapper included)
- Minimum SDK: 24
- Target / Compile SDK: 36

## Quickstart

1. Clone the repository:
   ```bash
   git clone [<repo-url>](https://github.com/MPrajwalKini/UPaiMonitor.git)
   ```
2. Open the project in Android Studio.
3. Let Android Studio sync and download dependencies (Gradle sync).
4. Run the app on a device or emulator that supports SMS features (an emulator can simulate SMS).

Build via command line:
```bash
./gradlew assembleDebug
```

## Permissions

The app declares the following runtime permissions in the manifest:

- `android.permission.RECEIVE_SMS` — receive SMS messages  
- `android.permission.READ_SMS` — read SMS contents  
- `android.permission.INTERNET` — (if needed for future syncing)  
- `android.permission.ACCESS_NETWORK_STATE` — (if needed for network checks)

Make sure to request the runtime permissions on devices running Android 6.0+.

## How it works (high level)

- `SmsReceiver` (registered in AndroidManifest) receives SMS broadcasts.  
- `SmsScanner` / `SmsMonitorManager` parse incoming message content using regex to find amounts and metadata.  
- Parsed transactions are wrapped into `Transaction` objects and stored via `TransactionRepository` which uses Room (`AppDatabase`).  
- UI components (Compose) and view models can observe and display stored transactions.

## Important files & structure

- **app/src/main/java/**
  - `AppDatabase.kt` — Room database setup  
  - `TransactionDao.kt` — DAO definitions  
  - `TransactionEntity.kt` — Room entity / conversion helpers  
  - `TransactionRepository.kt` — repository for DB operations  
  - `TransactionViewModel.kt` — ViewModel scaffolding for UI  
  - `SmsReceiver.kt` — BroadcastReceiver for SMS  
  - `SmsMonitorManager.kt` / `SmsScanner.kt` — monitoring/parsing logic  
  - `DebugHelper.kt` — simulate SMS and print debug info  
  - `MyApp.kt` — Application class, initializes DB & manager  
  - `MainActivity.kt` / Compose screens — UI entrypoints  

- **app/src/main/AndroidManifest.xml** — permissions, receiver, application config  
- **app/build.gradle.kts** — Android and dependency configuration  
- **gradle/** — wrapper and version catalog (`libs.versions.toml`)

Database file name: `upai_transactions.db` (created by `AppDatabase`)

Room configuration: `fallbackToDestructiveMigration()` (development only — this wipes DB schema changes)

## Debugging / Simulation

- Use `DebugHelper.simulateSMS(sender, body)` to inject a simulated SMS and create a transaction without needing actual SMS delivery.  
- Check logs (Logcat) for tags: `DebugHelper`, `MyApp`, etc.  
- `DebugHelper.parse` uses regex: `(?i)(?:Rs\.?|INR)\s*([0-9,.]+)` to extract amounts (basic currency detection).

## Running on device/emulator

- **Real device:** Install and grant SMS permissions. The app listens for SMS broadcasts and will store parsed transactions.  
- **Emulator:** Use Android Studio's Telephony tools or `adb` to send an SMS. Alternatively, simulate via `DebugHelper`.

Example emulator SMS:
```bash
adb -s <emulator-id> emu sms send <sender> "<message body containing Rs. or INR and an amount>"
```

## Notes & Caveats

- **Privacy & Security:** The app reads SMS content. Treat this data sensitively. For production, consider data storage, encryption, transport, and user consent.  
- **Parsing:** The amount parsing is simplistic. For production, expand parsing rules to handle more formats and currencies and to deduplicate transactions robustly.  
- **Database migration:** Current setup uses destructive migrations (`fallbackToDestructiveMigration`). Replace with proper migrations before releasing.

## Tests

- Example unit and instrumentation tests:
  - `app/src/test/java/.../ExampleUnitTest.kt`
  - `app/src/androidTest/java/.../ExampleInstrumentedTest.kt`

## Contributing

- Feel free to open issues or PRs with improvements.  
- When adding new DB fields/entities, add proper Room migrations.  
- Avoid storing sensitive data without user consent.

## License & Credits

This repository is **inspired by and adapted from** the work of [Adithya Pai B](https://github.com/adithyapaib), who originally developed and demonstrated the app concept.  
Additional changes, documentation, and restructuring have been added for educational and experimental use.

---

