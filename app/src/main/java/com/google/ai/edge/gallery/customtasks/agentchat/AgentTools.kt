/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.customtasks.agentchat

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.google.ai.edge.gallery.common.AgentAction
import com.google.ai.edge.gallery.common.ToolProgressAgentAction
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

private const val TAG = "AGAgentTools"

class AgentTools() : ToolSet {
  lateinit var context: Context

  private val _actionChannel = Channel<AgentAction>(Channel.UNLIMITED)
  val actionChannel: ReceiveChannel<AgentAction> = _actionChannel

  /** Sends an email using the device's email client. */
  @Tool(description = "Send an email. Opens the device email client with the given recipient, subject, and body.")
  fun sendEmail(
    @ToolParam(description = "The email address of the recipient.") recipient: String,
    @ToolParam(description = "The subject of the email.") subject: String,
    @ToolParam(description = "The body text of the email.") body: String,
  ): Map<String, String> {
    return runBlocking(Dispatchers.Default) {
      Log.d(TAG, "sendEmail tool called: recipient=$recipient, subject=$subject")
      _actionChannel.send(
        ToolProgressAgentAction(
          label = "Sending email to \"$recipient\"",
          inProgress = true,
          addItemTitle = "Send email",
          addItemDescription = "To: $recipient\nSubject: $subject",
        )
      )
      try {
        val intent = Intent(Intent.ACTION_SEND).apply {
          data = "mailto:".toUri()
          type = "text/plain"
          putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
          putExtra(Intent.EXTRA_SUBJECT, subject)
          putExtra(Intent.EXTRA_TEXT, body)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        mapOf("status" to "succeeded", "result" to "Email client opened for $recipient")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to send email", e)
        mapOf("status" to "failed", "error" to "Failed to open email client: ${e.message}")
      }
    }
  }

  /** Shows a location on an interactive map by opening Google Maps. */
  @Tool(description = "Show a location on an interactive map. Opens Google Maps with the specified location.")
  fun showInteractiveMap(
    @ToolParam(description = "The location or place name to show on the map.") location: String,
  ): Map<String, String> {
    return runBlocking(Dispatchers.Default) {
      Log.d(TAG, "showInteractiveMap tool called: location=$location")
      _actionChannel.send(
        ToolProgressAgentAction(
          label = "Opening map for \"$location\"",
          inProgress = true,
          addItemTitle = "Show interactive map",
          addItemDescription = "Location: $location",
        )
      )
      try {
        val encodedLocation = URLEncoder.encode(location, "UTF-8")
        val uri = "geo:0,0?q=$encodedLocation".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        mapOf("status" to "succeeded", "result" to "Map opened for $location")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to open map", e)
        mapOf("status" to "failed", "error" to "Failed to open map: ${e.message}")
      }
    }
  }

  /** Queries Wikipedia for a summary of a topic. */
  @Tool(description = "Query Wikipedia for a summary of a given topic. Returns the extract/summary text from the Wikipedia article.")
  fun queryWikipedia(
    @ToolParam(description = "The topic to search on Wikipedia. Use the primary entity, person, or event name.") topic: String,
    @ToolParam(description = "The 2-letter language code, e.g. 'en', 'es', 'fr', 'de'.") lang: String,
  ): Map<String, String> {
    return runBlocking(Dispatchers.IO) {
      Log.d(TAG, "queryWikipedia tool called: topic=$topic, lang=$lang")
      _actionChannel.send(
        ToolProgressAgentAction(
          label = "Querying Wikipedia for \"$topic\"",
          inProgress = true,
          addItemTitle = "Query Wikipedia",
          addItemDescription = "Topic: $topic (lang: $lang)",
        )
      )
      try {
        val encodedTopic = URLEncoder.encode(topic, "UTF-8")
        val apiUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTopic"
        val connection = URL(apiUrl).openConnection()
        connection.setRequestProperty("User-Agent", "BlueEdge/1.0")
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val extract = json.optString("extract", "No summary found.")
        val title = json.optString("title", topic)
        Log.d(TAG, "Wikipedia result for '$topic': $extract")
        mapOf("status" to "succeeded", "title" to title, "summary" to extract)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to query Wikipedia", e)
        mapOf("status" to "failed", "error" to "Failed to query Wikipedia: ${e.message}")
      }
    }
  }

  @Tool(
    description = "Run an Android intent. It is used to interact with the app to perform certain actions."
  )
  fun runIntent(
    @ToolParam(description = "The intent to run.") intent: String,
    @ToolParam(
      description = "A JSON string containing the parameter values required for the intent."
    )
    parameters: String,
  ): Map<String, String> {
    return runBlocking(Dispatchers.Default) {
      Log.d(TAG, "Run intent. Intent: '$intent', parameters: '$parameters'")
      _actionChannel.send(
        ToolProgressAgentAction(
          label = "Executing intent \"$intent\"",
          inProgress = true,
          addItemTitle = "Execute intent \"$intent\"",
          addItemDescription = "Parameters: $parameters",
        )
      )
      if (IntentHandler.handleAction(context, intent, parameters)) {
        return@runBlocking mapOf(
          "action" to intent,
          "parameters" to parameters,
          "result" to "succeeded",
        )
      } else {
        return@runBlocking mapOf(
          "action" to intent,
          "parameters" to parameters,
          "result" to "failed",
        )
      }
    }
  }

  fun sendAgentAction(action: AgentAction) {
    runBlocking(Dispatchers.Default) { _actionChannel.send(action) }
  }
}
