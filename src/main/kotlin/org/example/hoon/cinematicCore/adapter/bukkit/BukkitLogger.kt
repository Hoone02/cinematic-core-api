package org.example.hoon.cinematicCore.adapter.bukkit

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.example.hoon.cinematicCore.core.util.Logger

/**
 * Bukkit 로거 구현
 */
class BukkitLogger : Logger {
    private val console = Bukkit.getServer().consoleSender
    
    override fun info(message: String) {
        console.sendMessage(message)
    }
    
    override fun success(message: String) {
        console.sendMessage("${ChatColor.GREEN}$message${ChatColor.RESET}")
    }
    
    override fun warn(message: String) {
        console.sendMessage("${ChatColor.YELLOW}$message${ChatColor.RESET}")
    }
    
    override fun error(message: String) {
        console.sendMessage("${ChatColor.RED}$message${ChatColor.RESET}")
    }
    
    override fun debug(message: String) {
        console.sendMessage("${ChatColor.GRAY}$message${ChatColor.RESET}")
    }
    
    override fun reference(message: String) {
        console.sendMessage("${ChatColor.DARK_GRAY}$message${ChatColor.RESET}")
    }
}

