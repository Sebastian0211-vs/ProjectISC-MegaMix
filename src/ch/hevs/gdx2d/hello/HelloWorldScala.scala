package ch.hevs.gdx2d.hello

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import ch.hevs.gdx2d.desktop.PortableApplication
import ch.hevs.gdx2d.lib.GdxGraphics
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont

import javax.sound.midi._
import scala.collection.mutable

/**
 * Rhythm-game prototype – single-loop architecture.
 *
 * gdx2d only calls `onGraphicRender` each frame. We therefore run **all game
 * logic** (spawning, input, culling) inside that callback and keep no unused
 * overrides. Console logs print every few frames so you can monitor timing and
 * queue size while debugging.
 */
object SimpleRhythmGame {
  def main(args: Array[String]): Unit = new RhythmGameApp()
}

object Assets {
  var NoteBitmap: BitmapImage = null

  def init() ={
    NoteBitmap = new BitmapImage("data/Assets/Notes/noteOutline.png")
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────
case class Note(
                 startMs: Long,
                 lane: Int,
                 angle: Float,
                 spawnX: Float,
                 spawnY: Float,
                 destX: Float,   // nouvelle coordonnée destination X
                 destY: Float    // nouvelle coordonnée destination Y
               )
// ─────────────────────────────────────────────────────────────────────────────
//  MIDI → NOTES
// ─────────────────────────────────────────────────────────────────────────────
object NoteLoader {
  private val DefaultTPQ  = 480
  val OuterChartRadius: Float  = 500f
  val ChartRadius: Float = 100 // spawn radius
  private val DrumChannel = 9 // ignore GM drums by default

  def load(midiPath: String, cx: Float, cy: Float): Vector[Note] = {
    val seq = MidiSystem.getSequence(Gdx.files.internal(midiPath).file())
    val tpq = Option(seq.getResolution).filter(_ > 0).getOrElse(DefaultTPQ).toFloat


    // ── tempo map ──
    val tempo = mutable.TreeMap[Long, Float](0L -> 500000f) // default 120 BPM
    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) {
        trk.get(i).getMessage match {
          case meta: MetaMessage if meta.getType == 0x51 =>
            val d   = meta.getData
            val mpq = ((d(0) & 0xff) << 16) | ((d(1) & 0xff) << 8) | (d(2) & 0xff)
            tempo += trk.get(i).getTick -> mpq.toFloat
          case _ =>
        }
      }
    }

    def tickToMs(t: Long): Long = {
      var last = 0L; var us = 0f; var mpq = tempo.head._2
      tempo.takeWhile(_._1 <= t).foreach { case (tick, m) =>
        us += (tick - last) * mpq / tpq; last = tick; mpq = m
      }
      us += (t - last) * mpq / tpq
      (us / 1000).toLong
    }

    val active = mutable.Map[Int, Long]()
    val out    = Vector.newBuilder[Note]

    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) {
        trk.get(i).getMessage match {
          case sm: ShortMessage if sm.getChannel == 1 =>
            val pitch = sm.getData1
            val lane  = pitch % 4
            val angle = (((pitch / 4) % 12) * 30).toFloat
            sm.getCommand match {
              case ShortMessage.NOTE_ON if sm.getData2 > 0 =>
                active += pitch -> trk.get(i).getTick
              case ShortMessage.NOTE_OFF | ShortMessage.NOTE_ON =>
                val start = active.remove(pitch).getOrElse(trk.get(i).getTick)
                val ms    = tickToMs(start)
                // Spawn sur le cercle (extérieur)
                val spawnAngle = angle
                val sx = cx + math.cos(Math.toRadians(spawnAngle)).toFloat * OuterChartRadius
                val sy = cy + math.sin(Math.toRadians(spawnAngle)).toFloat * OuterChartRadius
                // End point aléatoire sur le cercle
                val endAngle = (math.random() * 360).toFloat
                var destX = cx + math.cos(Math.toRadians(endAngle)).toFloat * ChartRadius
                var destY = cy + math.sin(Math.toRadians(endAngle)).toFloat * ChartRadius

                destX = destX + (math.random().toFloat * 100 - 50) // random offset for visual variety
                destY = destY + (math.random().toFloat * 100 - 50) // random offset for visual variety
                out += Note(ms, lane, angle, sx, sy, destX, destY)
              case _ =>
            }
          case _ =>
        }
      }
    }

    out.result().sortBy(_.startMs)
  }

}

