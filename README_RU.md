<p align="center">
  <img src="ic_launcher.webp" width="128" height="128" alt="Иконка XDAetherium">
</p>

<p align="center">
  <a href="README.md">English</a> | <a href="README_RU.md">Русский</a>
</p>

# XDAetherium Android

**XDAetherium** — Android-лаунчер для запуска **Minecraft: Java Edition** на телефонах и планшетах. Проект основан на Amethyst/PojavLauncher/Boardwalk и содержит кастомные изменения под лёгкую сборку, Ely.by-аккаунты и удобный запуск Java Edition на Android.

> Проект не связан с Mojang, Microsoft или Ely.by. Для игры используйте собственный аккаунт и соблюдайте правила соответствующих сервисов.

## Возможности

- Запуск Minecraft: Java Edition на Android.
- Поддержка Microsoft-аккаунтов из базового лаунчера.
- Поддержка офлайн/локального аккаунта.
- Поддержка **Ely.by Account**:
  - вход по логину/e-mail и паролю;
  - поддержка 2FA-кода;
  - обновление токена;
  - регистрация через `account.ely.by`;
  - скины Ely.by в игре через `authlib-injector`;
  - голова Ely.by-скина в главном меню.
- Поддержка Forge/Fabric/OptiFine и обычных vanilla-версий Minecraft.
- Автоподбор и установка нужной Java runtime перед запуском.
- Исправления для legacy Java 8/Java 17+/Java 21 runtime.
- Исправленный fallback манифеста для OptiFine.
- Уменьшенный APK: оставлены только `arm64-v8a` и `armeabi-v7a`, без x86/x86_64.
- Кастомное название, иконки и дефолтная иконка профиля XDAetherium.
- Настройка принудительного русского языка.
- Действия «Отменить» и «Выйти» в пользовательском управлении.
- Отмена загрузок долгим нажатием на строку/прогресс загрузки.
- Убраны лишние ссылки upstream-проекта на wiki/Discord автора.

## Статус сборки

Текущая локальная debug-сборка проверялась на устройстве Android 12:

- package debug: `org.angelauramc.amethyst.debug`
- versionName: `LOCAL-20260510`
- APK debug: около `82 MB`
- ABI: `arm64-v8a`, `armeabi-v7a`
- x86/x86_64 в APK отсутствуют

## Сборка из исходников

### Требования

- JDK, совместимый с Android Gradle Plugin проекта.
- Android SDK.
- Android NDK `27.3.13750724` или совместимый установленный NDK.
- Интернет для загрузки Gradle-зависимостей и игровых файлов.

### Windows

```bat
gradlew.bat :app_pojavlauncher:assembleDebug --console=plain
```

### Linux/macOS

```bash
./gradlew :app_pojavlauncher:assembleDebug --console=plain
```

Готовый debug APK появится здесь:

```text
app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

Release-сборка:

```bash
./gradlew :app_pojavlauncher:assembleRelease --console=plain
```

На Windows используйте `gradlew.bat` вместо `./gradlew`.

## Установка debug APK

```bash
adb install -r app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

Для конкретного устройства:

```bash
adb -s <device_id> install -r app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

## Ely.by

В XDAetherium добавлен отдельный способ авторизации **Ely.by Account**.

Как это работает:

1. Лаунчер авторизуется через Ely.by Yggdrasil API.
2. Аккаунт сохраняется как обычный Minecraft-профиль лаунчера.
3. При запуске игры для Ely.by-аккаунта добавляется `authlib-injector` как Java-agent.
4. Minecraft получает session/profile/textures через Ely.by, поэтому скин виден в игре.
5. Для главного меню лаунчер отдельно скачивает PNG скина Ely.by, вырезает лицо и overlay-шлем, затем кеширует голову профиля.

Если скин в меню не обновился сразу, перезапустите лаунчер или заново выберите Ely.by-аккаунт.

## Оптимизация APK

Сборка настроена под ARM-устройства:

```gradle
abiFilters "arm64-v8a", "armeabi-v7a"
```

Это уменьшает APK и убирает ненужные x86/x86_64 библиотеки. Если нужна поддержка x86/x86_64, ABI-фильтры и assets LWJGL нужно возвращать отдельно.

## Перед публикацией на GitHub

Перед тем как делать публичный репозиторий:

- Не публикуйте signing credentials и пароли keystore. Вынесите их из Gradle-конфига в переменные окружения, `local.properties` или другой локальный файл, который находится в `.gitignore`.
- Не коммитьте `local.properties`, build-логи, временные APK и личные файлы.
- Оставьте исходный `LICENSE` и этот README.
- Укажите, что проект является fork/модификацией Amethyst/PojavLauncher/Boardwalk.
- Если выкладываете APK-релиз, выкладывайте соответствующий исходный код этой же версии.

## Лицензия

Основной проект распространяется по лицензии **GNU LGPLv3** — см. файл [LICENSE](LICENSE).

В проекте также используются компоненты с другими свободными лицензиями. В частности, добавленный `authlib-injector` распространяется по **GNU AGPLv3** и включён как отдельный Java-agent-компонент.

## Credits

Проект основан на работе следующих проектов и библиотек:

- [Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
- [Boardwalk](https://github.com/zhuowei/Boardwalk)
- [authlib-injector](https://github.com/yushijinhun/authlib-injector)
- [Ely.by](https://ely.by/) API и skin system
- LWJGL, OpenJDK, SDL, GL4ES, MobileGlues, Mesa и другие зависимости, используемые upstream-проектом

Спасибо всем авторам upstream-проектов и библиотек.
