# Сборка

## Требования

- Android Studio с поддержкой Android 16 / API 36.
- JDK 17.
- Android SDK Platform 36 и соответствующие Build Tools.
- Android Gradle Plugin 8.13.2; Gradle 8.13.

## Сборка в Android Studio

1. Откройте папку `DiceWarsAndroid`.
2. Установите Android SDK Platform 36 через SDK Manager.
3. Выполните Gradle Sync.
4. Выберите конфигурацию `app` и выполните `assembleDebug`.

Результат debug-сборки при успешной компиляции находится в `app/build/outputs/apk/debug/app-debug.apk`.

## Командная строка

В проекте есть `gradlew.bat` и `gradlew`: они скачивают официальный дистрибутив Gradle 8.13 в пользовательский кэш и проверяют его SHA-256. Это bootstrap-launcher без стандартного `gradle-wrapper.jar`; для сборки всё равно нужны JDK 17 и Android SDK Platform 36.

```powershell
.\gradlew.bat assembleDebug
```

В исходной рабочей среде не было `java`, `gradle`, `adb` или Android SDK. Политику среды также заблокировала загрузку исполняемых инструментов в рабочую папку, поэтому здесь APK получить нельзя. На пользовательской машине Gradle bootstrap скачает дистрибутив; Android SDK Platform 36 нужно предварительно установить.

