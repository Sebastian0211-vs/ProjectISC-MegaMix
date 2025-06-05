//package ch.hevs.gdx2d.hello
//
//import ch.hevs.gdx2d.desktop.PortableApplication
//import ch.hevs.gdx2d.lib.GdxGraphics
//import ch.hevs.gdx2d.lib.utils.Logger
//import com.badlogic.gdx.Gdx
//import com.badlogic.gdx.graphics.Color
//import com.badlogic.gdx.scenes.scene2d.InputEvent
//import com.badlogic.gdx.scenes.scene2d.Stage
//import com.badlogic.gdx.scenes.scene2d.ui.Skin
//import com.badlogic.gdx.scenes.scene2d.ui.TextButton
//import com.badlogic.gdx.scenes.scene2d.ui.TextField
//import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener
//import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
//
///**
// * Very simple UI demonstration
// *
// * @author Pierre-André Mudry (mui)
// * @version 1.1
// */
//object DemoGUI {
//  def main(args: Array[String]): Unit = {
//    new DemoGUI
//  }
//}
//
//class DemoGUI extends PortableApplication {
//  private[menus] var skin: Skin = null
//  private[menus] var stage: Stage = null
//  private[menus] var newGameButton: TextButton = null
//  private[menus] var quitGameButton: TextButton = null
//  private[menus] var textArea: TextField = null
//
//  override def onInit(): Unit = {
//    val buttonWidth = 180
//    val buttonHeight = 30
//    setTitle("GUI demonstration")
//    stage = new Stage
//    Gdx.input.setInputProcessor(stage) // Make the stage consume events
//
//    // Load the default skin (which can be configured in the JSON file)
//    skin = new Skin(Gdx.files.internal("data/ui/uiskin.json"))
//    newGameButton = new TextButton("Click me", skin) // Use the initialized skin
//
//    newGameButton.setWidth(buttonWidth)
//    newGameButton.setHeight(buttonHeight)
//    quitGameButton = new TextButton("Useless button", skin) // Use the initialized skin
//
//    quitGameButton.setWidth(buttonWidth)
//    quitGameButton.setHeight(buttonHeight)
//    newGameButton.setPosition(Gdx.graphics.getWidth / 2 - buttonWidth / 2, (Gdx.graphics.getHeight * .6).toInt)
//    quitGameButton.setPosition(Gdx.graphics.getWidth / 2 - buttonWidth / 2, (Gdx.graphics.getHeight * .7).toInt)
//    textArea = new TextField("Enter some text...", skin)
//    textArea.setWidth(buttonWidth)
//    textArea.setPosition(Gdx.graphics.getWidth / 2 - buttonWidth / 2, (Gdx.graphics.getHeight * .4).toInt)
//    textArea.setTextFieldListener(new TextField.TextFieldListener() {
//      override def keyTyped(textField: TextField, key: Char): Unit = {
//        textArea.setSelection(0, 0)
//        // When you press 'enter', do something
//        if (key == 13) Logger.log("You have typed " + textArea.getText)
//      }
//    })
//
//    /**
//     * Adds the buttons to the stage
//     */
//    stage.addActor(newGameButton)
//    stage.addActor(quitGameButton)
//    stage.addActor(textArea)
//
//    /**
//     * Register listener
//     */
//    newGameButton.addListener(new ClickListener() {
//      override def clicked(event: InputEvent, x: Float, y: Float): Unit = {
//        super.clicked(event, x, y)
//        if (newGameButton.isChecked) Logger.log("Button is checked")
//        else Logger.log("Button is not checked")
//      }
//    })
//  }
//
//  override def onGraphicRender(g: GdxGraphics): Unit = {
//    g.clear(Color.BLACK)
//    // This is required for having the GUI work properly
//    stage.act()
//    stage.draw()
//    g.drawStringCentered(getWindowHeight / 4, "Button status " + newGameButton.isChecked)
//    g.drawSchoolLogo()
//    g.drawFPS()
//  }
//
//  override def onDispose(): Unit = {
//    super.onDispose()
//    stage.dispose()
//    skin.dispose()
//  }
//}
