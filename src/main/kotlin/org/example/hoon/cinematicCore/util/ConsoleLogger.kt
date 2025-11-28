package org.example.hoon.cinematicCore.util

import org.bukkit.Bukkit
import org.bukkit.ChatColor

object ConsoleLogger {
    private val console = Bukkit.getServer().consoleSender
    
    /**
     * 일반 메시지 출력 (흰색)
     */
    fun info(message: String) {
        console.sendMessage(message)
    }
    
    /**
     * 성공 메시지 출력 (녹색)
     */
    fun success(message: String) {
        console.sendMessage("${ChatColor.GREEN}$message${ChatColor.RESET}")
    }
    
    /**
     * 경고 메시지 출력 (노란색)
     */
    fun warn(message: String) {
        console.sendMessage("${ChatColor.YELLOW}$message${ChatColor.RESET}")
    }
    
    /**
     * 오류 메시지 출력 (빨간색)
     */
    fun error(message: String) {
        console.sendMessage("${ChatColor.RED}$message${ChatColor.RESET}")
    }
    
    /**
     * 디버그 메시지 출력 (회색)
     */
    fun debug(message: String) {
        console.sendMessage("${ChatColor.GRAY}$message${ChatColor.RESET}")
    }

    fun reference(message: String) {
        console.sendMessage("${ChatColor.DARK_GRAY}$message${ChatColor.RESET}")
    }
}

