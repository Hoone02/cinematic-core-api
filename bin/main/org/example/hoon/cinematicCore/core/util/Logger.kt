package org.example.hoon.cinematicCore.core.util

/**
 * 로깅 인터페이스
 * 플랫폼 독립적인 로깅을 위한 코어 인터페이스
 */
interface Logger {
    /**
     * 일반 메시지 출력
     */
    fun info(message: String)
    
    /**
     * 성공 메시지 출력
     */
    fun success(message: String)
    
    /**
     * 경고 메시지 출력
     */
    fun warn(message: String)
    
    /**
     * 오류 메시지 출력
     */
    fun error(message: String)
    
    /**
     * 디버그 메시지 출력
     */
    fun debug(message: String)
    
    /**
     * 참조 메시지 출력
     */
    fun reference(message: String)
}

