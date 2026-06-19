package com.scantoftp.data.service

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scantoftp.domain.service.OcrResult
import com.scantoftp.domain.service.ReceiptTextRecognizer
import kotlinx.coroutines.tasks.await
import java.math.RoundingMode
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitReceiptTextRecognizer @Inject constructor() : ReceiptTextRecognizer {
    override suspend fun recognize(processedPath: String, captureTimestamp: Long): OcrResult {
        val bitmap = BitmapFactory.decodeFile(processedPath)
            ?: error("Unable to decode processed receipt for OCR.")
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = try {
            recognizer.process(image).await()
        } finally {
            recognizer.close()
        }

        val fallbackDate = formatFallbackDate(captureTimestamp)
        val vendor = extractVendor(result).ifBlank { "UnknownVendor" }
        val amount = extractAmount(result) ?: "0.00"
        val receiptDate = extractDate(result)?.let { formatRecognizedDate(it, captureTimestamp) } ?: fallbackDate

        return OcrResult(
            vendor = vendor,
            amount = amount,
            receiptDate = receiptDate,
            suggestedFilename = "${receiptDate}_${vendor}_$amount.jpg",
        )
    }

    private fun extractVendor(text: Text): String {
        val block = text.textBlocks
            .filter { it.text.isNotBlank() }
            .sortedWith(
                compareBy<Text.TextBlock> { it.boundingBox?.top ?: Int.MAX_VALUE }
                    .thenByDescending { (it.boundingBox?.width() ?: 0) * (it.boundingBox?.height() ?: 0) },
            )
            .firstOrNull() ?: return "UnknownVendor"

        return block.text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.any(Char::isLetter) }
            ?.replace(Regex("[^A-Za-z0-9]+"), "")
            ?.take(32)
            ?.ifBlank { "UnknownVendor" }
            ?: "UnknownVendor"
    }

    private fun extractAmount(text: Text): String? {
        val pattern = Regex("""(?:[$€£]\s?)?(\d{1,4}(?:[.,]\d{3})*(?:[.,]\d{2}))""")
        val matches = pattern.findAll(text.text).toList()
        val last = matches.lastOrNull()?.groupValues?.getOrNull(1) ?: return null
        val normalized = normalizeAmount(last)
        return normalized.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()
    }

    private fun extractDate(text: Text): String? {
        val patterns = listOf(
            Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b"""),
            Regex("""\b\d{4}-\d{1,2}-\d{1,2}\b"""),
            Regex("""\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[ .-]+\d{1,2}(?:[ ,.-]+\d{2,4})?\b""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(text.text)?.value }
    }

    private fun formatRecognizedDate(raw: String, fallbackTimestamp: Long): String {
        val normalized = raw.trim().replace(",", " ").replace(Regex("\\s+"), " ")
        val formatters = listOf(
            DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-M-d", Locale.US),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
        )

        val parsed = formatters.firstNotNullOfOrNull { formatter ->
            try {
                val date = LocalDate.parse(
                    normalized,
                    formatter.withResolverStyle(java.time.format.ResolverStyle.SMART),
                )
                date
            } catch (_: DateTimeParseException) {
                null
            }
        }

        if (parsed != null) {
            return "${parsed.month.getDisplayName(TextStyle.SHORT, Locale.US)}_${parsed.dayOfMonth.toString().padStart(2, '0')}"
        }

        return parseMonthDay(normalized) ?: formatFallbackDate(fallbackTimestamp)
    }

    private fun normalizeAmount(raw: String): String {
        val value = raw.trim()
        val lastComma = value.lastIndexOf(',')
        val lastDot = value.lastIndexOf('.')

        return when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    value.replace(".", "").replace(',', '.')
                } else {
                    value.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                if (value.matches(Regex(""".*,\d{2}$"""))) {
                    value.replace(".", "").replace(',', '.')
                } else {
                    value.replace(",", "")
                }
            }
            else -> value
        }
    }

    private fun formatFallbackDate(timestamp: Long): String {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return "${date.month.getDisplayName(TextStyle.SHORT, Locale.US)}_${date.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun parseMonthDay(raw: String): String? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("MMM d", Locale.US),
            DateTimeFormatter.ofPattern("MMMM d", Locale.US),
        )
        val parsed = formatters.firstNotNullOfOrNull { formatter ->
            try {
                MonthDay.parse(raw, formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: return null
        return "${parsed.month.getDisplayName(TextStyle.SHORT, Locale.US)}_${parsed.dayOfMonth.toString().padStart(2, '0')}"
    }
}
