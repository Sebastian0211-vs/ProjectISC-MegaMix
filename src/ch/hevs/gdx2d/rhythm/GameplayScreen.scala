package ch.hevs.gdx2d.rhythm
import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import ch.hevs.gdx2d.desktop.PortableApplication
import ch.hevs.gdx2d.rhythm.RhythmApi.baseUrl
import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.rhythm.InstrumentExtractor.extractInstruments
import ch.hevs.gdx2d.rhythm.guessLead.guessLeadInstrument
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.audio.{Music, Sound}
import com.badlogic.gdx.graphics.Color

import java.net.{HttpURLConnection, URL}
import javax.sound.midi._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Using


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

// ─────────────────────────────────────────────────────────────────────────────
//  DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────
case class Note(
                 startMs : Long,
                 lane    : Int,
                 angle   : Float,
                 spawnX  : Float,
                 spawnY  : Float,
                 destX   : Float,
                 destY   : Float,
               )

/** Small transient graphic that disappears after ~800ms */
case class Feedback(x: Float, y: Float, kind: String, born: Long)

/** ---------------------------------------------------------------------------
 *  MIDI → NOTES
 *  -------------------------------------------------------------------------*/
object NoteLoader {
  private val DefaultTPQ  = 480
  val OuterChartRadius: Float  = 1000f
  val ChartRadius: Float = 150 // spawn radius
  var secondRadius: Float = 280 // second chance radius

  var extraspace: Int = 0

  var occupied:ArrayBuffer[(Float, Float)] = new ArrayBuffer[(Float, Float)]
  private val displayMs          = 1200L   // note lifetime on screen
  private val DrumChannel        = 9

