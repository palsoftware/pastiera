package it.palsoftware.pastiera.inputmethod

/**
 * Trie (Prefix Tree) data structure for efficient word prediction.
 * Optimized for fast prefix matching and frequency-based ranking.
 */
class Trie {
    private val root = TrieNode()

    /**
     * Node in the trie structure.
     * Each node represents a character and can be a word ending.
     */
    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isEndOfWord = false
        var frequency = 0 // Higher frequency = more common word
        var word: String? = null // Store complete word at end nodes for easy retrieval
    }

    /**
     * Insert a word into the trie with an optional frequency.
     * @param word The word to insert (will be converted to lowercase)
     * @param frequency Word frequency (0-255, higher = more common)
     */
    fun insert(word: String, frequency: Int = 100) {
        if (word.isBlank()) return

        val lowercaseWord = word.lowercase().trim()
        var currentNode = root

        for (char in lowercaseWord) {
            currentNode = currentNode.children.getOrPut(char) { TrieNode() }
        }

        currentNode.isEndOfWord = true
        currentNode.frequency = frequency
        currentNode.word = lowercaseWord
    }

    /**
     * Search for a specific word in the trie.
     * @param word The word to search for
     * @return true if the word exists in the trie
     */
    fun search(word: String): Boolean {
        if (word.isBlank()) return false

        val node = findNode(word.lowercase().trim())
        return node?.isEndOfWord == true
    }

    /**
     * Find words with the given prefix, sorted by frequency.
     * @param prefix The prefix to search for
     * @param maxResults Maximum number of results to return (default: 3)
     * @return List of words matching the prefix, sorted by frequency (descending)
     */
    fun findWordsWithPrefix(prefix: String, maxResults: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()

        val lowercasePrefix = prefix.lowercase().trim()
        val prefixNode = findNode(lowercasePrefix)
            ?: return emptyList()

        val results = mutableListOf<Pair<String, Int>>() // Pair of (word, frequency)
        collectWords(prefixNode, results)

        // Sort by frequency (descending), then alphabetically
        return results
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first })
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Find the node corresponding to a given prefix.
     * @param prefix The prefix to search for
     * @return The TrieNode at the end of the prefix, or null if not found
     */
    private fun findNode(prefix: String): TrieNode? {
        var currentNode = root

        for (char in prefix) {
            currentNode = currentNode.children[char] ?: return null
        }

        return currentNode
    }

    /**
     * Collect all words from a given node using depth-first search.
     * @param node The node to start collecting from
     * @param results Mutable list to store results as (word, frequency) pairs
     * @param maxWords Maximum words to collect (prevents excessive memory usage)
     */
    private fun collectWords(
        node: TrieNode,
        results: MutableList<Pair<String, Int>>,
        maxWords: Int = 100 // Limit to prevent excessive memory usage
    ) {
        if (results.size >= maxWords) return

        // If this node is the end of a word, add it to results
        if (node.isEndOfWord && node.word != null) {
            results.add(Pair(node.word!!, node.frequency))
        }

        // Recursively collect words from all children
        for (child in node.children.values) {
            if (results.size >= maxWords) break
            collectWords(child, results, maxWords)
        }
    }

    /**
     * Get the size (number of words) in the trie.
     * This is an approximate count and may take time for large tries.
     */
    fun size(): Int {
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(root, results, maxWords = Int.MAX_VALUE)
        return results.size
    }

    /**
     * Clear all data from the trie.
     */
    fun clear() {
        root.children.clear()
    }
}
