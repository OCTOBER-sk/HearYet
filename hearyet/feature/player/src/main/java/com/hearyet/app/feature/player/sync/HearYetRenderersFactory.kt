package com.hearyet.app.feature.player.sync

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * A thin subclass of [NextRenderersFactory] that inserts [SharedAudioRenderer]
 * into the audio sink chain.  This is the injection point mandated by BE §6.
 *
 * In Media3 1.10+, audio processors are set on [DefaultAudioSink.Builder]
 * via [DefaultAudioSink.Builder.setAudioProcessors].  This factory overrides
 * [buildAudioSink] to add the shared processor before the sink is built.
 */
class HearYetRenderersFactory(
    private val sharedAudioRenderer: SharedAudioRenderer,
    context: Context,
) : NextRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): DefaultAudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(sharedAudioRenderer))
            .build()
    }
}
