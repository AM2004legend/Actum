package com.actum.ui.act

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actum.ui.home.HomeViewModel
import com.actum.ui.home.monoStyle
import com.actum.ui.home.sans
import com.actum.ui.home.serif
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.AmberOnContainer
import com.actum.ui.theme.Bone
import com.actum.ui.theme.BoneDim
import com.actum.ui.theme.InkOutline
import com.actum.data.Marker
import com.actum.model.ActumIntent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The events and reminders confirmed in Actum, marked on a month: a blue dot
 * for an event, a coral one for a reminder. Tap a day to see what is on it.
 * These are Actum's own marks. The event itself lives in the calendar app and
 * the alarm in the clock app, and removing a mark here removes neither.
 */
@Composable
internal fun CalendarTab(viewModel: HomeViewModel) {
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val byDay = remember(markers) { markers.groupBy { it.date } }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(month.format(MONTH), style = serif(26.sp, lineHeight = 30.sp), modifier = Modifier.weight(1f))
        Step("‹", "Earlier month") { monthText = month.minusMonths(1).toString() }
        Step("›", "Later month") { monthText = month.plusMonths(1).toString() }
    }

    Row(Modifier.fillMaxWidth()) {
        for (day in DAYS) {
            Text(day, style = monoStyle(11.sp, InkOutline), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }

    // The month slides the way time went: a later one in from the right.
    AnimatedContent(
        targetState = month,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(140)))
        },
        label = "month",
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Weeks start on Monday; the blanks before the 1st keep each date under its weekday.
            val blanks = shown.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
            val cells = List(blanks) { null } + (1..shown.lengthOfMonth()).map { shown.atDay(it) }
            for (week in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (day in week + List(7 - week.size) { null }) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                Day(
                                    day = day,
                                    isToday = day == today,
                                    isSelected = day == selected,
                                    marks = byDay[day.toString()].orEmpty(),
                                    onClick = { selectedText = day.toString() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        (if (selected == today) "Today · " else "") + selected.format(DAY),
        style = monoStyle(12.sp, BoneDim),
    )
    val onDay = byDay[selected.toString()].orEmpty().sortedBy { it.time ?: "" }
    if (onDay.isEmpty()) {
        Text(
            if (markers.isEmpty()) "Nothing marked yet. Confirm an event or a reminder and its day is marked here."
            else "Nothing marked on this day.",
            style = sans(15.sp, BoneDim, lineHeight = 22.sp),
        )
    }
    onDay.forEach { marker ->
        val intent = if (marker.kind == Marker.EVENT) ActumIntent.EVENT else ActumIntent.ALARM
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntentBadge(intent, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(marker.title, style = sans(16.sp, Bone, FontWeight.Medium, lineHeight = 22.sp))
                Text(
                    listOfNotNull(
                        if (marker.kind == Marker.EVENT) "event" else "reminder",
                        marker.time?.let { runCatching { LocalTime.parse(it).format(CLOCK) }.getOrNull() } ?: "all day".takeIf { marker.kind == Marker.EVENT },
                    ).joinToString(" · "),
                    style = monoStyle(11.sp, colorOf(intent)),
                )
            }
            Text(
                "remove",
                style = monoStyle(11.sp, InkOutline),
                modifier = Modifier.clickable { viewModel.delete(marker) }.padding(horizontal = 8.dp, vertical = 16.dp),
            )
        }
    }
}

/** One date: its number, a ring if it is today, filled if it is the one picked, and a dot for each kind of mark on it. */
@Composable
private fun Day(day: LocalDate, isToday: Boolean, isSelected: Boolean, marks: List<Marker>, onClick: () -> Unit) {
    val fill by animateColorAsState(if (isSelected) AmberBright else Color.Transparent, tween(180), label = "day fill")
    // A small press of weight as the choice lands.
    val scale by animateFloatAsState(if (isSelected) 1f else 0.92f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "day scale")
    Column(
        Modifier.size(width = 44.dp, height = 52.dp).clip(CircleShape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(fill, CircleShape)
                .border(1.4.dp, if (isToday && !isSelected) AmberBright else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = sans(14.sp, if (isSelected) AmberOnContainer else Bone, if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal),
            )
        }
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (marks.any { it.kind == Marker.EVENT }) Box(Modifier.size(5.dp).background(EventBlue, CircleShape))
            if (marks.any { it.kind == Marker.REMINDER }) Box(Modifier.size(5.dp).background(AlarmCoral, CircleShape))
        }
    }
}

@Composable
private fun Step(mark: String, says: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = says, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, style = sans(24.sp, BoneDim))
    }
}

private val DAYS = listOf("M", "T", "W", "T", "F", "S", "S")
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
