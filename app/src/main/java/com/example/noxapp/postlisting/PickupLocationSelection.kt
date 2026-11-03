package com.example.noxapp.postlisting

/**
 * Maps a stored pickup location onto the post form's location spinner.
 *
 * The spinner lists every campus building followed by one trailing custom/pinned entry. Anything
 * that does not match a building exactly (case-insensitive) is treated as a custom location.
 * Pure Kotlin on purpose: no Android imports, so the edit-mode mapping is unit tested on the JVM.
 */
object PickupLocationSelection {

    /**
     * Spinner position for [storedLocation]: the matching building index, or the trailing custom
     * entry when the label is blank or is not one of the [buildings].
     */
    fun selectionFor(storedLocation: String?, buildings: List<String>): Int {
        val stored = storedLocation?.trim().orEmpty()
        val index = buildings.indexOfFirst { it.equals(stored, ignoreCase = true) }
        return if (stored.isNotEmpty() && index >= 0) index else buildings.size
    }

    /** True when [position] is the trailing custom/pinned entry rather than a building. */
    fun isCustom(position: Int, customIndex: Int): Boolean = position >= customIndex
}
