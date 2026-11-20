package com.example.noxapp.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.noxapp.R
import com.example.noxapp.chat.adapters.ChatMessage
import com.example.noxapp.chat.adapters.ChatMessageAdapter
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.listing.ListingRepo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlin.math.max

class ChatConversationActivity : AppCompatActivity() {

    private val previewSellerId = "preview-seller"
    private val previewBuyerId = "preview-buyer"

    private val listingId: String
        get() = intent.getStringExtra("listingId").orEmpty()

    private val conversationId: String
        get() = intent.getStringExtra("conversationId").orEmpty()

    private val isPreview: Boolean
        get() = listingId.isBlank() && conversationId.isBlank()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val messages = mutableListOf(
        ChatMessage(previewBuyerId, "Hi! Is this still available?"),
        ChatMessage(previewSellerId, "Yes it is!"),
        ChatMessage(previewBuyerId, "Great! Can we meet on campus tomorrow?"),
        ChatMessage(previewSellerId, "Sure! I'm free after 2pm near the library."),
        ChatMessage(previewBuyerId, "Perfect, see you then!")
    )

    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var messagesRecyclerView: RecyclerView

    private var liveMessageListener: ListenerRegistration? = null
    private var saleStatusListener: ListenerRegistration? = null
    private var accessJob: Job? = null

