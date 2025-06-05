package ch.hevs.gdx2d.hello

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import ch.hevs.gdx2d.desktop.PortableApplication
import ch.hevs.gdx2d.lib.GdxGraphics
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.audio.{Music, Sound}
import com.badlogic.gdx.graphics.Color

import javax.sound.midi._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object SimpleRhythmGame {
  def main(args: Array[String]): Unit = new RhythmGameApp()
}

/** ---------------------------------------------------------------------------
 *  ASSETS
 *  -------------------------------------------------------------------------*/
object Assets {
  var NoteBitmap       : BitmapImage = _
  var BackgroundBitMap : BitmapImage = _
  var PerfectBitmap    : BitmapImage = _
  var GoodBitmap       : BitmapImage = _
  var MissBitmap       : BitmapImage = _
  var ArrowBitmap      : BitmapImage = _

  def init(): Unit = {
    NoteBitmap       = new BitmapImage("data/Assets/Notes/noteOutline.png")
    BackgroundBitMap = new BitmapImage("data/Assets/Background/teto.jpeg")
    PerfectBitmap    = new BitmapImage("data/Assets/Feedback/Perfect.png")
    GoodBitmap       = new BitmapImage("data/Assets/Feedback/Good.png")
    MissBitmap       = new BitmapImage("data/Assets/Feedback/Miss.png")
    ArrowBitmap      = new BitmapImage("data/Assets/Notes/fleche.png")
  }
}

/** ---------------------------------------------------------------------------
 *  DATA MODEL
 *  -------------------------------------------------------------------------*/
case class Note(
                 startMs : Long,
                 lane    : Int,
                 angle   : Float,
                 spawnX  : Float,
                 spawnY  : Float,
                 destX   : Float,
                 destY   : Float,
               )

/** Small transient graphic that disappears after ~800 ms */
case class Feedback(x: Float, y: Float, kind: String, born: Long)

/** ---------------------------------------------------------------------------
 *  MIDI → NOTES
 *  -------------------------------------------------------------------------*/
object NoteLoader {
  private val DefaultTPQ          = 480
  val    OuterChartRadius : Float = 1000f
  val    ChartRadius      : Float = 100f
  var    secondRadius     : Float = 300f

  private val DrumChannel        = 9
  private val displayMs          = 1200L   // note lifetime on screen

  def load(midiPath: String,
           cx: Float, cy: Float,
           channel: Int,
           difficulty: Int = 4): Vector[Note] = {

    val seq = MidiSystem.getSequence(Gdx.files.internal(midiPath).file())
    val tpq = Option(seq.getResolution).filter(_ > 0).getOrElse(DefaultTPQ).toFloat

    // difficulty → max notes simultaneously visible
    val maxOnScreen = difficulty match {
      case 1 => 3; case 2 => 5; case 3 => 7; case _ => 10
    }

    /* tempo map */
    val bpm   = extractBPM(midiPath).getOrElse(120f)
    val tempo = mutable.TreeMap[Long, Float](0L -> (60_000_000f / bpm))

    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) trk.get(i).getMessage match {
        case m: MetaMessage if m.getType == 0x51 =>
          val d   = m.getData
          val mpq = ((d(0)&0xff)<<16)|((d(1)&0xff)<<8)|(d(2)&0xff)
          tempo += trk.get(i).getTick -> mpq.toFloat
        case _ =>
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

    val active   = mutable.Map[Int, Long]()
    val out      = Vector.newBuilder[Note]
    val onScreen = mutable.Queue[Long]()   // timestamps of notes currently visible

    val groupSize     = 6
    val quarterCircle = 90
    var noteCount     = 0
    var pivotX = cx; var pivotY = cy

    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) {
        trk.get(i).getMessage match {
          case sm: ShortMessage =>
            val pitch = sm.getData1
            val lane  = pitch % 4

            val angle = (((pitch / 4) % 12) * 30).toFloat

            sm.getCommand match {
              case ShortMessage.NOTE_ON if sm.getData2 > 0 =>
                active += pitch -> trk.get(i).getTick

              case ShortMessage.NOTE_OFF | ShortMessage.NOTE_ON =>
                val start = active.remove(pitch).getOrElse(trk.get(i).getTick)
                val ms    = tickToMs(start)

                // maintain cap of visible notes
                while (onScreen.nonEmpty && ms - onScreen.head > displayMs) onScreen.dequeue()
                if (onScreen.size >= maxOnScreen) {
                  // skip
                  noteCount += 1
                } else {
                  // quarter‑circle placement
                  val groupIdx    = noteCount / groupSize
                  val idxInGroup  = noteCount % groupSize
                  val startAng    = groupIdx * quarterCircle
                  val stepAng     = quarterCircle / (groupSize - 1)
                  val destAng     = startAng + idxInGroup * stepAng

                  val sx = cx + Math.cos(Math.toRadians(destAng)).toFloat * OuterChartRadius
                  val sy = cy + Math.sin(Math.toRadians(destAng)).toFloat * OuterChartRadius

                  if (idxInGroup == 0) {
                    pivotX = cx + Math.cos(Math.random()*2*Math.PI).toFloat * ChartRadius
                    pivotY = cy + Math.sin(Math.random()*2*Math.PI).toFloat * ChartRadius
                  }
                  val dx = pivotX + Math.cos(Math.toRadians(destAng)).toFloat * secondRadius
                  val dy = pivotY + Math.sin(Math.toRadians(destAng)).toFloat * secondRadius

                  out += Note(ms, lane, angle, sx, sy, dx, dy)
                  onScreen.enqueue(ms)
                  noteCount += 1
                }

              case _ =>
            }
          case _ =>
        }
      }
    }
    out.result().sortBy(_.startMs)
  }

  private def extractBPM(path: String): Option[Float] = {
    val seq = MidiSystem.getSequence(new java.io.File(path))
    for (trk <- seq.getTracks; i <- 0 until trk.size()) {
      trk.get(i).getMessage match {
        case m: MetaMessage if m.getType == 0x51 =>
          val d   = m.getData
          val mpq = ((d(0)&0xff)<<16)|((d(1)&0xff)<<8)|(d(2)&0xff)
          return Some(60_000_000f / mpq)
        case _ =>
      }
    }
    None
  }
}

