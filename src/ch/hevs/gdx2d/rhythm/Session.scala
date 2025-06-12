// -----------------------------------------------------------------------------
//  Gestion de session – stockage local du jeton JWT
// -----------------------------------------------------------------------------
//  Cette petite classe utilitaire gère la persistance du token d'authentification
//  entre deux lancements du jeu. Le jeton est stocké dans un fichier texte
//  « data/session.txt » :
//    • load()  : charge le token s'il existe
//    • clear() : efface le fichier et réinitialise le token en mémoire
//
//  (La méthode save() est commentée car non utilisée ; elle pouvait être
//  réactivée si l'on souhaite conserver la session.)
// -----------------------------------------------------------------------------

package ch.hevs.gdx2d.rhythm

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Session {

  /** Jeton JWT courant (None si l'utilisateur n'est pas connecté). */
  var token: Option[String] = None

  /** Emplacement sur disque où le token est sauvegardé. */
  private val path = Paths.get("data/session.txt")

  // ---------------------------------------------------------------------------
  //  Sauvegarde – désactivée pour l'instant
  // ---------------------------------------------------------------------------
  /*
  def save(): Unit = {
    token.foreach { t =>
      Files.createDirectories(path.getParent)           // crée « data » si besoin
      Files.write(path, t.getBytes(StandardCharsets.UTF_8))
    }
  }
  */

  // ---------------------------------------------------------------------------
  //  Chargement du token depuis le disque (appelé au démarrage)
  // ---------------------------------------------------------------------------
  def load(): Unit = {
    if (Files.exists(path)) {
      val saved = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim
      if (saved.nonEmpty) token = Some(saved)
    }
  }

  // ---------------------------------------------------------------------------
  //  Réinitialisation complète de la session
  // ---------------------------------------------------------------------------
  def clear(): Unit = {
    token = None
    if (Files.exists(path)) Files.delete(path)
  }
}
