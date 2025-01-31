package ru.astar.tcprelay

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun IpAddressInput(
    modifier: Modifier,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    val maxLength = 15
    val filteredValue = remember(value) { enforceIpMask(value) }

    TextField(
        modifier = modifier,
        value = filteredValue,
        onValueChange = {
            if (it.length <= maxLength) {
                onValueChanged(enforceIpMask(it))
            }
        },
        label = { Text("Введите IP-адрес") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = IpVisualTransformation(),
    )
}

fun enforceIpMask(input: String): String {
    val cleanedInput = input.filter { it.isDigit() || it == '.' }
    val parts = cleanedInput.split('.')
        .map { it.take(3) }
        .take(4)
    return parts.joinToString(".")
}

class IpVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formattedText = enforceIpMask(text.text)
        return TransformedText(
            text = AnnotatedString(formattedText),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset
                override fun transformedToOriginal(offset: Int) = offset
            }
        )
    }
}