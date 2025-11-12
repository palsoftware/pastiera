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
     * Ottiene la stringa formattata con build number e data.
     * Formato: "build X - DD MMM YYYY"
     */
    fun getBuildInfoString(): String {
        val buildNumber = getBuildNumber()
        val buildDate = getBuildDate()
        return if (buildDate.isNotEmpty()) {
            "build $buildNumber - $buildDate"
        } else {
            "build $buildNumber"
        }
    }
}

