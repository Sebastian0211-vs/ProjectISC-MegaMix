// -----------------------------------------------------------------------------
//  Jeu de rythme – Implémentation principale
//  -----------------------------------------------------------------------------
//  Ce fichier regroupe :
//   • Le chargement des assets (bitmaps & sons)
//   • Le modèle de données (Note, Feedback)
//   • La conversion d'un fichier MIDI en séquence de notes jouables
//   • L'entité graphique NoteEntity (calcul de position & dessin)
//   • L'écran de jeu (GameplayScreen) : logique d'entrée, scoring, rendu
//
//  Toutes les explications et les choix d'implémentation clés sont commentés
//  en français afin de faciliter la compréhension pour les développeurs
//  francophones.
// -----------------------------------------------------------------------------

package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import ch.hevs.gdx2d.rhythm.RhythmApi.postScore
import ch.hevs.gdx2d.lib.GdxGraphics
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.audio.{Music, Sound}
import com.badlogic.gdx.graphics.Color
import javax.sound.midi._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// ─────────────────────────────────────────────────────────────────────────────
//  CHARGEMENT DES RESSOURCES GRAPHIQUES & SONS
// ─────────────────────────────────────────────────────────────────────────────
/**
 *  Singleton chargé de préparer toutes les ressources nécessaires avant le jeu.
 *  On y trouve les bitmaps pour les notes, l'arrière‑plan et les feedbacks.
 */
object Assets {
  // Bitmaps utilisés dans l'écran de jeu. Le _ indique qu'ils seront
  // initialisés dans la méthode init().
  var NoteBitmap       : BitmapImage = _
  var BackgroundBitMap : BitmapImage = _
  var PerfectBitmap    : BitmapImage = _
  var GoodBitmap       : BitmapImage = _
  var MissBitmap       : BitmapImage = _
  var ArrowBitmap      : BitmapImage = _

  /**
   *  Initialise toutes les images à partir du répertoire data/Assets.
   *  Doit être appelée une seule fois au lancement de l'application.
   */
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
//  MODÈLE DE DONNÉES
// ─────────────────────────────────────────────────────────────────────────────
/**
 *  Représentation logique d'une note.
 *  @param startMs   Horodatage (en ms) du moment où la note doit être jouée.
 *  @param lane      Piste / direction (0 = droite, 1 = haut, 2 = gauche, 3 = bas).
 *  @param angle     Angle original (en degrés) utilisé pour l'animation.
 *  @param spawnX/Y  Coordonnées de départ (au bord du cercle extérieur).
 *  @param destX/Y   Coordonnées cibles (sur le deuxième cercle).
 */
case class Note(
                 startMs : Long,
                 lane    : Int,
                 angle   : Float,
                 spawnX  : Float,
                 spawnY  : Float,
                 destX   : Float,
                 destY   : Float,
               )

/**
 *  Feedback graphique (Perfect / Good / Miss) affiché pendant 800 ms.
 *  @param x,y  Position à l'écran.
 *  @param kind Type de feedback.
 *  @param born Instant de création (ms) pour gérer la durée de vie.
 */
case class Feedback(x: Float, y: Float, kind: String, born: Long)

// ─────────────────────────────────────────────────────────────────────────────
//  CONVERSION MIDI → NOTES JOUABLES
// ─────────────────────────────────────────────────────────────────────────────
/**
 *  NoteLoader lit le fichier MIDI fourni, détecte le tempo et génère la liste
 *  de notes (Note) prêtes à être utilisées par le moteur de jeu.
 */
object NoteLoader {
  // ─── constantes internes ───
  private val DefaultTPQ          = 480          // Pulsations par noire par défaut
  private val OuterChartRadius    : Float = 1000 // Rayon du cercle de spawn
  private val ChartRadius         : Float = 150  // Rayon du cercle central (pivot aléatoire)
  private val SecondRadius        : Float = 280  // Rayon du cercle d'arrivée des notes

  private var extraSpace: Int = 0                // Décalage pour éviter les collisions
  private var occupied = ArrayBuffer.empty[(Float, Float)] // Positions déjà occupées
  private val displayMs           = 1200L        // Durée d'affichage d'une note (ms)

