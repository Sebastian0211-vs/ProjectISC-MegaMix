package ch.hevs.gdx2d.rhythm

import javax.sound.midi._
import scala.collection.mutable


object guessLead {
  def guessLeadInstrument(seq: Sequence): Option[Int] = {
    val channelNoteCounts = mutable.Map[Int, Int]()
    val channelNoteLengths = mutable.Map[Int, Long]()
    val activeNotes = mutable.Map[Int, Long]()

    seq.getTracks.foreach { track =>
      for (i <- 0 until track.size()) {
        track.get(i).getMessage match {
          case msg: ShortMessage =>
            val chan = msg.getChannel
            val cmd = msg.getCommand
            val key = msg.getData1
            val tick = track.get(i).getTick

            cmd match {
              case ShortMessage.NOTE_ON if msg.getData2 > 0 =>
                channelNoteCounts(chan) = channelNoteCounts.getOrElse(chan, 0) + 1
                activeNotes((chan << 8) | key) = tick

              case ShortMessage.NOTE_OFF =>
                val keyId = (chan << 8) | key
                activeNotes.get(keyId).foreach { start =>
                  val duration = tick - start
                  channelNoteLengths(chan) = channelNoteLengths.getOrElse(chan, 0L) + duration
                  activeNotes -= keyId
                }

              case ShortMessage.NOTE_ON if msg.getData2 == 0 =>
                val keyId = (chan << 8) | key
                activeNotes.get(keyId).foreach { start =>
                  val duration = tick - start
                  channelNoteLengths(chan) = channelNoteLengths.getOrElse(chan, 0L) + duration
                  activeNotes -= keyId
                }

              case _ => ()
            }


          case _ => ()
        }
      }
    }

    // Heuristic: choose the channel with the longest note duration
    channelNoteLengths
      .filterNot(_._1 == 9) // skip percussion
      .toSeq
      .sortBy { case (chan, duration) => -duration }
      .headOption
      .map(_._1)
  }
}
