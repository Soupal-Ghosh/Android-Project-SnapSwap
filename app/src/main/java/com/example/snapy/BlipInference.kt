package com.example.snapy

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class BlipInference(private val context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private lateinit var encoderSession: OrtSession
    private lateinit var decoderSession: OrtSession

    private val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    private val std  = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

    private val IMAGE_SIZE = 384
    private val MAX_TOKENS = 50
    private val BOS_TOKEN  = 30522L
    private val EOS_TOKEN  = 102L

    private lateinit var vocab: Map<Int, String>

    // ✅ Mutex to prevent concurrent inference crashes
    private val inferenceLock = kotlinx.coroutines.sync.Mutex()

    fun load() {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        // ✅ updated filenames
        encoderSession = env.createSession(copyAssetToCache("blip/encoder_uint8.onnx"), opts)
        decoderSession = env.createSession(copyAssetToCache("blip/decoder_uint8.onnx"), opts)
        vocab = loadVocab()
    }

    // ✅ Suspend function with mutex — only one inference at a time
    suspend fun caption(bitmap: Bitmap): String = inferenceLock.withLock {
        runInference(bitmap)
    }

    private fun runInference(bitmap: Bitmap): String {
        val pixelValues = preprocessImage(bitmap)
        val pixelTensor = OnnxTensor.createTensor(
            env,
            pixelValues,
            longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        )

        val encoderOut = encoderSession.run(mapOf("pixel_values" to pixelTensor))
        val encoderHidden = encoderOut["last_hidden_state"].get() as OnnxTensor

        val tokenIds = mutableListOf(BOS_TOKEN)

        for (i in 0 until MAX_TOKENS) {
            val seqLen = tokenIds.size.toLong()

            val inputIds = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenIds.toLongArray()),
                longArrayOf(1, seqLen)
            )
            val attentionMask = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(tokenIds.size) { 1L }),
                longArrayOf(1, seqLen)
            )

            val decoderOut = decoderSession.run(
                mapOf(
                    "input_ids"             to inputIds,
                    "attention_mask"        to attentionMask,
                    "encoder_hidden_states" to encoderHidden
                )
            )

            val logits = (decoderOut["logits"].get() as OnnxTensor).floatBuffer
            val vocabSize = logits.capacity() / tokenIds.size
            val lastOffset = (tokenIds.size - 1) * vocabSize

            var maxIdx = 0
            var maxVal = Float.NEGATIVE_INFINITY
            for (j in 0 until vocabSize) {
                val v = logits[lastOffset + j]
                if (v > maxVal) { maxVal = v; maxIdx = j }
            }

            inputIds.close()
            attentionMask.close()
            decoderOut.close()

            if (maxIdx.toLong() == EOS_TOKEN) break
            tokenIds.add(maxIdx.toLong())
        }

        encoderOut.close()
        pixelTensor.close()

        return tokenIds
            .drop(1)
            .mapNotNull { vocab[it.toInt()] }
            .joinToString(" ")
            .replace(" ##", "")
            .trim()
    }

    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val buf = FloatBuffer.allocate(3 * IMAGE_SIZE * IMAGE_SIZE)

        val r = FloatArray(IMAGE_SIZE * IMAGE_SIZE)
        val g = FloatArray(IMAGE_SIZE * IMAGE_SIZE)
        val b = FloatArray(IMAGE_SIZE * IMAGE_SIZE)

        for (y in 0 until IMAGE_SIZE) {
            for (x in 0 until IMAGE_SIZE) {
                val px  = scaled.getPixel(x, y)
                val idx = y * IMAGE_SIZE + x
                r[idx] = (Color.red(px)   / 255f - mean[0]) / std[0]
                g[idx] = (Color.green(px) / 255f - mean[1]) / std[1]
                b[idx] = (Color.blue(px)  / 255f - mean[2]) / std[2]
            }
        }

        buf.put(r); buf.put(g); buf.put(b)
        buf.rewind()
        return buf
    }

    // ✅ Copies asset to cache dir, loads by file path (no OOM)
    private fun copyAssetToCache(assetPath: String): String {
        val outFile = File(context.cacheDir, assetPath.replace("/", "_"))
        if (!outFile.exists()) {
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    private fun loadVocab(): Map<Int, String> =
        context.assets.open("blip/vocab.txt")
            .bufferedReader()
            .readLines()
            .mapIndexed { i, token -> i to token }
            .toMap()
}