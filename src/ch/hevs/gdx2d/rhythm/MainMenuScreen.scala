package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, Stage}
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.{ChangeListener, ClickListener}
import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.rhythm.RhythmApi.{login, register}
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._

import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Using

class MainMenuScreen(private val app: RhythmGame) extends Screen2d {

  // ───────────────────────────────────── UI widgets ────────────────────────
  private val skin          = new Skin(Gdx.files.internal("data/ui/uiskin.json"))
  private val stage         = new Stage()
  private val username      = new TextField("", skin)
  private val password      = new TextField("", skin)
  private val loginBtn      = new TextButton("Login", skin)
  private val songsBox      = new SelectBox[String](skin)
  private val playBtn       = new TextButton("Play", skin)
  private val difficultyBox = new SelectBox[String](skin)
  private val instrumentBox = new SelectBox[String](skin)

  object Assets {
    var BackgroundBitMap: BitmapImage = _
    var qrImage: BitmapImage           = _
    def init(): Unit = {
      BackgroundBitMap = new BitmapImage("data/Assets/BackGround/background.png")
      qrImage          = new BitmapImage("data/Assets/QRCode/qr-code.png")
    }
  }

  private var songs: Array[String] = Array.empty

  // ───────────────────────────── lifecycle ────────────────────────────────
  override def show(): Unit = {
    Session.load()
    if (Session.token.isDefined) {
      loginBtn.setDisabled(true)
      fetchSongs().foreach { list =>
        songs = list
        Gdx.app.postRunnable { () =>
          songsBox.setItems(songs: _*)
          updatePlayButtonState()
          loginBtn.setDisabled(false)
        }
      }
    }
    buildLayout()
    Assets.init()
    Gdx.input.setInputProcessor(stage)
  }

  override def render(g: GdxGraphics, dt: Float): Unit = {
    g.clear(Color.DARK_GRAY)
    stage.act(dt)
    g.drawTransformedPicture(1920 / 2, 1080 / 2, 0, 1.5f, Assets.BackgroundBitMap)
    g.drawTransformedPicture(1600, 180, 0, 0.3f, Assets.qrImage)
    g.drawString(1660, 100, "Scan to visit site!")
    stage.draw()
  }

  override def dispose(): Unit = stage.dispose()

  // ───────────────────────────── layout + logic ─────────────────────────────
  private def buildLayout(): Unit = {
    val title = new Label("ProjectISC - MegaMix", skin)
    title.setFontScale(2f)
    title.setPosition(260, 520)

    username.setMessageText("Username")
    username.setSize(300, 40)
    username.setPosition(260, 440)

    password.setMessageText("Password")
    password.setPasswordMode(true)
    password.setPasswordCharacter('*')
    password.setSize(300, 40)
    password.setPosition(260, 380)

    loginBtn.setSize(140, 40)
    loginBtn.setPosition(260, 320)
    loginBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit = {
        try doLogin()
        catch {
          case ex: Throwable =>
            println(s"Error with credentials: $ex")
            doRegister()
        }
      }
    })

    songsBox.setSize(300, 40)
    songsBox.setPosition(260, 250)
    songsBox.addListener(new ChangeListener {
      override def changed(event: ChangeEvent, actor: Actor): Unit = {
        Option(songsBox.getSelected).foreach(populateInstruments)
      }
    })

    playBtn.setSize(140, 40)
    playBtn.setPosition(260, 190)
    playBtn.setDisabled(true)
    playBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit = startGame()
    })

    difficultyBox.setItems("1 - Easy", "2 - Normal", "3 - Hard", "4 - Expert")
    difficultyBox.setSize(300, 40)
    difficultyBox.setPosition(260, 130)

    instrumentBox.setSize(300, 40)
    instrumentBox.setPosition(260, 70)

    Seq(title, username, password, loginBtn,
      songsBox, difficultyBox, instrumentBox, playBtn).foreach(stage.addActor)
  }

  private def doLogin(): Unit = {
    val user = username.getText
    val pass = password.getText
    loginBtn.setDisabled(true)

    login(user, pass).foreach { tok =>
      Session.token = Some(tok)
      fetchSongs().foreach { list =>
        songs = list
        Gdx.app.postRunnable { () =>
          songsBox.setItems(songs: _*)
          updatePlayButtonState()
          loginBtn.setDisabled(false)
        }
      }
    }
  }

  private def doRegister(): Unit = {
    val user = username.getText
    val pass = password.getText
    val success = register(user, pass)

    Gdx.app.postRunnable { () =>
      if (success) {
        println("✅ Registration successful")
        doLogin()
      } else {
        println("❌ Registration failed")
      }
    }
  }

  private def updatePlayButtonState(): Unit =
    playBtn.setDisabled(Session.token.isEmpty || songs.isEmpty)

  private def populateInstruments(song: String): Unit = {
    instrumentBox.setItems("Loading...")

    Future {
      val midiPath = downloadMidi(song)
      val seq      = javax.sound.midi.MidiSystem.getSequence(new java.io.File(midiPath))
      InstrumentExtractor.extractInstruments(seq)
        .map(i => s"Ch${i.channel}: ${i.name}")
        .toArray
    }.foreach { entries =>
      Gdx.app.postRunnable { () =>
        if (entries.nonEmpty) instrumentBox.setItems(entries: _*)
        else instrumentBox.setItems("No instruments found")
        instrumentBox.setSelectedIndex(0)
      }
    }
  }

  private def startGame(): Unit = {
    val tok        = Session.token.getOrElse(return)
    val song       = Option(songsBox.getSelected).getOrElse(return)
    val user       = username.getText
    val difficulty = difficultyBox.getSelected.charAt(0).asDigit

    // fix: channel must be returned
    val channel: Int = Option(instrumentBox.getSelected).flatMap { sel =>
      sel.split(":", 2).headOption.flatMap { head =>
        head.stripPrefix("Ch").toIntOption
      }
    }.getOrElse(0)

    // fix: balance parentheses
    Future(downloadMidi(song)).foreach { _ =>
      Gdx.app.postRunnable(() =>
        app.switchScreen(
          new GameplayScreen(app, user, tok, song, difficulty, channel)
        )
      )
    }
  }

  private def downloadMidi(song: String): String = {
    val dstDir = Paths.get("data/tmp")
    Files.createDirectories(dstDir)
    val dst    = dstDir.resolve(song)
    if (!Files.exists(dst)) {
      val src = new URL(s"https://midis.triceratops.ch/midis/$song")
      println(s"[Menu] Downloading $song …")
      Using.resource(src.openStream()) { in =>
        Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    dst.toString
  }

  private def fetchSongs(): Future[Array[String]] = Future {
    val url   = new URL("https://midis.triceratops.ch/list")
    val json  = scala.io.Source.fromInputStream(url.openStream()).mkString
    implicit val formats: Formats = DefaultFormats
    parse(json).extract[Array[String]]
  }
}
