
"use strict";

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

const { initializeApp } = require("firebase-admin/app");
const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

// Initialize Firebase Admin on the trusted backend.
initializeApp();

const db = getFirestore();

/**
 * Automatically updates a seller's average rating and
 * rating count whenever a buyer submits a new rating.
 *
 * Trigger:
 * sellerRatings/{conversationId}
 *
 * Uses a Firestore transaction to prevent:
 * 1. Duplicate rating counts from retried events.
 * 2. Incorrect averages from concurrent ratings.
 * 3. Counting a rating that does not match a completed sale.
 */
exports.aggregateSellerRatingOnCreate = onDocumentCreated(
  {
    document: "sellerRatings/{conversationId}",
    region: "northamerica-northeast2",
    maxInstances: 3
  },
  async (event) => {

    const snapshot = event.data;

    if (!snapshot) {
      logger.warn("Rating event has no document data.");
      return;
    }

    const rating = snapshot.data();
    const conversationId = event.params.conversationId;

    const sellerId = rating.sellerId;
    const buyerId = rating.buyerId;
    const listingId = rating.listingId;
    const stars = rating.stars;

    // Validate the rating before using administrator permissions.
    if (
      typeof sellerId !== "string" ||
      !sellerId ||
      typeof buyerId !== "string" ||
      !buyerId ||
      typeof listingId !== "string" ||
      !listingId ||
      sellerId === buyerId ||
      rating.conversationId !== conversationId ||
      !Number.isInteger(stars) ||
      stars < 1 ||
      stars > 5
    ) {
      logger.error(
        "Invalid rating document. Aggregation skipped.",
        { conversationId }
      );
      return;
    }

    const sellerRef = db.collection("users").doc(sellerId);

    const saleRef = db
      .collection("saleTransactions")
      .doc(conversationId);

    // Each rating receives one processing marker.
    // This prevents event retries from counting it twice.
    const processedRef = db
      .collection("ratingAggregationEvents")
      .doc(conversationId);

    await db.runTransaction(async (transaction) => {

      // All reads must occur before transaction writes.
      const [processedDoc, sellerDoc, saleDoc] =
        await Promise.all([
          transaction.get(processedRef),
          transaction.get(sellerRef),
          transaction.get(saleRef)
        ]);

      // This rating was already included in the average.
      if (processedDoc.exists) {
        logger.info("Rating already counted.", {
          conversationId
        });
        return;
      }

      if (!sellerDoc.exists) {
        throw new Error(
          `Seller profile not found: ${sellerId}`
        );
      }

      if (!saleDoc.exists) {
        throw new Error(
          `Sale transaction not found: ${conversationId}`
        );
      }

      const sale = saleDoc.data();

      // Only ratings from completed, matching purchases count.
      if (
        sale.status !== "completed" ||
        sale.conversationId !== conversationId ||
        sale.sellerId !== sellerId ||
        sale.buyerId !== buyerId ||
        sale.listingId !== listingId
      ) {
        logger.error(
          "Rating does not match a completed purchase.",
          { conversationId }
        );
        return;
      }

      // Read existing seller reputation.
      // Sellers with no ratings start at zero.
      const oldCount = sellerDoc.get("ratingCount") ?? 0;
      const oldAverage = sellerDoc.get("rating") ?? 0;

      if (
        !Number.isInteger(oldCount) ||
        oldCount < 0 ||
        typeof oldAverage !== "number" ||
        !Number.isFinite(oldAverage) ||
        (oldCount > 0 &&
          (oldAverage < 1 || oldAverage > 5))
      ) {
        throw new Error(
          `Invalid existing rating data for seller ${sellerId}`
        );
      }

      // Calculate the weighted running average.
      const newCount = oldCount + 1;

      const newAverage =
        ((oldAverage * oldCount) + stars) / newCount;

      // Update the seller's public reputation summary.
      transaction.update(sellerRef, {
        rating: newAverage,
        ratingCount: newCount
      });

      // Record that this specific rating was processed.
      // Created atomically with the profile update.
      transaction.create(processedRef, {
        conversationId,
        sellerId,
        stars,
        processedAt: FieldValue.serverTimestamp()
      });
    });

    logger.info("Seller rating aggregation finished.", {
      conversationId,
      sellerId
    });
  }
);
