package me.alexflesher.xelaTag

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class XelaTabCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (command.name.equals("xt", ignoreCase = true)) {
            if (args.size == 1) {
                val options = listOf("info", "runner", "hunter", "start", "stop", "reload")
                return options.sortedBy { if (it == "info") 0 else 1 }
                    .filter { it.startsWith(args[0].lowercase()) }
            } else if (args.size == 2 && args[0].equals("start", ignoreCase = true)) {
                // Suggest 'seconds' as the second argument
                return listOf("seconds")
            }
        }
        return emptyList()
    }
}