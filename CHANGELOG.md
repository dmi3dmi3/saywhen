# Changelog

What changed in each SayWhen release. Versions before 3.2.0 are described
on the [Releases](https://github.com/dmi3dmi3/saywhen/releases) page.

## 3.2.0

SayWhen now turns on the languages you write in by itself, and it no longer writes to just one calendar. A word after # in the phrase sends an event to another calendar or a task to Tasks.org.

- Phrase languages turn on by themselves. SayWhen takes them from your interface language, your system languages and your keyboards. If you write in Russian with an English interface, you no longer need to turn Russian on by hand. The "Phrase languages" page says why each language is on. Thanks to andrummer for the report.
- An event to another calendar, right from the phrase. Write the calendar's alias, like "lunch #work". Aliases come from your calendar names and can be edited on the "Where it goes" page. Typing # brings up suggestions, and the chosen calendar shows under the input field. Thanks to Vikranttiw and Jarige for the idea.
- Tasks in Tasks.org. Turn on Tasks.org on the "Where it goes" page, and its lists appear next to your calendars. A phrase with a list's alias creates a task with a due date, recurrence and a reminder. The #tasks alias sends the phrase to the default list. Thanks to uptake182 for the idea.
- Settings on one screen. Each group has its own row with the current value. Details are on separate pages.
- Time with a dot. Turn on the switch on the "How time is read" page, and "16.00" is read as 16:00. Thanks to andrummer for the idea.
- "+ event" shortcut for launchers. You can put the input window on a gesture or a separate icon wherever your launcher offers a shortcut rather than a widget. Thanks to Jarige for the idea.
- In English, "tmrw", "tmr" and "tdy" are read as tomorrow and today. Thanks to Thurvok for the idea.
- Several events in a row. When you start the next phrase, the line under the field shows what SayWhen understood again, instead of "Created ✓" from the previous event.