  /**
   *  Charge un fichier MIDI et retourne un Vector[Note] prêt pour le gameplay.
   *  @param midiPath          Chemin vers le fichier MIDI.
   *  @param cx,cy             Centre du repère (cercle).
   *  @param difficulty        Niveau de difficulté (1 à 4) → max notes simultanées.
   *  @param selectedChannel   Canal MIDI à jouer.
   */
  def load(
            midiPath: String,
            cx: Float, cy: Float,
            difficulty: Int = 4,
            selectedChannel: Int
          ): Vector[Note] = {

    // ─── Lecture du fichier MIDI ───
    val seq = MidiSystem.getSequence(Gdx.files.internal(midiPath).file())
    val tpq = Option(seq.getResolution).filter(_ > 0).getOrElse(DefaultTPQ).toFloat

    // Niveau de difficulté → nombre max de notes affichées simultanément
    val maxOnScreen = difficulty match {
      case 1 => 3
      case 2 => 5
      case 3 => 7
      case _ => 100
    }

    // ─── Extraction de la carte de tempo ───
    val bpm   = extractBPM(midiPath).getOrElse(120f)
    val tempo = mutable.TreeMap[Long, Float](0L -> (60_000_000f / bpm))

    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) trk.get(i).getMessage match {
        case m: MetaMessage if m.getType == 0x51 =>
          // 0x51 = meta‑event tempo
          val d   = m.getData
          val mpq = ((d(0)&0xff)<<16)|((d(1)&0xff)<<8)|(d(2)&0xff) // micro‑s/quarter
          tempo += trk.get(i).getTick -> mpq.toFloat
        case _ =>
      }
    }

    // Convertit un tick MIDI en millisecondes réelles en se basant sur la map tempo
    def tickToMs(t: Long): Long = {
      var last = 0L; var us = 0f; var mpq = tempo.head._2
      tempo.takeWhile(_._1 <= t).foreach { case (tick, m) =>
        us += (tick - last) * mpq / tpq
        last = tick
        mpq  = m
      }
      us += (t - last) * mpq / tpq
      (us / 1000).toLong
    }

    // ─── Analyse des events MIDI et génération des notes ───
    val active   = mutable.Map[Int, Long]()              // notes appuyées (pitch → tick)
    val out      = Vector.newBuilder[Note]               // résultat final
    val onScreen = mutable.Queue[Long]()                 // timestamps déjà affichés

    val groupSize      = 6      // Nombre de notes dans un même « groupe » visuel
    val quarterCircle  = 95     // Amplitude (°) d'un quart de cercle
    var noteCount      = 0      // Compteur global de notes traitées

    var interX = cx + math.cos(Math.toRadians(0)).toFloat * SecondRadius
    var interY = cy + math.sin(Math.toRadians(0)).toFloat * SecondRadius

    val currentGroup = ArrayBuffer.empty[(Float, Float)]

