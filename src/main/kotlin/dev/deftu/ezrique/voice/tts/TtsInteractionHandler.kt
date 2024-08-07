package dev.deftu.ezrique.voice.tts

import dev.deftu.ezrique.*
import dev.deftu.ezrique.voice.VoiceErrorCode
import dev.deftu.ezrique.voice.sql.ChannelLink
import dev.deftu.ezrique.voice.sql.GuildConfig
import dev.deftu.ezrique.voice.sql.MemberConfig
import dev.deftu.ezrique.voice.utils.*
import dev.kord.common.Color
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredEphemeralMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.embed

object TtsInteractionHandler : InteractionHandler {

    override val name = "tts"

    override fun setupCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(name, "Handles all TTS features.") {
            dmPermission = false

            group("config", "Handles all TTS configuration.") {
                subCommand("toggle", "Toggles TTS in your server.") {
                    boolean("value", "The new value of your server's toggle.")
                }

                subCommand("link", "Links a text channel to a voice channel.") {
                    channel("text_channel", "The text channel to link.") {
                        channelTypes = listOf(ChannelType.GuildText)
                    }

                    channel("voice_channel", "The voice channel to link.") {
                        channelTypes = listOf(ChannelType.GuildVoice)
                    }
                }

                subCommand("unlink", "Unlinks a text channel from a voice channel.") {
                    channel("text_channel", "The text channel to unlink.") {
                        required = true
                        channelTypes = listOf(ChannelType.GuildText)
                    }
                }
            }

            subCommand("toggle", "Toggles whether the bot will read out your messages.") {
                boolean("value", "The new toggle value.")
            }

            subCommand("voice", "Sets the voice to use for TTS.") {
                string("voice_batch_1", "The voice to use. (EITHER PICK 1 OR 2)") {
                    for (value in Voice.voicesBatch1) {
                        choice(value.desc, value.code)
                    }
                }

                string("voice_batch_2", "The voice to use. (EITHER PICK 1 OR 2)") {
                    for (value in Voice.voicesBatch2) {
                        choice(value.desc, value.code)
                    }
                }
            }

            subCommand("stop", "Stops the bot from speaking. (CLEARS THE QUEUE)")
            subCommand("skip", "Skips the current message.")
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
            "link" -> handleConfigLink(event, member, response, guild)
            "unlink" -> handleConfigUnlink(event, member, response)
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

        val newValue = event.interaction.command.booleans["value"] ?: !GuildConfig.isTtsEnabled(guild.id.get())
        val currentValue = try {
            GuildConfig.setTtsEnabled(guild.id.get(), newValue)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_TTS_TOGGLE_GUILD, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "TTS is now ${if (currentValue) "enabled" else "disabled"} in your server!"
            }
        }
    }

    private suspend fun handleConfigLink(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        if (!member.checkPermissions(
                Permissions(Permission.ManageChannels),
                response
        )) return

        val providedTextChannel = event.interaction.command.channels["text_channel"]?.asChannel()
        val providedVoiceChannel = event.interaction.command.channels["voice_channel"]?.asChannel()
        if (providedTextChannel == null || providedVoiceChannel == null) {
            val links = ChannelLink.getLinks(guild.id.get())

            response.respond {
                stateEmbed(EmbedState.SUCCESS) {
                    title = "Linked Channels"
                    description = buildString {
                        if (links.isEmpty()) {
                            append("No linked channels!")
                            return@buildString
                        }

                        links.forEach { link ->
                            val textChannel = guild.getChannelOrNull(Snowflake(link.textChannelId)) ?: return@forEach
                            val voiceChannel = guild.getChannelOrNull(Snowflake(link.voiceChannelId)) ?: return@forEach
                            appendLine("${textChannel.mention} ➡️ ${voiceChannel.mention}")
                        }
                    }
                }
            }

            return
        }

        try {
            ChannelLink.createLink(guild.id.get(), providedTextChannel.id.get(), providedVoiceChannel.id.get())
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_TTS_LINK, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Linked ${providedTextChannel.mention} to ${providedVoiceChannel.mention}!"
            }
        }
    }

    private suspend fun handleConfigUnlink(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior
    ) {
        if (!member.checkPermissions(
                Permissions(Permission.ManageChannels),
                response
        )) return

        val textChannel = event.interaction.command.channels["text_channel"]!!.asChannel()

        try {
            ChannelLink.removeLink(textChannel.id.get())
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_TTS_UNLINK, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Unlinked ${textChannel.mention}!"
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
            "toggle" -> handleBaseToggle(event, member, response, guild)
            "voice" -> handleBaseVoice(event, member, response)
            "stop" -> handleBaseStop(response, guild)
            "skip" -> handleBaseSkip(response, guild)
        }
    }

    private suspend fun handleBaseToggle(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        val newValue = event.interaction.command.booleans["value"] ?: !MemberConfig.isTtsEnabled(guild.id.get())
        val currentValue = try {
            MemberConfig.setTtsEnabled(member.id.get(), newValue)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_TTS_TOGGLE, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "TTS is now ${if (currentValue) "enabled" else "disabled"}!"
            }
        }
    }

    private suspend fun handleBaseVoice(
        event: ChatInputCommandInteractionCreateEvent,
        member: Member,
        response: DeferredEphemeralMessageInteractionResponseBehavior
    ) {
        val voiceCode1 = event.interaction.command.strings["voice_batch_1"]
        val voiceCode2 = event.interaction.command.strings["voice_batch_2"]
        if (voiceCode1 == null && voiceCode2 == null) {
            val currentVoice = MemberConfig.getVoice(member.id.get())
            response.respond {
                stateEmbed(EmbedState.SUCCESS) {
                    description = "Your current voice is ${currentVoice.desc}!"
                }
            }

            return
        } else if (voiceCode1 != null && voiceCode2 != null) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "You can only pick one voice!"
                }
            }

            return
        }

        val voice = Voice.fromCode(voiceCode1 ?: voiceCode2!!)

        try {
            MemberConfig.setVoice(member.id.get(), voice)
        } catch (t: Throwable) {
            handleError(t, VoiceErrorCode.SET_TTS_VOICE, response)
            return
        }

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Set your voice to ${voice.desc}!"
            }
        }
    }

    private suspend fun handleBaseStop(
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        val player = TtsHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.clear()

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Stopped speaking!"
            }
        }
    }

    private suspend fun handleBaseSkip(
        response: DeferredEphemeralMessageInteractionResponseBehavior,
        guild: Guild
    ) {
        val player = TtsHandler.getPlayer(guild.id.get(), response) ?: return
        player.scheduler.skip()

        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Skipped the current message!"
            }
        }
    }

}
