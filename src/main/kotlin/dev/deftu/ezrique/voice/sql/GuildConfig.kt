package dev.deftu.ezrique.voice.sql

import dev.deftu.ezrique.handleError
import dev.deftu.ezrique.voice.VoiceErrorCode
import dev.deftu.ezrique.voice.onboarding.OnboardingStep
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object GuildConfigTable : LongIdTable("guild_config") {

    val onboardingStep = enumeration("onboarding_step", OnboardingStep::class).default(OnboardingStep.SETUP_FEATURES)
    val music = bool("music").default(true)
    val tts = bool("tts").default(true)
    val djOnly = bool("dj_only").default(false)
    val djRole = long("dj_role").nullable()

}

class GuildConfig(
    id: EntityID<Long>
) : LongEntity(id) {

    companion object : LongEntityClass<GuildConfig>(GuildConfigTable) {

        suspend fun  getOnboardingStep(guildId: Long): OnboardingStep {
            return newSuspendedTransaction {
                findById(guildId)?.onboardingStep ?: OnboardingStep.SETUP_FEATURES
            }
        }

        suspend fun setOnboardingStep(guildId: Long, step: OnboardingStep) {
            newSuspendedTransaction {
                val config = findById(guildId) ?: new(guildId) {}
                config.onboardingStep = step
            }
        }

        suspend fun isMusicEnabled(guildId: Long): Boolean {
            return newSuspendedTransaction {
                findById(guildId)?.music ?: true
            }
        }

        suspend fun setMusicEnabled(guildId: Long, enabled: Boolean): Boolean {
            return newSuspendedTransaction {
                val config = findById(guildId) ?: new(guildId) {}
                config.music = enabled

                config.music
            }
        }

        suspend fun isTtsEnabled(guildId: Long): Boolean {
            return newSuspendedTransaction {
                findById(guildId)?.tts ?: true
            }
        }

        suspend fun setTtsEnabled(guildId: Long, enabled: Boolean): Boolean {
            return newSuspendedTransaction {
                val config = findById(guildId) ?: new(guildId) {}
                config.tts = enabled

                config.tts
            }
        }

        suspend fun isDjOnly(guildId: Long): Boolean {
            return newSuspendedTransaction {
                findById(guildId)?.djOnly ?: false
            }
        }

        suspend fun setDjOnly(guildId: Long, djOnly: Boolean): Boolean {
            return newSuspendedTransaction {
                val config = findById(guildId) ?: new(guildId) {}
                config.djOnly = djOnly

                config.djOnly
            }
        }

        suspend fun getDjRole(guildId: Long): Long? {
            return newSuspendedTransaction {
                findById(guildId)?.djRole
            }
        }

        suspend fun setDjRole(guildId: Long, djRole: Long?) {
            newSuspendedTransaction {
                val config = findById(guildId) ?: new(guildId) {}
                config.djRole = djRole
            }
        }

    }

    var onboardingStep by GuildConfigTable.onboardingStep
    var music by GuildConfigTable.music
    var tts by GuildConfigTable.tts
    var djOnly by GuildConfigTable.djOnly
    var djRole by GuildConfigTable.djRole

}
