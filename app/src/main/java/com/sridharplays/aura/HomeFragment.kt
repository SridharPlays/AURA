package com.sridharplays.aura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Find the new CardView mood selectors
        val cardHappy: CardView = view.findViewById(R.id.cardHappy)
        val cardChill: CardView = view.findViewById(R.id.cardChill)
        val goToMusicPlayerButton: Button = view.findViewById(R.id.goToMusicPlayerButton)

        // Set listeners for the CardViews
        cardHappy.setOnClickListener {
            // Here you would save the "Happy" mood to SharedPreferences
            requireActivity().getSharedPreferences("AuraAppPrefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("CURRENT_MOOD", "Happy")
                .apply()
            Toast.makeText(requireContext(), "Mood set to Happy!", Toast.LENGTH_SHORT).show()
        }

        cardChill.setOnClickListener {
            // Here you would save the "Chill" mood to SharedPreferences
            requireActivity().getSharedPreferences("AuraAppPrefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("CURRENT_MOOD", "Chill")
                .apply()
            Toast.makeText(requireContext(), "Mood set to Chill!", Toast.LENGTH_SHORT).show()
        }

        // Set listener for "Go to My Music" button
        goToMusicPlayerButton.setOnClickListener {
            val musicPlayerFragment = MusicPlayerFragment.newInstance("default")

            // Perform the fragment transaction to show the music player
            parentFragmentManager.beginTransaction().apply {
                replace(R.id.fragment_container, musicPlayerFragment)
                addToBackStack(null) // Allows the user to press 'back' to return here
                commit()
            }
        }

        return view
    }
}