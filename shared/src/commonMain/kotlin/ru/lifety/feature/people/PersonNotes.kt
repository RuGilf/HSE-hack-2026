package ru.lifety.feature.people

/** Newline storage keeps the public String model and every pre-existing note compatible. */
internal fun personNoteItems(text: String): List<String> = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()

internal fun personNoteText(items: List<String>): String = items.flatMap(::personNoteItems).joinToString("\n")

/** Pasting multiple lines into an item inserts separate rows at that position. */
internal fun replacePersonNoteItem(items: List<String>, index: Int, text: String): List<String> =
    items.take(index) + text.replace("\r\n", "\n").replace('\r', '\n').split('\n') + items.drop(index + 1)

internal fun personNotePreview(text: String): String = personNoteItems(text).joinToString(" · ")