  def load(midiPath: String,
           cx: Float, cy: Float,
           difficulty: Int = 4,
           selectedChannel: Int): Vector[Note] = {

    val seq = MidiSystem.getSequence(Gdx.files.internal(midiPath).file())




    val tpq = Option(seq.getResolution).filter(_ > 0).getOrElse(DefaultTPQ).toFloat

    // difficulty → max notes simultaneously visible
    val maxOnScreen = difficulty match {
      case 1 => 3; case 2 => 5; case 3 => 7; case _ => 100
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
    val onScreen = mutable.Queue[Long]()

    val groupSize = 6
    val quarterCircle = 95 // degrés
    var noteCount = 0

    var interX = cx + math.cos(Math.toRadians(0)).toFloat * secondRadius
    var interY = cy + math.sin(Math.toRadians(0)).toFloat * secondRadius

    var currentgroup: ArrayBuffer[(Float,Float)] = new ArrayBuffer[(Float,Float)]

    var numnotes: Int = 0
    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) {
        trk.get(i).getMessage match {
          case sm: ShortMessage if selectedChannel == sm.getChannel =>


          val pitch = sm.getData1
            val lane  = pitch % 4

            val angle = (((pitch / 4) % 12) * 30).toFloat

            sm.getCommand match {
              case ShortMessage.NOTE_ON if sm.getData2 > 0 =>
                active += pitch -> trk.get(i).getTick

              case ShortMessage.NOTE_OFF | ShortMessage.NOTE_ON =>

                val start = active.remove(pitch).getOrElse(trk.get(i).getTick)
                val ms    = tickToMs(start)
                // Calcul du groupe et de l'angle dans le quart de cercle
                while (onScreen.nonEmpty && ms - onScreen.head > displayMs) onScreen.dequeue()
                if (onScreen.size >= maxOnScreen) {
                  // skip
                  noteCount += 1
                } else {
                val groupIdx = noteCount / groupSize
                val idxInGroup = noteCount % groupSize
                val startAngle = groupIdx * quarterCircle
                val angleStep = quarterCircle / (groupSize - 1)
                var destAngle = startAngle + idxInGroup * angleStep // 1.5 pour étendre l'angle de destination


                // Point de spawn (extérieur)
                var sx = cx + math.cos(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraspace)
                var sy = cy + math.sin(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraspace)
                // Destination sur le quart de cercle intérieur

                if(idxInGroup==0){
                  interX = cx + math.cos(math.random()*2*math.Pi).toFloat * ChartRadius
                  interY = cy + math.sin(math.random()*2*math.Pi).toFloat * ChartRadius

                  occupied++=currentgroup
                  currentgroup.clear()

                  extraspace = 0
                }
                var destX = interX + math.cos(Math.toRadians(destAngle)).toFloat * secondRadius
                var destY = interY + math.sin(Math.toRadians(destAngle)).toFloat * secondRadius


                val laneOffset = 60
                var key = (destX, destY)


                for(i <- 0 to 30) {
                  if (occupied.forall { case (x, y) =>
                    math.abs(x - key._1) >= 120 || math.abs(y - key._2) >= 120
                  }) {}else {

                    noteCount = noteCount + groupSize - idxInGroup
                    destAngle = (groupIdx + 1) * quarterCircle

                    if(i > 15){
                      noteCount += groupSize
                      destAngle += quarterCircle
                    }
                    if(i > 10){
                      occupied = occupied.tail
                    }

                    sx = cx + math.cos(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraspace)
                    sy = cy + math.sin(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraspace)

                    interX += (math.random().toFloat * laneOffset - laneOffset / 2)
                    interY += (math.random().toFloat * laneOffset - laneOffset / 2)

                    destX = interX + math.cos(Math.toRadians(destAngle)).toFloat * secondRadius
                    destY = interY + math.sin(Math.toRadians(destAngle)).toFloat * secondRadius
                    //println(s"[NoteLoader] Collision detected at $key, retrying with new position $i")
                    key = (destX, destY)
                    occupied ++= currentgroup
                    currentgroup.clear()
                    if (i == 29) println("failed finding a spot"+occupied)
                  }
                }
                currentgroup.addOne(key)
                while(occupied.length >= 20){
                  occupied = occupied.tail
                }

                out += Note(ms, lane, angle, sx, sy, destX, destY)
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

  val lane: Int   = n.lane
  val hitTime: Long = n.startMs
}}



class GameplayScreen(app: RhythmGame,
                     user : String,
                     token: String,
                     path : String,
                     difficulty : Int,
                     selectedChannel: Int) extends Screen2d {


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
  // wall-clock zero for manual timing fallback
  private var t0: Long = 0L
  private var sfx:Sound = _
  private var comboUp:Sound = _
  private var midiSeq: Sequencer = _






  // ----------------------------------------------------------------------

  private def pollInput(): Seq[Int] =
    Seq(Input.Keys.D -> 0, Input.Keys.W -> 1, Input.Keys.A -> 2, Input.Keys.S -> 3)
      .collect { case (k, l) if Gdx.input.isKeyJustPressed(k) => l }

  private def palette(l: Int): Color = l match {
    case 0 => new Color(0f/255, 121/255, 255f/255, 1f)
    case 1 => new Color(0f/255, 223f/255, 162f/255, 1f)
    case 2 => new Color(246f/255, 250f/255, 112f/255, 1f)
    case _ => new Color(255f/255, 0f/255, 96f/255, 1f)
  }

  override def show(): Unit = {
    cx = Gdx.graphics.getWidth / 2f
    cy = Gdx.graphics.getHeight / 2f

    sfx     = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/sfx.wav"))
    comboUp  = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/comboUp.wav"))

    val midiPath = s"data/tmp/$path"
    val seq = MidiSystem.getSequence(new java.io.File(midiPath))


    upcoming = NoteLoader.load(midiPath, cx, cy, difficulty,selectedChannel)



    midiSeq = MidiSystem.getSequencer()
    midiSeq.open()
    midiSeq.setSequence(Gdx.files.internal(midiPath).read())
    val synth = MidiSystem.getSynthesizer
    synth.open()
    for (ch <- synth.getChannels if ch != null) ch.controlChange(7, 127)
    midiSeq.start()


    t0 = System.currentTimeMillis()
  }

  private def nowMs: Long =
    Option(music).map(m => (m.getPosition * 1000).toLong)
      .filter(_ > 0).getOrElse(System.currentTimeMillis() - t0)

  // ----------------------------------------------------------------------
  override def render(g: GdxGraphics, dt: Float): Unit = {
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
        .filter(_.lane == lane)
        .minByOption(e => math.abs(e.hitTime - now))
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
          } else {
            feedbacks += Feedback(e.destX, e.destY, "Miss", now)
            live -= e
            combo = 1
            combocounter = 0
          }
        }
    }

    val justMissed = live.filter(e => now - e.hitTime > hitWindow)
    justMissed.foreach { e =>
      feedbacks += Feedback(e.destX, e.destY, "Miss", now)
      combo = 1
      combocounter = 0
    }
    live --= justMissed

    feedbacks.filterInPlace(f => now - f.born < 800)

    if (frame % LOG_EVERY_N_FRAMES == 0)
      println(
        s"[Frame $frame] now=$now live=${live.size} queue=${upcoming.size} score=$score"
      )

    if (Seq(Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D).exists(Gdx.input.isKeyJustPressed)) {
      sfx.play()
    }

    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      postScore(token, path, score)
      println(s"[Gameplay] Score posted: $score")

      Option(music).foreach(_.stop())

      if (midiSeq != null && midiSeq.isOpen) {
        midiSeq.stop()
        midiSeq.close()
      }

      app.switchScreen(new MainMenuScreen(app))
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




  def postScore(str: String, str1: String, i: Int): Boolean = {
    val json = s"""{"song": "$str1", "score": $i}"""
    val url = new URL(s"$baseUrl/score")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Authorization", s"$str")
    conn.setDoOutput(true)

    Using.resource(conn.getOutputStream) { os =>
      os.write(json.getBytes("UTF-8"))
    }

    val responseCode = conn.getResponseCode
    responseCode == 200
  }

  override def dispose(): Unit = {
    Option(music).foreach(_.dispose())
    Option(sfx).foreach(_.dispose())
    Option(comboUp).foreach(_.dispose())

    if (midiSeq != null && midiSeq.isOpen) {
      midiSeq.stop()
      midiSeq.close()
    }
  }


}


