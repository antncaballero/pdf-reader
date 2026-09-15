package com.acaba.pdfreader.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun GoToPageDialog(
    pageCount: Int?,
    currentPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (pageIndex: Int) -> Unit,
) {
    var value by remember(pageCount, currentPage) { mutableStateOf(currentPage.coerceAtLeast(1).toString()) }
    val enteredPage = value.toIntOrNull()
    val isValid = enteredPage != null && enteredPage >= 1 && (pageCount == null || enteredPage <= pageCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ir a página") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter(Char::isDigit).take(7) },
                label = { Text("Número de página") },
                supportingText = {
                    Text(
                        when {
                            pageCount == null -> "Introduce una página desde la 1."
                            value.isNotEmpty() && !isValid -> "Elige una página entre 1 y $pageCount."
                            else -> "$pageCount páginas"
                        },
                    )
                },
                isError = value.isNotEmpty() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(enteredPage!! - 1) },
            ) {
                Text("Ir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
