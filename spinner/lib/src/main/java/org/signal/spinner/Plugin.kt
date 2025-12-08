package org.wave.spinner

interface Plugin {
  fun get(parameters: Map<String, List<String>>): PluginResult
  val name: String
  val path: String
}
