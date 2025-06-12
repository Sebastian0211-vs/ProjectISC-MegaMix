// -----------------------------------------------------------------------------
//  Extracteur d'instruments MIDI – utilitaire pour l'écran de sélection
// -----------------------------------------------------------------------------
//  Cette classe parcourt une Sequence MIDI afin d'identifier :
//   • Les banques (MSB/LSB) et les Program Change utilisés par canal
//   • Les percussions déclenchées sur le canal 10 (index 9)
//   • Les éventuels noms d'instruments définis dans les meta‑events (type 0x04)
//
//  Elle renvoie une liste InstrumentChoice, réutilisée par le menu pour remplir
//  la ComboBox des instruments disponibles.
// -----------------------------------------------------------------------------

package ch.hevs.gdx2d.rhythm

import javax.sound.midi.{MetaMessage, Sequence, ShortMessage}
import scala.collection.mutable

object InstrumentExtractor {

  // ──────────────────────────────────────────────────────────────────────────
  //  Type retourné pour la UI
  // ──────────────────────────────────────────────────────────────────────────
  case class InstrumentChoice(
                               channel : Int,  // N° de canal (0‒15)
                               program : Int,  // N° de programme (0‒127) ou -1 pour percussions
                               bankMsb : Int,  // Bank Select MSB (CC#0)
                               bankLsb : Int,  // Bank Select LSB (CC#32)
                               name    : String // Nom lisible de l'instrument
                             )

  // ──────────────────────────────────────────────────────────────────────────
  //  Tables de noms – standard GM1
  // ──────────────────────────────────────────────────────────────────────────
  /** Noms GM1 pour les instruments mélodiques (Program Change 0‑127). */
  private val gmMelodicNames: Array[String] = Array(
    "Acoustic Grand Piano", "Bright Piano", "Electric Grand", "Honky-Tonk", "Electric Piano 1", "Electric Piano 2",
    "Harpsichord", "Clavinet", "Celesta", "Glockenspiel", "Music Box", "Vibraphone", "Marimba", "Xylophone", "Tubular Bells", "Dulcimer",
    "Drawbar Organ", "Percussive Organ", "Rock Organ", "Church Organ", "Reed Organ", "Accordion", "Harmonica", "Tango Accordion",
    "Acoustic Guitar (nylon)", "Acoustic Guitar (steel)", "Electric Guitar (jazz)", "Electric Guitar (clean)", "Electric Guitar (muted)", "Overdriven Guitar",
    "Distortion Guitar", "Guitar Harmonics", "Acoustic Bass", "Electric Bass (finger)", "Electric Bass (pick)", "Fretless Bass", "Slap Bass 1", "Slap Bass 2",
    "Synth Bass 1", "Synth Bass 2", "Violin", "Viola", "Cello", "Contrabass", "Tremolo Strings", "Pizzicato Strings",
    "Orchestral Harp", "Timpani", "String Ensemble 1", "String Ensemble 2", "Synth Strings 1", "Synth Strings 2", "Choir Aahs", "Voice Oohs",
    "Synth Choir", "Orchestra Hit", "Trumpet", "Trombone", "Tuba", "Muted Trumpet", "French Horn", "Brass Section",
    "Synth Brass 1", "Synth Brass 2", "Soprano Sax", "Alto Sax", "Tenor Sax", "Baritone Sax", "Oboe", "English Horn",
    "Bassoon", "Clarinet", "Piccolo", "Flute", "Recorder", "Pan Flute", "Blown Bottle", "Shakuhachi",
    "Whistle", "Ocarina", "Lead 1 (square)", "Lead 2 (sawtooth)", "Lead 3 (calliope)", "Lead 4 (chiff)", "Lead 5 (charang)", "Lead 6 (voice)",
    "Lead 7 (fifths)", "Lead 8 (bass+lead)", "Pad 1 (new age)", "Pad 2 (warm)", "Pad 3 (polysynth)", "Pad 4 (choir)", "Pad 5 (bowed)", "Pad 6 (metallic)",
    "Pad 7 (halo)", "Pad 8 (sweep)", "FX 1 (rain)", "FX 2 (soundtrack)", "FX 3 (crystal)", "FX 4 (atmosphere)", "FX 5 (brightness)", "FX 6 (goblins)",
    "FX 7 (echoes)", "FX 8 (sci-fi)", "Sitar", "Banjo", "Shamisen", "Koto", "Kalimba", "Bag pipe", "Fiddle", "Shanai",
    "Tinkle Bell", "Agogo", "Steel Drums", "Woodblock", "Taiko Drum", "Melodic Tom", "Synth Drum", "Reverse Cymbal",
    "Guitar Fret Noise", "Breath Noise", "Seashore", "Bird Tweet", "Telephone Ring", "Helicopter", "Applause", "Gunshot"
  )

