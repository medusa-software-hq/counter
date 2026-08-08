package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.PrintMessage
import java.nio.file.Path
import software.medusa.counter.cli.api.ApiException
import software.medusa.counter.cli.api.CounterApiClient
import software.medusa.counter.cli.auth.DefaultTokenRefresher
import software.medusa.counter.cli.auth.NotLoggedInException
import software.medusa.counter.cli.auth.Session
import software.medusa.counter.cli.config.ConfigBaseDir
import software.medusa.counter.cli.config.ConfigStore
import software.medusa.counter.cli.config.Environment

private fun counterConfigBase(): Path =
    ConfigBaseDir.resolve(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home"))

internal fun Environment.configStore(): ConfigStore =
    ConfigStore(resolveConfigDirPath(counterConfigBase()))

internal fun Environment.apiClient(): CounterApiClient =
    CounterApiClient(apiEndpoint, Session(configStore(), refresher = DefaultTokenRefresher(this)))

/** Turns the two expected failures into clean, actionable CLI errors. */
internal inline fun <T> runCounter(block: () -> T): T =
    try {
      block()
    } catch (e: NotLoggedInException) {
      throw PrintMessage(e.message ?: "Not signed in.", statusCode = 1, printError = true)
    } catch (e: ApiException) {
      throw PrintMessage(e.message ?: "API error.", statusCode = 1, printError = true)
    }
