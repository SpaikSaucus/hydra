package com.personal.hydra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.hydra.R
import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Number input with -/+ buttons AND a tappable numeric text field, so the value
 * can be typed directly with the numeric keyboard.
 */
@Composable
fun IntStepperField(
    value: Int,
    onValueChange: (Int) -> Unit,
    suffix: String,
    min: Int,
    max: Int,
    step: Int = 1,
) {
    // Re-seed the editable text whenever the external value changes.
    var text by remember(value) { mutableStateOf(value.toString()) }
    // Flag when the typed number falls outside [min, max] (the committed value is
    // still coerced, but the field turns red so the clamp isn't silent).
    val parsed = text.toIntOrNull()
    val isError = text.isNotEmpty() && (parsed == null || parsed < min || parsed > max)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { onValueChange((value - step).coerceIn(min, max)) }) {
            Icon(Icons.Rounded.Remove, contentDescription = "-")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(6)
                text = digits
                digits.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
            },
            singleLine = true,
            isError = isError,
            suffix = { Text(suffix) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
        )
        IconButton(onClick = { onValueChange((value + step).coerceIn(min, max)) }) {
            Icon(Icons.Rounded.Add, contentDescription = "+")
        }
    }
}

/** Whole-number weight, editable by keyboard, in the user's unit (kg / lb). */
@Composable
fun WeightStepper(kg: Double, unit: UnitSystem, onChange: (Double) -> Unit) {
    val metric = unit == UnitSystem.METRIC
    val displayed = if (metric) kg.roundToInt() else UnitConverter.kgToLb(kg).roundToInt()
    val min = if (metric) Ranges.WEIGHT_MIN.roundToInt() else UnitConverter.kgToLb(Ranges.WEIGHT_MIN).roundToInt()
    val max = if (metric) Ranges.WEIGHT_MAX.roundToInt() else UnitConverter.kgToLb(Ranges.WEIGHT_MAX).roundToInt()
    IntStepperField(
        value = displayed,
        onValueChange = { v -> onChange(if (metric) v.toDouble() else UnitConverter.lbToKg(v.toDouble())) },
        suffix = if (metric) "kg" else "lb",
        min = min,
        max = max,
        step = 1,
    )
}

/** A button showing the current country; tapping opens a searchable picker. */
@Composable
fun CountryField(code: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (code.isBlank()) stringResource(R.string.country_unset) else Locale("", code).displayCountry
    OutlinedButton(onClick = { open = true }) { Text(label) }
    if (open) {
        CountryPickerDialog(
            onPick = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun CountryPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val all = remember {
        Locale.getISOCountries()
            .map { it to Locale("", it).displayCountry }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second }
    }
    var query by remember { mutableStateOf("") }
    val filtered = all.filter { (c, name) ->
        query.isBlank() || name.contains(query, ignoreCase = true) || c.equals(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.select_country)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(320.dp)) {
                    items(filtered) { (c, name) ->
                        TextButton(onClick = { onPick(c) }, modifier = Modifier.fillMaxWidth()) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
    )
}