/** ---------------------------------------------------------------------------
 *  NOTE ENTITY – draws relative to spawn/destination
 *  -------------------------------------------------------------------------*/
class NoteEntity(n: Note, colour: Color) {
  val lane    : Int   = n.lane
  val hitTime : Long  = n.startMs
  val destX   : Float = n.destX
  val destY   : Float = n.destY

  private def pos(now: Long): (Float, Float) = {
    val t = ((n.startMs - now).toFloat / 1200f).max(0f).min(1f)
    val x = n.spawnX + (n.destX - n.spawnX) * (1 - t)
    val y = n.spawnY + (n.destY - n.spawnY) * (1 - t)
    (x, y)
  }

  def hittable(now: Long, win: Int): Boolean = (n.startMs - now).abs <= win

  def draw(g: GdxGraphics, now: Long): Unit = {
    val (x, y) = pos(now)
    val distance      = math.hypot(n.destX - x, n.destY - y).toFloat
    val transparency  = math.min(1f, 0.5f - distance / 1000f)
    val ghost         = new Color(colour.r, colour.g, colour.b, transparency)

    var arrowAngle = n.angle

    g.drawFilledCircle(x, y, 60, colour)
    g.drawTransformedPicture(n.destX, n.destY, 0, 0.2f, Assets.NoteBitmap)
    g.drawFilledCircle(n.destX, n.destY, 60, ghost)

    //set arrow angle based on lane
    n.lane match {
      case 0 => arrowAngle = 0f
      case 1 => arrowAngle = 90f
      case 2 => arrowAngle = 180f
      case _ => arrowAngle = 270f
    }
    g.drawTransformedPicture(n.destX, n.destY, arrowAngle, 0.2f, Assets.ArrowBitmap)

  }
}

/** ---------------------------------------------------------------------------
 *  GAME APP – single‑loop logic
 *  -------------------------------------------------------------------------*/
class RhythmGameApp extends PortableApplication(1920, 1080) {
  // timings
  private val hitWindow      = 120      // "Good" ±ms
  private val perfectWindow  = 50
  private val lead           = 1200L
  private val LOG_EVERY_N_FRAMES = 120

  // state
  private val live       = mutable.ListBuffer[NoteEntity]()
  private var upcoming   = Vector.empty[Note]
  private val feedbacks  = mutable.ListBuffer[Feedback]()
  private var score      = 0
  private var combo      = 0
  private var combocounter = 0
  private var music      : Music = _

  private var cx = 0f; private var cy = 0f
  private var frame = 0
  private var t0: Long = 0L
  private var sfx:Sound = _
  private var comboUp:Sound = _

