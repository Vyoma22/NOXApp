package com.example.noxapp.postlisting

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.example.noxapp.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The post form survives rotation.
 *
 * Picked photos, the camera's temp file and a map pin are not views, so they only live in the
 * activity's fields. These tests drive the real save and restore path and then read the form the
 * student would see, rather than the fields themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PostListingDraftTest {

    private val firstPhoto = Uri.parse("content://media/external/images/media/1")
    private val secondPhoto = Uri.parse("content://media/external/images/media/2")
    private val cameraPhoto =
        Uri.parse("content://com.example.noxapp.fileprovider/cache/captured_media_1.jpg")

    @Before
    fun setUpFirebase() {
        // PostListing holds a FirebaseStorage reference. Nothing here contacts a server; the
        // form is only filled in and saved, never submitted.
        val context = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("test-key")
                    .setProjectId("nox-test")
                    .setStorageBucket("nox-test.appspot.com")
                    .build()
            )
        }
    }

    @Test
    fun rotationKeepsPickedPhotosAndTheMapPin() {
        val original = Robolectric.buildActivity(PostListing::class.java).setup()
        addPhotos(original.get(), firstPhoto, secondPhoto)
        setField(original.get(), "pickedLat", 43.94500)
        setField(original.get(), "pickedLng", -78.89600)
        setField(original.get(), "pickedLabel", "Simcoe Hall")

        val recreated = recreate(original)

        assertEquals(
            recreated.getString(R.string.post_media_count, 2),
            recreated.findViewById<TextView>(R.id.btnText2).text.toString()
        )
        val pin = recreated.findViewById<TextView>(R.id.tvPickedLocation)
        assertTrue(pin.isVisible)
        assertEquals(
            recreated.getString(R.string.post_picked_location_coords, "Simcoe Hall", 43.94500, -78.89600),
            pin.text.toString()
        )
    }

    @Test
    fun rotationKeepsTheCameraCaptureTarget() {
        val original = Robolectric.buildActivity(PostListing::class.java).setup()
        setField(original.get(), "cameraTempUri", cameraPhoto)

        val recreated = recreate(original)

        // Losing this URI would drop the photo the camera app just wrote.
        assertEquals(cameraPhoto, getField(recreated, "cameraTempUri"))
    }

    @Test
    fun aFreshFormStartsWithoutMediaOrAPin() {
        val fresh = Robolectric.buildActivity(PostListing::class.java).setup().get()

        assertEquals(
            fresh.getString(R.string.post_media_count, 0),
            fresh.findViewById<TextView>(R.id.btnText2).text.toString()
        )
        assertFalse(fresh.findViewById<TextView>(R.id.tvPickedLocation).isVisible)
    }

    /** Saves the live form and rebuilds it from that bundle, the way a rotation does. */
    private fun recreate(original: ActivityController<PostListing>): PostListing {
        val state = Bundle()
        original.saveInstanceState(state)
        return Robolectric.buildActivity(PostListing::class.java).create(state).get()
    }

    private fun addPhotos(form: PostListing, vararg photos: Uri) {
        @Suppress("UNCHECKED_CAST")
        val picked = getField(form, "pickedImages") as MutableList<Uri>
        picked.addAll(photos)
    }

    private fun getField(form: PostListing, name: String): Any? =
        PostListing::class.java.getDeclaredField(name).apply { isAccessible = true }.get(form)

    private fun setField(form: PostListing, name: String, value: Any?) {
        PostListing::class.java.getDeclaredField(name).apply { isAccessible = true }.set(form, value)
    }
}