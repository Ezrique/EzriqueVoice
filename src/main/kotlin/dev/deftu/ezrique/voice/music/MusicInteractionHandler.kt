package dev.deftu.ezrique.voice.music

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.handleError
import dev.deftu.ezrique.stateEmbed
import dev.deftu.ezrique.voice.VoiceErrorCode
import dev.deftu.ezrique.voice.sql.GuildConfig
import dev.deftu.ezrique.voice.utils.*
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.response.DeferredEphemeralMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.embed
import java.util.concurrent.TimeUnit

object MusicInteractionHandler : InteractionHandler {

    override val name = "music"

    override fun setupCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(name, "Handles all music features.") {
            dmPermission = false

            group("config", "Handles all music configuration.") {
                subCommand("toggle", "Enables music commands.") {
                    boolean("value", "The new value of your server's toggle.")
                }

                subCommand("djonly", "Sets whether only DJ's can use music commands.") {
                    boolean("value", "Whether to enable DJ only mode.") {
                        required = true
                    }
                }

                subCommand("djrole", "Sets the DJ role.") {
                    role("role", "The role to set as the DJ role.") {
                        required = true
                    }
                }
            }

            subCommand("remove", "Removes a track from the queue.") {
                integer("index", "The index of the track to remove.") {
                    required = true
                }
            }

            subCommand("youtube", "Plays a track from YouTube.") {
                string("query", "The query to search for.") {
                    required = true
                    // TODO: Add autocomplete
                }
            }

            subCommand("soundcloud", "Plays a track from SoundCloud.") {
                string("query", "The query to search for.") {
                    required = true
                    // TODO: Add autocomplete
                }
            }

            subCommand("spotify", "Plays a track from Spotify.") {
                string("query", "The query to search for.") {
                    required = true
                    // TODO: Add autocomplete
                }
            }

            subCommand("volume", "Changes the volume of the player.") {
                integer("volume", "The volume to set the player to.") {
                    required = true
                    maxValue = 100
                    minValue = 0
                }
            }

            subCommand("seek", "Seeks to a specific position in the current track.") {
                integer("position", "The position to seek to, in seconds.") {
                    required = true
                }
            }

            subCommand("loop", "Loops the current track.")
            subCommand("queue", "Shows the current queue.")
            subCommand("resume", "Resumes the current track.")
            subCommand("pause", "Pauses the current track.")
            subCommand("skip", "Skips the current track.")
            subCommand("clear", "Clears the queue.")
        }
    }

    override suspend fun handleCommand(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?,
        commandName: String,
        subCommandName: String?,
        groupName: String?
    ) {
        val response = event.interaction.deferEphemeralResponse()
        if (!guild.checkGuild(response)) return
        guild!!

        val member = event.interaction.user.asMember(guild.id)
        when (groupName) {
            "config" -> handleConfigCommands(event, member, response, guild, subCommandName)
            else -> handleBaseCommands(event, member, response, guild, subCommandName)
        }
    }

    private suspend fun handleConfigCommands(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
        subCommandName: String?,
    ) {
        when (subCommandName) {
            "toggle" -> handleConfigToggle(event, member, response, guild)
            "djonly" -> handleConfigDjOnly(event, member, response, guild)
            "djrole" -> handleConfigDjRole(event, member, response, guild)
        }
    }

    private suspend fun handleConfigToggle(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkPermissions(
            Permissions(Permission.ManageGuild),
            response
        )) return

        val newValue = event.interaction.command.booleans["value"] ?: !GuildConfig.isMusicEnabled(guild.id.get())
        val currentValue = try {
            GuildConfig.setMusicEnabled(guild.id.get(), newValue)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_TOGGLE_GUILD, response = response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Music is now ${if (currentValue) "enabled" else "disabled"}. in your server!"
            }
        }
    }

    private suspend fun handleConfigDjOnly(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkPermissions(
            Permissions(Permission.ManageRoles),
            response
        )) return

        val newValue = event.interaction.command.booleans["enabled"] ?: !GuildConfig.isDjOnly(guild.id.get())
        val currentValue = try {
            GuildConfig.setDjOnly(guild.id.get(), newValue)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_DJ_ONLY_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "DJ only mode is now ${if (currentValue) "enabled" else "disabled"}. in your server!"
            }
        }
    }

    private suspend fun handleConfigDjRole(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkPermissions(
            Permissions(Permission.ManageRoles),
            response
        )) return

        val role = event.interaction.command.roles["role"]!!

        try {
            GuildConfig.setDjRole(guild.id.get(), role.id.get())
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_DJ_ROLE_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "DJ role is now ${role.mention}!"
            }
        }
    }

    private suspend fun handleBaseCommands(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
        subCommandName: String?,
    ) {
        when (subCommandName) {
            "remove" -> handleBaseRemove(event, member, response, guild)
            "youtube" -> handleBaseYouTube(event, member, response, guild)
            "soundcloud" -> handleBaseSoundCloud(member, response, guild)
            "spotify" -> handleBaseSpotify(member, response, guild)
            "volume" -> handleBaseVolume(event, member, response, guild)
            "seek" -> handleBaseSeek(event, member, response, guild)
            "loop" -> handleBaseLoop(member, response, guild)
            "queue" -> handleBaseQueue(response, guild)
            "resume" -> handleBaseResume(member, response, guild)
            "pause" -> handleBasePause(member, response, guild)
            "skip" -> handleBaseSkip(member, response, guild)
            "clear" -> handleBaseClear(member, response, guild)
        }
    }

    private suspend fun handleBaseRemove(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        val index = event.interaction.command.integers["index"]!!.toInt()
        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        if (index < 1 || index > player.scheduler.getQueueSize()) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "There is no track at index `$index`! Please enter a value between 1 and ${player.scheduler.getQueueSize()}."
                }
            }

            return
        }


        val entry = try {
            player.scheduler.removeAt(index + 1)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.REMOVE_MUSIC_TRACK_GUILD, response)
            return
        }

        response.respond {
            if (entry == null) {
                stateEmbed(EmbedState.ERROR) {
                    description = "There is no track at index `$index`!"
                }
            } else {
                stateEmbed(EmbedState.SUCCESS) {
                    description = "Removed track `${entry.track.info.title}` from the queue!"
                }
            }
        }
    }

    private suspend fun handleBaseYouTube(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        val videoIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
        val videoUrlRegex = Regex("^(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})")

        val query = event.interaction.command.strings["query"]!!
        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return

        var input = query
        if (!videoIdRegex.matches(input) && !videoUrlRegex.matches(input)) input = "ytsearch: $input"

        try {
            MusicHandler.playFromYouTube(input, player, response)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.LOAD_AND_PLAY_MUSIC, response)
        }
    }

    private suspend fun handleBaseSoundCloud(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        response.respond {
            embed {
                title = "SoundCloud"
                color = Color(0xFF5500)
                description = "SoundCloud integration is currently in development!"
            }
        }
    }

    private suspend fun handleBaseSpotify(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        response.respond {
            embed {
                title = "Spotify"
                color = Color(0x1DB954)
                description = "Spotify integration is currently in development!"
            }
        }
    }

    private suspend fun handleBaseVolume(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        if (!member.checkPermissions(
            Permissions(Permission.ManageGuild),
            response
        )) return

        val volume = event.interaction.command.integers["volume"]!!.toInt()
        if (volume < 0 || volume > 100) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "Please enter a value between 0 and 100!"
                }
            }

            return
        }

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return

        try {
            player.player.volume = volume
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_VOLUME_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Set the volume to `$volume`!"
            }
        }
    }

    private suspend fun handleBaseSeek(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkDj(guild, response)) return

        val position = event.interaction.command.integers["position"]!!.toLong()
        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        val entry = player.scheduler.current
        if (entry == null) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "There is no track currently playing!"
                }
            }

            return
        }

        val durationInSeconds = player.scheduler.getDuration(TimeUnit.SECONDS)
        if (position < 0 || position > durationInSeconds) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "Please enter a value between 0 and $durationInSeconds!"
                }
            }

            return
        }

        try {
            val adjustedPosition = TimeUnit.MILLISECONDS.convert(position, TimeUnit.SECONDS)
            player.scheduler.seekTo(adjustedPosition)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_VOLUME_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Seeked to `$position`!"
            }
        }
    }

    private suspend fun handleBaseLoop(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        if (!member.checkDj(guild, response)) return

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        val entry = player.scheduler.current
        if (entry == null) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "There is no track currently playing!"
                }
            }

            return
        }

        try {
            player.scheduler.isLooping = !player.scheduler.isLooping
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_MUSIC_VOLUME_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                val track = entry.track
                description = "Now looping [${track.info.title}](${track.info.uri})!"
            }
        }
    }

    private suspend fun handleBaseQueue(
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return

        val queue = player.scheduler.getQueueItems()
        val currentTrack = player.player.playingTrack

        response.respond {
            embed {
                title = "Queue"
                color = Color(0x00FF00)
                description = buildString {
                    if (currentTrack != null) {
                        appendLine("Currently Playing: [${currentTrack.info.title}](${currentTrack.info.uri})")
                        appendLine()
                    } else if (queue.isEmpty()) {
                        append("The queue is empty!")
                        return@buildString
                    }

                    queue.forEachIndexed { index, entry ->
                        val track = entry.track
                        appendLine("${index + 1}. [${track.info.title}](${track.info.uri})")
                    }
                }
            }
        }
    }

    private suspend fun handleBaseResume(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        if (!member.checkDj(guild, response)) return

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.setPaused(false)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Resumed!"
            }
        }
    }

    private suspend fun handleBasePause(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        if (!member.checkDj(guild, response)) return

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.setPaused(true)

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Paused!"
            }
        }
    }

    private suspend fun handleBaseSkip(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        if (!member.checkDj(guild, response)) return

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.skip()

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Skipped!"
            }
        }
    }

    private suspend fun handleBaseClear(
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild,
    ) {
        if (!member.checkDj(guild, response)) return

        val player = MusicHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.clear()

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Cleared!"
            }
        }
    }

}
