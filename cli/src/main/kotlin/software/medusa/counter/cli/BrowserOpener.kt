package software.medusa.counter.cli

/** Opens a URL in the user's browser; returns false if no opener could be launched. */
interface BrowserOpener {
  fun open(url: String): Boolean
}
