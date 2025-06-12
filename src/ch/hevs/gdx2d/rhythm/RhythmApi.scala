package ch.hevs.gdx2d.rhythm

import java.net.{HttpURLConnection, URL, URLEncoder}
import scala.io.Source
import scala.util.Using

/**
 *  API REST du jeu : enregistrements, connexion, envoi de scores et lecture
 *  du classement. Toutes les requêtes pointent vers https://midis.triceratops.ch
 */
object RhythmApi {

  /** URL racine de l’API. */
  private val baseUrl = "https://midis.triceratops.ch"

  /** Exemple d’utilisation depuis la ligne de commande. */
  def main(args: Array[String]): Unit = {
    val username = "sebas"
    val password = "hunter2"
    val song     = "bad apple.mid"
    val score    = 29_700

    // ─── Connexion ───────────────────────────────────────────────────────────
    val tokenOpt = login(username, password)

    tokenOpt match {
      case Some(token) =>
        println(s"✅ Token: $token")          // connexion OK
        postScore(token, song, score)        // envoi du score
        fetchLeaderboard(song)               // récupération du classement
      case None =>
        println("❌ Login échoué.")           // connexion KO
    }
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Authentification & utilisateur
  // ───────────────────────────────────────────────────────────────────────────

  /**
   *  Enregistre un nouvel utilisateur.
   *  @return true si le serveur renvoie HTTP 201 (créé), false sinon.
   */
  def register(username: String, password: String): Boolean = {
    val json = s"""{"username": "$username", "password": "$password"}"""
    val url  = new URL(s"$baseUrl/register")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setDoOutput(true)

    // Envoi du corps JSON
    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val responseCode = conn.getResponseCode
    responseCode == 201
  }

  /**
   *  Connexion d’un utilisateur existant.
   *  @return Option(token JWT) si succès, None sinon.
   */
  def login(username: String, password: String): Option[String] = {
    val json = s"""{"username": "$username", "password": "$password"}"""
    val url  = new URL(s"$baseUrl/login")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setDoOutput(true)

    // Envoi du corps JSON
    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    // Lecture de la réponse (JSON)
    val response = Using.resource(conn.getInputStream) { is =>
      Source.fromInputStream(is).mkString
    }

    // Extraction naïve du token via regex
    val tokenRegex = """"token"\s*:\s*"([^"]+)"""".r
    tokenRegex.findFirstMatchIn(response).map(_.group(1))
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Scores
  // ───────────────────────────────────────────────────────────────────────────

  /**
   *  Envoie un score au serveur.
   *  @param str  JWT d’authentification (« Bearer … »)
   *  @param str1 Nom du fichier MIDI (piste)
   *  @param i    Score numérique
   *  @return true si le serveur renvoie HTTP 200, false sinon.
   *
   *  ⚠️ Les noms de paramètres ne sont pas très explicites
   *  (« str », « str1 », « i »), mais ils sont conservés ici pour compatibilité.
   */
  def postScore(str: String, str1: String, i: Int): Boolean = {
    val json = s"""{"song": "$str1", "score": $i}"""
    val url  = new URL(s"$baseUrl/score")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Authorization", s"$str") // JWT dans l’en-tête
    conn.setDoOutput(true)

    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val responseCode = conn.getResponseCode
    responseCode == 200
  }

  /**
   *  Récupère et affiche le classement pour une chanson donnée.
   *  (Simple impression console ; on pourrait renvoyer le JSON brut ou le
   *  parser pour l’afficher autrement.)
   */
  private def fetchLeaderboard(song: String): Unit = {
    // Encodage URL du nom de fichier pour éviter les espaces, etc.
    val encoded = URLEncoder.encode(song, "UTF-8")
    val url      = new URL(s"$baseUrl/leaderboard?song=$encoded")
    val response = Using.resource(url.openStream()) {
      Source.fromInputStream(_).mkString
    }

    println(s"📊 Leaderboard pour '$song' :\n$response")
  }
}
