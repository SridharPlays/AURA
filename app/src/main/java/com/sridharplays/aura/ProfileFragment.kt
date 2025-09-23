package com.sridharplays.aura

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.squareup.picasso.Picasso
import java.io.File

class ProfileFragment : Fragment() {

    // --- View Components ---
    private lateinit var fullNameTextView: TextView
    private lateinit var usernameTextView: TextView
    private lateinit var moodTextView: TextView
    private lateinit var moodImageView: ImageView
    private lateinit var profileImageView: ShapeableImageView

    // To hold the URI for the photo taken by the camera
    private var tempImageUri: Uri? = null

    // --- ActivityResultLaunchers for getting images and permissions ---
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val imageUri = result.data?.data
            imageUri?.let {
                saveProfileImageUri(it)
                Picasso.get().load(it).into(profileImageView)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            tempImageUri?.let {
                saveProfileImageUri(it)
                Picasso.get().load(it).into(profileImageView)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val isCameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (isCameraGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        initializeViews(view)
        setupClickListeners(view)
        return view
    }

    override fun onResume() {
        super.onResume()
        updateProfileUI()
        loadProfileImage()
    }

    private fun initializeViews(view: View) {
        fullNameTextView = view.findViewById(R.id.txtFullName)
        usernameTextView = view.findViewById(R.id.txtUsername)
        moodTextView = view.findViewById(R.id.textCurrentMood)
        moodImageView = view.findViewById(R.id.imageCurrentMood)
        profileImageView = view.findViewById(R.id.imgGalleryPreview)
    }

    private fun setupClickListeners(view: View) {
        // Listener to open the image picker
        profileImageView.setOnClickListener {
            showImagePickerOptions()
        }

        // Listener for the settings card
        val settingsCard: CardView = view.findViewById(R.id.cardSettings)
        settingsCard.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        // Listener for the mood selector card
        val moodSelector: CardView = view.findViewById(R.id.cardCurrentMood)
        moodSelector.setOnClickListener {
            val intent = Intent(requireContext(), MoodSelectorActivity::class.java)
            startActivity(intent)
        }

        // Listener for the music player card
        val musicPlayerCard: CardView = view.findViewById(R.id.cardGoToPlayer)
        musicPlayerCard.setOnClickListener {
            val musicPlayerFragment = MusicPlayerFragment.newInstance("default")
            parentFragmentManager.beginTransaction().apply {
                replace(R.id.fragment_container, musicPlayerFragment)
                addToBackStack(null)
                commit()
            }
        }

        val musicRecapCard: CardView = view.findViewById(R.id.cardVibeRecap)
        musicRecapCard.setOnClickListener {
            startActivity(Intent(requireContext(), VibeRecapActivity::class.java))
        }
    }

    private fun showImagePickerOptions() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_image_picker, null)
        dialog.setContentView(view)

        val cameraOption = view.findViewById<TextView>(R.id.option_camera)
        val galleryOption = view.findViewById<TextView>(R.id.option_gallery)

        cameraOption.setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpenCamera()
        }

        galleryOption.setOnClickListener {
            dialog.dismiss()
            openGallery()
        }
        dialog.show()
    }

    private fun checkCameraPermissionAndOpenCamera() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        tempImageUri = createImageUri()
        intent.putExtra(MediaStore.EXTRA_OUTPUT, tempImageUri)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun createImageUri(): Uri {
        val imageFile = File(requireContext().filesDir, "profile_picture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imageFile)
    }

    private fun saveProfileImageUri(uri: Uri) {
        val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("PROFILE_IMAGE_URI", uri.toString()).apply()
    }

    private fun loadProfileImage() {
        val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        val uriString = sharedPrefs.getString("PROFILE_IMAGE_URI", null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            Picasso.get().load(uri).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).into(profileImageView)
        }
    }

    private fun updateProfileUI() {
        val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)

        // Update username
        val username = sharedPrefs.getString("USERNAME", "Aura User")
        if (username.isNullOrEmpty() || username == "Aura User") {
            fullNameTextView.text = "Aura User"
            usernameTextView.text = "@aura_user"
        } else {
            fullNameTextView.text = username
            usernameTextView.text = "@${username.replace(" ", "").toLowerCase()}"
        }

        // Update current mood
        val currentMood = sharedPrefs.getString("CURRENT_MOOD", "Happy") // Default to Happy
        moodTextView.text = currentMood

        val moodDrawableId = when (currentMood?.lowercase()) {
            "happy" -> R.drawable.ic_mood_happy
            "sad" -> R.drawable.ic_mood_sad
            "sleepy" -> R.drawable.ic_mood_sleepy
            "motivated" -> R.drawable.ic_mood_motivated
            "excited" -> R.drawable.ic_mood_excited
            "romantic" -> R.drawable.ic_mood_romantic
            "chill" -> R.drawable.ic_mood_chill
            "energetic" -> R.drawable.ic_mood_energetic
            else -> R.drawable.ic_mood_happy
        }
        moodImageView.setImageResource(moodDrawableId)
    }
}