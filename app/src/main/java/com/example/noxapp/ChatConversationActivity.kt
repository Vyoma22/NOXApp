
package com.example.noxapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.noxapp.adapters.ChatMessage
import com.example.noxapp.adapters.ChatMessageAdapter
import com.example.noxapp.data.ChatRepository
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.data.SaleTransactionRepository
import com.example.noxapp.data.SellerRatingRepository
import com.example.noxapp.models.SaleStatus
import com.example.noxapp.models.SaleTransaction
import com.example.noxapp.ui.SellerRatingDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ChatConversationActivity : AppCompatActivity() {

    private val previewSellerId = "preview-seller"
    private val previewBuyerId = "preview-buyer"

    private val listingId: String
        get() = intent.getStringExtra("listingId").orEmpty()

    private val conversationId: String
        get() = intent.getStringExtra("conversationId").orEmpty()

    private val isPreview: Boolean
        get() = listingId.isBlank() && conversationId.isBlank()

    private val messages = mutableListOf(
        ChatMessage(previewBuyerId, "Hi! Is this still available?"),
        ChatMessage(previewSellerId, "Yes it is!"),
        ChatMessage(
            previewBuyerId,
            "Great! Can we meet on campus tomorrow?"
        ),
        ChatMessage(
            previewSellerId,
            "Sure! I'm free after 2pm near the library."
        ),
        ChatMessage(
            previewBuyerId,
            "Perfect, see you then!"
        )
    )

    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var messagesRecyclerView: RecyclerView

    private var liveMessageListener: ListenerRegistration? = null
    private var saleStatusListener: ListenerRegistration? = null

    private var currentSaleTransaction: SaleTransaction? = null
    private var currentListingSellerId = ""

    // The transaction security rules are now deployed.
    private val enableSaleStatusListener = true
    private val enableLiveSaleActions = true

    // Keep the original button label when switching to Rate Seller.
    private var confirmPurchaseDefaultText: CharSequence =
        "Confirm Purchase"

    // Prevent repeated automatic rating popups.
    private var ratingPromptShownForVisit = false

    // Prevent repeated rating submissions.
    private var ratingSubmissionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat_conversation)

        findViewById<ImageButton>(
            R.id.chatBackButton
        ).setOnClickListener {
            finish()
        }

        setupMessages()
        setupSendButton()
        setupSellerConfirmation()
        setupListingHeader()
    }

    // --------------------------------------------------
    // SELLER AND BUYER PURCHASE CONFIRMATION
    // --------------------------------------------------

    private fun setupSellerConfirmation() {

        val sellerButton =
            findViewById<View>(R.id.confirmSaleButton)

        val sellerCard =
            findViewById<View>(R.id.chatSellerConfirmCard)

        val buyerCard =
            findViewById<View>(R.id.chatBuyerConfirmCard)

        val confirmSale =
            findViewById<View>(R.id.sendSaleConfirmationButton)

        val confirmPurchase =
            findViewById<TextView>(R.id.confirmPurchaseButton)

        val reportIssue =
            findViewById<View>(R.id.reportPurchaseIssueButton)

        confirmPurchaseDefaultText = confirmPurchase.text

        // Keep the original Amazon Cat preview working.
        sellerButton.visibility =
            if (isPreview) View.VISIBLE else View.GONE

        sellerCard.visibility = View.GONE
        buyerCard.visibility = View.GONE

        // SELLER: Open or close confirmation card.
        sellerButton.setOnClickListener {

            if (
                !isPreview &&
                (
                        !enableLiveSaleActions ||
                                FirebaseAuth.getInstance()
                                    .currentUser?.uid != currentListingSellerId
                        )
            ) {
                return@setOnClickListener
            }

            sellerCard.visibility =
                if (sellerCard.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        // SELLER: Confirm sale to a specific buyer.
        confirmSale.setOnClickListener {

            if (isPreview) {

                sellerCard.visibility = View.GONE
                buyerCard.visibility = View.VISIBLE

                Toast.makeText(
                    this,
                    "Preview: Seller confirmed the exchange",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (
                !enableLiveSaleActions ||
                conversationId.isBlank() ||
                FirebaseAuth.getInstance()
                    .currentUser?.uid != currentListingSellerId ||
                currentSaleTransaction != null
            ) {
                return@setOnClickListener
            }

            confirmSale.isEnabled = false

            SaleTransactionRepository.confirmSale(
                conversationId = conversationId,

                onSuccess = {

                    if (!isFinishing && !isDestroyed) {

                        confirmSale.isEnabled = true
                        sellerCard.visibility = View.GONE

                        Toast.makeText(
                            this,
                            "Waiting for buyer confirmation",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },

                onError = { error ->

                    if (!isFinishing && !isDestroyed) {

                        confirmSale.isEnabled = true

                        Toast.makeText(
                            this,
                            error.message ?: "Confirmation failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }

        // BUYER: Confirm purchase OR complete an unfinished rating.
        confirmPurchase.setOnClickListener {

            if (isPreview) {
                openSellerRating(preview = true)
                return@setOnClickListener
            }

            if (
                !enableLiveSaleActions ||
                conversationId.isBlank()
            ) {
                return@setOnClickListener
            }

            val buyerId =
                FirebaseAuth.getInstance()
                    .currentUser?.uid.orEmpty()

            val sale = currentSaleTransaction

            // Verify that this purchase belongs to the buyer
            // and corresponds to the current conversation.
            if (
                sale == null ||
                sale.buyerId != buyerId ||
                sale.listingId != listingId ||
                sale.conversationId != conversationId ||
                sale.sellerId != currentListingSellerId
            ) {

                Toast.makeText(
                    this,
                    "Purchase confirmation is not available",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // NEW:
            // The purchase is already confirmed, but the buyer
            // still needs to submit their mandatory rating.
            if (sale.status == SaleStatus.AWAITING_RATING) {

                openSellerRating(preview = false)

                return@setOnClickListener
            }

            if (!sale.canBuyerConfirm(buyerId)) {

                Toast.makeText(
                    this,
                    "Purchase confirmation is not available",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            confirmPurchase.isEnabled = false

            SaleTransactionRepository.confirmPurchase(
                conversationId = conversationId,

                onSuccess = {

                    if (!isFinishing && !isDestroyed) {

                        confirmPurchase.isEnabled = true

                        // The Firestore listener will notice
                        // AWAITING_RATING and open the rating
                        // popup after the update is confirmed.
                        Toast.makeText(
                            this,
                            "Purchase confirmed. Please rate the seller.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },

                onError = { error ->

                    if (!isFinishing && !isDestroyed) {

                        confirmPurchase.isEnabled = true

                        Toast.makeText(
                            this,
                            error.message ?: "Confirmation failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }

        // BUYER: Report a purchase problem.
        reportIssue.setOnClickListener {

            if (isPreview) {

                Toast.makeText(
                    this,
                    "Preview only: No issue was submitted",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                openPurchaseIssueEmail()
            }
        }
    }

    // --------------------------------------------------
    // REPORT A PURCHASE ISSUE
    // --------------------------------------------------

    private fun openPurchaseIssueEmail() {

        val uid =
            FirebaseAuth.getInstance()
                .currentUser?.uid.orEmpty()

        val sale = currentSaleTransaction

        // Only the buyer involved in an active purchase
        // can use this reporting action.
        if (
            sale == null ||
            sale.buyerId != uid ||
            sale.conversationId != conversationId ||
            sale.listingId != listingId ||
            sale.sellerId != currentListingSellerId ||
            (
                    sale.status != SaleStatus.PENDING_BUYER &&
                            sale.status != SaleStatus.AWAITING_RATING
                    )
        ) {

            Toast.makeText(
                this,
                "Issue reporting is not available for this purchase",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // Use NOX's existing support email.
        val supportEmail =
            getString(R.string.settings_report_email)

        val subject =
            "NOX purchase issue - listing $listingId"

        val body = """
            Hello NOX Support,

            I need help with a purchase.

            Listing ID: $listingId
            Conversation ID: $conversationId
            Buyer ID: $uid
            Seller ID: ${sale.sellerId}
            Purchase status: ${sale.status}

            What happened:
            [Please describe the issue here]
        """.trimIndent()

        val emailIntent =
            Intent(Intent.ACTION_SENDTO).apply {

                data = Uri.parse("mailto:$supportEmail")

                putExtra(
                    Intent.EXTRA_EMAIL,
                    arrayOf(supportEmail)
                )

                putExtra(
                    Intent.EXTRA_SUBJECT,
                    subject
                )

                putExtra(
                    Intent.EXTRA_TEXT,
                    body
                )
            }

        // Opening a draft does not mean the email was sent.
        runCatching {

            startActivity(
                Intent.createChooser(
                    emailIntent,
                    "Report purchase issue"
                )
            )

        }.onSuccess {

            Toast.makeText(
                this,
                "Send the email draft to submit your report",
                Toast.LENGTH_LONG
            ).show()

        }.onFailure {

            Toast.makeText(
                this,
                "No email app available to report this issue",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // --------------------------------------------------
    // CHAT MESSAGES
    // --------------------------------------------------

    private fun setupMessages() {

        messagesRecyclerView =
            findViewById(R.id.chatMessagesRecyclerView)

        messageAdapter = ChatMessageAdapter(
            if (listingId.isBlank()) {
                messages.toList()
            } else {
                emptyList()
            },
            previewSellerId
        )

        messagesRecyclerView.layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

        messagesRecyclerView.adapter = messageAdapter
    }

    // --------------------------------------------------
    // SEND MESSAGE
    // --------------------------------------------------

    private fun setupSendButton() {

        val messageInput =
            findViewById<EditText>(R.id.chatMessageInput)

        val sendButton =
            findViewById<TextView>(R.id.chatSendButton)

        // Real Firebase conversation.
        if (
            conversationId.isNotBlank() &&
            listingId.isNotBlank()
        ) {

            messageInput.isEnabled = true
            sendButton.isEnabled = true
            sendButton.alpha = 1f

            sendButton.setOnClickListener {

                val text =
                    messageInput.text.toString().trim()

                if (text.isEmpty()) {
                    return@setOnClickListener
                }

                // Prevent duplicate sends.
                sendButton.isEnabled = false
                messageInput.isEnabled = false

                ChatRepository.sendMessage(
                    conversationId = conversationId,
                    messageText = text,

                    onSuccess = {

                        if (!isFinishing && !isDestroyed) {

                            messageInput.text.clear()
                            messageInput.isEnabled = true
                            sendButton.isEnabled = true
                        }
                    },

                    onError = { error ->

                        if (!isFinishing && !isDestroyed) {

                            messageInput.isEnabled = true
                            sendButton.isEnabled = true

                            Toast.makeText(
                                this,
                                "Message failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            return
        }

        // Listing preview without a conversation.
        if (listingId.isNotBlank()) {

            messageInput.isEnabled = false
            messageInput.hint =
                "Messaging is not connected yet"

            sendButton.isEnabled = false
            sendButton.alpha = 0.4f

            return
        }

        // Original Amazon Cat preview.
        sendButton.setOnClickListener {

            val text =
                messageInput.text.toString().trim()

            if (text.isNotEmpty()) {

                messages.add(
                    ChatMessage(previewSellerId, text)
                )

                messageAdapter.updateMessages(
                    messages.toList()
                )

                messageInput.text.clear()

                messagesRecyclerView.scrollToPosition(
                    messages.lastIndex
                )
            }
        }
    }

    // --------------------------------------------------
    // PRODUCT INFORMATION
    // --------------------------------------------------

    private fun setupListingHeader() {

        if (listingId.isBlank()) return

        val thumbnail =
            findViewById<ImageView>(R.id.chatItemThumbnail)

        val studentName =
            findViewById<TextView>(R.id.chatPersonName)

        val itemDetails =
            findViewById<TextView>(R.id.chatItemDetails)

        val avatar =
            findViewById<TextView>(R.id.chatAvatar)

        studentName.text = "Loading seller..."
        itemDetails.text = "Loading listing..."
        thumbnail.setImageDrawable(null)

        ListingRepo.getListing(listingId) { listing ->

            if (isFinishing || isDestroyed) {
                return@getListing
            }

            if (listing == null) {

                studentName.text = "Seller unavailable"
                itemDetails.text = "Listing unavailable"
                thumbnail.setImageDrawable(null)

                return@getListing
            }

            val currentUserId =
                FirebaseAuth.getInstance().currentUser?.uid

            // Display the other participant.
            val name =
                if (currentUserId == listing.sellerId) {

                    "Buyer"

                } else {

                    listing.sellerName.ifBlank {
                        "NOX student"
                    }
                }

            studentName.text = name

            currentListingSellerId = listing.sellerId

            renderSaleActions()

            val initials = name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") {
                    it.first().uppercaseChar().toString()
                }

            avatar.text = initials

            itemDetails.text =
                "${listing.title} · \$${listing.price.toInt()}"

            val photoUrl =
                listing.imageUrls.firstOrNull {
                    it.isNotBlank()
                }

            if (photoUrl.isNullOrBlank()) {

                thumbnail.setImageDrawable(null)

            } else {

                thumbnail.load(photoUrl) {
                    crossfade(true)
                    placeholder(R.color.nox_chip_bg)
                    error(R.color.nox_chip_bg)
                }
            }
        }
    }

    // --------------------------------------------------
    // FIRESTORE LISTENERS
    // --------------------------------------------------

    override fun onStart() {
        super.onStart()

        listenForLiveMessages()

        if (
            enableSaleStatusListener &&
            conversationId.isNotBlank() &&
            listingId.isNotBlank()
        ) {

            saleStatusListener =
                SaleTransactionRepository.listenToTransaction(
                    conversationId = conversationId,

                    onUpdate = { transaction ->

                        if (!isFinishing && !isDestroyed) {

                            currentSaleTransaction = transaction

                            renderSaleActions()
                        }
                    },

                    onError = { error ->

                        if (!isFinishing && !isDestroyed) {

                            Toast.makeText(
                                this,
                                "Unable to load sale status: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
        }
    }

    override fun onStop() {

        liveMessageListener?.remove()
        liveMessageListener = null

        saleStatusListener?.remove()
        saleStatusListener = null

        // Allow a new reminder when reopening Chat.
        ratingPromptShownForVisit = false

        super.onStop()
    }

    private fun listenForLiveMessages() {

        if (conversationId.isBlank()) return

        val currentUserId =
            FirebaseAuth.getInstance().currentUser?.uid
                ?: return

        messageAdapter = ChatMessageAdapter(
            emptyList(),
            currentUserId
        )

        messagesRecyclerView.adapter = messageAdapter

        liveMessageListener =
            ChatRepository.listenToMessages(
                conversationId = conversationId,

                onUpdate = { records ->

                    if (isFinishing || isDestroyed) {
                        return@listenToMessages
                    }

                    val chatMessages = records.map {
                        ChatMessage(
                            it.senderId,
                            it.text
                        )
                    }

                    messageAdapter.updateMessages(chatMessages)

                    if (chatMessages.isNotEmpty()) {

                        messagesRecyclerView.scrollToPosition(
                            chatMessages.lastIndex
                        )
                    }
                },

                onError = { error ->

                    if (!isFinishing && !isDestroyed) {

                        Toast.makeText(
                            this,
                            "Unable to load messages: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
    }

    // --------------------------------------------------
    // SHOW CORRECT SALE BUTTONS FOR EACH USER
    // --------------------------------------------------

    private fun renderSaleActions() {

        if (isPreview || listingId.isBlank()) return

        val currentUserId =
            FirebaseAuth.getInstance().currentUser?.uid

        val sellerButton =
            findViewById<View>(R.id.confirmSaleButton)

        val sellerCard =
            findViewById<View>(R.id.chatSellerConfirmCard)

        val buyerCard =
            findViewById<View>(R.id.chatBuyerConfirmCard)

        val confirmPurchase =
            findViewById<TextView>(R.id.confirmPurchaseButton)

        val isSeller =
            currentUserId != null &&
                    currentListingSellerId.isNotBlank() &&
                    currentUserId == currentListingSellerId

        // Accept only the transaction for this conversation.
        val transaction =
            currentSaleTransaction?.takeIf {

                it.listingId == listingId &&
                        it.conversationId == conversationId &&
                        it.sellerId == currentListingSellerId
            }

        val status =
            transaction?.status ?: SaleStatus.ACTIVE

        val canAct =
            enableLiveSaleActions &&
                    conversationId.isNotBlank()

        // Seller sees Confirm Sale only before a sale begins.
        sellerButton.visibility =
            if (
                canAct &&
                isSeller &&
                status == SaleStatus.ACTIVE
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (!isSeller || status != SaleStatus.ACTIVE) {
            sellerCard.visibility = View.GONE
        }

        val isBuyer =
            transaction != null &&
                    currentUserId == transaction.buyerId

        val buyerNeedsAction =
            status == SaleStatus.PENDING_BUYER ||
                    status == SaleStatus.AWAITING_RATING

        // Buyer can confirm the purchase or finish their rating.
        buyerCard.visibility =
            if (
                canAct &&
                isBuyer &&
                buyerNeedsAction
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // NEW: Reuse the existing button for unfinished ratings.
        confirmPurchase.text =
            if (status == SaleStatus.AWAITING_RATING) {

                "Rate Seller"

            } else {

                confirmPurchaseDefaultText
            }

        // NEW: If the buyer returns without rating,
        // automatically remind them once per visit.
        if (
            canAct &&
            isBuyer &&
            status == SaleStatus.AWAITING_RATING &&
            !ratingPromptShownForVisit
        ) {

            ratingPromptShownForVisit = true

            window.decorView.post {

                if (
                    !isFinishing &&
                    !isDestroyed &&
                    currentSaleTransaction?.status ==
                    SaleStatus.AWAITING_RATING
                ) {

                    openSellerRating(preview = false)
                }
            }
        }
    }

    // --------------------------------------------------
    // MANDATORY SELLER RATING
    // --------------------------------------------------

    private fun openSellerRating(preview: Boolean) {

        if (!preview) {

            val uid =
                FirebaseAuth.getInstance()
                    .currentUser?.uid.orEmpty()

            val sale = currentSaleTransaction

            // Only the confirmed buyer may rate the seller.
            if (
                sale == null ||
                sale.buyerId != uid ||
                sale.conversationId != conversationId ||
                sale.listingId != listingId ||
                sale.sellerId != currentListingSellerId ||
                sale.status != SaleStatus.AWAITING_RATING
            ) {

                Toast.makeText(
                    this,
                    "Rating is not available until purchase is confirmed",
                    Toast.LENGTH_LONG
                ).show()

                return
            }
        }

        SellerRatingDialog.show(this) { stars, review, dialog ->

            if (preview) {

                Toast.makeText(
                    this,
                    "Preview: $stars-star rating selected",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()

                findViewById<View>(
                    R.id.chatBuyerConfirmCard
                ).visibility = View.GONE

                return@show
            }

            if (
                !enableLiveSaleActions ||
                ratingSubmissionInProgress
            ) {
                return@show
            }

            ratingSubmissionInProgress = true

            SellerRatingRepository.submitRating(
                conversationId = conversationId,
                stars = stars,
                review = review,

                onSuccess = {

                    ratingSubmissionInProgress = false

                    dialog.dismiss()

                    if (!isFinishing && !isDestroyed) {

                        Toast.makeText(
                            this,
                            "Rating submitted successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },

                onError = { error ->

                    ratingSubmissionInProgress = false

                    if (!isFinishing && !isDestroyed) {

                        Toast.makeText(
                            this,
                            error.message ?: "Unable to save rating",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}
