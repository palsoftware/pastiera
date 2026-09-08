package it.palsoftware.pastiera.update

/** Compare numeric versions and SemVer prereleases; unknown formats never suggest an update. */
internal fun compareReleaseVersions(first: String, second: String): Int? {
    val pattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
    val a = pattern.matchEntire(normalizeReleaseVersion(first)) ?: return null
    val b = pattern.matchEntire(normalizeReleaseVersion(second)) ?: return null
    val av = a.groupValues[1].split('.').map(String::toBigInteger)
    val bv = b.groupValues[1].split('.').map(String::toBigInteger)
    for (i in 0 until maxOf(av.size, bv.size)) {
        val order = (av.getOrNull(i) ?: java.math.BigInteger.ZERO).compareTo(bv.getOrNull(i) ?: java.math.BigInteger.ZERO)
        if (order != 0) return order
    }
    val ap = a.groupValues[2]
    val bp = b.groupValues[2]
    if (ap == bp) return 0
    if (ap.isEmpty()) return 1
    if (bp.isEmpty()) return -1
    val ai = ap.split('.')
    val bi = bp.split('.')
    for (i in 0 until minOf(ai.size, bi.size)) {
        val an = ai[i].toBigIntegerOrNull()
        val bn = bi[i].toBigIntegerOrNull()
        val order = when {
            an != null && bn != null -> an.compareTo(bn)
            an != null -> -1
            bn != null -> 1
            else -> ai[i].compareTo(bi[i])
        }
        if (order != 0) return order
    }
    return ai.size.compareTo(bi.size)
}

internal fun findNewerNightlyRelease(releases: List<GitHubRelease>, current: String): ReleaseInfo? =
    releases.filter { !it.draft && it.prerelease && it.tagName.startsWith("nightly/") &&
        (compareReleaseVersions(it.tagName, current) ?: -1) > 0 }
        .maxWithOrNull { a, b -> compareReleaseVersions(a.tagName, b.tagName) ?: 0 }
        ?.let { ReleaseInfo(it.tagName, it.name ?: it.tagName, it.htmlUrl, it.downloadUrl) }