  override def onInit(): Unit = {
    setTitle("ISCProject – MegaMix")
    Assets.init()
    cx = Gdx.graphics.getWidth / 2f
    cy = Gdx.graphics.getHeight / 2f



    sfx = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/sfx.wav"))
    comboUp = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/comboUp.wav"))



    val midiPath = "data/ANAMANAGUCHI - Miku.mid"
    upcoming = NoteLoader.load(midiPath, cx, cy, 1, 1)
    println(s"[Init] Parsed ${upcoming.size} notes – first at ${upcoming.headOption.map(_.startMs).getOrElse(-1)} ms")

    music = Gdx.audio.newMusic(Gdx.files.internal("data/Miku.mp3"))

    val sequencer = MidiSystem.getSequencer(); sequencer.open()
    sequencer.setSequence(Gdx.files.internal(midiPath).read())
    val synth = MidiSystem.getSynthesizer; synth.open()
    for (ch <- synth.getChannels if ch != null) ch.controlChange(7, 127)
    sequencer.start()

    t0 = System.currentTimeMillis()
  }

  private def nowMs: Long = {
    val p = (music.getPosition * 1000).toLong
    if (p > 0) p else System.currentTimeMillis() - t0
  }

  override def onGraphicRender(g: GdxGraphics): Unit = {
    frame += 1
    val now = nowMs

    // ─── spawn upcoming ───
    upcoming = upcoming
      .groupBy(n => (n.lane, n.startMs))
      .values
      .map(_.head)
      .toVector
      .sortBy(_.startMs)

    var spawned = 0
    while (upcoming.nonEmpty && upcoming.head.startMs - lead <= now) {
      val n = upcoming.head
      upcoming = upcoming.tail
      live += new NoteEntity(n, palette(n.lane))
      spawned += 1
    }
    if (spawned > 0)
      println(s"[Spawn] $spawned notes (queue left: ${upcoming.size}) @ $now ms")



    // ─── input & judgement ───
    for (lane <- pollInput()) {
      live
        .filter(_.lane == lane)                       // look only in this lane
        .minByOption(e => math.abs(e.hitTime - now))  // …pick the closest note
        .foreach { e =>
          if (e.hittable(now, hitWindow)) {
            val diff        = (e.hitTime - now).abs
            val (kind, pts) =
              if (diff <= perfectWindow) ("Perfect", 300) else ("Good", 100)

            combocounter+=1
            if (combocounter > 9) {
              combo = math.min(2* combo,16)
              combocounter = 0
              comboUp.play()
            }
            score += pts* combo
            feedbacks += Feedback(e.destX, e.destY, kind, now)
            live -= e
          } else {                                    // pressed outside window
            feedbacks += Feedback(e.destX, e.destY, "Miss", now)
            live -= e
            combo = 1
            combocounter = 0
          }
        }
    }

    // ─── auto-miss notes that scrolled past ───
    val justMissed = live.filter(e => now - e.hitTime > hitWindow)
    justMissed.foreach { e =>
      feedbacks += Feedback(e.destX, e.destY, "Miss", now)
      combo = 1
      combocounter = 0
    }
    live --= justMissed                               // remove from play

    // ─── prune old feedbacks ───
    feedbacks.filterInPlace(f => now - f.born < 800)

    // ─── periodic debug log ───
    if (frame % LOG_EVERY_N_FRAMES == 0)
      println(
        s"[Frame $frame] now=$now live=${live.size} queue=${upcoming.size} score=$score"
      )

    if (Seq(Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D).exists(Gdx.input.isKeyJustPressed)) {
      sfx.play()
    }

    // ─── rendering ───
    g.clear(Color.DARK_GRAY)

    live.foreach(_.draw(g, now))

    for (fb <- feedbacks) {
      val bmp = fb.kind match {
        case "Perfect" => Assets.PerfectBitmap
        case "Good"    => Assets.GoodBitmap
        case _         => Assets.MissBitmap
      }
      g.drawTransformedPicture(fb.x, fb.y, 0, 0.1f, bmp)
    }

    g.drawString(20, Gdx.graphics.getHeight - 40, s"Active notes: ${live.size}")
    g.drawString(20, Gdx.graphics.getHeight - 80, s"Score: $score")
    g.drawString(20, Gdx.graphics.getHeight - 120, s"Combo: x$combo ; Counter: $combocounter")

    g.drawFPS()
  }

  private def pollInput(): Seq[Int] =
    Seq(Input.Keys.D -> 0, Input.Keys.W -> 1, Input.Keys.A -> 2, Input.Keys.S -> 3)
      .collect { case (k, l) if Gdx.input.isKeyJustPressed(k) => l }

  private def palette(l: Int): Color = l match {
    case 0 => new Color(0f/255, 121/255, 255f/255, 1f)
    case 1 => new Color(0f/255, 223f/255, 162f/255, 1f)
    case 2 => new Color(246f/255, 250f/255, 112f/255, 1f)
    case _ => new Color(255f/255, 0f/255, 96f/255, 1f)
  }
}
