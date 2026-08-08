package software.medusa.counter.cli

/** The platform [BrowserOpener]: hands the URL to the OS default handler. */
object SystemBrowserOpener : BrowserOpener {
  override fun open(url: String): Boolean {
    val os = System.getProperty("os.name").lowercase()
    val command =
        when {
          "mac" in os || "darwin" in os -> listOf("open", url)
          "win" in os -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
          else -> listOf("xdg-open", url)
        }
    return runCatching { ProcessBuilder(command).inheritIO().start() }.isSuccess
  }
}
