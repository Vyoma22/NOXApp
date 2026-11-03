package com.example.noxapp.postlisting

/**
 * Required-field rules for the post listing form.
 *
 * Pure Kotlin on purpose: no Android imports, so the rules can be unit tested on the JVM.
 * The activity maps the returned fields and errors to its inline error views.
 */
object PostListingValidator {

    /** Fields of the post listing form, in the order they appear on screen. */
    enum class Field {
        MEDIA,
        TITLE,
        CATEGORY,
        PRICE,
        CONDITION,
        DESCRIPTION
    }

    /** Why a field failed validation. */
    enum class Error {
        MEDIA_REQUIRED,
        TITLE_REQUIRED,
        CATEGORY_REQUIRED,
        PRICE_INVALID,
        CONDITION_REQUIRED,
        DESCRIPTION_REQUIRED
    }

    /** A snapshot of the values entered in the form. */
    data class Input(
        val hasMedia: Boolean,
        val title: String,
        val priceText: String,
        val description: String,
        val category: String,
        val condition: String
    )

    /**
     * Returns every field that fails validation, in form order.
     * An empty map means the form is valid.
     */
    fun validate(input: Input): Map<Field, Error> {
        val errors = linkedMapOf<Field, Error>()

        if (!input.hasMedia) errors[Field.MEDIA] = Error.MEDIA_REQUIRED
        if (input.title.isBlank()) errors[Field.TITLE] = Error.TITLE_REQUIRED
        if (input.category.isBlank()) errors[Field.CATEGORY] = Error.CATEGORY_REQUIRED

        val price = parsePrice(input.priceText)
        if (price == null || price < 0) errors[Field.PRICE] = Error.PRICE_INVALID

        if (input.condition.isBlank()) errors[Field.CONDITION] = Error.CONDITION_REQUIRED
        if (input.description.isBlank()) errors[Field.DESCRIPTION] = Error.DESCRIPTION_REQUIRED

        return errors
    }

    /**
     * Parses the price field. Accepts a leading "$" and surrounding spaces, returning null
     * when the text is blank or not a number. A zero price is valid; see [validate].
     */
    fun parsePrice(priceText: String): Double? =
        priceText.trim().replace("$", "").toDoubleOrNull()
}
