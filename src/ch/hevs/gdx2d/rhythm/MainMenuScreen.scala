// -----------------------------------------------------------------------------
//  Écran principal – Menu du jeu de rythme
//  -----------------------------------------------------------------------------
//  Cette classe implémente le menu d'accueil : authentification, sélection de
//  chanson, difficulté, instrument, et lancement de la partie. Tout est
//  commenté en français pour une compréhension optimale par l'équipe.
// -----------------------------------------------------------------------------

package ch.hevs.gdx2d.rhythm

import ch.hevs.gdx2d.components.bitmaps.BitmapImage
import ch.hevs.gdx2d.lib.GdxGraphics
import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, Stage}
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.{ChangeListener, ClickListener}
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._

import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Using

/**
 *  Écran du menu principal. Permet :
 *   • Connexion / inscription (via RhythmApi)
 *   • Sélection d'une chanson et d'un instrument MIDI
 *   • Choix de la difficulté
 *   • Démarrage de la partie (GameplayScreen)
 */
class MainMenuScreen(private val app: RhythmGame) extends Screen2d {

  // ────────────────────────────────────────────
  //  Widgets UI (Scene2D)
  // ────────────────────────────────────────────
  private val skin          = new Skin(Gdx.files.internal("data/ui/uiskin.json"))
  private val stage         = new Stage()
  private val username      = new TextField("", skin)
  private val password      = new TextField("", skin)
  private val loginBtn      = new TextButton("Login", skin)
  private val songsBox      = new SelectBox[String](skin)
  private val playBtn       = new TextButton("Play", skin)
  private val difficultyBox = new SelectBox[String](skin)
  private val instrumentBox = new SelectBox[String](skin)

  // Assets graphiques statiques pour l'arrière-plan et le QR code
  object Assets {
    var background: BitmapImage = _
    var qrCode    : BitmapImage = _
    def init(): Unit = {
      background = new BitmapImage("data/Assets/BackGround/background.png")
      qrCode     = new BitmapImage("data/Assets/QRCode/qr-code.png")
    }
  }

  private var songs: Array[String] = Array.empty // liste mise à jour après login

  // ────────────────────────────────────────────
  //  Cycle de vie de l'écran
  // ────────────────────────────────────────────
  override def show(): Unit = {
    Session.load()                       // charge éventuel token sauvegardé

    // Si l'utilisateur est déjà connecté → précharge la liste de chansons
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

    buildLayout()                        // construit tous les widgets
    Assets.init()                        // charge bitmaps
    Gdx.input.setInputProcessor(stage)   // Scene2D capte les entrées
  }

  /**
   *  Boucle de rendu : efface l'écran, dessine l'arrière-plan et la scène UI.
   */
  override def render(g: GdxGraphics, dt: Float): Unit = {
    g.clear(Color.DARK_GRAY)
    stage.act(dt)

    // Arrière-plan centré (zoom 1.5) et QR code discrètement à droite
    g.drawTransformedPicture(1920 / 2, 1080 / 2, 0, 1.5f, Assets.background)
    g.drawTransformedPicture(1600, 180, 0, 0.3f, Assets.qrCode)
    g.drawString(1660, 100, "Scan to visit site!")

    stage.draw() // widgets Scene2D par-dessus
  }

  override def dispose(): Unit = stage.dispose()

