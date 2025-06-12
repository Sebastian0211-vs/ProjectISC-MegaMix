// -----------------------------------------------------------------------------
//  Point d'entrée principal – Application LibGDX
// -----------------------------------------------------------------------------
//  La classe RhythmGame dérive de PortableApplication (wrapper HEVS pour
//  configurer rapidement une fenêtre Desktop). Elle orchestre les écrans :
//   • MainMenuScreen (menu principal)
//   • GameplayScreen  (partie en cours)
//  et gère le cycle de vie global (init, rendu, dispose).
// -----------------------------------------------------------------------------

package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.desktop.PortableApplication
import com.badlogic.gdx.Gdx

/**
 *  Application principale. Instanciée par le launcher (par ex. DesktopMain).
 */
class RhythmGame extends PortableApplication(1920, 1080) {

  // Écran actuellement affiché (menu ou gameplay)
  private var current: Screen2d = _

  // ---------------------------------------------------------------------------
  //  Gestion du changement d'écran
  // ---------------------------------------------------------------------------
  /**
   *  Change d'écran en nettoyant l'ancien (dispose) puis en affichant le
   *  nouveau (show). Cette méthode est appelée depuis MainMenuScreen pour
   *  lancer une partie, ou depuis GameplayScreen pour revenir au menu.
   */
  def switchScreen(next: Screen2d): Unit = {
    if (current != null) current.dispose() // libère ressources ancien écran
    current = next
    current.show()                         // initialise le nouvel écran
  }

  // ---------------------------------------------------------------------------
  //  Initialisation de l'application (appelée une seule fois)
  // ---------------------------------------------------------------------------
  override def onInit(): Unit = {
    Session.load()  // tente de charger le token JWT sauvegardé
    Assets.init()   // charge les bitmaps communs (note, background, etc.)
    switchScreen(new MainMenuScreen(this)) // démarre sur le menu principal
  }

  // ---------------------------------------------------------------------------
  //  Rendu graphique – boucle principale
  // ---------------------------------------------------------------------------
  override def onGraphicRender(g: GdxGraphics): Unit =
    current.render(g, Gdx.graphics.getDeltaTime)

  // ---------------------------------------------------------------------------
  //  Libération finale (non indispensable, mais propre)
  // ---------------------------------------------------------------------------
  def dispose(): Unit =
    if (current != null) current.dispose()
}
