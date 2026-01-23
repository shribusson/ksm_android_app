# Copilot Instructions for KSM Android App

## Project Overview

This is a Bitrix24 task management application designed for kiosk mode on Android tablets (specifically Lenovo TB310XU). The app allows warehouse workers to view and manage their tasks from Bitrix24 in a locked-down kiosk environment.

**Key Features:**
- Task list with pagination and filtering
- Task timer functionality
- Multi-user support with webhook-based authentication
- Kiosk mode with device admin capabilities
- Offline support with local caching
- Automatic crash recovery and logging

## Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material3
- **Architecture:** Clean Architecture with MVVM pattern
- **Dependency Injection:** Hilt/Dagger
- **Networking:** Retrofit + OkHttp with Kotlinx Serialization
- **Database:** Room (SQLite)
- **Async:** Kotlin Coroutines + Flow
- **Background Work:** WorkManager
- **Security:** EncryptedSharedPreferences (androidx.security)
- **Logging:** Timber + Custom File Logging

**Build System:**
- Gradle with Kotlin DSL (`.gradle.kts`)
- Android Gradle Plugin 8.2.2
- Kotlin 1.9.22
- Min SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Compile SDK: 34

## Project Structure

```
app/src/main/java/com/example/bitrix_app/
├── BitrixApp.kt                    # Application class with Hilt setup
├── MainActivity.kt                  # Main entry point with Compose UI
├── TimerService.kt                  # Foreground service for task timer
├── EncryptedPreferences.kt          # Secure storage wrapper
├── FileLoggingTree.kt               # Custom logging implementation
├── data/
│   ├── local/                       # Room database entities & DAOs
│   │   ├── mapper/                  # Entity to domain mappers
│   ├── remote/                      # Network API definitions
│   │   ├── dto/                     # Data transfer objects
│   └── repository/                  # Repository implementations
├── domain/
│   ├── model/                       # Domain models
│   ├── repository/                  # Repository interfaces
│   └── usecase/                     # Business logic use cases
├── di/                              # Hilt dependency injection modules
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   └── RepositoryModule.kt
├── ui/
│   ├── component/                   # Reusable Compose components
│   ├── screen/                      # Screen composables
│   ├── viewmodel/                   # ViewModels for screens
│   ├── theme/                       # Material3 theme definitions
│   └── util/                        # UI utilities
├── util/                            # General utilities
└── worker/                          # WorkManager workers for background tasks
```

## Build and Test Commands

### Building the app
```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Running tests
```bash
# Unit tests
./gradlew test

# Unit tests with coverage
./gradlew testDebugUnitTest

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests com.example.bitrix_app.data.repository.TaskRepositoryImplTest
```

### Code quality
```bash
# Lint check
./gradlew lint

