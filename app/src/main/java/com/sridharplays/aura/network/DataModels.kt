package com.sridharplays.aura.network

import com.google.gson.annotations.SerializedName

// Data class for the main journal entry, now with ID and Note
data class JournalEntry(
    @SerializedName("id") val id: String,
    @SerializedName("timestamp") val date: String,
    @SerializedName("mood") val mood: String,
    @SerializedName("note") val note: String
)

// Add this new data class to your DataModels.kt file
data class LatestMoodResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: JournalEntry
)

// Data class for the API response when fetching entries
data class JournalResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<JournalEntry>
)

// Data class for POSTing a new mood
data class MoodPostData(
    val action: String = "addMood",
    val mood: String
)

// Data class for POSTing a note update
data class NoteUpdateData(
    val action: String = "updateNote",
    val id: String,
    val note: String
)