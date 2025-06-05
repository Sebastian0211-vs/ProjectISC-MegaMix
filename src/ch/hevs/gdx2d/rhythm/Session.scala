package ch.hevs.gdx2d.rhythm

object Session {
  /** Filled once at login, read-only afterwards */
  var token: Option[String] = None
}