# Check for dependencies updates
./gradlew dependencyUpdates
```

### Installation
```bash
# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep Bitrix
adb logcat -s BitrixApp
```

## Architecture Patterns

### Clean Architecture Layers

1. **Presentation Layer** (`ui/`):
   - Compose UI screens
   - ViewModels with StateFlow/LiveData
   - UI state classes

2. **Domain Layer** (`domain/`):
   - Business logic in Use Cases
   - Domain models (pure Kotlin classes)
   - Repository interfaces

3. **Data Layer** (`data/`):
   - Repository implementations
   - Local data sources (Room)
   - Remote data sources (Retrofit)
   - DTOs and entity mappers

### Key Patterns

- **MVVM:** ViewModels expose UI state via StateFlow/LiveData
- **Repository Pattern:** Abstract data sources behind interfaces
- **Dependency Injection:** Hilt manages all dependencies
- **Mapper Pattern:** Separate DTOs from domain models
- **Single Source of Truth:** Room database is the source of truth for tasks

## Coding Conventions

### General Guidelines

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names (avoid single letters except loop counters)
- Prefer immutable data structures (`val` over `var`, `data class` with `copy()`)
- Use Kotlin's null safety features (avoid `!!` operator)
- Prefer extension functions over utility classes
- Use sealed classes for representing finite states

### Compose UI Guidelines

- **Remember state properly:** Use `remember`, `rememberSaveable` appropriately
- **Avoid recomposition issues:** Be mindful of key parameters in lists
- **Composable naming:** PascalCase for `@Composable` functions
- **State hoisting:** Hoist state to appropriate level, pass callbacks down
- **Preview functions:** Add `@Preview` annotations for visual testing

### Coroutines & Flow

- Use `viewModelScope` in ViewModels for lifecycle-aware coroutines
- Use `Flow` for streams of data
- Handle errors with `try-catch` or `catch` operator
- Use `Dispatchers.IO` for network/database operations
- Use `Dispatchers.Main` for UI updates (automatic in `viewModelScope`)

### Hilt/Dependency Injection

- Use constructor injection whenever possible
- Annotate ViewModels with `@HiltViewModel`
- Provide dependencies in appropriate modules (`AppModule`, `NetworkModule`, etc.)
- Use `@Singleton` for app-wide single instances
- Use qualifiers for multiple instances of the same type

### Error Handling & Logging

- Use Timber for logging: `Timber.d()`, `Timber.e()`, etc.
- Never use `println()` or `Log.*()` directly
- Log errors with stack traces: `Timber.e(exception, "Message")`
- Production logs are saved to files in app's internal storage
- Include context in error messages

### Testing

- Write unit tests for repositories, use cases, and ViewModels
- Use `@Before` and `@After` for test setup/cleanup
- Mock dependencies using test doubles
- Test both success and error cases
- Keep tests focused and independent

## Important Files and Locations

### Configuration Files

- `build.gradle.kts` (root) - Project-level Gradle configuration
- `app/build.gradle.kts` - App-level dependencies and build config
- `settings.gradle.kts` - Project settings
- `gradle.properties` - Gradle properties and flags
- `proguard-rules.pro` - ProGuard rules for release builds

### Key Documentation

- `CURRENT_STATUS.md` - Current state of the app, recent fixes, and issues (in Russian)
- `NEXT_STEPS.md` - Development roadmap and next tasks
- `KIOSK_SETUP.md` - Instructions for setting up kiosk mode on tablets
- `KIOSK_README.md` - Kiosk mode documentation

### Data Storage

- EncryptedSharedPreferences: User webhooks and sensitive data
- Room Database: Cached tasks and sync queue
- File logs: `/data/data/com.example.bitrix_app/files/logs/`
- Crash reports: `/data/data/com.example.bitrix_app/files/last_crash.txt`

### Entry Points

- `BitrixApp.kt` - Application initialization, Hilt setup, logging initialization
- `MainActivity.kt` - Main activity, kiosk mode setup, crash recovery
- `TimerService.kt` - Foreground service for task timers

## Development Workflow

### Making Changes

1. **Understand the context**: Read related code and documentation first
2. **Follow Clean Architecture**: Put code in the appropriate layer
3. **Write tests**: Add unit tests for new logic
4. **Test locally**: Build and test on a device/emulator
5. **Check logs**: Use `adb logcat` to verify behavior
6. **Update documentation**: Update relevant `.md` files if needed

### Adding Dependencies

1. Add to `app/build.gradle.kts` in the appropriate section
2. Use consistent versions (check existing dependencies)
3. Sync Gradle after adding
4. Update ProGuard rules if needed

### Working with Kiosk Mode

- Device admin functionality is critical for kiosk mode
- Lock task mode prevents users from exiting the app
- Crash recovery ensures app restarts automatically
- Test on actual hardware (Lenovo TB310XU) when possible

### Common Tasks

**Adding a new screen:**
1. Create composable in `ui/screen/`
2. Create ViewModel in `ui/viewmodel/`
3. Define UI state in ViewModel
4. Wire up in navigation

**Adding a new API endpoint:**
1. Define DTO in `data/remote/dto/`
2. Add endpoint to API service interface
3. Update repository interface in `domain/repository/`
4. Implement in repository class in `data/repository/`
5. Create/update use case in `domain/usecase/`

**Adding a new database entity:**
1. Create entity in `data/local/`
2. Add DAO interface
3. Create mapper in `data/local/mapper/`
4. Update database version in `BitrixDatabase.kt`
5. Provide migration if needed

## Special Considerations

### Bitrix24 Integration

- Uses webhook URLs for authentication (no OAuth)
- Each user has their own webhook URL
- API responses use custom JSON structure
- Tasks are fetched with pagination
- API base URL pattern: `{webhook_base}/tasks.task.list.json`

### Kiosk Mode Requirements

- App must run in Lock Task Mode
- Device Admin permissions required
- Home button and recent apps must be disabled
- Automatic restart on crash
- No access to system settings

### Performance Considerations

- LazyColumn items must have unique keys (use `"$index-${task.id}"` pattern)
- Avoid duplicate task IDs in pagination
- File logging is thread-safe with size limits (1MB per file, max 5 files)
- Background sync uses WorkManager for reliability

### Security

- Sensitive data stored with EncryptedSharedPreferences
- No hardcoded credentials or API keys
- Release builds use signing config from `release.keystore`
- ProGuard enabled for release (currently disabled for safety)

## Known Issues and Workarounds

- **LazyColumn duplicate keys:** Fixed by using index-based composite keys
- **Task pagination duplicates:** Deduplication logic in `TaskListViewModel`
- **User 240 not found:** Check SharedPreferences for legacy data
- **Device admin restrictions:** Some features require device owner mode

## Useful ADB Commands

```bash
# View app logs
adb logcat | grep Bitrix

# View app-specific logs
adb logcat -s BitrixApp

# Check app status
adb shell ps | grep bitrix

# View SharedPreferences (requires debuggable app)
adb shell "run-as com.example.bitrix_app cat /data/data/com.example.bitrix_app/shared_prefs/BitrixAppPrefs.xml"

# Pull log files
adb pull /data/data/com.example.bitrix_app/files/logs/

# Pull crash report
adb pull /data/data/com.example.bitrix_app/files/last_crash.txt

# Clear app data
adb shell pm clear com.example.bitrix_app

# Uninstall app
adb uninstall com.example.bitrix_app
```

## Language Note

Most documentation and comments in the codebase are in Russian, as this is for a Russian-speaking development team. However, code (variable names, function names, classes) follows English naming conventions.
