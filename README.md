# SayWhen

**No forms, no fields. Just say when.**

One phrase — and it's on your calendar. Type it the way you'd say it, in
English, Russian, Italian, Spanish, or German:

> Gym every tuesday and friday at 9
>
> Movie tomorrow at 11 for 1.5h !10
>
> Dentist june 3 at 12:30

SayWhen parses the phrase as you type: date, time, duration, recurrence,
a “!10” reminder. It shows a live preview of what it understood and writes
the event straight to the calendar you chose. No sign-up, no server, no
network access at all: the app doesn't even declare the `INTERNET` permission.

| Input window | Settings | Widget |
|---|---|---|
| ![Input window with a parsed phrase](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Settings](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![Home-screen widget](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |

## Install

Get it on [F-Droid](https://f-droid.org/packages/com.dmi3dmi3.saywhen/),
or grab the APK from [Releases](https://github.com/dmi3dmi3/saywhen/releases)
and open it on the device (Android 8.0+). Since v3.0 both channels ship the
same developer-signed APK, so you can switch between them freely.

### Verify

Release APKs are signed with a key whose certificate SHA-256 fingerprint is:

```
522e011d3835ba12de4f8c1278783a567111fac7d2b5cf32e029cb21f2dc42ea
```

Check with `apksigner verify --print-certs app-release.apk`.

## Permissions & privacy

**Events go to your calendar and nowhere else.**

- `READ_CALENDAR` — list your calendars in settings and pick the target one.
- `WRITE_CALENDAR` — insert the event you asked for.

That's the whole list. No analytics, no crash reporting, no identifiers.
Details in [PRIVACY.md](PRIVACY.md).

## Building

```
./gradlew :app:assembleDebug
```

Kotlin + Jetpack Compose, two modules: `:parser` (pure JVM, the phrase
parser) and `:app` (Android).

## Known limitations

SayWhen writes events through Android's system calendar storage
(`CalendarContract`), so it can only target calendars registered with the
system. **Proton Calendar** doesn't register one: its events live in the
app's own encrypted storage, so it never shows up in the calendar list and
no third-party app can write to it, SayWhen included. Proton offers no
CalDAV either, so bridges like DAVx5 don't help. Workaround:
[Sync Provider for Proton](https://apps.olausson.de/sync_provider_for_proton/),
a paid third-party bridge that exposes Proton calendars to the system.
Install it, then pick its calendar in SayWhen settings.

Also mind sync latency. Events created by SayWhen show up in on-device
calendar apps immediately but reach the cloud (web calendar, other devices)
with your account's regular sync cycle. Events created online take the same
cycle to appear on the device.

## License

[GPL-3.0](LICENSE). Depends on AndroidX / Jetpack Compose (Apache-2.0).

## Mirror notice

This is a **read-only source mirror**: development happens in a private
repository, and each release lands here as a single source drop. Patches are
not accepted and pull requests will be closed. Bugs and ideas are welcome in
[Issues](https://github.com/dmi3dmi3/saywhen/issues).

---

# SayWhen (по-русски)

**Без форм и полей. Просто скажи когда.**

Одна фраза — и событие в календаре. Пишите так, как сказали бы вслух, на
русском, английском, итальянском, испанском или немецком:

> Спорт каждый вторник и пятницу в 9
>
> Кино завтра в 11 на полтора часа !10
>
> Стоматолог 3 июня в 12:30

SayWhen разбирает фразу прямо при вводе: дату, время, длительность,
повторяемость, напоминание «!10». Показывает, что понял, и записывает
событие в выбранный вами календарь. Без регистрации, без сервера и вообще
без доступа в сеть: приложение даже не объявляет разрешение `INTERNET`.

**Установка:** [F-Droid](https://f-droid.org/packages/com.dmi3dmi3.saywhen/)
или APK на странице [Releases](https://github.com/dmi3dmi3/saywhen/releases)
(Android 8.0+). С v3.0 в обоих источниках один и тот же APK с подписью
разработчика, так что переходить между ними можно свободно.

**Приватность:** события идут только в ваш календарь и больше никуда.
Подробности в [PRIVACY.md](PRIVACY.md).

**Зеркало:** разработка идёт в приватном репозитории, сюда попадает срез
исходников на каждый релиз. Патчи не принимаются, PR будут закрыты. Баги и
идеи присылайте в [Issues](https://github.com/dmi3dmi3/saywhen/issues).