    seq.getTracks.foreach { trk =>
      for (i <- 0 until trk.size()) trk.get(i).getMessage match {
        // On ne garde que les messages du canal sélectionné
        case sm: ShortMessage if selectedChannel == sm.getChannel =>
          val pitch = sm.getData1
          val lane  = pitch % 4                 // Répartition sur 4 lanes
          val angle = (((pitch / 4) % 12) * 30).toFloat // 12 * 30° = cercle complet

          sm.getCommand match {
            // NOTE_ON (vélocité > 0) → début de note
            case ShortMessage.NOTE_ON if sm.getData2 > 0 =>
              active += pitch -> trk.get(i).getTick

            // NOTE_OFF ou NOTE_ON (vel = 0) → fin de note
            case ShortMessage.NOTE_OFF | ShortMessage.NOTE_ON =>
              val start = active.remove(pitch).getOrElse(trk.get(i).getTick)
              val ms    = tickToMs(start)

              // Gestion de la file pour respecter le maxOnScreen
              while (onScreen.nonEmpty && ms - onScreen.head > displayMs) onScreen.dequeue()
              if (onScreen.size >= maxOnScreen) {
                noteCount += 1 // On saute la note si trop d'éléments à l'écran
              } else {
                // ─── Placement de la note ───
                val groupIdx    = noteCount / groupSize
                val idxInGroup  = noteCount % groupSize
                val startAngle  = groupIdx * quarterCircle
                val angleStep   = quarterCircle / (groupSize - 1)
                var destAngle   = startAngle + idxInGroup * angleStep

                // Calcul du point de spawn (bord extérieur)
                var sx = cx + math.cos(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraSpace)
                var sy = cy + math.sin(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraSpace)

                // Point d'intersection (cercle intermédiaire) réinitialisé au début d'un groupe
                if (idxInGroup == 0) {
                  interX = cx + math.cos(math.random()*2*math.Pi).toFloat * ChartRadius
                  interY = cy + math.sin(math.random()*2*math.Pi).toFloat * ChartRadius

                  occupied ++= currentGroup
                  currentGroup.clear()
                  extraSpace = 0
                }

                var destX = interX + math.cos(Math.toRadians(destAngle)).toFloat * SecondRadius
                var destY = interY + math.sin(Math.toRadians(destAngle)).toFloat * SecondRadius

                // Gestion rudimentaire des collisions : on re‑essaie jusqu'à trouver un espace libre
                val laneOffset = 60
                var key = (destX, destY)
                for (retry <- 0 to 30) {
                  if (occupied.forall { case (x, y) =>
                    math.abs(x - key._1) >= 120 || math.abs(y - key._2) >= 120
                  }) {
                    // Emplacement libre → on valide
                  } else {
                    // Collision détectée → on change d'angle et on retente
                    noteCount = noteCount + groupSize - idxInGroup
                    destAngle = (groupIdx + 1) * quarterCircle

                    if (retry > 15) {
                      noteCount += groupSize
                      destAngle += quarterCircle
                    }
                    if (retry > 10 && occupied.nonEmpty) occupied = occupied.tail

                    sx = cx + math.cos(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraSpace)
                    sy = cy + math.sin(Math.toRadians(destAngle)).toFloat * (OuterChartRadius + extraSpace)

                    interX += (math.random().toFloat * laneOffset - laneOffset / 2)
                    interY += (math.random().toFloat * laneOffset - laneOffset / 2)

                    destX = interX + math.cos(Math.toRadians(destAngle)).toFloat * SecondRadius
                    destY = interY + math.sin(Math.toRadians(destAngle)).toFloat * SecondRadius

                    key = (destX, destY)
                    occupied ++= currentGroup
                    currentGroup.clear()
                    if (retry == 29) println("[NoteLoader] Impossible de trouver une place :" + occupied)
                  }
                }

                // On évite que les notes soient hors‑écran en ajustant Y
                while (destY < 50)  destY += 30
                while (destY > 1030) destY -= 30

                currentGroup.addOne(key)       // Sauvegarde pour vérif collisions
                while (occupied.length >= 20) occupied = occupied.tail // Limite mémoire

                // Ajout de la note au résultat
                out += Note(ms, lane, angle, sx, sy, destX, destY)
                onScreen.enqueue(ms)
                noteCount += 1
              }
            case _ =>
          }
        case _ =>
      }
    }

    out.result().sortBy(_.startMs) // tri final par timestamp
  }

  /**
   *  Extrait le BPM d'un fichier MIDI si l'information est présente.
   */
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

// ─────────────────────────────────────────────────────────────────────────────
//  ENTITÉ GRAPHIQUE DES NOTES
// ─────────────────────────────────────────────────────────────────────────────
/**
 *  Représente une note à l'écran. Responsable du calcul de sa position
 *  (interpolation spawn → destination) et de son dessin.
 */
class NoteEntity(n: Note, colour: Color) {
  val lane    : Int   = n.lane
  val hitTime : Long  = n.startMs
  val destX   : Float = n.destX
  val destY   : Float = n.destY

  /**
   *  Calcule la position interpolée entre spawn et destination en fonction du temps.
   *  @return (x, y) courants de la note.
   */
  private def pos(now: Long): (Float, Float) = {
    val t = ((n.startMs - now).toFloat / 1200f).max(0f).min(1f)
    val x = n.spawnX + (n.destX - n.spawnX) * (1 - t)
    val y = n.spawnY + (n.destY - n.spawnY) * (1 - t)
    (x, y)
  }

  /**
   *  Indique si la note est dans la fenêtre de hit.
   */
  def hittable(now: Long, win: Int): Boolean = (n.startMs - now).abs <= win

