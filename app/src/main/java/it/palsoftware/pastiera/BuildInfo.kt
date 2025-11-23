package it.palsoftware.pastiera

import it.palsoftware.pastiera.BuildConfig

/**
 * Fornisce informazioni sulla build dell'app.
 */
object BuildInfo {
    /**
     * Ottiene il numero di build incrementale.
     */
    fun getBuildNumber(): Int {
        return BuildConfig.BUILD_NUMBER
    }
    
    /**
     * Ottiene la data della build.
     */
    fun getBuildDate(): String {
        return BuildConfig.BUILD_DATE
    }
    
    /**
     * Ottiene la stringa formattata con versione, build number e data.
     * Formato: "Ver. X.X - Build X - DD MMM YYYY"
     */
    fun getBuildInfoString(): String {
        val version = BuildConfig.VERSION_NAME
        val buildNumber = getBuildNumber()
        val buildDate = getBuildDate()
        return when {
            buildDate.isNotEmpty() -> "Ver. $version - Build $buildNumber - $buildDate"
            else -> "Ver. $version - Build $buildNumber"
        }
    }
}

