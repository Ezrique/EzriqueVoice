package dev.deftu.ezrique.voice.music

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import dev.deftu.ezrique.voice.VoiceHandler
import dev.deftu.ezrique.voice.audio.DefaultTrackScheduler
import dev.deftu.ezrique.voice.audio.GuildPlayer
import dev.deftu.ezrique.voice.eventBus
import dev.deftu.ezrique.voice.events.VoiceChannelJoinEvent
import dev.deftu.ezrique.voice.utils.getPlayer
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredEphemeralMessageInteractionResponseBehavior

object MusicHandler {
    private val guildPlayers = mutableMapOf<Snowflake, GuildPlayer<DefaultTrackScheduler>>()
    val playerManager = DefaultAudioPlayerManager().apply {
        configuration.resamplingQuality = AudioConfiguration.ResamplingQuality.HIGH
        registerSourceManager(YoutubeAudioSourceManager())
        // TODO - registerSourceManager(SpotifyAudioSouceManager())
    }

    fun initialize() {
        eventBus.on<VoiceChannelJoinEvent> { event ->
            val audioPlayer = playerManager.createPlayer()
            val scheduler = DefaultTrackScheduler(audioPlayer)
            audioPlayer.addListener(scheduler)

            val player = GuildPlayer(audioPlayer, scheduler)
            guildPlayers[event.guildId] = player
            VoiceHandler.registerPlayer(event.guildId, player)
        }
    }

    fun playFromYouTube(
        input: String,
        player: GuildPlayer<DefaultTrackScheduler>,
        response: DeferredEphemeralMessageInteractionResponseBehavior
    ) = playerManager.loadItem(input, YouTubeAudioLoadResultHandler(player, response))

    suspend fun getPlayer(
        guildId: Long,
        response: DeferredEphemeralMessageInteractionResponseBehavior
    ) = guildPlayers.getPlayer(guildId, response)
}