  /**
   *  Dessine la note avec un cercle fantôme pour l'effet de traînée.
   */
  def draw(g: GdxGraphics, now: Long): Unit = {
    val (x, y)  = pos(now)
    val distance      = math.hypot(n.destX - x, n.destY - y).toFloat
    val transparency  = math.min(1f, 0.5f - distance / 1000f)
    val ghost         = new Color(colour.r, colour.g, colour.b, transparency)

    var arrowAngle = n.angle

    // Cercle principal (note)
    g.drawFilledCircle(x, y, 60, colour)
    // Cercle fantôme à la destination pour donner une indication visuelle
    g.drawTransformedPicture(n.destX, n.destY, 0, 0.2f, Assets.NoteBitmap)
    g.drawFilledCircle(n.destX, n.destY, 60, ghost)

    // Flèche indiquant la direction (lane)
    n.lane match {
      case 0 => arrowAngle = 0f
      case 1 => arrowAngle = 90f
      case 2 => arrowAngle = 180f
      case _ => arrowAngle = 270f
    }
    g.drawTransformedPicture(n.destX, n.destY, arrowAngle, 0.2f, Assets.ArrowBitmap)
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAMEPLAY SCREEN : logique temps réel et rendu
// ─────────────────────────────────────────────────────────────────────────────
class GameplayScreen(
                      app            : RhythmGame,
                      token          : String,
                      path           : String,
                      difficulty     : Int,
                      selectedChannel: Int
                    ) extends Screen2d {

  // ─── constantes de timing ───
  private val hitWindow      = 120        // Fenêtre « Good » ± ms
  private val perfectWindow  = 50         // Fenêtre « Perfect » ± ms
  private val lead           = 1200L      // Temps d'avance pour le spawn des notes
  private val LOG_EVERY_N_FRAMES = 120    // Fréquence d'affichage dans la console

  // ─── état dynamique ───
  private val live       = mutable.ListBuffer[NoteEntity]()
  private var upcoming   = Vector.empty[Note]
  private val feedbacks  = mutable.ListBuffer[Feedback]()
  private var score      = 0
  private var combo      = 1    // Multiplicateur de combo (min 1)
  private var comboCounter = 0  // Compteur avant le prochain comboUp
  private var music      : Music = _

  private var cx = 0f; private var cy = 0f
  private var frame = 0

  private var t0: Long = 0L
  private var sfx: Sound = _
  private var comboUp: Sound = _
  private var midiSeq: Sequencer = _

  // ----------------------------------------------------------------------
  //  Gestion des entrées clavier (WASD → lanes)
  // ----------------------------------------------------------------------
  private def pollInput(): Seq[Int] =
    Seq(Input.Keys.D -> 0, Input.Keys.W -> 1, Input.Keys.A -> 2, Input.Keys.S -> 3)
      .collect { case (k, l) if Gdx.input.isKeyJustPressed(k) => l }

  // Palette de couleurs associée aux 4 lanes
  private def palette(l: Int): Color = l match {
    case 0 => new Color(0f/255, 121/255, 255f/255, 1f) // Bleu
    case 1 => new Color(0f/255, 223f/255, 162f/255, 1f) // Vert
    case 2 => new Color(246f/255, 250f/255, 112f/255, 1f) // Jaune
    case _ => new Color(255f/255, 0f/255, 96f/255, 1f)   // Rouge
  }

  // ----------------------------------------------------------------------
  //  Initialisation de l'écran de jeu
  // ----------------------------------------------------------------------
  override def show(): Unit = {
    // Centre de l'écran (pour le cercle)
    cx = Gdx.graphics.getWidth / 2f
    cy = Gdx.graphics.getHeight / 2f

    // Effets sonores
    sfx      = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/sfx.wav"))
    comboUp  = Gdx.audio.newSound(Gdx.files.internal("data/Assets/sfx/comboUp.wav"))

    // Chargement des notes depuis le MIDI sélectionné
    val midiPath = s"data/tmp/$path"
    upcoming = NoteLoader.load(midiPath, cx, cy, difficulty, selectedChannel)

    // Lecture MIDI (séquenceur Java Sound)
    midiSeq = MidiSystem.getSequencer()
    midiSeq.open()
    midiSeq.setSequence(Gdx.files.internal(midiPath).read())
    val synth = MidiSystem.getSynthesizer
    synth.open()
    // Volume au maximum
    for (ch <- synth.getChannels if ch != null) ch.controlChange(7, 127)
    midiSeq.start()

    t0 = System.currentTimeMillis() // point de référence pour le temps
  }

  /**
   *  Temps courant dans la partie (ms).
   *  Si on lit une Music LibGDX, on utilise sa position, sinon le temps système.
   */
  private def nowMs: Long =
    Option(music).map(m => (m.getPosition * 1000).toLong)
      .filter(_ > 0).getOrElse(System.currentTimeMillis() - t0)

  // ----------------------------------------------------------------------
  //  Boucle principale : update logique + rendu
  // ----------------------------------------------------------------------
  override def render(g: GdxGraphics, dt: Float): Unit = {
    frame += 1
    val now = nowMs

    // ─── spawn des prochaines notes ───
    // On élimine les doublons (mêmes lane & timestamp) et on les trie.
    upcoming = upcoming
      .groupBy(n => (n.lane, n.startMs))
      .values.map(_.head).toVector.sortBy(_.startMs)

    var spawned = 0
    while (upcoming.nonEmpty && upcoming.head.startMs - lead <= now) {
      val n = upcoming.head
      upcoming = upcoming.tail
      live += new NoteEntity(n, palette(n.lane))
      spawned += 1
    }
    if (spawned > 0)
      println(s"[Spawn] $spawned notes (queue left: ${upcoming.size}) @ $now ms")

    // ─── Gestion des entrées & jugement ───
    for (lane <- pollInput()) {
      live
        .filter(_.lane == lane)
        .minByOption(e => math.abs(e.hitTime - now)) // plus proche de la frappe
        .foreach { e =>
          if (e.hittable(now, hitWindow)) {
            val diff = (e.hitTime - now).abs
            val (kind, pts) = if (diff <= perfectWindow) ("Perfect", 300) else ("Good", 100)

            comboCounter += 1
            if (comboCounter > 9) {
              combo = math.min(2 * combo, 16) // augmentation exponentielle limitée
              comboCounter = 0
              comboUp.play()
            }
            score += pts * combo
            feedbacks += Feedback(e.destX, e.destY, kind, now)
            live -= e
          } else {
            // Râté : on réinitialise le combo
            feedbacks += Feedback(e.destX, e.destY, "Miss", now)
            live -= e
            combo = 1
            comboCounter = 0
          }
        }
    }

    // Gestion des notes manquées (trop tard)
    val justMissed = live.filter(e => now - e.hitTime > hitWindow)
    justMissed.foreach { e =>
      feedbacks += Feedback(e.destX, e.destY, "Miss", now)
      combo = 1
      comboCounter = 0
    }
    live --= justMissed

    // On garde les feedbacks pendant 800 ms maximum
    feedbacks.filterInPlace(f => now - f.born < 800)

    // Log périodique (debug)
    if (frame % LOG_EVERY_N_FRAMES == 0)
      println(s"[Frame $frame] now=$now live=${live.size} queue=${upcoming.size} score=$score")

    // Effet sonore sur frappe (peu importe le jugement)
    if (Seq(Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D).exists(Gdx.input.isKeyJustPressed)) {
      sfx.play(0.1f)
    }

    // Quitter la partie → Escape
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

    // ─── Rendu ───
    g.clear(Color.DARK_GRAY)
    live.foreach(_.draw(g, now))

    // Feedbacks (Perfect / Good / Miss)
    for (fb <- feedbacks) {
      val bmp = fb.kind match {
        case "Perfect" => Assets.PerfectBitmap
        case "Good"    => Assets.GoodBitmap
        case _          => Assets.MissBitmap
      }
      g.drawTransformedPicture(fb.x, fb.y, 0, 0.1f, bmp)
    }

    // HUD (informations de debug)
    g.drawString(20, Gdx.graphics.getHeight - 40,  s"Active notes: ${live.size}")
    g.drawString(20, Gdx.graphics.getHeight - 80,  s"Score: $score")
    g.drawString(20, Gdx.graphics.getHeight - 120, s"Combo: x$combo ; Counter: $comboCounter")
    g.drawFPS()
  }

  // ----------------------------------------------------------------------
  //  Libération des ressources
  // ----------------------------------------------------------------------
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
