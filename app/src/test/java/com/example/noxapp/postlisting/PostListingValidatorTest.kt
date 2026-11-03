package com.example.noxapp.postlisting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostListingValidatorTest {

    private fun validInput() = PostListingValidator.Input(
        hasMedia = true,
        title = "Desk chair",
        priceText = "45",
        description = "Solid wood, a few scratches.",
        category = "Furniture",
        condition = "Like New"
    )

    @Test fun blankTitleIsRequired() {
        val errors = PostListingValidator.validate(validInput().copy(title = "   "))
        assertEquals(1, errors.size)
        assertEquals(
            PostListingValidator.Error.TITLE_REQUIRED,
            errors[PostListingValidator.Field.TITLE]
        )
    }

    @Test fun blankDescriptionIsRequired() {
        val errors = PostListingValidator.validate(validInput().copy(description = ""))
        assertEquals(1, errors.size)
        assertEquals(
            PostListingValidator.Error.DESCRIPTION_REQUIRED,
            errors[PostListingValidator.Field.DESCRIPTION]
        )
    }

    @Test fun unparseablePriceIsInvalid() {
        val errors = PostListingValidator.validate(validInput().copy(priceText = "free"))
        assertEquals(1, errors.size)
        assertEquals(
            PostListingValidator.Error.PRICE_INVALID,
            errors[PostListingValidator.Field.PRICE]
        )
    }

    @Test fun blankPriceIsInvalid() {
        val errors = PostListingValidator.validate(validInput().copy(priceText = "  "))
        assertEquals(
            PostListingValidator.Error.PRICE_INVALID,
            errors[PostListingValidator.Field.PRICE]
        )
    }

    @Test fun negativePriceIsInvalid() {
        val errors = PostListingValidator.validate(validInput().copy(priceText = "-5"))
        assertEquals(
            PostListingValidator.Error.PRICE_INVALID,
            errors[PostListingValidator.Field.PRICE]
        )
    }

    @Test fun zeroPriceIsValid() {
        assertTrue(PostListingValidator.validate(validInput().copy(priceText = "0")).isEmpty())
    }

    @Test fun missingMediaIsRequired() {
        val errors = PostListingValidator.validate(validInput().copy(hasMedia = false))
        assertEquals(1, errors.size)
        assertEquals(
            PostListingValidator.Error.MEDIA_REQUIRED,
            errors[PostListingValidator.Field.MEDIA]
        )
    }

    @Test fun validFormHasNoErrors() {
        assertTrue(PostListingValidator.validate(validInput()).isEmpty())
    }

    @Test fun reportsEveryProblemAtOnceInFormOrder() {
        val errors = PostListingValidator.validate(
            PostListingValidator.Input(
                hasMedia = false,
                title = "",
                priceText = "abc",
                description = "",
                category = "",
                condition = ""
            )
        )
        assertEquals(6, errors.size)
        assertEquals(
            listOf(
                PostListingValidator.Field.MEDIA,
                PostListingValidator.Field.TITLE,
                PostListingValidator.Field.CATEGORY,
                PostListingValidator.Field.PRICE,
                PostListingValidator.Field.CONDITION,
                PostListingValidator.Field.DESCRIPTION
            ),
            errors.keys.toList()
        )
    }

    @Test fun priceIgnoresDollarSignAndSurroundingSpaces() {
        assertEquals(12.5, PostListingValidator.parsePrice("  \$12.50 ")!!, 0.0)
        assertNull(PostListingValidator.parsePrice(""))
    }
}