  /**
   *  Mapping simplifié note → nom pour les percussions (canal 10). On n'en
   *  dresse qu'une courte liste des sons les plus courants.
   */
  private val percussionMap: Map[Int, String] = Map(
    35 -> "Acoustic Bass Drum", 36 -> "Bass Drum 1", 38 -> "Acoustic Snare",
    40 -> "Electric Snare", 42 -> "Closed Hi-Hat", 46 -> "Open Hi-Hat",
  )

  // ──────────────────────────────────────────────────────────────────────────
  //  Fonction principale – parcours de la Sequence
  // ──────────────────────────────────────────────────────────────────────────
  /**
   *  Analyse la Sequence MIDI et retourne la liste des InstrumentChoice.
   */
  def extractInstruments(seq: Sequence): Seq[InstrumentChoice] = {

    // Maps mutables pour suivre l'état par canal
    val bankMsb  = mutable.Map[Int, Int]().withDefaultValue(0) // CC#0
    val bankLsb  = mutable.Map[Int, Int]().withDefaultValue(0) // CC#32
    val programs = mutable.Map[Int, Int]().withDefaultValue(0) // Program Change

    val metaNames       = mutable.Map[Int, String]()           // nom via Meta (type 0x04)
    val percussionNotes = mutable.Map[Int, Int]()              // première note jouée canal 9

    // ─── Parcours de tous les tracks / événements ───
    seq.getTracks.foreach { track =>
      for (i <- 0 until track.size()) {
        track.get(i).getMessage match {
          case msg: ShortMessage =>
            msg.getCommand match {
              case ShortMessage.CONTROL_CHANGE => msg.getData1 match {
                case 0  => bankMsb(msg.getChannel) = msg.getData2      // Bank MSB
                case 32 => bankLsb(msg.getChannel) = msg.getData2      // Bank LSB
                case _  => // autres CC ignorés
              }
              case ShortMessage.PROGRAM_CHANGE =>
                programs(msg.getChannel) = msg.getData1               // Program
              case ShortMessage.NOTE_ON if msg.getChannel == 9 && msg.getData2 > 0 =>
                // On retient la première note de percussion pour nommer l'instrument
                percussionNotes.getOrElseUpdate(9, msg.getData1)
              case _ => // autres messages ignorés
            }

          case mm: MetaMessage if mm.getType == 0x04 =>
            // MetaEvent type 0x04 = Track/Instrument Name
            val name = new String(mm.getData, "UTF-8").trim
            // On associe par convention au canal 0 (ou autre logique si souhaitée)
            metaNames(0) = name
          case _ => // événements non gérés
        }
      }
    }

    // ─── Construit la liste finale des canaux rencontrés ───
    val channels = (programs.keys ++ percussionNotes.keys).toSet

    // Pour chaque canal → InstrumentChoice
    channels.toSeq.sorted.map { ch =>
      if (ch == 9) {
        // Canal 10 = batterie / percussions GM
        val note = percussionNotes.getOrElse(9, -1)
        val name = percussionMap.getOrElse(note, s"Percussion $note")
        InstrumentChoice(ch, program = -1, bankMsb = 0, bankLsb = 0, name)
      } else {
        val prg = programs(ch); val msb = bankMsb(ch); val lsb = bankLsb(ch)
        val name = metaNames.getOrElse(ch, resolveName(prg, msb, lsb))
        InstrumentChoice(ch, prg, msb, lsb, name)
      }
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Résolution du nom de l'instrument (fallback)
  // ──────────────────────────────────────────────────────────────────────────
  private def resolveName(prg: Int, msb: Int, lsb: Int): String = {
    // Cas le plus courant : banque GM1 (0,0)
    if (msb == 0 && lsb == 0)
      gmMelodicNames.lift(prg).getOrElse(s"Program $prg")
    else
      s"Bank($msb,$lsb) Prog $prg" // sinon on affiche la banque brute
  }
}