    private var currentSaleTransaction: SaleTransaction? = null
    private var currentListingSellerId = ""
    private var confirmPurchaseDefaultText: CharSequence = "Confirm Purchase"
    private var ratingPromptShownForVisit = false
    private var ratingSubmissionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_conversation)
        setupWindowInsets()

        findViewById<ImageButton>(R.id.chatBackButton).setOnClickListener { finish() }

        setupMessages()
        setupSendButton()
        setupSellerConfirmation()
        setupListingHeader()
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.main)
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomInset = max(systemBars.bottom, imeBottom)
            view.updatePadding(
                left = systemBars.left,
                top = baseTop + systemBars.top,
                right = systemBars.right,
                bottom = baseBottom + bottomInset
            )
            if (imeBottom > 0 && ::messageAdapter.isInitialized && messageAdapter.itemCount > 0) {
                messagesRecyclerView.post {
                    messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                }
            }
            insets
        }
    }

    // --------------------------------------------------
    // SELLER AND BUYER PURCHASE CONFIRMATION
    // --------------------------------------------------

    private fun setupSellerConfirmation() {
        val sellerButton = findViewById<View>(R.id.confirmSaleButton)
        val sellerCard = findViewById<View>(R.id.chatSellerConfirmCard)
        val buyerCard = findViewById<View>(R.id.chatBuyerConfirmCard)
        val confirmSale = findViewById<View>(R.id.sendSaleConfirmationButton)
        val confirmPurchase = findViewById<TextView>(R.id.confirmPurchaseButton)
        val reportIssue = findViewById<View>(R.id.reportPurchaseIssueButton)

        confirmPurchaseDefaultText = confirmPurchase.text
        sellerButton.isVisible = isPreview
        sellerCard.isVisible = false
        buyerCard.isVisible = false

        sellerButton.setOnClickListener {
            if (!isPreview && currentUserId != currentListingSellerId) return@setOnClickListener
            sellerCard.isVisible = !sellerCard.isVisible
        }

        confirmSale.setOnClickListener {
            if (isPreview) {
                sellerCard.isVisible = false
                buyerCard.isVisible = true
                Toast.makeText(this, "Preview: Seller confirmed the exchange", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (conversationId.isBlank() || currentUserId != currentListingSellerId || currentSaleTransaction != null) {
                return@setOnClickListener
            }

            confirmSale.isEnabled = false
            SaleTransactionRepository.confirmSale(
                conversationId = conversationId,
                onSuccess = {
                    if (!isFinishing && !isDestroyed) {
                        confirmSale.isEnabled = true
                        sellerCard.isVisible = false
                        Toast.makeText(this, "Waiting for buyer confirmation", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    if (!isFinishing && !isDestroyed) {
                        confirmSale.isEnabled = true
                        Toast.makeText(this, error.message ?: "Confirmation failed", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        confirmPurchase.setOnClickListener {
            if (isPreview) {
                openSellerRating(preview = true)
                return@setOnClickListener
            }
            if (conversationId.isBlank()) return@setOnClickListener

            val buyerId = currentUserId
            val sale = validBuyerSale(buyerId)
            if (sale == null) {
                Toast.makeText(this, "Purchase confirmation is not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sale.status == SaleStatus.AWAITING_RATING) {
                openSellerRating(preview = false)
                return@setOnClickListener
            }

            if (!sale.canBuyerConfirm(buyerId)) {
                Toast.makeText(this, "Purchase confirmation is not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            confirmPurchase.isEnabled = false
            SaleTransactionRepository.confirmPurchase(
                conversationId = conversationId,
                onSuccess = {
                    if (!isFinishing && !isDestroyed) {
                        confirmPurchase.isEnabled = true
                        Toast.makeText(this, "Purchase confirmed. Please rate the seller.", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    if (!isFinishing && !isDestroyed) {
                        confirmPurchase.isEnabled = true
                        Toast.makeText(this, error.message ?: "Confirmation failed", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        reportIssue.setOnClickListener {
            if (isPreview) {
                Toast.makeText(this, "Preview only: No issue was submitted", Toast.LENGTH_SHORT).show()
            } else {
                openPurchaseIssueEmail()
            }
        }
    }

    private fun validBuyerSale(uid: String): SaleTransaction? =
        currentSaleTransaction?.takeIf {
            uid.isNotBlank() &&
                    it.buyerId == uid &&
                    it.listingId == listingId &&
                    it.conversationId == conversationId &&
                    it.sellerId == currentListingSellerId
        }

    // --------------------------------------------------
    // REPORT A PURCHASE ISSUE
    // --------------------------------------------------

    private fun openPurchaseIssueEmail() {
        val uid = currentUserId
        val sale = validBuyerSale(uid)
        if (sale == null || (sale.status != SaleStatus.PENDING_BUYER && sale.status != SaleStatus.AWAITING_RATING)) {
            Toast.makeText(this, "Issue reporting is not available for this purchase", Toast.LENGTH_LONG).show()
            return
        }

        val supportEmail = getString(R.string.settings_report_email)
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

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$supportEmail")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
            putExtra(Intent.EXTRA_SUBJECT, "NOX purchase issue - listing $listingId")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        runCatching {
            startActivity(Intent.createChooser(emailIntent, "Report purchase issue"))
        }.onSuccess {
            Toast.makeText(this, "Send the email draft to submit your report", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "No email app available to report this issue", Toast.LENGTH_LONG).show()
        }
    }

    // --------------------------------------------------
    // CHAT MESSAGES
    // --------------------------------------------------

    private fun setupMessages() {
        messagesRecyclerView = findViewById(R.id.chatMessagesRecyclerView)
        messageAdapter = ChatMessageAdapter(
            if (listingId.isBlank()) messages.toList() else emptyList(),
            previewSellerId
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun setupSendButton() {
        val messageInput = findViewById<EditText>(R.id.chatMessageInput)
        val sendButton = findViewById<TextView>(R.id.chatSendButton)

        if (conversationId.isNotBlank() && listingId.isNotBlank()) {
            messageInput.isEnabled = true
            sendButton.isEnabled = true
            sendButton.alpha = 1f

            sendButton.setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener

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
                            Toast.makeText(this, "Message failed: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            return
        }

        if (listingId.isNotBlank()) {
            messageInput.isEnabled = false
            messageInput.hint = "Messaging is not connected yet"
            sendButton.isEnabled = false
            sendButton.alpha = 0.4f
            return
        }

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                messages.add(ChatMessage(previewSellerId, text))
                messageAdapter.updateMessages(messages.toList())
                messageInput.text.clear()
                messagesRecyclerView.scrollToPosition(messages.lastIndex)
            }
        }
    }

    // --------------------------------------------------
    // PRODUCT INFORMATION
    // --------------------------------------------------

    private fun setupListingHeader() {
        if (listingId.isBlank()) return

        val thumbnail = findViewById<ImageView>(R.id.chatItemThumbnail)
        val studentName = findViewById<TextView>(R.id.chatPersonName)
        val itemDetails = findViewById<TextView>(R.id.chatItemDetails)
        val avatar = findViewById<TextView>(R.id.chatAvatar)

        studentName.text = "Loading seller..."
        itemDetails.text = "Loading listing..."
        thumbnail.setImageDrawable(null)

        ListingRepo.getListing(listingId) { listing ->
            if (isFinishing || isDestroyed) return@getListing
            if (listing == null) {
                studentName.text = "Seller unavailable"
                itemDetails.text = "Listing unavailable"
                thumbnail.setImageDrawable(null)
                return@getListing
            }

            val name = if (currentUserId == listing.sellerId) {
                "Buyer"
            } else {
                listing.sellerName.ifBlank { "NOX student" }
            }

            studentName.text = name
            currentListingSellerId = listing.sellerId
            renderSaleActions()

            avatar.text = name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }

            itemDetails.text = "${listing.title} · \$${listing.price.toInt()}"

            val photoUrl = listing.imageUrls.firstOrNull { it.isNotBlank() }
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
        accessJob?.cancel()
        accessJob = checkAccessOnStart {
            listenForLiveMessages()
            listenForSaleStatus()
        }
    }

    private fun listenForSaleStatus() {
        if (conversationId.isBlank() || listingId.isBlank()) return

        saleStatusListener = SaleTransactionRepository.listenToTransaction(
            conversationId = conversationId,
            onUpdate = { transaction ->
                if (!isFinishing && !isDestroyed) {
                    currentSaleTransaction = transaction
                    renderSaleActions()
                }
            },
            onError = { error ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Unable to load sale status: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onStop() {
        accessJob?.cancel()
        liveMessageListener?.remove()
        liveMessageListener = null
        saleStatusListener?.remove()
        saleStatusListener = null
        ratingPromptShownForVisit = false
        super.onStop()
    }

    private fun listenForLiveMessages() {
        if (conversationId.isBlank()) return
        val uid = currentUserId.ifBlank { return }

        messageAdapter = ChatMessageAdapter(emptyList(), uid)
        messagesRecyclerView.adapter = messageAdapter

        liveMessageListener = ChatRepository.listenToMessages(
            conversationId = conversationId,
            onUpdate = { records ->
                if (isFinishing || isDestroyed) return@listenToMessages
                val chatMessages = records.map { ChatMessage(it.senderId, it.text) }
                messageAdapter.updateMessages(chatMessages)
                if (chatMessages.isNotEmpty()) {
                    messagesRecyclerView.scrollToPosition(chatMessages.lastIndex)
                }
            },
            onError = { error ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Unable to load messages: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // --------------------------------------------------
    // SHOW CORRECT SALE BUTTONS FOR EACH USER
    // --------------------------------------------------

    private fun renderSaleActions() {
        if (isPreview || listingId.isBlank()) return

        val uid = currentUserId
        val sellerButton = findViewById<View>(R.id.confirmSaleButton)
        val sellerCard = findViewById<View>(R.id.chatSellerConfirmCard)
        val buyerCard = findViewById<View>(R.id.chatBuyerConfirmCard)
        val confirmPurchase = findViewById<TextView>(R.id.confirmPurchaseButton)

        val isSeller = uid.isNotBlank() && currentListingSellerId.isNotBlank() && uid == currentListingSellerId
        val transaction = currentSaleTransaction?.takeIf {
            it.listingId == listingId &&
                    it.conversationId == conversationId &&
                    it.sellerId == currentListingSellerId
        }
        val status = transaction?.status ?: SaleStatus.ACTIVE
        val canAct = conversationId.isNotBlank()

        sellerButton.isVisible = canAct && isSeller && status == SaleStatus.ACTIVE
        if (!isSeller || status != SaleStatus.ACTIVE) {
            sellerCard.isVisible = false
        }

        val isBuyer = transaction != null && uid == transaction.buyerId
        val buyerNeedsAction = status == SaleStatus.PENDING_BUYER || status == SaleStatus.AWAITING_RATING
        buyerCard.isVisible = canAct && isBuyer && buyerNeedsAction

        confirmPurchase.text = if (status == SaleStatus.AWAITING_RATING) "Rate Seller" else confirmPurchaseDefaultText

        if (canAct && isBuyer && status == SaleStatus.AWAITING_RATING && !ratingPromptShownForVisit) {
            ratingPromptShownForVisit = true
            window.decorView.post {
                if (!isFinishing && !isDestroyed && currentSaleTransaction?.status == SaleStatus.AWAITING_RATING) {
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
            val sale = validBuyerSale(currentUserId)
            if (sale == null || sale.status != SaleStatus.AWAITING_RATING) {
                Toast.makeText(this, "Rating is not available until purchase is confirmed", Toast.LENGTH_LONG).show()
                return
            }
        }

        SellerRatingDialog.show(this) { stars, review, dialog ->
            if (preview) {
                Toast.makeText(this, "Preview: $stars-star rating selected", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                findViewById<View>(R.id.chatBuyerConfirmCard).isVisible = false
                return@show
            }

            if (ratingSubmissionInProgress) return@show
            ratingSubmissionInProgress = true

            SellerRatingRepository.submitRating(
                conversationId = conversationId,
                stars = stars,
                review = review,
                onSuccess = {
                    ratingSubmissionInProgress = false
                    dialog.dismiss()
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Rating submitted successfully!", Toast.LENGTH_LONG).show()
                    }
                },
                onError = { error ->
                    ratingSubmissionInProgress = false
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, error.message ?: "Unable to save rating", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}