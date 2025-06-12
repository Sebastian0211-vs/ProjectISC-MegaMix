package ch.hevs.gdx2d.rhythm

import javax.sound.midi.{MetaMessage, MidiEvent, Sequence, ShortMessage}
import scala.collection.mutable

object InstrumentExtractor {
  case class InstrumentChoice(
                               channel: Int,
                               program: Int,
                               bankMsb: Int,
                               bankLsb: Int,
                               name: String
                             )

  // GM1 melodic instrument names (0-127)
  private val gmMelodicNames: Array[String] = Array(
    // ... same 128 entries as before ...
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

  // GM percussion map for channel 9 (10 in spec)
  private val percussionMap: Map[Int, String] = Map(
    35 -> "Acoustic Bass Drum", 36 -> "Bass Drum 1", 38 -> "Acoustic Snare",
    40 -> "Electric Snare", 42 -> "Closed Hi-Hat", 46 -> "Open Hi-Hat",
    // Add additional key->drum mappings here
  )

  def extractInstruments(seq: Sequence): Seq[InstrumentChoice] = {
    // Track bank select (CC0, CC32) and program change per channel
    val bankMsb = mutable.Map[Int, Int]().withDefaultValue(0)
    val bankLsb = mutable.Map[Int, Int]().withDefaultValue(0)
    val programs = mutable.Map[Int, Int]().withDefaultValue(0)
    // Optional meta-defined instrument names per channel
    val metaNames = mutable.Map[Int, String]()
    // For percussion: track first note-ons per channel
    val percussionNotes = mutable.Map[Int, Int]()

    seq.getTracks.foreach { track =>
      for (i <- 0 until track.size()) {
        val ev = track.get(i)
        ev.getMessage match {
          case msg: ShortMessage =>
            msg.getCommand match {
              case ShortMessage.CONTROL_CHANGE =>
                msg.getData1 match {
                  case 0  => bankMsb(msg.getChannel) = msg.getData2
                  case 32 => bankLsb(msg.getChannel) = msg.getData2
                  case _  => // ignore
                }
              case ShortMessage.PROGRAM_CHANGE =>
                programs(msg.getChannel) = msg.getData1
              case ShortMessage.NOTE_ON if msg.getChannel == 9 && msg.getData2 > 0 =>
                percussionNotes.getOrElseUpdate(9, msg.getData1)
              case _ => // ignore
            }
          case mm: MetaMessage if mm.getType == 0x04 =>
            val name = new String(mm.getData, "UTF-8").trim
            // If there are per-channel meta names, you'd parse channel info; assuming channel 0
            metaNames(0) = name
          case _ => // ignore other message types
        }
      }
    }

    // Build a choice per channel (except channel 9 melodic entries)
    (0 until seq.getTracks.length).flatMap { _ => Nil } // placeholder, we want channels found

    val channels = (programs.keys ++ percussionNotes.keys).toSet
    channels.toSeq.sorted.map { ch =>
      if (ch == 9) {
        // percussion
        val note = percussionNotes.getOrElse(9, -1)
        val name = percussionMap.getOrElse(note, s"Percussion $note")
        InstrumentChoice(ch, program = -1, bankMsb = 0, bankLsb = 0, name)
      } else {
        val prg = programs(ch)
        val msb = bankMsb(ch)
        val lsb = bankLsb(ch)
        // choose name from meta override if any
        val name = metaNames.getOrElse(ch, resolveName(prg, msb, lsb))
        InstrumentChoice(ch, prg, msb, lsb, name)
      }
    }
  }

  private def resolveName(prg: Int, msb: Int, lsb: Int): String = {
    // For GM1 default bank
    if (msb == 0 && lsb == 0) {
      gmMelodicNames.lift(prg).getOrElse(s"Program $prg")
    } else {
      // Banked instrument: you would look up in a GM2/GS/XG table
      s"Bank($msb,$lsb) Prog $prg"
    }
  }
}
