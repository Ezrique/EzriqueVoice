package dev.deftu.ezrique.voice

import dev.deftu.ezrique.EmbedState
import dev.deftu.ezrique.stateEmbed
import dev.deftu.ezrique.voice.utils.InteractionHandler
import dev.deftu.ezrique.voice.utils.checkGuild
import dev.kord.common.Color
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.flow.count

object BaseInteractionHandler : InteractionHandler {
    override val name = ""

    override fun setupCommands(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("join", "Joins the voice channel you're in.") {
            dmPermission = false

            channel("channel", "The voice channel to join.") {
                channelTypes = listOf(ChannelType.GuildVoice)
            }
        }

        builder.input("leave", "Leaves the voice channel.") {
            dmPermission = false
        }

        builder.input("about", "Shows information about the bot.") {
            dmPermission = false
        }

        builder.input("perf", "Shows the bot's performance.") {
            dmPermission = false
        }
    }

    override suspend fun handleCommand(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?,
        commandName: String,
        subCommandName: String?,
        groupName: String?
    ) {
        when (commandName) {
            "join" -> handleJoin(event, guild)
            "leave" -> handleLeave(event, guild)
            "about" -> handleAbout(event, guild)
            "perf" -> handlePerf(event, guild)
        }
    }

    @OptIn(KordVoice::class)
    private suspend fun handleJoin(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?
    ) {
        val response = event.interaction.deferEphemeralResponse()
        if (!guild.checkGuild(response)) return
        guild!! // Assert non-null

        val member = event.interaction.user.asMember(guild.id)
        var channel = event.interaction.command.channels["channel"]?.asChannelOf<VoiceChannel>()
        if (channel == null || member.permissions?.contains(Permission.ManageChannels) == false) {
            channel = member.getVoiceStateOrNull()?.getChannelOrNull() as? VoiceChannel
            if (channel == null) {
                response.respond {
                    stateEmbed(EmbedState.ERROR) {
                        description = "You have to be in a voice channel to use this command!"
                    }
                }

                return
            }
        }

        if (channel.guildId != guild.id) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "You have to be in a voice channel **in this server** to use this command!"
                }
            }

            return
        }

        val connection = VoiceConnectionManager.getConnection(guild.id)
        if (connection != null) {
            response.respond {
                stateEmbed(EmbedState.ERROR) {
                    description = "I'm already in a voice channel! Use `/leave` to make me leave."
                }
            }

            return
        }

        VoiceConnectionManager.connectTo(event.kord, event.shard, guild.id, channel)
        response.respond {
            stateEmbed(EmbedState.SUCCESS) {
                description = "Joined ${channel.mention}!"
            }
        }
    }

    private suspend fun handleLeave(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?
    ) {
        val response = event.interaction.deferEphemeralResponse()
        if (!guild.checkGuild(response)) return
        guild!! // Assert non-null

        val member = event.interaction.user.asMember(guild.id)
        val channel = member.getVoiceStateOrNull()?.getChannelOrNull() as? VoiceChannel
        var flag = true
        if (channel == null) {
            if (member.permissions?.contains(Permission.ManageChannels) == true) {
                flag = false
            } else {
                response.respond {
                    embed {
                        title = "Not in a Voice Channel"
                        color = Color(0x8C0317)
                        description = "You have to be in a voice channel to use this command!"
                    }
                }

                return
            }
        }

        if (channel?.guildId != guild.id && flag) {
            response.respond {
                embed {
                    title = "Not in a Voice Channel"
                    color = Color(0x8C0317)
                    description = "You have to be in a voice channel **in this server** to use this command!"
                }
            }

            return
        }

        val didLeave = VoiceConnectionManager.leave(event.kord, event.shard, guild.id)
        response.respond {
            if (didLeave) {
                stateEmbed(EmbedState.SUCCESS) {
                    description = "Left ${channel?.mention ?: "the voice channel"}!"
                }
            } else {
                stateEmbed(EmbedState.ERROR) {
                    description = "I'm not in a voice channel!"
                }
            }
        }
    }

    private suspend fun handleAbout(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?
    ) {
        val response = event.interaction.deferEphemeralResponse()
        response.respond {
            embed {
                title = "Ezrique Voice"
                color = Color(0xC33F3F)
                description = "Hello! I'm **Ezrique**, more specifically, **Ezrique Voice**. I was made to help you with your voice channels. I was made by **@deftu**. If I'm not doing what you want, you can find help [in this Discord server](https://discord.gg/AzcAYDUru9)."

                field {
                    name = "Version"
                    value = EzriqueVoice.VERSION
                    inline = true
                }

                field {
                    name = "Guild Count"
                    value = "${event.kord.guilds.count()}"
                    inline = true
                }
            }
        }
    }

    private suspend fun handlePerf(
        event: ChatInputCommandInteractionCreateEvent,
        guild: Guild?
    ) {
        val response = event.interaction.deferEphemeralResponse()

        val used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val max = Runtime.getRuntime().maxMemory()
        val percent = (used.toDouble() / max.toDouble()) * 100

        response.respond {
            embed {
                title = "Ezrique Voice Performance"
                color = Color(0xC33F3F)
                description = "Here's some information about my performance."

                field {
                    name = "Ping"
                    value = event.interaction.kord.gateway.averagePing.toString()
                    inline = true
                }

                field {
                    name = "RAM Usage"
                    value = "${used / 1024 / 1024}MB / ${max / 1024 / 1024}MB (${percent.toInt()}%)"
                }
            }
        }
    }
}
