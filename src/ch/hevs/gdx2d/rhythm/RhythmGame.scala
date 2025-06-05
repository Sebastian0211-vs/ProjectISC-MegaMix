// RhythmGame.scala
package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.desktop.PortableApplication
import com.badlogic.gdx.Gdx

class RhythmGame extends PortableApplication(1920, 1080) {

  private var current: Screen2d = _

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
