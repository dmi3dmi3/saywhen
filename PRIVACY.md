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
| `org.tasks.permission.READ_TASKS` | List your Tasks.org lists. Asked only when you turn on the Tasks.org integration. |
| `org.tasks.permission.WRITE_TASKS` | Create the task you asked for in that list. Same switch. |

That is the complete list. No location, no contacts, no microphone, no network.
The two Tasks.org permissions are defined by the Tasks.org app itself and
exist only on a device where it is installed.

## What leaves the device

Nothing leaves the device through SayWhen. The event you create is written to
the calendar **you** selected via Android's standard calendar storage
(`CalendarContract`). If that calendar belongs to a synced account (e.g.
Google), the account's own sync engine uploads the event — exactly as it would
for an event created in any other calendar app. SayWhen does not talk to any
server.

With the Tasks.org integration turned on, a task you create goes into the
Tasks.org app on the same device through its own interface. Tasks.org then
syncs it with its accounts, exactly as it would for a task created in
Tasks.org itself.

## What we collect

Nothing. No analytics, no crash reporting, no advertising or device
identifiers, no telemetry of any kind. The phrases you type are parsed on the
device, in the languages you actually use: the app takes them from your
interface language, your system languages and your keyboards. Neither the
phrases nor these languages are stored or sent anywhere.

## Links

The few links in the app (source code, issues, license, this document) are
opened by your system browser, outside the app.

---

# Приватность

**События идут только в ваш календарь — больше никуда.**

У SayWhen нет собственного доступа в сеть: приложение не объявляет разрешение
`INTERNET`, поэтому система не даёт ему выйти в сеть. Всё написанное ниже
проверяется по исходному коду в этом репозитории.

## Разрешения

| Разрешение | Зачем |
|---|---|
| `READ_CALENDAR` | Показать список ваших календарей в настройках и выбрать нужный. |
| `WRITE_CALENDAR` | Записать созданное вами событие в этот календарь. |
| `org.tasks.permission.READ_TASKS` | Показать ваши списки Tasks.org. Запрашивается только когда вы включаете интеграцию с Tasks.org. |
| `org.tasks.permission.WRITE_TASKS` | Создать задачу в этом списке. Тот же тумблер. |

Это полный список. Ни геолокации, ни контактов, ни микрофона, ни сети.
Два разрешения Tasks.org объявляет само приложение Tasks.org, и существуют
они только на устройстве, где оно установлено.

## Что покидает устройство

Через SayWhen — ничего. Созданное событие записывается в выбранный **вами**
календарь через штатное календарное хранилище Android (`CalendarContract`).
Если календарь принадлежит синхронизируемому аккаунту (например, Google), в
облако событие отправляет синхронизация этого аккаунта, ровно так же, как
событие из любого другого календарного приложения. SayWhen не обращается ни к каким
серверам.

С включённой интеграцией Tasks.org созданная задача уходит в приложение
Tasks.org на том же устройстве через его собственный интерфейс. Дальше
Tasks.org синхронизирует её со своими аккаунтами ровно так же, как задачу,
созданную в самом Tasks.org.

## Что мы собираем

Ничего. Ни аналитики, ни отчётов о сбоях, ни рекламных идентификаторов, ни
идентификаторов устройства, никакой телеметрии. Введённые фразы разбираются на устройстве, на тех языках,
которыми вы пользуетесь: приложение берёт их из языка интерфейса, языков
системы и клавиатур. Ни фразы, ни эти языки нигде не хранятся и никуда не
отправляются.

## Ссылки

Немногочисленные ссылки в приложении (исходный код, сообщения о проблемах,
лицензия, этот документ) открывает системный браузер, вне приложения.
