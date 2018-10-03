package com.spotify.data.example

import com.spotify.data.api.SpotifyApiCaller
import com.spotify.data.api.schemas.{CodePubTrack, CodePubTrackAudioFeatures}
import com.spotify.scio._
import com.spotify.scio.values.SCollection

import scala.io.Source

// ABOUT AVRO MAGIC:
// Each field in an Avro model can be accessed with predefined getter.
// This is done in compile time, based on .avsc schema files.
// Example: If a model has a field called `name`, you can get name with `getName`.
object CodePubPlaylistJob {

  def main(cmdlineArgs: Array[String]): Unit = {
    // Create a ScioContext (http://spotify.github.io/scio/api/com/spotify/scio/ScioContext.html)
    val sc = ScioContext()

    // Get the top tracks for your user
    val topTracksRequest: Seq[CodePubTrack] = SpotifyApiCaller.getTopTracks()

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Step1. Extract the Ids from your top tracks
    // First check how `CodePubTrack.avsc` looks like and remember Avro magic from slides!
    // Here's one more hint: use `.toString` to convert to String.
    val ids: Seq[String] = topTracksRequest.map(s => s.getId().toString() )
    ///////////////////////////////////////////////////////////////////////////////////////////////

    // Get audio features for your top tracks
    val topTrackAudioFeatures: Seq[CodePubTrackAudioFeatures] = SpotifyApiCaller.getTrackAudioFeatures(ids)

    // Convert your top track audio features to an SCollection
    // Don't be afraid of SCollection. You could think it as Seq or Array.
    val parallelTrackAudioFeatures: SCollection[CodePubTrackAudioFeatures] = sc.parallelize(topTrackAudioFeatures)

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Step2. Use a filter to select your mood of the playlist.
    // Check out `CodePubTrackAudioFeatures.avsc` schema and explore which features are available.
    // You already have some recipes inside slides.
    // For example, I'd like to create a playlist for dance with danceability > 0.7 songs
    // BE CREATIVE!!
    val trackAudioFeaturesInMood = parallelTrackAudioFeatures.filter(
        p => p.getEnergy() < 0.1 &&
             p.getInstrumentalness() > 0.8 &&
             p.getTempo > 100 &&
             p.getValence() < 0.3 &&
             p.getLoudness < -30
         )
    ///////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Step3. Extract the track Ids
    // You need schema here again. We did the similar thing already, right?
    val trackIdsInMood = trackAudioFeaturesInMood.map(t => t.getId())
    ///////////////////////////////////////////////////////////////////////////////////////////////

    // Save the results to a text file
    // DISCLAIMER: We need to save it as a text file in order for the playlist API call to
    // be able to read the output. Normally you would not do this part in a pipeline.
    trackIdsInMood.saveAsTextFile("playlist-tracks", numShards = 1)

    // Kick off the pipeline and wait for the results
    sc.close().waitUntilDone()

    // Read the file we created
    val playlistTracks = Source
      .fromFile("playlist-tracks/part-00000-of-00001.txt")
      .getLines()
      .toSeq

    // Fetch your user ID based on the token you provided
    val userId = SpotifyApiCaller.getUserID()

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Step4. Define your playlist name. For example, "CodePub Playlist!"
    val playlistName = "foo_bar"
    ///////////////////////////////////////////////////////////////////////////////////////////////

    // Create a new playlist for your user
    SpotifyApiCaller.createPlaylist(userId, playlistName)

    // Find the ID for your playlist based on its name
    val playlistId = SpotifyApiCaller.getPlaylistId(playlistName)

    // Add your dancing songs to your new playlist!
    SpotifyApiCaller.addPlaylistSong(userId, playlistId, playlistTracks)

  }
}
