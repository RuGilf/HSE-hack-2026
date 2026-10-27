package ru.lifety.feature.people

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import ru.lifety.core.model.Person
import ru.lifety.core.model.personValidationError
import ru.lifety.core.repository.PersonRepository
import ru.lifety.core.time.localDate
import ru.lifety.core.time.today
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol

/** Shared editor opened both from People and the calendar; writes complete before closing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonEditorSheet(initial: Person, isNew: Boolean, repository: PersonRepository, onDismiss: () -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.fullName) }
    var day by rememberSaveable(initial.id) { mutableStateOf(initial.birthdayDay?.toString().orEmpty()) }
    var month by rememberSaveable(initial.id) { mutableStateOf(initial.birthdayMonth?.toString().orEmpty()) }
    var year by rememberSaveable(initial.id) { mutableStateOf(initial.birthYear?.toString().orEmpty()) }
    var interests by rememberSaveable(initial.id) { mutableStateOf(initial.interests) }
    var hobbies by rememberSaveable(initial.id) { mutableStateOf(initial.hobbies) }
    var gifts by rememberSaveable(initial.id) { mutableStateOf(initial.giftIdeas) }
    var attempted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val currentDay = today()
    val draft = Person(initial.id, name.trim(), month.toIntOrNull(), day.toIntOrNull(), year.toIntOrNull(), interests.trim(), hobbies.trim(), gifts.trim())
    val inputError = when {
        day.isNotEmpty() && day.toIntOrNull() == null || month.isNotEmpty() && month.toIntOrNull() == null || year.isNotEmpty() && year.toIntOrNull() == null -> "Проверьте дату рождения"
        else -> personValidationError(draft, currentDay)
    }
    val scope = rememberCoroutineScope()
    val blocked by rememberUpdatedState(busy || closing)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !blocked || closing })
    val close: () -> Unit = { if (!busy && !closing) { closing = true; scope.launch { sheet.hide(); onDismiss() } } }
    fun write(deleting: Boolean = false) {
        if (busy || closing) return
        attempted = true
        if (!deleting && inputError != null) return
        busy = true
        error = null
        scope.launch {
            try {
                if (deleting) repository.delete(initial.id) else repository.upsert(draft)
                closing = true
                sheet.hide()
                onDismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Не удалось сохранить изменения. Попробуйте ещё раз." }
            finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!blocked) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, enabled = !blocked, modifier = Modifier.semantics { contentDescription = "Закрыть карточку человека" }) { LifetyIcon(LifetySymbol.Close) }
                Text(if (isNew) "Новый человек" else "Карточка человека", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!isNew) TextButton(onClick = { confirmDelete = true }, enabled = !blocked) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it.take(200) }, label = { Text("ФИО или имя") }, modifier = Modifier.fillMaxWidth(),
                    enabled = !blocked, isError = attempted && name.isBlank(), maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("День рождения", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { pickingDate = true }, enabled = !blocked,
                            modifier = Modifier.semantics { contentDescription = "Выбрать день рождения в календаре" }) { LifetyIcon(LifetySymbol.Calendar) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("День") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !blocked, modifier = Modifier.weight(1f))
                        OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2) }, label = { Text("Месяц") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !blocked, modifier = Modifier.weight(1f))
                        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Год") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !blocked, modifier = Modifier.weight(1.3f))
                    }
                    Text("Год можно не указывать. День рождения появится в календаре каждый год.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (day.toIntOrNull() == 29 && month.toIntOrNull() == 2) Text("В невисокосные годы — 28 февраля.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (day.isNotEmpty() || month.isNotEmpty() || year.isNotEmpty()) TextButton(onClick = { day = ""; month = ""; year = "" }, enabled = !blocked) { Text("Убрать дату рождения") }
                }
                NoteField("Интересы", "Например, искусство и путешествия", interests, !blocked) { interests = it }
                NoteField("Увлечения", "Что любит делать в свободное время", hobbies, !blocked) { hobbies = it }
                NoteField("Идеи для подарков", "Пожелания, ссылки, размеры — всё, что пригодится", gifts, !blocked) { gifts = it }
            }
            (error ?: inputError?.takeIf { attempted })?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Button(onClick = { write() }, enabled = !blocked, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text(if (busy) "Сохраняем…" else "Сохранить")
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить ${initial.fullName}?") },
        text = { Text("Карточка и день рождения в календаре будут удалены. Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; write(deleting = true) }, enabled = !blocked) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
    if (pickingDate) {
        val selected = runCatching { LocalDate(year.toIntOrNull() ?: 2000, month.toInt(), day.toInt()) }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(), yearRange = 1..currentDay.year)
        DatePickerDialog(onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { millis ->
                    val date = localDate(millis, TimeZone.UTC)
                    day = date.day.toString(); month = date.month.number.toString()
                    if (year.isNotEmpty()) year = date.year.toString()
                }
                pickingDate = false
            }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Отмена") } }) {
            Column {
                Text("Выберите день и месяц. Если год известен, укажите его в карточке.", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                DatePicker(picker)
            }
        }
    }
}

@Composable
private fun NoteField(label: String, hint: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(10_000)) }, label = { Text(label) }, placeholder = { Text(hint) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, minLines = 2, maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
}
