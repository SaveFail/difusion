package com.masstext.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File

/**
 * Reproduce el mensaje pregrabado hacia el interlocutor sacándolo por el
 * ALTAVOZ mientras la llamada queda en el auricular. Así el cancelador de eco
 * (que referencia el auricular) NO reconoce el mensaje como eco y el micrófono
 * lo capta y lo transmite al cliente.
 *
 * Se decodifica el audio a PCM (MediaExtractor + MediaCodec) y se reproduce con
 * un AudioTrack enrutado explícitamente al altavoz (setPreferredDevice).
 */
object CallUplinkAudio {

    private var worker: Thread? = null

    @Volatile
    private var stopFlag = false

    @Volatile
    private var onFinished: (() -> Unit)? = null

    fun play(context: Context, file: File, finished: (() -> Unit)? = null) {
        stop()
        stopFlag = false
        onFinished = finished
        val appCtx = context.applicationContext
        worker = Thread {
            var ok = false
            try {
                ok = streamToSpeaker(appCtx, file)
            } catch (_: Throwable) {
            } finally {
                val cb = onFinished
                onFinished = null
                worker = null
                if (!stopFlag && ok) cb?.invoke()
            }
        }.also { it.name = "CallUplinkAudio"; it.isDaemon = true; it.start() }
    }

    fun stop() {
        stopFlag = true
        worker?.interrupt()
        worker = null
        onFinished = null
    }

    private fun streamToSpeaker(context: Context, file: File): Boolean {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return false
        }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return false }
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO
        else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Enrutar SOLO el mensaje al altavoz. La llamada sigue en el auricular,
        // así el AEC no cancela este audio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val speaker = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) track.preferredDevice = speaker
            }
        }
        track.play()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var produced = false
        while (!outputDone && !stopFlag) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                else -> if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk, 0, info.size)
                        track.write(chunk, 0, chunk.size)
                        produced = true
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        runCatching { track.stop() }
        runCatching { track.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
        return produced
    }
}
