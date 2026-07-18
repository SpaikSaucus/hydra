package com.personal.hydra.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.hydra.R
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarChart
import com.personal.hydra.ui.components.SectionCard
import com.personal.hydra.ui.hydraViewModel
import com.personal.hydra.util.VolumeFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun HistoryRoute() {
    val vm = hydraViewModel { HistoryViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(state: HistoryUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        if (!state.loading && state.days.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_history),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stat_current_streak), stringResource(R.string.days_unit, state.currentStreak))
                StatCard(stringResource(R.string.stat_best_streak), stringResource(R.string.days_unit, state.bestStreak))
                StatCard(stringResource(R.string.stat_days_completed), state.daysCompleted.toString())
                StatCard(stringResource(R.string.stat_avg_completion), "${(state.averagePercent * 100).roundToInt()}%")
            }

            SectionCard(title = stringResource(R.string.last_30_days)) {
                BarChart(bars = state.chart)
            }

            SectionCard(title = stringResource(R.string.achievements)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.achievements.forEach { (a, unlocked) -> AchievementBadge(a, unlocked) }
                }
            }

            SectionCard(title = stringResource(R.string.history_title)) {
                state.days.forEach { DayRow(it, state.unit) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementBadge(a: Achievement, unlocked: Boolean) {
    val icon = if (a.kind == Achievement.Kind.DAYS) Icons.Rounded.WaterDrop else Icons.Rounded.LocalFireDepartment
    val tint = if (unlocked) tierColor(a.tier) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            stringResource(achievementTitle(a)),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun DayRow(day: DayStat, unit: UnitSystem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(day.date.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
        Text(
            VolumeFormat.volume(day.totalMl, unit) + " / " + VolumeFormat.volume(day.goalMl, unit),
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${(minOf(day.percent, 1.0) * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        if (day.completed) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun achievementTitle(a: Achievement): Int = when (a) {
    Achievement.FIRST_DAY -> R.string.ach_first_day
    Achievement.DAYS_7 -> R.string.ach_days_7
    Achievement.DAYS_30 -> R.string.ach_days_30
    Achievement.DAYS_100 -> R.string.ach_days_100
    Achievement.DAYS_365 -> R.string.ach_days_365
    Achievement.STREAK_3 -> R.string.ach_streak_3
    Achievement.PERFECT_WEEK -> R.string.ach_perfect_week
    Achievement.STREAK_14 -> R.string.ach_streak_14
    Achievement.STREAK_30 -> R.string.ach_streak_30
    Achievement.STREAK_100 -> R.string.ach_streak_100
    Achievement.STREAK_365 -> R.string.ach_streak_365
}

private fun tierColor(tier: Achievement.Tier): Color = when (tier) {
    Achievement.Tier.BRONZE -> Color(0xFFCD7F32)
    Achievement.Tier.SILVER -> Color(0xFFB0BEC5)
    Achievement.Tier.GOLD -> Color(0xFFFFC107)
}
