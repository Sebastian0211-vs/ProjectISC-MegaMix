// RhythmGame.scala   (keep this one)
package ch.hevs.gdx2d.rhythm          // ← keep everything in this package

import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.desktop.PortableApplication
import com.badlogic.gdx.Gdx

class RhythmGame extends PortableApplication(1920, 1080) {

  private var current: Screen2d = _        // Screen2d is our light adapter

  def switchScreen(next: Screen2d): Unit = {
    if (current != null) current.dispose()
    current = next
    current.show()
  }

  override def onInit(): Unit = {
    Assets.init()
    switchScreen(new MainMenuScreen(this))
  }

  override def onGraphicRender(g: GdxGraphics): Unit =
    current.render(g, Gdx.graphics.getDeltaTime)

  def dispose(): Unit =
    if (current != null) current.dispose()
}
