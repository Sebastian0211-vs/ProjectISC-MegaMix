package ch.hevs.gdx2d.rhythm

import java.net.{HttpURLConnection, URL, URLEncoder}
import scala.io.Source
import scala.util.Using

object RhythmApi {
  val baseUrl = "https://midis.triceratops.ch"

  def main(args: Array[String]): Unit = {
    val username = "sebas"
    val password = "hunter2"
    val song = "bad apple.mid"
    val score = 29700

    val tokenOpt = login(username, password)
    tokenOpt match {
      case Some(token) =>
        println(s"✅ Token: $token")
        postScore(token, song, score)
        fetchLeaderboard(song)
      case None =>
        println("❌ Login échoué.")
    }
  }

  def register(username: String, password: String): Boolean = {
    val json = s"""{"username": "$username", "password": "$password"}"""
    val url = new URL(s"$baseUrl/register")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setDoOutput(true)

    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val responseCode = conn.getResponseCode
    responseCode == 201
  }


  def login(username: String, password: String): Option[String] = {
    val json = s"""{"username": "$username", "password": "$password"}"""
    val url = new URL(s"$baseUrl/login")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setDoOutput(true)

    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val response = Using.resource(conn.getInputStream) { is =>
      Source.fromInputStream(is).mkString
    }

    // Extraction du token de manière simple
    val tokenRegex = """"token"\s*:\s*"([^"]+)"""".r
    tokenRegex.findFirstMatchIn(response).map(_.group(1))
  }

  def postScore(str: String, str1: String, i: Int) = {
    val json = s"""{"song": "$str1", "score": $i}"""
    val url = new URL(s"$baseUrl/score")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Authorization", s"$str")
    conn.setDoOutput(true)

    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val responseCode = conn.getResponseCode
    responseCode == 200
  }

  def fetchLeaderboard(song: String): Unit = {
    val encoded = URLEncoder.encode(song, "UTF-8")
    val url = new URL(s"$baseUrl/leaderboard?song=$encoded")
    val response = Using.resource(url.openStream()) {
      Source.fromInputStream(_).mkString
    }

    println(s"📊 Leaderboard pour '$song' :\n$response")
  }
}