  // ────────────────────────────────────────────
  //  Construction de l'interface
  // ────────────────────────────────────────────
  private def buildLayout(): Unit = {
    // Titre principal
    val title = new Label("ProjectISC - MegaMix", skin)
    title.setFontScale(2f)
    title.setPosition(260, 520)

    // Champs texte login / mot de passe
    username.setMessageText("Username"); username.setSize(300, 40); username.setPosition(260, 440)
    password.setMessageText("Password"); password.setPasswordMode(true); password.setPasswordCharacter('*');
    password.setSize(300, 40); password.setPosition(260, 380)

    // Bouton Login / Register auto fallback
    loginBtn.setSize(140, 40); loginBtn.setPosition(260, 320)
    loginBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit = {
        try doLogin()
        catch {
          case ex: Throwable =>
            println(s"Error with credentials: $ex")
            doRegister() // tentative d'inscription si login rate
        }
      }
    })

    // Combo chanson (rempli après login)
    songsBox.setSize(300, 40); songsBox.setPosition(260, 250)
    songsBox.addListener(new ChangeListener {
      override def changed(event: ChangeEvent, actor: Actor): Unit = {
        Option(songsBox.getSelected).foreach(populateInstruments)
      }
    })

    // Bouton Play (activé seulement si token + chanson)
    playBtn.setSize(140, 40); playBtn.setPosition(260, 190); playBtn.setDisabled(true)
    playBtn.addListener(new ClickListener {
      override def clicked(e: InputEvent, x: Float, y: Float): Unit = startGame()
    })

    // Sélecteurs difficulté & instrument
    difficultyBox.setItems("1 - Easy", "2 - Normal", "3 - Hard", "4 - Expert")
    difficultyBox.setSize(300, 40); difficultyBox.setPosition(260, 130)
    instrumentBox.setSize(300, 40); instrumentBox.setPosition(260, 70)

    // Ajout de tous les widgets à la scène
    Seq(title, username, password, loginBtn,
      songsBox, difficultyBox, instrumentBox, playBtn).foreach(stage.addActor)
  }

  // ────────────────────────────────────────────
  //  Authentification
  // ────────────────────────────────────────────
  private def doLogin(): Unit = {
    val user = username.getText; val pass = password.getText
    loginBtn.setDisabled(true)

    RhythmApi.login(user, pass).foreach { tok =>
      Session.token = Some(tok)
      fetchSongs().foreach { list =>
        songs = list
        Gdx.app.postRunnable { () =>
          songsBox.setItems(songs: _*)
          updatePlayButtonState(); loginBtn.setDisabled(false)
        }
      }
    }
  }

  /** Tentative d'inscription puis connexion automatique si succès. */
  private def doRegister(): Unit = {
    val user = username.getText; val pass = password.getText
    val success = RhythmApi.register(user, pass)

    Gdx.app.postRunnable { () =>
      if (success) {
        println("✅ Registration successful"); doLogin()
      } else println("❌ Registration failed")
    }
  }

  /** Active / désactive le bouton Play selon login + liste chargée. */
  private def updatePlayButtonState(): Unit =
    playBtn.setDisabled(Session.token.isEmpty || songs.isEmpty)

  // ────────────────────────────────────────────
  //  Chargement des instruments pour la chanson choisie
  // ────────────────────────────────────────────
  private def populateInstruments(song: String): Unit = {
    instrumentBox.setItems("Loading…")

    Future {
      val midiPath = downloadMidi(song)
      val seq      = javax.sound.midi.MidiSystem.getSequence(new java.io.File(midiPath))
      InstrumentExtractor.extractInstruments(seq)
        .map(i => s"Ch${i.channel}: ${i.name}").toArray
    }.foreach { entries =>
      Gdx.app.postRunnable { () =>
        if (entries.nonEmpty) instrumentBox.setItems(entries: _*)
        else instrumentBox.setItems("No instruments found")
        instrumentBox.setSelectedIndex(0)
      }
    }
  }

  // ────────────────────────────────────────────
  //  Lancement de la partie
  // ────────────────────────────────────────────
  private def startGame(): Unit = {
    val tok        = Session.token.getOrElse(return)
    val song       = Option(songsBox.getSelected).getOrElse(return)
    val difficulty = difficultyBox.getSelected.charAt(0).asDigit

    // Extraction du numéro de canal MIDI depuis la chaîne « ChX: name »
    val channel: Int = Option(instrumentBox.getSelected).flatMap { sel =>
      sel.split(":", 2).headOption.flatMap(_.stripPrefix("Ch").toIntOption)
    }.getOrElse(0)

    // Téléchargement éventuel hors thread UI puis bascule vers le gameplay
    Future(downloadMidi(song)).foreach { _ =>
      Gdx.app.postRunnable(() =>
        app.switchScreen(new GameplayScreen(app, tok, song, difficulty, channel))
      )
    }
  }

  // ────────────────────────────────────────────
  //  Téléchargement local du fichier MIDI (cache data/tmp)
  // ────────────────────────────────────────────
  private def downloadMidi(song: String): String = {
    val dstDir = Paths.get("data/tmp"); Files.createDirectories(dstDir)
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

  // ────────────────────────────────────────────
  //  Récupération de la liste des chansons depuis l'API
  // ────────────────────────────────────────────
  private def fetchSongs(): Future[Array[String]] = Future {
    val url  = new URL("https://midis.triceratops.ch/list")
    val json = scala.io.Source.fromInputStream(url.openStream()).mkString
    implicit val formats: Formats = DefaultFormats
    parse(json).extract[Array[String]]
  }
}
