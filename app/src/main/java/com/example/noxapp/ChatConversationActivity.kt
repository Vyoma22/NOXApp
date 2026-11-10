
package com.example.noxapp

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
import com.example.noxapp.data.ListingRepo

class ChatConversationActivity : AppCompatActivity() {

    // Temporary user IDs for our UI preview.
    private val previewSellerId = "preview-seller"
    private val previewBuyerId = "preview-buyer"

    // A real listing ID will be provided when
    // conversations are connected to listings.
    private val listingId: String
        get() = intent.getStringExtra("listingId").orEmpty()

    // Sample conversation from our finalized NOX design.
    private val messages = mutableListOf(
        ChatMessage(
            previewBuyerId,
            "Hi! Is this still available?"
        ),
        ChatMessage(
            previewSellerId,
            "Yes it is!"
        ),
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat_conversation)

        // Load the actual product information if a listing ID exists.
        setupListingHeader()

        // Back button.
        findViewById<ImageButton>(R.id.chatBackButton)
            .setOnClickListener {
                finish()
            }

        // Set up our chat features.
        setupMessages()
        setupSendButton()
        setupSellerConfirmation()
    }

    // --------------------------------------------------
    // SELLER CONFIRMATION
    // --------------------------------------------------

    private fun setupSellerConfirmation() {

        val confirmSaleButton =
            findViewById<View>(R.id.confirmSaleButton)

        val sellerConfirmCard =
            findViewById<View>(R.id.chatSellerConfirmCard)

        // The sale process is still a UI preview.
        // Do not show the preview sale action in real listing chats.
        confirmSaleButton.visibility =
            if (listingId.isBlank()) View.VISIBLE else View.GONE

        // Hide the large card initially.
        sellerConfirmCard.visibility = View.GONE

        // Top Confirm Sale button opens or closes the card.
        confirmSaleButton.setOnClickListener {

            sellerConfirmCard.visibility =
                if (sellerConfirmCard.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        // Preview only: do not update any real listing.
        findViewById<View>(R.id.sendSaleConfirmationButton)
            .setOnClickListener {

                Toast.makeText(
                    this,
                    "UI preview only — sale has not been recorded",
                    Toast.LENGTH_SHORT
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
            if (listingId.isBlank()) messages.toList() else emptyList(),
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

        // Real messaging will be enabled after Firebase integration.
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

                // Add a local preview message.
                messages.add(
                    ChatMessage(previewSellerId, text)
                )

                // Refresh message bubbles.
                messageAdapter.updateMessages(
                    messages.toList()
                )

                // Clear the input field.
                messageInput.text.clear()

                // Scroll to the newest message.
                messagesRecyclerView.scrollToPosition(
                    messages.lastIndex
                )
            }
        }
    }

    // --------------------------------------------------
    // DYNAMIC PRODUCT INFORMATION AND THUMBNAIL
    // --------------------------------------------------

    private fun setupListingHeader() {

        // Our sample chat has no real listing.
        if (listingId.isBlank()) return

        val thumbnail =
            findViewById<ImageView>(R.id.chatItemThumbnail)

        val sellerName =
            findViewById<TextView>(R.id.chatPersonName)

        val itemDetails =
            findViewById<TextView>(R.id.chatItemDetails)

        val avatar =
            findViewById<TextView>(R.id.chatAvatar)

        // Display temporary loading information.
        sellerName.text = "Loading seller..."
        itemDetails.text = "Loading listing..."
        thumbnail.setImageDrawable(null)

        // Retrieve the associated listing from Firestore.
        // This is read-only.
        ListingRepo.getListing(listingId) { listing ->

            if (isFinishing || isDestroyed) {
                return@getListing
            }

            if (listing == null) {
                sellerName.text = "Seller unavailable"
                itemDetails.text = "Listing unavailable"
                thumbnail.setImageDrawable(null)
                return@getListing
            }

            // Display the actual seller's name.
            val name = listing.sellerName.ifBlank {
                "NOX student"
            }

            sellerName.text = name

            // Generate initials for the seller avatar.
            avatar.text = name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") {
                    it.first().uppercaseChar().toString()
                }

            // Display the actual product title and price.
            itemDetails.text =
                "${listing.title} · \$${listing.price.toInt()}"

            // Retrieve the first valid listing photo.
            val photoUrl = listing.imageUrls
                .firstOrNull { it.isNotBlank() }

            if (photoUrl.isNullOrBlank()) {

                // No product photo available.
                thumbnail.setImageDrawable(null)

            } else {

                // Coil displays the product image.
                thumbnail.load(photoUrl) {
                    crossfade(true)
                    placeholder(R.color.nox_chip_bg)
                    error(R.color.nox_chip_bg)
                }
            }
        }
    }
}
