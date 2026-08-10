# Privacy

**Events go to your calendar — nowhere else.**

SayWhen has no network access of its own: the app does not declare the
`INTERNET` permission, so the OS forbids it any network I/O. Everything below
is verifiable in this repository's source code.

## Permissions

| Permission | Why |
|---|---|
| `READ_CALENDAR` | List your calendars in settings and pick the target one. |
| `WRITE_CALENDAR` | Insert the event you asked for into that calendar. |

That is the complete list. No location, no contacts, no microphone, no network.

## What leaves the device

Nothing leaves the device through SayWhen. The event you create is written to
the calendar **you** selected via Android's standard calendar storage
(`CalendarContract`). If that calendar belongs to a synced account (e.g.
Google), the account's own sync engine uploads the event — exactly as it would
for an event created in any other calendar app. SayWhen does not talk to any
server.

## What we collect

Nothing. No analytics, no crash reporting, no advertising or device
identifiers, no telemetry of any kind. The phrases you type are parsed locally
and are not stored by the app after the event is created.

## Links

The few links in the app (source code, issues, license, this document) are
opened by your system browser, outside the app.

---

# Приватность

**События идут только в ваш календарь — больше никуда.**

У SayWhen нет собственного доступа в сеть: приложение не объявляет разрешение
`INTERNET`, поэтому ОС запрещает ему любой сетевой ввод-вывод. Всё написанное
ниже проверяется по исходникам в этом репозитории.

## Разрешения

| Разрешение | Зачем |
|---|---|
| `READ_CALENDAR` | Показать список ваших календарей в настройках и выбрать целевой. |
| `WRITE_CALENDAR` | Записать созданное вами событие в этот календарь. |

Это полный список. Ни геолокации, ни контактов, ни микрофона, ни сети.

## Что покидает устройство

Через SayWhen — ничего. Созданное событие записывается в выбранный **вами**
календарь через штатное календарное хранилище Android (`CalendarContract`).
Если календарь принадлежит синхронизируемому аккаунту (например, Google), в
облако событие несёт штатный синк этого аккаунта — ровно так же, как событие
из любого другого календарного приложения. SayWhen не обращается ни к каким
серверам.

## Что мы собираем

Ничего. Ни аналитики, ни крашей, ни рекламных или девайс-идентификаторов,
никакой телеметрии. Введённые фразы разбираются локально и после создания
события приложением не хранятся.

## Ссылки

Немногочисленные ссылки в приложении (исходники, issues, лицензия, этот
документ) открывает системный браузер, вне приложения.
