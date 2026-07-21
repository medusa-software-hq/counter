package software.medusa.counter.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val configDirName = "ms-counter"
private const val credentialsFileName = "credentials.json"

private val json = Json {
  ignoreUnknownKeys = true
  prettyPrint = true
}

/**
 * Cached sign-in: the long-lived refresh token plus the most recent ID token and its expiry. The
 * refresh token is the sensitive bit — it stands in for the human until revoked — so this is
 * written 0600 (dir 0700), set atomically at creation where the platform supports POSIX
 * permissions.
 */
@Serializable
data class Credentials(
    val refreshToken: String,
    val idToken: String,
    val idTokenExpiresAtEpochSec: Long,
    val email: String,
)

/** `$XDG_CONFIG_HOME/ms-counter/`, falling back to `~/.config/ms-counter/` — also on macOS. */
fun configDir(
    xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
    userHome: String = System.getProperty("user.home"),
): Path {
  val base =
      if (!xdgConfigHome.isNullOrBlank()) Path.of(xdgConfigHome) else Path.of(userHome, ".config")
  return base.resolve(configDirName)
}

fun credentialsFile(dir: Path = configDir()): Path = dir.resolve(credentialsFileName)

fun loadCredentials(dir: Path = configDir()): Credentials? {
  val file = credentialsFile(dir)
  if (!Files.exists(file)) return null
  return json.decodeFromString(Files.readString(file))
}

/** Writes [credentials] with dir 0700 / file 0600, set atomically at creation where supported. */
fun saveCredentials(credentials: Credentials, dir: Path = configDir()) {
  if (!Files.exists(dir)) {
    runCatching {
          Files.createDirectory(
              dir,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
          )
        }
        .getOrElse { Files.createDirectories(dir) }
  }

  val file = credentialsFile(dir)
  Files.deleteIfExists(file)
  runCatching {
        Files.createFile(
            file,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        )
      }
      .getOrElse { Files.createFile(file) }
  Files.writeString(file, json.encodeToString(credentials))
}

fun deleteCredentials(dir: Path = configDir()) {
  Files.deleteIfExists(credentialsFile(dir))
}
