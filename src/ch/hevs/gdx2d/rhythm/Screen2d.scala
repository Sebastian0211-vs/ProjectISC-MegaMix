// Screen2d.scala
package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.lib.GdxGraphics

trait Screen2d {
  def show(): Unit = {}
  def render(g: GdxGraphics, dt: Float): Unit
  def dispose(): Unit = {}
}
