// MainMenuScreen.scala  – single-window refactor
// -----------------------------------------------------------------------------
// This screen replaces the old PortableApplication subclass.  It lives inside
// the one and only desktop window owned by `RhythmGame` and drives the login /
// song-selection flow.  When the player presses Play we download the chosen
// MIDI (if not already cached) and hand control to GameplayScreen.
// -----------------------------------------------------------------------------
package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.{InputEvent, Stage}
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import ch.hevs.gdx2d.lib.GdxGraphics
import ch.hevs.gdx2d.hello.RhythmApiDemo._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Using

/** Menu → login → song list → Play.  All in one Stage. */
class MainMenuScreen(private val app: RhythmGame) extends Screen2d {

  // ───────────────────────────────────── UI widgets ────────────────────────
  private val skin      = new Skin(Gdx.files.internal("data/ui/uiskin.json"))
  private val stage     = new Stage()
  private val username  = new TextField("", skin)
  private val password  = new TextField("", skin)
  private val loginBtn  = new TextButton("Login", skin)
  private val songsBox  = new SelectBox[String](skin)
  private val playBtn   = new TextButton("Play",  skin)

  object Assets {
    var BackgroundBitMap : BitmapImage = _
    def init(): Unit = {
      BackgroundBitMap = new BitmapImage("data/Assets/BackGround/background.png")
    }
  }

  // runtime state -----------------------------------------------------------
  private var songs : Array[String] = Array.empty

  // ───────────────────────────── lifecycle callbacks ───────────────────────
  override def show(): Unit = {
    buildLayout()
    Assets.init()  // Load the background image
    Gdx.input.setInputProcessor(stage)
  }

  override def render(g: GdxGraphics, dt: Float): Unit = {
    g.clear(Color.DARK_GRAY)


    stage.act(dt)
    g.drawTransformedPicture(1920/2, 1080/2,0,1 , Assets.BackgroundBitMap)
    stage.draw()

  }

  override def dispose(): Unit = stage.dispose()

  // ───────────────────────────────── helper methods ────────────────────────
  private def buildLayout(): Unit = {
    // Title
    val title = new Label("Rhythm MegaMix", skin)
    title.setFontScale(2f)
    title.setPosition(260, 520)





    // Username / password fields
    username.setMessageText("Username")
    username.setSize(300, 40); username.setPosition(260, 440)

    password.setMessageText("Password")
    password.setPasswordMode(true); password.setPasswordCharacter('*')
    password.setSize(300, 40); password.setPosition(260, 380)

    // Login button
    loginBtn.setSize(140, 40); loginBtn.setPosition(260, 320)
    loginBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit =
        doLogin()
    })

    // Song list and Play
    songsBox.setSize(300, 40); songsBox.setPosition(260, 250)

    playBtn.setSize(140, 40); playBtn.setPosition(260, 190)
    playBtn.setDisabled(true)
    playBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit =
        startGame()
    })

    // Add actors to the stage
    stage.addActor(title)
    stage.addActor(username); stage.addActor(password)
    stage.addActor(loginBtn)
    stage.addActor(songsBox); stage.addActor(playBtn)
  }

  /** Called when Login is pressed. */
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

  /** Play becomes clickable only when we have both a token and a song list. */
  private def updatePlayButtonState(): Unit =
    playBtn.setDisabled(Session.token.isEmpty || songs.isEmpty)

  /** Download the chosen MIDI (if needed) and switch to gameplay. */
  private def startGame(): Unit = {
    val tok   = Session.token.getOrElse(return)
    val song  = Option(songsBox.getSelected).getOrElse(return)
    val user  = username.getText

    Future(downloadMidi(song)).foreach { _ =>
      Gdx.app.postRunnable { () =>
        app.switchScreen(new GameplayScreen(app, user, tok, song))
      }
    }
  }

  /** GET https://midis.triceratops.ch/midis/<song> and cache under data/tmp/. */
  private def downloadMidi(song: String): String = {
    val dstDir = Paths.get("data/tmp"); Files.createDirectories(dstDir)
    val dst    = dstDir.resolve(song)
    if (!Files.exists(dst)) {                           // cache hit?
      val src = new URL(s"https://midis.triceratops.ch/midis/$song")
      println(s"[Menu] Downloading $song …")
      Using.resource(src.openStream()) { in =>
        Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    dst.toString
  }

  /** Asynchronous GET of the server’s song list. */
  private def fetchSongs(): Future[Array[String]] = Future {
    val url  = new URL("https://midis.triceratops.ch/list")
    val json = scala.io.Source.fromInputStream(url.openStream).mkString
    implicit val f: Formats = DefaultFormats
    parse(json).extract[Array[String]]
  }
}
