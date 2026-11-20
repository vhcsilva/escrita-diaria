package com.escritadiaria.app.ink

import android.content.Context
import android.util.Log
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.InkStroke
import com.escritadiaria.app.data.LINE_SPACING
import com.escritadiaria.app.data.Ritual
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.reminders.Notifs
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/**
 * Converte a escrita à mão em texto usando o ML Kit Digital Ink (pt-BR), no próprio tablet.
 * O pacote de idioma é baixado uma vez; depois funciona sem internet.
 */
object Recognition {
    private const val TAG = "Recognition"
    private val lock = Mutex()
    private var recognizer: DigitalInkRecognizer? = null

    // Tenta português do Brasil; se o ML Kit não tiver essa variante, cai para português genérico.
    private val model: DigitalInkRecognitionModel? by lazy {
        listOf("pt-BR", "pt", "pt-PT").firstNotNullOfOrNull { tag ->
            runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) }.getOrNull()
        }?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    val isSupported: Boolean get() = model != null

    suspend fun isModelReady(): Boolean {
        val m = model ?: return false
        return runCatching { RemoteModelManager.getInstance().isModelDownloaded(m).awaitTask() }.getOrDefault(false)
    }

    /** Baixa o pacote de idioma se faltar. Retorna true se estiver disponível. */
    suspend fun ensureModel(timeoutMs: Long = 120_000): Boolean {
        val m = model ?: return false
        if (isModelReady()) return true
        val ok = withTimeoutOrNull(timeoutMs) {
            runCatching {
                RemoteModelManager.getInstance().download(m, DownloadConditions.Builder().build()).awaitTask()
                true
            }.getOrElse { Log.w(TAG, "download falhou", it); false }
        }
        return ok == true
    }

    private fun client(): DigitalInkRecognizer? {
        val m = model ?: return null
        return recognizer ?: DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
            .also { recognizer = it }
    }

    /** Agrupa traços pela linha da pauta onde está o centro vertical de cada um. */
    fun groupLines(strokes: List<InkStroke>): List<List<InkStroke>> =
        strokes.groupBy { it.line }.toSortedMap().values.toList()

    /** Reconhece os traços de uma linha. Retorna null se o pacote não estiver pronto ou der erro. */
    suspend fun recognizeStrokes(strokes: List<InkStroke>, width: Float): String? {
        if (strokes.isEmpty()) return ""
        val c = client() ?: return null
        return runCatching { recognizeLine(c, strokes, width) }
            .getOrElse { Log.w(TAG, "reconhecimento falhou", it); null }
    }

    private suspend fun recognizeLine(c: DigitalInkRecognizer, line: List<InkStroke>, width: Float): String {
        val ink = Ink.builder()
        line.forEach { s ->
            val sb = Ink.Stroke.builder()
            s.points.forEach { p -> sb.addPoint(Ink.Point.create(p.x, p.y, p.t)) }
            ink.addStroke(sb.build())
        }
        val ctx = RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(WritingArea(width.coerceAtLeast(100f), LINE_SPACING * 1.5f))
            .build()
        val result = c.recognize(ink.build(), ctx).awaitTask()
        return result.candidates.firstOrNull()?.text.orEmpty()
    }

    /** Texto de uma página, uma linha da pauta por linha de texto. */
    suspend fun recognizePage(strokes: List<InkStroke>, pageW: Float): String? {
        if (strokes.isEmpty()) return ""
        val c = client() ?: return null
        return runCatching {
            groupLines(strokes).map { recognizeLine(c, it, pageW) }.filter { it.isNotBlank() }.joinToString("\n")
        }.getOrElse { Log.w(TAG, "reconhecimento falhou", it); null }
    }

    /** Reconhece os blocos de um ritual. Retorna null se o pacote de idioma não estiver disponível. */
    suspend fun recognizeRitual(r: Ritual, download: Boolean = true): Ritual? = lock.withLock {
        val ready = if (download) ensureModel() else isModelReady()
        if (!ready) return@withLock null
        val texts = HashMap<String, String>()
        for (b in r.blocks()) {
            texts[b] = recognizePage(InkCodec.decode(r.ink(b)), r.pageW) ?: return@withLock null
        }
        val b3 = texts["b3"].orEmpty()
        val action = r.actionText.ifBlank { b3.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
        r.copy(
            b1Text = texts["b1"].orEmpty(),
            b2Text = texts["b2"].orEmpty(),
            b3Text = b3,
            actionText = action,
            recognized = true,
        ).reindexed()
    }

    /** Reconhece rituais concluídos que ficaram sem texto (por exemplo, sem internet para baixar o pacote). */
    suspend fun processPending(context: Context) {
        val dao = AppDb.get(context).rituals()
        val pending = dao.pendingRecognition()
        if (pending.isEmpty()) return
        if (!ensureModel()) return
        val settings = SettingsStore(context).snapshot()
        val today = LocalDate.now().toEpochDay()
        for (r in pending) {
            val updated = recognizeRitual(r, download = false) ?: continue
            dao.update(updated)
            if (updated.epochDay == today && settings.pinAction && !updated.actionDone && updated.actionText.isNotBlank()) {
                Notifs.action(context, updated.id, updated.actionText)
            }
        }
    }
}