// ─────────────────────────────────────────────────────────────────────────────
//  NOTE ENTITY – draws relative to (cx, cy)
// ─────────────────────────────────────────────────────────────────────────────
class NoteEntity(n: Note, colour: Color) {
  private val dirX = -n.spawnX
  private val dirY = -n.spawnY

  private def pos(now: Long): (Float, Float) = {
    val t = ((n.startMs - now).toFloat / 1200f).max(0f).min(1f)
    val x = n.spawnX + (n.destX - n.spawnX) * (1 - t)
    val y = n.spawnY + (n.destY - n.spawnY) * (1 - t)
    (x, y)
  }

  def hittable(now: Long, win: Int): Boolean = (n.startMs - now).abs <= win

  def draw(g: GdxGraphics, now: Long): Unit = {
    val (x, y) = pos(now)
    val ghost  = new Color(colour.r, colour.g, colour.b, 0.01f)
    // Ligne du point de spawn à la destination
    g.drawLine(n.spawnX, n.spawnY, n.destX, n.destY, ghost)
    g.drawFilledCircle(x, y, 15, colour)

    g.drawTransformedPicture(n.destX, n.destY, 30, 0.05.toFloat, Assets.NoteBitmap)
  }

  val lane: Int   = n.lane
  val hitTime: Long = n.startMs
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAME APP – all logic in onGraphicRender
// ─────────────────────────────────────────────────────────────────────────────
class RhythmGameApp extends PortableApplication(1920, 1080) {
  // console log cadence
  private val LOG_EVERY_N_FRAMES = 120

  private val hitWindow  = 120
  private val live       = mutable.ListBuffer[NoteEntity]()
  private var upcoming   = Vector.empty[Note]
  private var music: Music = _

  // screen centre
  private var cx = 0f; private var cy = 0f
  private var frame = 0
  // wall-clock zero for manual timing fallback
  private var t0: Long = 0L

  override def onInit(): Unit = {
    setTitle("ISCProject - MegaMix")

    Assets.init()

    cx = Gdx.graphics.getWidth / 2f
    cy = Gdx.graphics.getHeight / 2f


    //Gdx.graphics.setWindowedMode(1920, 1080)

    upcoming = NoteLoader.load("data/song.mid",  cx, cy)
    println(s"[Init] Parsed ${upcoming.size} notes – first at ${upcoming.headOption.map(_.startMs).getOrElse(-1)} ms")

    music = Gdx.audio.newMusic(Gdx.files.internal("data/Teto Territory.mp3"))
    music.play()
    t0 = System.currentTimeMillis()
  }

  private def nowMs: Long = {
    val p = (music.getPosition * 1000).toLong
    if (p > 0) p else System.currentTimeMillis() - t0
  }

  override def onGraphicRender(g: GdxGraphics): Unit = {
    frame += 1
    val now = nowMs

    // --- spawn notes ---
    val lead = 1200L
    var spawned = 0
    while (upcoming.nonEmpty && upcoming.head.startMs - lead <= now) {
      val n = upcoming.head; upcoming = upcoming.tail

//      val randomXOffset = math.random().toFloat * 100 - 50 // random offset for visual variety
//      val randomYOffset = math.random().toFloat * 100 - 50 // random offset for visual variety
      live += new NoteEntity(n, palette(n.lane))
      spawned += 1
    }
    if (spawned > 0) println(s"[Spawn] $spawned notes (queue left: ${upcoming.size}) @ ${now} ms")

    // --- input + hit detection ---
    pollInput().foreach { l =>
      live.find(e => e.lane == l && e.hittable(now, hitWindow)).foreach(live -= _)
    }

    // --- cull expired notes ---
    live.filterInPlace(e => now - e.hitTime < 100)

    // --- periodic debug log ---
    if (frame % LOG_EVERY_N_FRAMES == 0)
      println(s"[Frame $frame] now=$now live=${live.size} queue=${upcoming.size}")

    // --- rendering ---
    g.clear(Color.DARK_GRAY)

    live.foreach(_.draw(g, now))


    g.drawString(20, Gdx.graphics.getHeight - 40, s"Active notes: ${live.size}")
    g.drawFPS()
  }

  private def pollInput(): Seq[Int] =
    Seq(Input.Keys.D -> 0, Input.Keys.F -> 1, Input.Keys.J -> 2, Input.Keys.K -> 3)
      .collect { case (k, l) if Gdx.input.isKeyJustPressed(k) => l }

  private def palette(l: Int): Color = l match {
    case 0 => Color.PINK
    case 1 => Color.GOLD
    case 2 => Color.BLUE
    case _ => Color.FIREBRICK
  }
}