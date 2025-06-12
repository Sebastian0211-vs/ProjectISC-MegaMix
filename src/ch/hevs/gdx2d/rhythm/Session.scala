package ch.hevs.gdx2d.rhythm

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Session {
  var token: Option[String] = None
  private val path = Paths.get("data/session.txt")

//  def save(): Unit = {
//    token.foreach { t =>
//      Files.createDirectories(path.getParent)
//      Files.write(path, t.getBytes(StandardCharsets.UTF_8))
//    }
//  }

  def load(): Unit = {
    if (Files.exists(path)) {
      val saved = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim
      if (saved.nonEmpty)
        token = Some(saved)
    }
  }

  def clear(): Unit = {
    token = None
    if (Files.exists(path)) Files.delete(path)
  }
}
